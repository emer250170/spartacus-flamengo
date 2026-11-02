import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService, WindowRef } from '@spartacus/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

export type SocioPaymentMethod =
  | 'CARTAO_MENSAL'
  | 'CARTAO_ANUAL'
  | 'PIX'
  | 'BOLETO';

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
  orderCode?: string;
  orderDate?: Date;
  /** Pix e boleto: vencimento, código fictício e confirmação */
  due?: Date;
  code?: string;
  confirmedAt?: Date;
  awaiting: boolean;
  summary?: string;
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
};

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

  /** Fecha o pedido com Pix ou boleto (sem cartão) */
  placeOrder(cartId: string): Observable<SocioPayment> {
    return this.http
      .post<OccSocioPayment>(`${this.cartUrl(cartId)}/order`, {})
      .pipe(
        map(toPayment),
        tap(() => this.methods.delete(cartId)),
        catchError((e) => throwError(() => new Error(errorMessage(e))))
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
    return method === 'PIX' || method === 'BOLETO';
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
  orderCode?: string;
  orderDate?: string;
  due?: string;
  code?: string;
  confirmedAt?: string;
  awaiting?: boolean;
  summary?: string;
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
    orderCode: r?.orderCode ?? undefined,
    orderDate: date(r?.orderDate),
    due: date(r?.due),
    code: r?.code ?? undefined,
    confirmedAt: date(r?.confirmedAt),
    awaiting: !!r?.awaiting,
    summary: r?.summary ?? undefined,
  };
}

function errorMessage(e: any): string {
  return (
    e?.error?.errors?.[0]?.message ??
    e?.message ??
    'Não foi possível concluir o pagamento.'
  );
}
