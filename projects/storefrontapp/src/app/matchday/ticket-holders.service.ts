import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/** Quem vai usar a poltrona do carrinho (ingresso adicional de quem não é sócio) */
export interface TicketHolderInfo {
  seatCode: string;
  gameCode?: string;
  /** Ingresso adicional ainda sem os dados de quem vai usar */
  required?: boolean;
  name?: string;
  /** 123.456.789-09 */
  cpf?: string;
  /** aaaa-mm-dd */
  birthDate?: string;
  email?: string;
}

/**
 * Dados de quem vai usar cada ingresso adicional (API /users/current/ticketholders
 * da extensão flamengomatchday). Recusas do servidor voltam como erro com a
 * mensagem em português (errors[0].message).
 */
@Injectable({ providedIn: 'root' })
export class TicketHoldersService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  protected get url(): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/ticketholders`;
  }

  list(cartId: string): Observable<TicketHolderInfo[]> {
    return this.http
      .get<{ holders?: TicketHolderInfo[] }>(this.url, { params: { cartId } })
      .pipe(
        map((r) => r?.holders ?? []),
        catchError((e) => this.fail(e))
      );
  }

  /** Grava quem vai usar a poltrona (name vazio = volta para o comprador) */
  save(holder: {
    cartId: string;
    seatCode: string;
    name: string;
    cpf: string;
    birthDate: string;
    email: string;
  }): Observable<TicketHolderInfo[]> {
    return this.http
      .put<{ holders?: TicketHolderInfo[] }>(this.url, holder)
      .pipe(
        map((r) => r?.holders ?? []),
        catchError((e) => this.fail(e))
      );
  }

  protected fail(e: HttpErrorResponse): Observable<never> {
    const message: string = e?.error?.errors?.[0]?.message ?? '';
    return throwError(() => new Error(message));
  }
}
