import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { AuthService, OccEndpointsService, WindowRef } from '@spartacus/core';
import { FlaI18nService } from '../i18n/fla-i18n.service';
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
  Membership,
  SOCIO_CATEGORY,
  SOCIO_RULES,
  SocioGuest,
  SocioPlan,
  SocioPlanRules,
  SUSPENDED_GROUP,
  BIOMETRY_GROUP,
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

/** Marcador com as regras do plano no fim da descrição do produto */
const PLAN_MARKER = /<!--\s*socio-plan\s*([\s\S]*?)-->/i;
/** Código dos planos antigos (compatibilidade, ver SOCIO_RULES) */
const LEGACY_PLAN_CODE = /^NACAO-MARACA-(\d+)$/;

/**
 * Planos de sócio-torcedor e a situação do torcedor logado.
 *
 * - Planos: busca da categoria socio_torcedor (traz o preço) + detalhe de
 *   cada produto (traz os benefícios e o marcador de regras na descrição).
 * - Plano do torcedor: grupos de clientes do SAP Commerce (grupo do plano sem
 *   o marcador de pendente).
 * - Ondas de venda: datas cadastradas em cada jogo (Backoffice, tela Jogos).
 */
@Injectable({ providedIn: 'root' })
export class SocioService {
  private plans$?: Observable<SocioPlan[]>;
  private refresh$ = new BehaviorSubject<number>(0);
  private membership$?: Observable<Membership>;

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected auth: AuthService,
    protected winRef: WindowRef,
    protected activeCart: ActiveCartFacade,
    protected i18n: FlaI18nService
  ) {
    // nome e benefícios dos planos vêm da API no idioma do site
    let first = true;
    this.i18n.lang$.subscribe(() => {
      if (!first) {
        this.plans$ = undefined;
      }
      first = false;
    });
  }

  // ---------- planos ----------

  /** Planos na ordem dos cards (order, depois código) */
  getPlans(): Observable<SocioPlan[]> {
    if (!this.plans$) {
      this.plans$ = this.search().pipe(
        switchMap((products) => {
          if (!products.length) {
            return of([] as SocioPlan[]);
          }
          // só a descrição diz se o produto é plano (tem o marcador) ou convidado
          return forkJoin(
            products.map((p) =>
              this.loadDescription(p.code).pipe(
                map((description) => this.toPlan(p, description, products))
              )
            )
          ).pipe(
            map((plans) =>
              plans
                .filter((p): p is SocioPlan => p !== null)
                .sort(
                  (a, b) => a.order - b.order || a.code.localeCompare(b.code)
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

  /** Plano que vale para ingressos (comprado ou no carrinho) ou null */
  getMemberPlan(): Observable<SocioPlan | null> {
    return this.getMembership().pipe(
      map((m) => m.plan),
      distinctUntilChanged((a, b) => a?.code === b?.code)
    );
  }

  /**
   * Plano comprado, plano no carrinho e o que vale agora. Lido de novo quando o
   * plano do carrinho muda (o SAP Commerce acerta os grupos nessa hora) e na
   * troca de idioma (os planos vêm de novo, com o nome no idioma do site).
   */
  getMembership(): Observable<Membership> {
    if (!this.membership$) {
      // planos no idioma atual (o cache é zerado na troca de idioma)
      const plans$ = this.i18n.lang$.pipe(switchMap(() => this.getPlans()));
      const cart$ = combineLatest([plans$, this.activeCart.getEntries()]).pipe(
        map(([plans, entries]) => {
          const codes = entries.map((e) => e.product?.code ?? '');
          return {
            plans,
            inCart: this.best(plans.filter((p) => codes.includes(p.code))),
          };
        }),
        distinctUntilChanged(
          (a, b) => a.plans === b.plans && a.inCart?.code === b.inCart?.code
        )
      );
      this.membership$ = combineLatest([
        this.auth.isUserLoggedIn().pipe(distinctUntilChanged()),
        this.refresh$,
        cart$,
      ]).pipe(
        switchMap(([loggedIn, , { plans, inCart }]) =>
          loggedIn
            ? this.loadGroups().pipe(
                map((uids) =>
                  this.membership(
                    this.memberPlan(plans, uids),
                    inCart,
                    uids.includes(SUSPENDED_GROUP),
                    uids.includes(BIOMETRY_GROUP)
                  )
                )
              )
            : of(this.membership(null, null, false, false))
        ),
        shareReplay({ bufferSize: 1, refCount: true })
      );
    }
    return this.membership$;
  }

  /** Lê de novo o plano do torcedor (depois de uma compra) */
  refreshMembership(): void {
    this.refresh$.next(this.refresh$.value + 1);
  }

  protected membership(
    member: SocioPlan | null,
    inCart: SocioPlan | null,
    suspended: boolean,
    biometry: boolean
  ): Membership {
    // no empate de onda vale o comprado (vem primeiro na lista); suspenso não
    // tem onda de sócio nem desconto (as promoções excluem o grupo)
    return {
      member,
      inCart,
      plan: suspended
        ? null
        : this.best([member, inCart].filter((p): p is SocioPlan => !!p)),
      suspended,
      biometry,
    };
  }

  /** Plano de melhor onda (onda 0 = venda geral); no empate, o primeiro */
  protected best(plans: SocioPlan[]): SocioPlan | null {
    return plans.reduce<SocioPlan | null>(
      (best, p) =>
        !best || this.memberWave(p) < this.memberWave(best) ? p : best,
      null
    );
  }

  /** Plano comprado: grupo do plano sem o grupo marcador de pendente */
  protected memberPlan(plans: SocioPlan[], uids: string[]): SocioPlan | null {
    return this.best(
      plans.filter(
        (p) =>
          !!p.group &&
          uids.includes(p.group) &&
          !(p.pending && uids.includes(p.pending))
      )
    );
  }

  /** Grupos de clientes do torcedor logado */
  protected loadGroups(): Observable<string[]> {
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
        map((res) => (res.userGroups ?? []).map((g) => g.uid ?? '')),
        catchError(() => of([] as string[]))
      );
  }

  // ---------- ondas de venda ----------

  /** Onda do torcedor: a do plano, ou venda geral (sem plano ou onda 0) */
  memberWave(plan: SocioPlan | null): number {
    return plan && plan.wave >= 1 && plan.wave < GENERAL_SALE_WAVE
      ? plan.wave
      : GENERAL_SALE_WAVE;
  }

  /** % de desconto do plano na inteira (0 para quem não é sócio) */
  discount(plan: SocioPlan | null): number {
    return plan?.discount ?? 0;
  }

  /** "Onda 1 · Nação Maraca 1" com os planos que compram na onda */
  waveLabel(wave: number, plans: SocioPlan[]): string {
    const names = plans
      .filter((p) => p.wave === wave)
      .map((p) => p.name)
      .join(', ');
    return this.i18n.waveLabel(wave, names);
  }

  /**
   * Situação de venda de um jogo para o torcedor, pelas datas das ondas
   * cadastradas no jogo (onda 1, 2, 3 e venda geral). Jogo sem datas está em
   * venda geral.
   */
  waveStatus(
    waveStarts: string[] | undefined,
    plan: SocioPlan | null,
    now = new Date()
  ): WaveStatus {
    const mine = this.memberWave(plan);
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
            pageSize: '50',
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

  /** Plano a partir do produto, ou null se o produto não é plano (convidado) */
  protected toPlan(
    p: OccProduct,
    description: string,
    all: OccProduct[]
  ): SocioPlan | null {
    const rules =
      this.parseRules(description) ?? this.legacyRules(p.code) ?? null;
    if (!rules) {
      return null;
    }
    const guestProduct =
      rules.maxGuests > 0 && rules.guest
        ? all.find((g) => g.code === rules.guest)
        : undefined;
    const guest: SocioGuest | undefined = guestProduct
      ? {
          code: guestProduct.code,
          name: this.clean(guestProduct.name),
          price: guestProduct.price?.value ?? 0,
        }
      : undefined;
    return {
      code: p.code,
      name: this.clean(p.name),
      summary: this.clean(p.summary),
      benefits: this.benefits(description.replace(PLAN_MARKER, '')),
      price: p.price?.value ?? 0,
      soldOut:
        p.stock?.stockLevelStatus === 'outOfStock' || p.stock?.stockLevel === 0,
      guest,
      maxGuests: guest ? rules.maxGuests : 0,
      discount: rules.discount,
      storeDiscount: rules.storeDiscount,
      wave: rules.wave,
      group: rules.group,
      pending: rules.pending,
      family: rules.family,
      order: rules.order,
    };
  }

  /** Regras do marcador <!--socio-plan {...}-->; null se não há ou é inválido */
  protected parseRules(description: string): SocioPlanRules | null {
    const json = description.match(PLAN_MARKER)?.[1];
    if (!json) {
      return null;
    }
    let raw: unknown;
    try {
      raw = JSON.parse(json);
    } catch {
      return null;
    }
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
      return null;
    }
    const r = raw as Record<string, unknown>;
    const int = (v: unknown, min: number, max: number, fallback: number) => {
      const n = Number(v);
      return Number.isInteger(n) && n >= min && n <= max ? n : fallback;
    };
    const str = (v: unknown) => (typeof v === 'string' ? v.trim() : '');
    const group = str(r.group);
    if (!group) {
      // sem grupo não dá para saber quem é sócio do plano
      return null;
    }
    return {
      wave: int(r.wave, 0, GENERAL_SALE_WAVE - 1, 0),
      discount: int(r.discount, 0, 100, 0),
      storeDiscount: int(r.storeDiscount, 0, 100, 0),
      maxGuests: int(r.maxGuests, 0, 99, 0),
      guest: str(r.guest),
      group,
      pending: str(r.pending),
      family: str(r.family),
      order: int(r.order, -9999, 9999, 9999),
    };
  }

  /**
   * Compatibilidade (até a migração do backend): NACAO-MARACA-N sem marcador
   * usa as regras antigas de SOCIO_RULES.
   */
  protected legacyRules(code: string): SocioPlanRules | null {
    const n = Number(code.match(LEGACY_PLAN_CODE)?.[1]);
    const old = SOCIO_RULES[n];
    return old
      ? {
          wave: old.wave,
          discount: old.discount,
          storeDiscount: 0,
          maxGuests: old.maxGuests,
          guest: old.guestCode,
          group: `nacao-maraca-${n}`,
          pending: `nacao-pendente-${n}`,
          family: 'Nação Maraca',
          order: n,
        }
      : null;
  }

  /** "<ul><li>a</li><li>b</li></ul>" -> ["a", "b"] */
  protected benefits(html: string): string[] {
    const items = Array.from(html.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)).map(
      (m) => this.clean(m[1])
    );
    if (items.length) {
      return items;
    }
    const text = this.clean(html);
    return text ? [text] : [];
  }

  protected clean(text?: string): string {
    return (text ?? '')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/<[^>]+>/g, '')
      .replace(/&amp;/g, '&')
      .replace(/&nbsp;/g, ' ')
      .trim();
  }
}
