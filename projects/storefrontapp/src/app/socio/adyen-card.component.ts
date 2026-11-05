import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { RoutingService, WindowRef } from '@spartacus/core';
import { OrderFacade } from '@spartacus/order/root';
import { Subscription } from 'rxjs';
import { distinctUntilChanged, filter, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  AdyenCardResult,
  AdyenCardService,
  AdyenCardSession,
} from './adyen-card.service';
import { SocioPayment, SocioPaymentService } from './socio-payment.service';

/** Classe no body enquanto o cartão é pela Adyen: esconde o "Finalizar pedido" do Spartacus */
const ADYEN_CLASS = 'fla-pay-adyen';

/**
 * Cartão pela Adyen na revisão do pedido (05/11), no lugar do "Finalizar pedido"
 * do Spartacus: Termos e Condições, o formulário seguro de cartão da Adyen
 * (número, validade, código, nome; parcelas no plano anual; 3D Secure) e o botão
 * "Pagar e finalizar". Antes de cobrar, o servidor confere o carrinho com as
 * regras do pedido; com o pagamento autorizado, o pedido fecha pelo servidor e a
 * loja segue para a confirmação. Só aparece com a Adyen ligada e o cartão na
 * lista flamengo.pagamento.adyen.meios (scheme).
 */
