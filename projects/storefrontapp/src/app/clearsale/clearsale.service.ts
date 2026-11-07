import { DOCUMENT } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Inject, Injectable, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { OccEndpointsService } from '@spartacus/core';
import {
  BehaviorSubject,
  Observable,
  of,
  Subscription,
  throwError,
} from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  filter,
  map,
  switchMap,
  take,
  tap,
} from 'rxjs/operators';

interface ClearSaleConfig {
  ativo: boolean;
  fingerprint?: string | null;
}

/**
 * ClearSale na loja (07/11): ao entrar no checkout, se a análise estiver ligada (Backoffice, aba ClearSale
 * da loja base; GET /flamengo/clearsale), carrega uma vez o script de identificação do aparelho
 * (device.clearsale.com.br/p/fp.js) com a chave pública e usa o guid do carrinho como sessão. O mesmo guid
 * vira o guid do pedido, que o servidor manda como sessionID na análise.
 */
@Injectable({ providedIn: 'root' })
export class FlaClearSaleService implements OnDestroy {
  protected config?: Observable<ClearSaleConfig>;
  protected loaded = false;
  protected lastSession?: string;
  protected subscription = new Subscription();

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected router: Router,
    protected activeCart: ActiveCartFacade,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  /** Chamado uma vez na subida da loja: observa a navegação e liga o script no checkout */
  start(): void {
    this.subscription.add(
      this.router.events
        .pipe(
          filter((e): e is NavigationEnd => e instanceof NavigationEnd),
          map((e) => /\/checkout(\/|$|\?)/.test(e.urlAfterRedirects)),
          distinctUntilChanged(),
          filter((inCheckout) => inCheckout),
          switchMap(() => this.loadConfig()),
          filter((c) => !!c.ativo && !!c.fingerprint),
          switchMap((c) =>
            this.activeCart.getActive().pipe(
              map((cart) => (cart as { guid?: string })?.guid),
              filter((guid): guid is string => !!guid && guid.length >= 6),
              take(1),
              tap((guid) => this.fingerprint(c.fingerprint as string, guid))
            )
          )
        )
        .subscribe()
    );
  }

  protected loadConfig(): Observable<ClearSaleConfig> {
    if (!this.config) {
      const url = `${this.occEndpoints.getBaseUrl()}/flamengo/clearsale`;
      this.config = this.http.get<ClearSaleConfig>(url).pipe(
        // sem o endpoint (servidor antes do deploy) ou erro: sem ClearSale
        catchError(() => of({ ativo: false } as ClearSaleConfig))
      );
    }
    return this.config.pipe(take(1));
  }

  protected fingerprint(app: string, session: string): void {
    const w = this.document.defaultView as any;
    if (!w) {
      return;
    }
    if (!this.loaded) {
      // snippet do manual (behavior-analytics/sdk/browser), carregado uma vez
      w['CsdpObject'] = 'csdp';
      w.csdp =
        w.csdp ||
        function (...args: unknown[]) {
          (w.csdp.q = w.csdp.q || []).push(args);
        };
      w.csdp.l = Date.now();
      const s = this.document.createElement('script');
      s.async = true;
      s.src = 'https://device.clearsale.com.br/p/fp.js';
      this.document.body.appendChild(s);
      w.csdp('app', app);
      this.loaded = true;
    }
    if (this.lastSession !== session) {
      w.csdp('sessionid', session);
      this.lastSession = session;
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

export interface FlaCustomerData {
  /** carregado do servidor (ou sem o endpoint: tratado como completo) */
  loaded: boolean;
  cpf?: string | null;
  telefone?: string | null;
  faltando: string[];
}

/**
 * CPF e telefone de quem já tinha conta (07/11): o checkout pede uma vez o que falta
 * (GET/PUT /users/current/flamengo/dados) e o "Finalizar" espera os dois (FlaCheckoutAcceptService).
 */
@Injectable({ providedIn: 'root' })
export class FlaCustomerDataService {
  readonly state$ = new BehaviorSubject<FlaCustomerData>({
    loaded: false,
    faltando: [],
  });

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  get complete(): boolean {
    const s = this.state$.value;
    return s.loaded && s.faltando.length === 0;
  }

  protected url(): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/flamengo/dados`;
  }

  load(): void {
    this.http
      .get<FlaCustomerData>(this.url())
      .pipe(
        map((d) => ({ ...d, loaded: true, faltando: d?.faltando ?? [] })),
        // sem o endpoint (servidor antes do deploy): não bloqueia o checkout
        catchError(() => of({ loaded: true, faltando: [] } as FlaCustomerData))
      )
      .subscribe((d) => this.state$.next(d));
  }

  /** Grava o que falta; erro: a mensagem do servidor em português */
  save(cpf: string, telefone: string): Observable<FlaCustomerData> {
    return this.http.put<FlaCustomerData>(this.url(), { cpf, telefone }).pipe(
      map((d) => ({ ...d, loaded: true, faltando: d?.faltando ?? [] })),
      tap((d) => this.state$.next(d)),
      catchError((e: HttpErrorResponse) =>
        throwError(
          () =>
            e?.error?.errors?.[0]?.message ||
            'Não foi possível gravar agora. Tente de novo.'
        )
      )
    );
  }
}
