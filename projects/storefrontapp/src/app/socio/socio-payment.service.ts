import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService, WindowRef } from '@spartacus/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

export type SocioPaymentMethod =
  | 'CARTAO_MENSAL'
  | 'CARTAO_ANUAL'
  | 'PIX'
  | 'BOLETO'
  /** 05/11: 1ª mensalidade pelo Pix (QR Code da Adyen) e as próximas pelo Pix Automático */
  | 'PIX_AUTOMATICO';

/** Forma de pagamento do plano (item 7): opções do carrinho ou situação do pedido */
export interface SocioPayment {
  enabled: boolean;
  planCode?: string;
  planName?: string;
  upfrontAllowed: boolean;
  /** Mensalidade (plano + convidados) e o ano inteiro (12 mensalidades) */
  monthly: number;
  months: number;
  annual: number;
  methods: SocioPaymentMethod[];
  maxInstallments: number;
  method?: SocioPaymentMethod;
  installments?: number;
  total: number;
  /** Desconto do Pix (06/11): percentual da promoção publicada e o total no Pix e nas outras formas */
  pixPercent?: number;
  pixTotal?: number;
  baseTotal?: number;
  orderCode?: string;
  orderDate?: Date;
  /** Pix e boleto: vencimento, código fictício e confirmação */
  due?: Date;
  code?: string;
  confirmedAt?: Date;
  awaiting: boolean;
  summary?: string;
  /** Formas que passam pela Adyen (05/11): hoje Pix e boleto; o boleto pede o CPF */
  adyenMethods: SocioPaymentMethod[];
  /** Pedido pago pela Adyen: pspReference, imagem do QR Code do Pix e PDF do boleto */
  provider?: 'adyen';
  pspReference?: string;
  qrImageUrl?: string;
  pdfUrl?: string;
}

const NONE: SocioPayment = {
  enabled: false,
  upfrontAllowed: false,
  monthly: 0,
  months: 12,
  annual: 0,
  methods: [],
  maxInstallments: 12,
  total: 0,
  awaiting: false,
  adyenMethods: [],
};

/** Chave do CPF do pagador na sessão da aba (por carrinho) */
const CPF_KEY = 'fla-payer-cpf:';

/** Classe no body enquanto o plano está com Pix/boleto: esconde o cartão do checkout */
const UPFRONT_CLASS = 'fla-pay-upfront';

/**
 * Pix e boleto à vista e plano aguardando pagamento (item 7 do plano de evolução).
 * Com plano no carrinho, o pagamento oferece cartão com a mensalidade (como
 * antes), cartão com o ano inteiro em parcelas, e Pix ou boleto com o ano
 * inteiro. Pix e boleto fecham o pedido sem cartão; o plano ativa quando o
 * clube confirma o pagamento. Tudo calculado no servidor (sociopayment).
 */
