import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/** Sessão de checkout da Adyen para o carrinho (POST .../adyen/sessions) */
export interface AdyenCardSession {
  id: string;
  sessionData: string;
  reference: string;
  /** Em centavos */
  amount: number;
  currency: string;
  clientKey: string;
  environment: 'test' | 'live';
  installments: number[];
  preselectedInstallments?: number;
}

/** Resultado do componente da Adyen que fecha o pedido (POST .../adyen/order) */
export interface AdyenCardResult {
  sessionId: string;
  sessionResult: string;
  resultCode: string;
}

/**
 * Cartão pela Adyen (05/11): sessão, conferência do carrinho antes de cobrar e o
 * fechamento do pedido com o resultado da Adyen. O adaptador do pedido usa o
 * resultado guardado aqui para fechar pelo servidor (cartão só com pagamento
 * autorizado na Adyen).
 */
@Injectable({ providedIn: 'root' })
export class AdyenCardService {
  protected results = new Map<string, AdyenCardResult>();

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  session(cartId: string): Observable<AdyenCardSession> {
    return this.http
      .post<AdyenCardSession>(`${this.cartUrl(cartId)}/adyen/sessions`, {})
      .pipe(catchError((e) => throwError(() => new Error(message(e)))));
  }

  /** As regras do "Finalizar pedido" antes de cobrar o cartão */
  check(cartId: string): Observable<void> {
    return this.http.post<void>(`${this.cartUrl(cartId)}/adyen/check`, {}).pipe(
      map(() => undefined),
      catchError((e) => throwError(() => new Error(message(e))))
    );
  }

  /** Guarda o resultado da Adyen para o "Finalizar pedido" do Spartacus */
  setResult(cartId: string, result: AdyenCardResult): void {
    this.results.set(cartId, result);
  }

  takeResult(cartId: string): AdyenCardResult | undefined {
    const r = this.results.get(cartId);
    this.results.delete(cartId);
    return r;
  }

  /** Fecha o pedido com o cartão já autorizado na Adyen */
  placeOrder(
    cartId: string,
    result: AdyenCardResult
  ): Observable<{ orderCode: string }> {
    return this.http
      .post<{ orderCode: string }>(`${this.cartUrl(cartId)}/adyen/order`, result)
      .pipe(
        catchError((e) =>
          throwError(() =>
            Object.assign(new Error(message(e)), { status: e?.status })
          )
        )
      );
  }

  protected cartUrl(cartId: string): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/carts/${encodeURIComponent(cartId)}`;
  }
}

function message(e: any): string {
  return (
    e?.error?.errors?.[0]?.message ??
    e?.message ??
    'Não foi possível falar com a Adyen. Tente de novo.'
  );
}