@Component({
  selector: 'fla-adyen-card',
  templateUrl: './adyen-card.component.html',
  styleUrls: ['./socio-payment.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdyenCardComponent implements OnInit, OnDestroy {
  @ViewChild('cardHost', { static: false }) cardHost?: ElementRef<HTMLElement>;

  visible = false;
  loading = false;
  busy = false;
  terms = false;
  termsTouched = false;
  error = '';
  amount = 0;

  protected cartId?: string;
  protected session?: AdyenCardSession;
  protected card?: any;
  protected subscription = new Subscription();

  constructor(
    protected socioPayment: SocioPaymentService,
    protected adyen: AdyenCardService,
    protected activeCart: ActiveCartFacade,
    protected orderFacade: OrderFacade,
    protected routing: RoutingService,
    protected i18n: FlaI18nService,
    protected winRef: WindowRef,
    protected zone: NgZone,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.activeCart
        .getActiveCartId()
        .pipe(
          filter((id): id is string => !!id),
          distinctUntilChanged(),
          switchMap((id) => {
            this.cartId = id;
            return this.socioPayment.options(id);
          })
        )
        .subscribe((p) => this.apply(p))
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.unmount();
    this.mark(false);
  }

  t(key: string, params?: Record<string, unknown>): string {
    return this.i18n.t(key, params);
  }

  payText(): string {
    return this.t('acard.pay', { amount: this.i18n.money(this.amount / 100) });
  }

  setTerms(checked: boolean): void {
    this.terms = checked;
    this.termsTouched = true;
    this.cd.markForCheck();
  }

  /** Confere o carrinho no servidor e manda o cartão para a Adyen */
  pay(): void {
    if (this.busy || !this.card || !this.cartId) {
      return;
    }
    this.termsTouched = true;
    this.error = '';
    if (!this.terms) {
      this.cd.markForCheck();
      return;
    }
    if (!this.card.isValid) {
      this.card.showValidation?.();
      this.cd.markForCheck();
      return;
    }
    this.busy = true;
    this.cd.markForCheck();
    this.adyen.check(this.cartId).subscribe({
      next: () => this.card.submit(),
      error: (e: Error) => this.fail(e.message),
    });
  }

  protected apply(p: SocioPayment): void {
    const show = this.socioPayment.isAdyenCard(p);
    this.mark(show);
    if (show === this.visible) {
      return;
    }
    this.visible = show;
    this.cd.markForCheck();
    if (show) {
      this.start();
    } else {
      this.unmount();
    }
  }

  /** Cria a sessão na Adyen e monta o formulário de cartão (ou conclui a volta do 3D Secure) */
  protected start(): void {
    if (!this.cartId) {
      return;
    }
    this.loading = true;
    this.error = '';
    this.cd.markForCheck();
    const params = new URLSearchParams(
      this.winRef.nativeWindow?.location?.search ?? ''
    );
    const redirectResult = params.get('redirectResult');
    const returningSession = params.get('sessionId');
    this.adyen
      .session(this.cartId)
      .pipe(take(1))
      .subscribe({
        next: (s) => {
          this.session = s;
          this.amount = s.amount;
          this.loadStyles();
          this.zone.runOutsideAngular(() =>
            this.mount(s, redirectResult, returningSession)
          );
        },
        error: (e: Error) => this.fail(e.message, false),
      });
  }

  protected async mount(
    s: AdyenCardSession,
    redirectResult: string | null,
    returningSession: string | null
  ): Promise<void> {
    try {
      const { AdyenCheckout, Card } = await import('@adyen/adyen-web');
      // volta do 3D Secure por redirecionamento: conclui a sessão de onde o torcedor saiu
      const returning = !!redirectResult && !!returningSession;
      const checkout = await AdyenCheckout({
        session: returning
          ? { id: returningSession as string }
          : { id: s.id, sessionData: s.sessionData },
        clientKey: s.clientKey,
        environment: s.environment,
        locale: this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US',
        countryCode: 'BR',
        onPaymentCompleted: (result: any) =>
          this.zone.run(() =>
            this.completed(
              returning ? (returningSession as string) : s.id,
              result
            )
          ),
        onPaymentFailed: (result: any) =>
          this.zone.run(() =>
            this.fail(
              this.t('acard.refused', { code: result?.resultCode ?? '-' })
            )
          ),
        onError: (e: any) =>
          this.zone.run(() => {
            // o torcedor fechou o 3D Secure: só libera o botão de novo
            if (e?.name === 'CANCEL') {
              this.busy = false;
              this.cd.markForCheck();
              return;
            }
            this.fail(e?.message || this.t('acard.error'));
          }),
      });
      if (returning) {
        this.zone.run(() => {
          this.busy = true;
          this.loading = false;
          this.cd.markForCheck();
        });
        checkout.submitDetails({ details: { redirectResult } });
        return;
      }
      const host = this.cardHost?.nativeElement;
      if (!host) {
        return;
      }
      this.unmount();
      this.card = new Card(checkout, {
        showPayButton: false,
        hasHolderName: true,
        holderNameRequired: true,
      }).mount(host);
      this.zone.run(() => {
        this.loading = false;
        this.cd.markForCheck();
      });
    } catch (e: any) {
      this.zone.run(() => this.fail(e?.message || this.t('acard.error'), false));
    }
  }

  /** Pagamento autorizado: fecha o pedido pelo "Finalizar pedido" do Spartacus (o adaptador usa o resultado) */
  protected completed(sessionId: string, result: any): void {
    if (!this.cartId) {
      return;
    }
    if (result?.resultCode !== 'Authorised') {
      this.fail(this.t('acard.refused', { code: result?.resultCode ?? '-' }));
      return;
    }
    const r: AdyenCardResult = {
      sessionId,
      sessionResult: result.sessionResult,
      resultCode: result.resultCode,
    };
    this.adyen.setResult(this.cartId, r);
    this.busy = true;
    this.cd.markForCheck();
    this.orderFacade.placeOrder(true).subscribe({
      next: () => {
        this.mark(false);
        this.routing.go({ cxRoute: 'orderConfirmation' });
      },
      error: (e: Error) =>
        // o cartão foi autorizado, mas o pedido não fechou: o atendimento estorna pela Adyen
        this.fail(this.t('acard.orderError', { message: e?.message ?? '' }), false),
    });
  }

  protected fail(message: string, retry = true): void {
    this.busy = false;
    this.loading = false;
    this.error = message || this.t('acard.error');
    this.cd.markForCheck();
    if (retry && this.session && this.cartId) {
      // a mesma sessão aceita outra tentativa; o formulário volta a ficar pronto
      this.card?.setStatus?.('ready');
    }
  }

  protected unmount(): void {
    try {
      this.card?.unmount?.();
    } catch {
      // componente já removido
    }
    this.card = undefined;
  }

  protected mark(on: boolean): void {
    const body = this.winRef.document?.body;
    body?.classList.toggle(ADYEN_CLASS, on);
    if (on) {
      this.socioPayment.markUpfront(true);
    }
  }

  /** CSS da Adyen (gerado pelo build como adyen.css, carregado só aqui) */
  protected loadStyles(): void {
    const doc = this.winRef.document;
    if (!doc || doc.getElementById('fla-adyen-css')) {
      return;
    }
    const link = doc.createElement('link');
    link.id = 'fla-adyen-css';
    link.rel = 'stylesheet';
    link.href = new URL('adyen.css', doc.baseURI).toString();
    doc.head.appendChild(link);
  }
}
