import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';

export interface PersonalizationOption {
  code: string;
  name: string;
  price?: number;
}

export interface PersonalizationOptions {
  available: boolean;
  nameNumber?: PersonalizationOption;
  sponsor?: PersonalizationOption;
  patches?: PersonalizationOption[];
}

/** Personalização gravada na linha do manto (carrinho ou pedido) */
export interface PersonalizationInfo {
  entryNumber: number;
  productCode: string;
  name?: string;
  number?: string;
  sponsor?: boolean;
  patchCode?: string;
  patchName?: string;
  /** Linhas de preço (FLA-PERS-*) desta personalização */
  addOnEntries?: number[];
}

export interface PersonalizationChoice {
  name: string;
  number: string;
  sponsor: boolean;
  patchCode: string;
}

/** Linhas de preço da personalização no carrinho */
export const PERSONALIZATION_ADDON = /^FLA-PERS-/;

/**
 * Personalização do manto (API /flapersonalization da extensão flamengomatchday): opções com
 * preço por produto, leitura e gravação na linha do manto do carrinho e leitura no pedido.
 * Recusas do servidor voltam como erro com a mensagem em português (errors[0].message).
 */
@Injectable({ providedIn: 'root' })
export class FlaPersonalizationService {
  protected optionsCache = new Map<string, Observable<PersonalizationOptions>>();

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  protected get base(): string {
    return this.occEndpoints.getBaseUrl();
  }

  options(productCode: string): Observable<PersonalizationOptions> {
    let cached = this.optionsCache.get(productCode);
    if (!cached) {
      cached = this.http
        .get<PersonalizationOptions>(`${this.base}/flapersonalization/options`, {
          params: { productCode },
        })
        .pipe(
          catchError(() => of({ available: false })),
          shareReplay({ bufferSize: 1, refCount: false })
        );
      this.optionsCache.set(productCode, cached);
    }
    return cached;
  }

  forCart(userId: string, cartId: string): Observable<PersonalizationInfo[]> {
    return this.http
      .get<{ items?: PersonalizationInfo[] }>(
        `${this.base}/users/${userId}/carts/${cartId}/flapersonalization`
      )
      .pipe(
        map((r) => r?.items ?? []),
        catchError(() => of([]))
      );
  }

  forOrder(userId: string, code: string): Observable<PersonalizationInfo[]> {
    return this.http
      .get<{ items?: PersonalizationInfo[] }>(
        `${this.base}/users/${userId}/orders/${code}/flapersonalization`
      )
      .pipe(
        map((r) => r?.items ?? []),
        catchError(() => of([]))
      );
  }

  save(
    userId: string,
    cartId: string,
    productCode: string,
    choice: PersonalizationChoice
  ): Observable<PersonalizationInfo[]> {
    return this.http
      .put<{ items?: PersonalizationInfo[] }>(
        `${this.base}/users/${userId}/carts/${cartId}/flapersonalization`,
        {
          productCode,
          name: choice.name,
          number: choice.number,
          sponsor: choice.sponsor,
          patchCode: choice.patchCode,
        }
      )
      .pipe(
        map((r) => r?.items ?? []),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }
}

/** "EMERSON · 10 · Kit de patrocínio · Patch Campeão da Libertadores" */
export function personalizationSummary(
  p: PersonalizationInfo,
  sponsorLabel: string
): string {
  return [
    p.name,
    p.number,
    p.sponsor ? sponsorLabel : undefined,
    p.patchName,
  ]
    .filter((x) => !!x)
    .join(' · ');
}

/** Só letras e espaços, sem acento, maiúsculas, até 13 */
export function cleanPersonalizationName(value: string): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z ]/g, '')
    .replace(/\s{2,}/g, ' ')
    .slice(0, 13);
}

/** Só dígitos, até 2 */
export function cleanPersonalizationNumber(value: string): string {
  return (value ?? '').replace(/\D/g, '').slice(0, 2);
}
