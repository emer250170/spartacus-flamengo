import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  OnDestroy,
  OnInit,
  Optional,
} from '@angular/core';
import { WindowRef } from '@spartacus/core';
import { OrderFacade } from '@spartacus/order/root';
import { Subscription, of } from 'rxjs';
import { distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioPayment, SocioPaymentService } from './socio-payment.service';
import { qrPattern } from './socio-qr';

/**
 * Pix ou boleto do plano (item 7): QR Code e Pix copia e cola, ou a linha
 * digitável do boleto, com o vencimento e "o plano ativa quando o pagamento for
 * confirmado"; depois da confirmação, "Pagamento confirmado". Na confirmação do
 * pedido (outlet antes do agradecimento) lê o pedido feito; na área do sócio
 * recebe o pagamento pendente por [payment]. Os códigos são fictícios.
 */
@Component({
  selector: 'fla-socio-payment-status',
  templateUrl: './socio-payment-status.component.html',
  styleUrls: ['./socio-payment.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioPaymentStatusComponent implements OnInit, OnDestroy {
  @Input() payment?: SocioPayment;
  copied = false;
  qr: boolean[][] = [];

  protected subscription = new Subscription();

  constructor(
    protected socioPayment: SocioPaymentService,
    protected i18n: FlaI18nService,
    protected winRef: WindowRef,
    protected cd: ChangeDetectorRef,
    @Optional() protected orderFacade?: OrderFacade
  ) {}

  get visible(): boolean {
    const p = this.payment;
    return !!p?.method && !!p.orderCode && p.method !== 'CARTAO_MENSAL';
  }

  get upfront(): boolean {
    return this.socioPayment.isUpfront(this.payment?.method);
  }

  /** Pedido sem plano (item 14): textos da loja ("liberar o pedido") */
  key(base: string): string {
    return this.payment?.planCode ? base : base + '.store';
  }

  ngOnInit(): void {
    if (this.payment) {
      this.qr = qrPattern(this.payment.code ?? this.payment.orderCode ?? '');
      return;
    }
    if (!this.orderFacade) {
      return;
    }
    this.subscription.add(
      this.orderFacade
        .getOrderDetails()
        .pipe(
          map((order) => order?.code),
          distinctUntilChanged(),
          switchMap((code) =>
            code ? this.socioPayment.orderStatus(code) : of(undefined)
          )
        )
        .subscribe((p) => {
          this.payment = p;
          this.qr = qrPattern(p?.code ?? p?.orderCode ?? '');
          this.cd.markForCheck();
        })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  t(key: string, params?: Record<string, unknown>): string {
    return this.i18n.t(key, params);
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  when(date?: Date): string {
    if (!date) {
      return '-';
    }
    const two = (n: number) => String(n).padStart(2, '0');
    return `${two(date.getDate())}/${two(date.getMonth() + 1)}/${date.getFullYear()} ${two(date.getHours())}:${two(date.getMinutes())}`;
  }

  copy(): void {
    const code = this.payment?.code;
    const clipboard = this.winRef.nativeWindow?.navigator?.clipboard;
    if (!code || !clipboard) {
      return;
    }
    clipboard.writeText(code).then(() => {
      this.copied = true;
      this.cd.markForCheck();
    });
  }

  /** "Cartão em 10x de R$ 80,52" para o ano inteiro no cartão */
  cardLine(): string {
    const p = this.payment;
    if (!p) {
      return '';
    }
    const n = p.installments ?? 1;
    return n === 1
      ? this.t('spay.r.annual1', {
          name: this.t('spay.m.CARTAO_ANUAL'),
          amount: this.brl(p.total),
        })
      : this.t('spay.r.annualN', {
          name: this.t('spay.m.CARTAO_ANUAL'),
          n,
          amount: this.brl(Math.round((p.total / n) * 100) / 100),
          total: this.brl(p.total),
        });
  }
}