@Injectable({ providedIn: 'root' })
export class SocioPaymentService {
  /** Última forma conhecida por carrinho (o guard e o pedido consultam o servidor de novo) */
  protected methods = new Map<string, SocioPaymentMethod | undefined>();
  /**
   * CPF de quem paga o Pix/boleto da Adyen, por carrinho. Fica na memória e na sessão da aba
   * (06/11: recarregar o pagamento não pede o CPF de novo); some ao fechar a aba e ao fechar o pedido.
   */
  protected payerCpf = new Map<string, string>();

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected winRef: WindowRef
  ) {}

  /** O que o checkout oferece para o carrinho */
  options(cartId: string): Observable<SocioPayment> {
    return this.http.get<OccSocioPayment>(this.cartUrl(cartId)).pipe(
      map(toPayment),
      tap((p) => this.methods.set(cartId, p.method)),
      catchError(() => of(NONE))
    );
  }

  /** Escolhe a forma (e as parcelas no cartão com o ano inteiro); o servidor recalcula o carrinho */
  choose(
    cartId: string,
    method: SocioPaymentMethod,
    installments?: number
  ): Observable<SocioPayment> {
    return this.http
      .put<OccSocioPayment>(this.cartUrl(cartId), {
        method,
        installments: method === 'CARTAO_ANUAL' ? (installments ?? 1) : null,
      })
      .pipe(
        map(toPayment),
        tap((p) => this.methods.set(cartId, p.method)),
        catchError((e) => throwError(() => new Error(errorMessage(e))))
      );
  }

  /** Fecha o pedido com Pix ou boleto (sem cartão); boleto da Adyen leva o CPF do pagador */
  placeOrder(cartId: string): Observable<SocioPayment> {
    const cpf = this.payerCpfOf(cartId);
    return this.http
      .post<OccSocioPayment>(`${this.cartUrl(cartId)}/order`, cpf ? { cpf } : {})
      .pipe(
        map(toPayment),
        tap(() => {
          this.methods.delete(cartId);
          this.setPayerCpf(cartId, '');
        }),
        // guarda o status: recusa 400 o Spartacus já mostra sozinho
        catchError((e) =>
          throwError(() =>
            Object.assign(new Error(errorMessage(e)), { status: e?.status })
          )
        )
      );
  }

  /** Situação do pagamento de um pedido (Pix copia e cola, linha do boleto, vencimento) */
  orderStatus(orderCode: string): Observable<SocioPayment> {
    return this.http
      .get<OccSocioPayment>(
        `${this.occEndpoints.getBaseUrl()}/users/current/orders/${encodeURIComponent(orderCode)}/sociopayment`
      )
      .pipe(
        map(toPayment),
        catchError(() => of(NONE))
      );
  }

  /** Pix ou boleto mais novo ainda não pago (área do sócio) */
  pending(): Observable<SocioPayment> {
    return this.http
      .get<OccSocioPayment>(
        `${this.occEndpoints.getBaseUrl()}/users/current/sociopayment`
      )
      .pipe(
        map(toPayment),
        catchError(() => of(NONE))
      );
  }

  isUpfront(method?: SocioPaymentMethod): boolean {
    return method === 'PIX' || method === 'BOLETO' || method === 'PIX_AUTOMATICO';
  }

  /** Pix à vista ou Pix Automático: QR Code e copia e cola */
  isPix(method?: SocioPaymentMethod): boolean {
    return method === 'PIX' || method === 'PIX_AUTOMATICO';
  }

  /**
   * Cartão pela Adyen (05/11): a forma escolhida é cartão (sem forma = cartão) e a Adyen
   * cobra o cartão; o formulário seguro dela aparece na revisão, no lugar do cartão salvo.
   */
  isAdyenCard(p?: SocioPayment, method?: SocioPaymentMethod): boolean {
    const m = method ?? p?.method ?? 'CARTAO_MENSAL';
    return !!p?.enabled && !this.isUpfront(m) && !!p.adyenMethods.includes(m);
  }

  /** Boleto pela Adyen: precisa do CPF de quem paga */
  needsCpf(p?: SocioPayment, method?: SocioPaymentMethod): boolean {
    const m = method ?? p?.method;
    // boleto e Pix pela Adyen: o CPF de quem paga (o Pix Automático exige)
    return (
      (m === 'BOLETO' || m === 'PIX' || m === 'PIX_AUTOMATICO') &&
      !!p?.adyenMethods.includes(m)
    );
  }

  setPayerCpf(cartId: string, cpf: string): void {
    const digits = (cpf ?? '').replace(/\D/g, '');
    if (digits) {
      this.payerCpf.set(cartId, digits);
    } else {
      this.payerCpf.delete(cartId);
    }
    try {
      const store = this.winRef.nativeWindow?.sessionStorage;
      if (digits) {
        store?.setItem(CPF_KEY + cartId, digits);
      } else {
        store?.removeItem(CPF_KEY + cartId);
      }
    } catch {
      // sem sessionStorage (aba privada, bloqueio): fica só na memória
    }
  }

  payerCpfOf(cartId: string): string {
    const known = this.payerCpf.get(cartId);
    if (known) {
      return known;
    }
    try {
      const saved = this.winRef.nativeWindow?.sessionStorage?.getItem(CPF_KEY + cartId) ?? '';
      if (saved) {
        this.payerCpf.set(cartId, saved);
      }
      return saved;
    } catch {
      return '';
    }
  }

  /** Esconde (ou mostra de volta) o cartão do Spartacus no pagamento e na revisão */
  markUpfront(upfront: boolean): void {
    this.winRef.document?.body?.classList.toggle(UPFRONT_CLASS, upfront);
  }

  protected cartUrl(cartId: string): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/carts/${encodeURIComponent(cartId)}/sociopayment`;
  }
}

interface OccSocioPayment {
  enabled?: boolean;
  planCode?: string;
  planName?: string;
  upfrontAllowed?: boolean;
  monthly?: number;
  months?: number;
  annual?: number;
  methods?: SocioPaymentMethod[];
  maxInstallments?: number;
  method?: SocioPaymentMethod;
  installments?: number;
  total?: number;
  pixPercent?: number;
  pixTotal?: number;
  baseTotal?: number;
  orderCode?: string;
  orderDate?: string;
  due?: string;
  code?: string;
  confirmedAt?: string;
  awaiting?: boolean;
  summary?: string;
  adyenMethods?: SocioPaymentMethod[];
  provider?: string;
  pspReference?: string;
  qrImageUrl?: string;
  pdfUrl?: string;
}

function toPayment(r: OccSocioPayment): SocioPayment {
  const date = (v?: string) => (v ? new Date(v) : undefined);
  return {
    enabled: !!r?.enabled,
    planCode: r?.planCode ?? undefined,
    planName: r?.planName ?? undefined,
    upfrontAllowed: !!r?.upfrontAllowed,
    monthly: r?.monthly ?? 0,
    months: r?.months ?? 12,
    annual: r?.annual ?? 0,
    methods: r?.methods ?? [],
    maxInstallments: r?.maxInstallments ?? 12,
    method: r?.method ?? undefined,
    installments: r?.installments ?? undefined,
    total: r?.total ?? 0,
    pixPercent: r?.pixPercent || undefined,
    pixTotal: r?.pixTotal ?? undefined,
    baseTotal: r?.baseTotal ?? undefined,
    orderCode: r?.orderCode ?? undefined,
    orderDate: date(r?.orderDate),
    due: date(r?.due),
    code: r?.code ?? undefined,
    confirmedAt: date(r?.confirmedAt),
    awaiting: !!r?.awaiting,
    summary: r?.summary ?? undefined,
    adyenMethods: r?.adyenMethods ?? [],
    provider: r?.provider === 'adyen' ? 'adyen' : undefined,
    pspReference: r?.pspReference ?? undefined,
    qrImageUrl: safeUrl(r?.qrImageUrl),
    pdfUrl: safeUrl(r?.pdfUrl),
  };
}

/** Só links https (vêm da Adyen) */
function safeUrl(v?: string): string | undefined {
  return v && /^https:\/\//i.test(v) ? v : undefined;
}

function errorMessage(e: any): string {
  return (
    e?.error?.errors?.[0]?.message ??
    e?.message ??
    'Não foi possível concluir o pagamento.'
  );
}
