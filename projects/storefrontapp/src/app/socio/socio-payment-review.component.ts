import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { Subscription } from 'rxjs';
import { distinctUntilChanged, filter, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioPayment, SocioPaymentService } from './socio-payment.service';

/**
 * Revisão do pedido com plano (item 7): a forma de pagamento escolhida ("Pix à
 * vista, R$ 805,20" / "Cartão em 10x de R$ 80,52"). Com Pix ou boleto, avisa que o
 * código aparece depois de finalizar e que o plano ativa com o pagamento, e esconde
 * o cartão da revisão. Antes do Finalizar pedido (outlet CheckoutPlaceOrder).
 */
@Component({
  selector: 'fla-socio-payment-review',
  templateUrl: './socio-payment-review.component.html',
  styleUrls: ['./socio-payment.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioPaymentReviewComponent implements OnInit, OnDestroy {
  opts?: SocioPayment;

  protected subscription = new Subscription();

  constructor(
    protected payment: SocioPaymentService,
    protected activeCart: ActiveCartFacade,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  get visible(): boolean {
    // sem plano (item 14): só Pix ou boleto; o cartão já aparece na revisão do Spartacus
    return (
      !!this.opts?.enabled &&
      !!this.opts.method &&
      (!!this.opts.planCode || this.payment.isUpfront(this.opts.method))
    );
  }

  get store(): boolean {
    return !this.opts?.planCode;
  }

  title(): string {
    return this.store
      ? this.i18n.t('spay.r.title.store')
      : this.i18n.t('spay.r.title', { plan: this.opts?.planName });
  }

  note(): string {
    const pix = this.opts?.method === 'PIX';
    const key = pix ? 'spay.r.pixNote' : 'spay.r.boletoNote';
    return this.i18n.t(this.store ? key + '.store' : key);
  }

  get upfront(): boolean {
    return this.visible && this.payment.isUpfront(this.opts?.method);
  }

  ngOnInit(): void {
    this.subscription.add(
      this.activeCart
        .getActiveCartId()
        .pipe(
          filter((id): id is string => !!id),
          distinctUntilChanged(),
          switchMap((id) => this.payment.options(id))
        )
        .subscribe((opts) => {
          this.opts = opts;
          this.payment.markUpfront(this.upfront);
          this.cd.markForCheck();
        })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.payment.markUpfront(false);
  }

  /** Uma linha: forma e valor */
  line(): string {
    const o = this.opts;
    if (!o?.method) {
      return '';
    }
    const name = this.i18n.t('spay.m.' + o.method);
    if (this.store) {
      return this.i18n.t('spay.r.store', {
        name,
        amount: this.i18n.money(o.total),
      });
    }
    if (o.method === 'CARTAO_MENSAL') {
      return this.i18n.t('spay.r.monthly', {
        name,
        amount: this.i18n.money(o.monthly),
      });
    }
    if (o.method === 'CARTAO_ANUAL') {
      const n = o.installments ?? 1;
      return n === 1
        ? this.i18n.t('spay.r.annual1', {
            name,
            amount: this.i18n.money(o.annual),
          })
        : this.i18n.t('spay.r.annualN', {
            name,
            n,
            amount: this.i18n.money(Math.round((o.annual / n) * 100) / 100),
            total: this.i18n.money(o.annual),
          });
    }
    return this.i18n.t('spay.r.upfront', {
      name,
      amount: this.i18n.money(o.annual),
    });
  }
}
