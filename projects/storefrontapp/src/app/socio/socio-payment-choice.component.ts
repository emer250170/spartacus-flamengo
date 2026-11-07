import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { RoutingService } from '@spartacus/core';
import { Subscription } from 'rxjs';
import { distinctUntilChanged, filter, switchMap, tap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { isValidCpf, maskCpf } from './socio-guests.service';
import {
  SocioPayment,
  SocioPaymentMethod,
  SocioPaymentService,
} from './socio-payment.service';

/**
 * Pagamento do checkout com plano de sócio no carrinho (item 7 do plano de
 * evolução): cartão com a mensalidade (como antes), cartão com o ano inteiro em
 * parcelas, Pix ou boleto com o ano inteiro. Entra antes do pagamento do
 * Spartacus pelo outlet do componente CMS CheckoutPaymentDetails. Com Pix ou
 * boleto, o cartão some (classe no body) e "Continuar" leva à revisão. Sem plano
 * no carrinho, aparece só com a operação do pedido da loja ligada (item 14:
 * cartão, Pix ou boleto pelo total); com as chaves desligadas, não aparece nada.
 */
@Component({
  selector: 'fla-socio-payment-choice',
  templateUrl: './socio-payment-choice.component.html',
  styleUrls: ['./socio-payment.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioPaymentChoiceComponent implements OnInit, OnDestroy {
  opts?: SocioPayment;
  selected: SocioPaymentMethod = 'CARTAO_MENSAL';
  installments = 12;
  busy = false;
  error = '';
  /** Boleto pela Adyen (05/11): CPF de quem paga */
  cpf = '';
  cpfTouched = false;

  protected cartId?: string;
  protected subscription = new Subscription();

  constructor(
    protected payment: SocioPaymentService,
    protected activeCart: ActiveCartFacade,
    protected routing: RoutingService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  get visible(): boolean {
    return !!this.opts?.enabled && this.opts.methods.length > 0;
  }

  /** Carrinho sem plano (item 14): textos da loja e o total do carrinho */
  get store(): boolean {
    return !this.opts?.planCode;
  }

  title(): string {
    return this.store
      ? this.t('spay.title.store')
      : this.t('spay.title', { plan: this.opts?.planName });
  }

  sub(): string {
    return this.t(this.store ? 'spay.sub.store' : 'spay.sub');
  }

  name(m: SocioPaymentMethod): string {
    return this.t(
      this.store && m === 'CARTAO_MENSAL' ? 'spay.m.store.' + m : 'spay.m.' + m
    );
  }

  hint(m: SocioPaymentMethod): string {
    // cartão da loja pela Adyen (05/11): não há cartão salvo para escolher abaixo
    if (
      this.store &&
      m === 'CARTAO_MENSAL' &&
      this.payment.isAdyenCard(this.opts, m)
    ) {
      return this.t('spay.h.store.adyenCard');
    }
    return this.t(this.store ? 'spay.h.store.' + m : 'spay.h.' + m);
  }

  /** Parcelas do ano no cartão: o cartão vem abaixo, ou na revisão quando é pela Adyen */
  cardWhere(): string {
    return this.t(this.adyenCard ? 'spay.cardReview' : 'spay.cardBelow');
  }

  get upfront(): boolean {
    return this.visible && this.payment.isUpfront(this.selected);
  }

  /** Cartão pela Adyen (05/11): o cartão é informado na revisão, no formulário da Adyen */
  get adyenCard(): boolean {
    return this.visible && this.payment.isAdyenCard(this.opts, this.selected);
  }

  /** Sem o cartão do Spartacus neste passo: Pix, boleto ou cartão pela Adyen */
  get direct(): boolean {
    return this.upfront || this.adyenCard;
  }

  /** Pix à vista ou Pix Automático */
  get isPix(): boolean {
    return this.payment.isPix(this.selected);
  }

  /** Boleto pela Adyen: pede o CPF de quem paga */
  get needsCpf(): boolean {
    return this.upfront && this.payment.needsCpf(this.opts, this.selected);
  }

  /** Pix/boleto pela Adyen com o CPF do cadastro (07/11): mostra o CPF mascarado, sem campo */
  get registeredCpf(): string | null {
    return this.upfront &&
      this.payment.usesRegisteredCpf(this.opts, this.selected)
      ? (this.opts?.payerCpf ?? null)
      : null;
  }

  get cpfOk(): boolean {
    return isValidCpf(this.cpf);
  }

  /** Pix/boleto pela Adyen: texto real; sem a Adyen, o simulado de sempre */
  nextText(): string {
    if (this.adyenCard) {
      return this.t('spay.cardNext.adyen');
    }
    // 07/11: com o CPF do cadastro, o texto não pede para informar o CPF
    const onFile = this.registeredCpf ? '.cpfOnFile' : '';
    if (this.selected === 'PIX_AUTOMATICO') {
      return this.t('spay.pixAutoNext' + onFile);
    }
    const pix = this.selected === 'PIX';
    const adyen = !!this.opts?.adyenMethods.includes(this.selected);
    return this.t(
      (pix ? 'spay.pixNext' : 'spay.boletoNext') +
        (adyen ? '.adyen' + onFile : '')
    );
  }

  setCpf(value: string): void {
    this.cpf = maskCpf(value);
    if (this.cartId) {
      this.payment.setPayerCpf(this.cartId, this.cpfOk ? this.cpf : '');
    }
    this.cd.markForCheck();
  }

  get parcels(): number[] {
    const max = this.opts?.maxInstallments ?? 12;
    return Array.from({ length: max }, (_, i) => i + 1);
  }

  ngOnInit(): void {
    this.subscription.add(
      this.activeCart
        .getActiveCartId()
        .pipe(
          filter((id): id is string => !!id),
          distinctUntilChanged(),
          tap((id) => {
            this.cartId = id;
            this.cpf = maskCpf(this.payment.payerCpfOf(id));
          }),
          switchMap((id) => this.payment.options(id))
        )
        .subscribe((opts) => this.apply(opts))
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    // a revisão marca de novo se o plano estiver com Pix/boleto
    this.payment.markUpfront(false);
  }

  t(key: string, params?: Record<string, unknown>): string {
    return this.i18n.t(key, params);
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  /** "R$ 67,10 hoje e depois todo mês" / "R$ 805,20 em até 12x" / "R$ 805,20 à vista" */
  /** Selo "5% off" no card do Pix (loja, com a promoção do Pix publicada) */
  pixOff(m: SocioPaymentMethod): string {
    const pct = this.opts?.pixPercent;
    if (!this.store || m !== 'PIX' || !pct) {
      return '';
    }
    return this.t('spay.pixOff', {
      p: String(Math.round(pct * 100) / 100).replace('.', ','),
    });
  }

  amount(m: SocioPaymentMethod): string {
    const o = this.opts;
    if (!o) {
      return '';
    }
    if (this.store) {
      // com desconto no Pix (06/11): o Pix com o desconto e as outras formas sem ele
      const total = o.pixPercent
        ? m === 'PIX'
          ? (o.pixTotal ?? o.total)
          : (o.baseTotal ?? o.total)
        : o.total;
      return this.t(
        m === 'CARTAO_MENSAL' ? 'spay.a.store.CARD' : 'spay.a.store.UPFRONT',
        { amount: this.brl(total) }
      );
    }
    switch (m) {
      case 'CARTAO_MENSAL':
        return this.t('spay.a.CARTAO_MENSAL', { amount: this.brl(o.monthly) });
      case 'PIX_AUTOMATICO':
        return this.t('spay.a.PIX_AUTOMATICO', { amount: this.brl(o.monthly) });
      case 'CARTAO_ANUAL':
        return this.t('spay.a.CARTAO_ANUAL', {
          amount: this.brl(o.annual),
          n: o.maxInstallments,
        });
      default:
        return this.t('spay.a.UPFRONT', { amount: this.brl(o.annual) });
    }
  }

  parcelText(n: number): string {
    const annual = this.opts?.annual ?? 0;
    return n === 1
      ? this.t('spay.parcel1', { amount: this.brl(annual) })
      : this.t('spay.parcelN', {
          n,
          amount: this.brl(Math.round((annual / n) * 100) / 100),
        });
  }

  pick(m: SocioPaymentMethod): void {
    if (m === this.selected || this.busy) {
      return;
    }
    this.save(m, m === 'CARTAO_ANUAL' ? this.installments : undefined);
  }

  setInstallments(value: string): void {
    const n = Number(value);
    if (!n || n === this.installments) {
      return;
    }
    this.installments = n;
    this.save('CARTAO_ANUAL', n);
  }

  back(): void {
    this.routing.go({ cxRoute: 'checkoutDeliveryMode' });
  }

  next(): void {
    if (this.needsCpf && !this.cpfOk) {
      this.cpfTouched = true;
      return;
    }
    this.routing.go({ cxRoute: 'checkoutReviewOrder' });
  }

  protected save(m: SocioPaymentMethod, installments?: number): void {
    if (!this.cartId) {
      return;
    }
    const previous = this.selected;
    this.selected = m;
    this.busy = true;
    this.error = '';
    this.payment.markUpfront(
      this.payment.isUpfront(m) || this.payment.isAdyenCard(this.opts, m)
    );
    this.cd.markForCheck();
    this.payment.choose(this.cartId, m, installments).subscribe({
      next: (opts) => {
        this.busy = false;
        this.apply(opts);
        // totais do carrinho mudam (ano inteiro = 12 mensalidades)
        this.activeCart.reloadActiveCart();
      },
      error: (e: Error) => {
        this.busy = false;
        this.selected = previous;
        this.payment.markUpfront(
          this.payment.isUpfront(previous) ||
            this.payment.isAdyenCard(this.opts, previous)
        );
        this.error = e.message || this.t('spay.error');
        this.cd.markForCheck();
      },
    });
  }

  protected apply(opts: SocioPayment): void {
    this.opts = opts;
    this.selected = opts.method ?? 'CARTAO_MENSAL';
    this.installments =
      opts.method === 'CARTAO_ANUAL'
        ? (opts.installments ?? 1)
        : opts.maxInstallments;
    this.payment.markUpfront(this.direct);
    this.cd.markForCheck();
  }
}
