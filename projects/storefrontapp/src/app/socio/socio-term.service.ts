import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthService, OccEndpointsService } from '@spartacus/core';
import { BehaviorSubject, Observable, combineLatest, of, throwError } from 'rxjs';
import { catchError, shareReplay, switchMap, tap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';

/** Termo do Nação (GET /users/current/socioterm) */
export interface SocioTerm {
  /** Chave flamengo.socio.termo.ativo */
  enabled: boolean;
  /** O torcedor é sócio (plano comprado) */
  member: boolean;
  /** Versão atual (vazia: nenhuma publicada) */
  version?: string;
  title?: string;
  /** Parágrafos separados por linha em branco */
  text?: string;
  /** O que mudou na versão atual */
  summary?: string;
  publishedAt?: string;
  /** Aceitou a versão atual */
  accepted: boolean;
  acceptedVersion?: string;
  acceptedAt?: string;
  acceptedOrigin?: string;
  acceptedOrder?: string;
}

/** Termo desligado / não logado: nada aparece */
const NONE: SocioTerm = { enabled: false, member: false, accepted: false };

/**
 * Termo do Nação versionado (item 4 do plano de evolução): versão atual e o
 * aceite do torcedor logado. Aceite no checkout (com o carrinho, obrigatório
 * para fechar pedido com plano) e na área do sócio (versão nova). Lido de novo
 * na troca de idioma (título e texto vêm na língua do site) e depois de aceitar.
 */
@Injectable({ providedIn: 'root' })
export class SocioTermService {
  private refresh$ = new BehaviorSubject<number>(0);

  readonly term$: Observable<SocioTerm> = combineLatest([
    this.auth.isUserLoggedIn(),
    this.i18n.lang$,
    this.refresh$,
  ]).pipe(
    switchMap(([loggedIn]) =>
      loggedIn
        ? this.http
            .get<SocioTerm>(this.url)
            .pipe(catchError(() => of(NONE)))
        : of(NONE)
    ),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected auth: AuthService,
    protected i18n: FlaI18nService
  ) {}

  protected get url(): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/socioterm`;
  }

  /** Aceita a versão atual (com cartId: aceite do checkout) */
  accept(cartId?: string): Observable<SocioTerm> {
    return this.http
      .post<SocioTerm>(`${this.url}/acceptance`, cartId ? { cartId } : {})
      .pipe(
        tap(() => this.refresh$.next(this.refresh$.value + 1)),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }

  /** Parágrafos do texto do Termo */
  paragraphs(term: SocioTerm): string[] {
    return (term.text ?? '')
      .split(/\n\s*\n/)
      .map((p) => p.trim())
      .filter((p) => !!p);
  }

  /** Data dd/mm/aaaa de um ISO com fuso */
  static day(iso?: string): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    return isNaN(d.getTime())
      ? ''
      : d.toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  }
}
