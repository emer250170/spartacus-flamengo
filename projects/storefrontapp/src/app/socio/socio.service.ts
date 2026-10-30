import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthService, OccEndpointsService, WindowRef } from '@spartacus/core';
import { BehaviorSubject, Observable, combineLatest, forkJoin, of } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import {
  GENERAL_SALE_WAVE,
  SOCIO_CATEGORY,
  SOCIO_GROUP_PREFIX,
  SOCIO_PLAN_PREFIX,
  SOCIO_RULES,
  SocioGuest,
  SocioPlan,
  WaveStatus,
} from './socio.model';

interface OccProduct {
  code: string;
  name?: string;
  summary?: string;
  description?: string;
  price?: { value?: number };
  stock?: { stockLevel?: number; stockLevelStatus?: string };
}

/**
 * Planos de sócio-torcedor e a situação do torcedor logado.
 *
 * - Planos: busca da categoria socio_torcedor (traz o preço) + detalhe de
 *   cada plano (traz os benefícios na descrição).
 * - Plano do torcedor: grupos de clientes do SAP Commerce (nacao-maraca-N).
 * - Ondas de venda: datas cadastradas em cada jogo (Backoffice, tela Jogos).
 */
@Injectable({ providedIn: 'root' })
export class SocioService {
  private plans$?: Observable<SocioPlan[]>;
  private refresh$ = new BehaviorSubject<number>(0);
  private level$?: Observable<number | null>;

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected auth: AuthService,
    protected winRef: WindowRef
  ) {}

  // ---------- planos ----------

  getPlans(): Observable<SocioPlan[]> {
    if (!this.plans$) {
      this.plans$ = this.search().pipe(
        switchMap((products) => {
          const plans = products
            .filter((p) => p.code.startsWith(SOCIO_PLAN_PREFIX))
            .sort((a, b) => a.code.localeCompare(b.code));
          if (!plans.length) {
            return of([] as SocioPlan[]);
          }
          return forkJoin(
            plans.map((p) =>
              this.loadDescription(p.code).pipe(
                map((description) => this.toPlan(p, description, products))
              )
            )
          );
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.plans$;
  }

  // ---------- plano do torcedor ----------

  /** Nível do plano do torcedor logado (1, 2 ou 3) ou null se não é sócio */
  getMemberLevel(): Observable<number | null> {
    if (!this.level$) {
      this.level$ = combineLatest([
        this.auth.isUserLoggedIn().pipe(distinctUntilChanged()),
        this.refresh$,
      ]).pipe(
        switchMap(([loggedIn]) =>
          loggedIn ? this.loadLevel() : of(null as number | null)
        ),
        shareReplay({ bufferSize: 1, refCount: true })
      );
    }
    return this.level$;
  }

  /** Lê de novo o plano do torcedor (depois de uma compra) */
  refreshMembership(): void {
    this.refresh$.next(this.refresh$.value + 1);
  }

  protected loadLevel(): Observable<number | null> {
    const base = this.occEndpoints.getBaseUrl();
    return this.http
      .get<{ uid?: string }>(`${base}/users/current`, {
        params: { fields: 'uid' },
      })
      .pipe(
        switchMap((user) =>
          user.uid
            ? this.http.get<{ userGroups?: { uid?: string }[] }>(
                `${base}/users/${encodeURIComponent(user.uid)}/customergroups`,
                { params: { fields: 'userGroups(uid)', pageSize: '100' } }
              )
            : of({ userGroups: [] })
        ),
        map((res) => {
          const levels = (res.userGroups ?? [])
            .map((g) => g.uid ?? '')
            .filter((uid) => uid.startsWith(SOCIO_GROUP_PREFIX))
            .map((uid) => Number(uid.substring(SOCIO_GROUP_PREFIX.length)))
            .filter((n) => SOCIO_RULES[n] !== undefined);
          return levels.length ? Math.min(...levels) : null;
        }),
        catchError(() => of(null))
      );
  }

  // ---------- ondas de venda ----------

  /** Onda do torcedor: a do plano, ou venda geral para quem não é sócio */
  memberWave(level: number | null): number {
    return level ? SOCIO_RULES[level].wave : GENERAL_SALE_WAVE;
  }

  /** % de desconto do plano na inteira (0 para quem não é sócio) */
  discount(level: number | null): number {
    return level ? SOCIO_RULES[level].discount : 0;
  }

  /**
   * Situação de venda de um jogo para o torcedor, pelas datas das ondas
   * cadastradas no jogo (onda 1, 2, 3 e venda geral). Jogo sem datas está em
   * venda geral.
   */
  waveStatus(
    waveStarts: string[] | undefined,
    level: number | null,
    now = new Date()
  ): WaveStatus {
    const mine = this.memberWave(level);
    if (!waveStarts || waveStarts.length < GENERAL_SALE_WAVE) {
      return { current: GENERAL_SALE_WAVE, mine, open: true };
    }
    let current = 0;
    waveStarts.forEach((iso, i) => {
      if (new Date(iso).getTime() <= now.getTime()) {
        current = Math.max(current, i + 1);
      }
    });
    return {
      current,
      mine,
      open: current >= mine,
      opensAt: new Date(waveStarts[mine - 1]),
    };
  }

  // ---------- OCC ----------

  protected search(): Observable<OccProduct[]> {
    return this.http
      .get<{ products?: OccProduct[] }>(
        `${this.occEndpoints.getBaseUrl()}/products/search`,
        {
          params: {
            query: `:relevance:allCategories:${SOCIO_CATEGORY}`,
            pageSize: '20',
            fields:
              'products(code,name,summary,price(value),stock(stockLevel,stockLevelStatus))',
          },
        }
      )
      .pipe(
        map((res) => res.products ?? []),
        catchError(() => of([] as OccProduct[]))
      );
  }

  /** A descrição vem só no detalhe do produto (a busca não traz) */
  protected loadDescription(code: string): Observable<string> {
    return this.http
      .get<OccProduct>(
        `${this.occEndpoints.getBaseUrl()}/products/${encodeURIComponent(code)}`,
        { params: { fields: 'description' } }
      )
      .pipe(
        map((p) => p.description ?? ''),
        catchError(() => of(''))
      );
  }

  protected toPlan(
    p: OccProduct,
    description: string,
    all: OccProduct[]
  ): SocioPlan {
    const level = Number(p.code.substring(SOCIO_PLAN_PREFIX.length)) || 3;
    const rules = SOCIO_RULES[level] ?? SOCIO_RULES[3];
    const guestProduct = all.find((g) => g.code === rules.guestCode);
    const guest: SocioGuest | undefined = guestProduct
      ? {
          code: guestProduct.code,
          name: this.clean(guestProduct.name),
          price: guestProduct.price?.value ?? 0,
        }
      : undefined;
    return {
      code: p.code,
      level,
      name: this.clean(p.name),
      summary: this.clean(p.summary),
      benefits: this.benefits(description),
      price: p.price?.value ?? 0,
      soldOut:
        p.stock?.stockLevelStatus === 'outOfStock' || p.stock?.stockLevel === 0,
      guest,
      maxGuests: rules.maxGuests,
      discount: rules.discount,
      wave: rules.wave,
      wavesLabel: rules.wavesLabel,
    };
  }

  /** "<ul><li>a</li><li>b</li></ul>" -> ["a", "b"] */
  protected benefits(html: string): string[] {
    const items = Array.from(html.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)).map(
      (m) => this.clean(m[1])
    );
    return items.length ? items : html ? [this.clean(html)] : [];
  }

  protected clean(text?: string): string {
    return (text ?? '')
      .replace(/<[^>]+>/g, '')
      .replace(/&amp;/g, '&')
      .replace(/&nbsp;/g, ' ')
      .trim();
  }
}
