import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
  OnInit,
  Renderer2,
  inject,
} from '@angular/core';
import { OrderEntry } from '@spartacus/cart/base/root';
import { RoutingService } from '@spartacus/core';
import { Order, OrderFacade } from '@spartacus/order/root';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs/operators';
import {
  EstudioService,
  MeCarrinhoItem,
  MePedido,
} from '../estudio/estudio.service';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { Membership } from '../socio/socio.model';
import { SocioService } from '../socio/socio.service';
import {
  SocioPayment,
  SocioPaymentService,
} from '../socio/socio-payment.service';
import { SocioPointsService } from '../socio/socio-points.service';
import { SocioTicketHolderService } from '../socio/socio-ticket-holder.service';
import {
  FlaPersonalizationService,
  PERSONALIZATION_ADDON,
  PersonalizationInfo,
  personalizationSummary,
} from './fla-personalization.service';
import { FlaStoreSearchService } from './fla-store-search.service';

/** Itens sem entrega física (ingresso, experiência, plano, estacionamento, comida, personalização) */
export const DIGITAL =
  /^(JOGO-|EXP-|NACAO-|FLA-EST-|FLA-FOOD-|FLA-PERS-|FLA-PERSONALIZACAO)/;
/** Ingresso de jogo (poltrona ou setor) */
const TICKET = /^JOGO-/;
/** Camiseta do Mengo Estúdio (produto por modelo e tecido) */
const ME_CAMISETA = /^ME-CAM-/;

/** Página de confirmação do pedido (rota orderConfirmation) */
function onConfirmation(routing: RoutingService): Observable<boolean> {
  return routing.getRouterState().pipe(
    map((s) => s?.state?.semanticRoute === 'orderConfirmation'),
    distinctUntilChanged()
  );
}

/** Pedido da confirmação; nas outras páginas, nada */
function confirmedOrder(
  routing: RoutingService,
  orders: OrderFacade
): Observable<Order | undefined> {
  return onConfirmation(routing).pipe(
    switchMap((on) => (on ? orders.getOrderDetails() : of(undefined))),
    map((o) => (o?.code ? o : undefined)),
    shareReplay({ bufferSize: 1, refCount: true })
  );
}

/** Pix ou boleto do pedido (aguardando ou confirmado); cartão: undefined */
function paymentOf(
  order$: Observable<Order | undefined>,
  socioPayment: SocioPaymentService
): Observable<SocioPayment | undefined> {
  return order$.pipe(
    map((o) => o?.code),
    distinctUntilChanged(),
    switchMap((code) =>
      code
        ? socioPayment.orderStatus(code).pipe(
            map((p) => (p?.method ? p : undefined)),
            startWith(undefined)
          )
        : of(undefined)
    ),
    shareReplay({ bufferSize: 1, refCount: true })
  );
}

interface HeroView {
  code: string;
  date: string;
  email?: string;
  awaiting: boolean;
  tickets: boolean;
}

/**
 * Confirmação do pedido (mockup "Confirmação", 06/11), topo: selo verde, "Pedido confirmado. Vamos,
 * Flamengo!" (ou "Pedido recebido", com Pix ou boleto aguardando), número e data do pedido, e-mail
 * da confirmação e os botões. Logo abaixo vêm o Pix/boleto (outlet do agradecimento do Spartacus)
 * e o "Seu dia de jogo" (MatchdayOrderConfirmationComponent). Põe no <html> a classe fla-oc, que o
 * tema usa para esconder os componentes padrão da página. Entra pelo outlet do slot BodyContent (antes).
 */
@Component({
  selector: 'fla-oc-hero',
  template: `
    <section
      class="fla-oc-box fla-oc-hero"
      *ngIf="view$ | async as v"
      aria-labelledby="fla-oc-title"
    >
      <span
        class="fla-oc-check"
        [class.is-wait]="v.awaiting"
        aria-hidden="true"
      >
        <svg
          *ngIf="!v.awaiting"
          width="32"
          height="32"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="3"
          focusable="false"
        >
          <path d="M5 12l5 5 9-10" />
        </svg>
        <svg
          *ngIf="v.awaiting"
          width="32"
          height="32"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          focusable="false"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        </svg>
      </span>
      <h1 id="fla-oc-title">
        {{ (v.awaiting ? 'oc.titleWait' : 'oc.title') | flaT }}
      </h1>
      <p
        class="fla-oc-number"
        [innerHTML]="
          'oc.number'
            | flaT: { code: '<strong>' + v.code + '</strong>', date: v.date }
        "
      ></p>
      <p class="fla-oc-muted">
        <ng-container *ngIf="v.email">{{
          'oc.sent' | flaT: { email: v.email }
        }}</ng-container>
        <ng-container *ngIf="v.awaiting">
          {{ 'oc.waitSub' | flaT }}</ng-container
        >
        <ng-container *ngIf="!v.awaiting && v.tickets">
          {{ 'oc.inAccount' | flaT }}</ng-container
        >
      </p>
      <div class="fla-oc-actions">
        <a
          class="fla-oc-btn"
          [routerLink]="
            { cxRoute: 'orderDetails', params: { code: v.code } } | cxUrl
          "
          >{{ (v.tickets ? 'oc.tickets' : 'oc.track') | flaT }}</a
        >
        <a
          class="fla-oc-btn is-outline"
          [routerLink]="{ cxRoute: 'orders' } | cxUrl"
          >{{ 'oc.orders' | flaT }}</a
        >
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaOcHeroComponent implements OnInit, OnDestroy {
  protected order$ = confirmedOrder(this.routing, this.orders);

  readonly view$: Observable<HeroView | null> = combineLatest([
    this.order$,
    paymentOf(this.order$, this.socioPayment),
    this.i18n.lang$,
  ]).pipe(
    map(([o, pay, lang]) =>
      o?.code
        ? {
            code: o.code,
            date: this.when(o.created, lang === 'pt'),
            email: (o as any).user?.uid,
            awaiting: !!pay?.awaiting,
            tickets: (o.entries ?? []).some((e) =>
              TICKET.test(e.product?.code ?? '')
            ),
          }
        : null
    )
  );

  protected subscription = new Subscription();

  constructor(
    protected routing: RoutingService,
    protected orders: OrderFacade,
    protected socioPayment: SocioPaymentService,
    protected i18n: FlaI18nService,
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      onConfirmation(this.routing).subscribe((on) =>
        on
          ? this.renderer.addClass(this.document.documentElement, 'fla-oc')
          : this.renderer.removeClass(this.document.documentElement, 'fla-oc')
      )
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.renderer.removeClass(this.document.documentElement, 'fla-oc');
  }

  /** "6 nov 2026, 17:42" (pt) ou "Nov 6, 2026, 5:42 PM" (en), no horário de Brasília */
  protected when(created: Date | string | undefined, pt: boolean): string {
    if (!created) {
      return '';
    }
    const d = new Date(created);
    const date = d.toLocaleDateString(pt ? 'pt-BR' : 'en-US', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'America/Sao_Paulo',
    });
    const time = d.toLocaleTimeString(pt ? 'pt-BR' : 'en-US', {
      hour: pt ? '2-digit' : 'numeric',
      minute: '2-digit',
      timeZone: 'America/Sao_Paulo',
    });
    return `${pt ? date.replace(/\./g, '').replace(/ de /g, ' ') : date}, ${time}`;
  }
}

export interface OcItem {
  entry: OrderEntry;
  name: string;
  details: string[];
  price: string;
  /** Camiseta do Mengo Estúdio: estampa e configuração (PR 4) */
  me?: MeCarrinhoItem;
}

export interface OcCard {
  l1: string;
  l2?: string;
  l3?: string;
}

export interface BodyView {
  items: OcItem[];
  subtotal: string;
  discount?: string;
  discountLabel: string;
  pixOff?: string;
  delivery?: string;
  total: string;
  shipping?: OcCard;
  payment: OcCard;
  points?: { n: number; plan?: string };
}

/**
 * Confirmação do pedido (mockup), corpo: os itens com o resumo de valores (o desconto do Pix em
 * linha própria) e, ao lado, Entrega, Pagamento e os pontos Nação ganhos no pedido; embaixo,
 * "Voltar para a loja". Entra pelo outlet do slot BodyContent (depois).
 */
@Component({
  selector: 'fla-oc-body',
  template: `
    <ng-container *ngIf="view$ | async as v">
      <div class="fla-oc-cols">
        <section
          class="fla-oc-box fla-oc-items"
          aria-labelledby="fla-oc-items-title"
        >
          <h2 id="fla-oc-items-title" class="fla-oc-h2">
            {{ 'oc.items' | flaT }}
          </h2>
          <ul class="fla-ci-items">
            <li *ngFor="let i of v.items">
              <span class="fla-ci-thumb">
                <fla-me-camiseta
                  *ngIf="i.me as me; else ocThumb"
                  [estampa]="me.estampa"
                  [cor]="me.cor"
                ></fla-me-camiseta>
                <ng-template #ocThumb>
                  <cx-media
                    [container]="i.entry.product?.images?.PRIMARY"
                    format="thumbnail"
                    [alt]="''"
                  ></cx-media>
                </ng-template>
              </span>
              <span class="fla-ci-item">
                <strong>{{ i.name }}</strong>
                <span *ngFor="let d of i.details">{{ d }}</span>
              </span>
              <strong>{{ i.price }}</strong>
            </li>
          </ul>
          <div class="fla-oc-totals">
            <div class="fla-cp-line">
              <span>{{ 'cd.subtotal' | flaT }}</span
              ><span>{{ v.subtotal }}</span>
            </div>
            <div class="fla-cp-line fla-cp-discount" *ngIf="v.discount">
              <span>{{ v.discountLabel }}</span
              ><span>− {{ v.discount }}</span>
            </div>
            <div class="fla-cp-line fla-cp-discount" *ngIf="v.pixOff">
              <span>{{ 'oc.pixDiscount' | flaT }}</span
              ><span>− {{ v.pixOff }}</span>
            </div>
            <div class="fla-cp-line" *ngIf="v.delivery">
              <span>{{ 'cp.shipping' | flaT }}</span
              ><span>{{ v.delivery }}</span>
            </div>
            <div class="fla-cp-line fla-cp-total">
              <span>{{ 'cd.total' | flaT }}</span
              ><span>{{ v.total }}</span>
            </div>
          </div>
        </section>
        <div class="fla-oc-side">
          <section
            class="fla-oc-box fla-oc-card"
            *ngIf="v.shipping"
            aria-labelledby="fla-oc-ship-title"
          >
            <h2 id="fla-oc-ship-title" class="fla-oc-h3">
              {{ 'oc.delivery' | flaT }}
            </h2>
            <span>{{ v.shipping.l1 }}</span>
            <span class="fla-oc-muted" *ngIf="v.shipping.l2">{{
              v.shipping.l2
            }}</span>
            <span class="fla-oc-muted" *ngIf="v.shipping.l3">{{
              v.shipping.l3
            }}</span>
          </section>
          <section
            class="fla-oc-box fla-oc-card"
            aria-labelledby="fla-oc-pay-title"
          >
            <h2 id="fla-oc-pay-title" class="fla-oc-h3">
              {{ 'oc.payment' | flaT }}
            </h2>
            <span>{{ v.payment.l1 }}</span>
            <span class="fla-oc-muted" *ngIf="v.payment.l2">{{
              v.payment.l2
            }}</span>
          </section>
          <section
            class="fla-oc-box fla-oc-card fla-oc-points"
            *ngIf="v.points"
          >
            <strong>{{ 'oc.points' | flaT: { n: v.points.n } }}</strong>
            <span>{{
              (v.points.plan ? 'oc.pointsPlan' : 'oc.pointsSub')
                | flaT: { plan: v.points.plan }
            }}</span>
          </section>
        </div>
      </div>
      <a class="fla-oc-back" routerLink="/">{{ 'oc.back' | flaT }}</a>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaOcBodyComponent {
  /** Pedido mostrado (confirmação; o detalhe do pedido em Minha conta troca a fonte) */
  protected order$: Observable<Order | undefined>;

  readonly view$: Observable<BodyView | null>;

  protected readonly estudio = inject(EstudioService);
  /** Parte Mengo Estúdio do pedido (só busca quando há camiseta do Estúdio); recarregada pelo cancelamento */
  protected me$!: Observable<MePedido | null>;

  constructor(
    protected routing: RoutingService,
    protected orders: OrderFacade,
    protected socioPayment: SocioPaymentService,
    protected socio: SocioService,
    protected points: SocioPointsService,
    protected holders: SocioTicketHolderService,
    protected personalization: FlaPersonalizationService,
    protected storeSearch: FlaStoreSearchService,
    protected i18n: FlaI18nService
  ) {
    this.order$ = this.orderSource();
    const code$ = this.order$.pipe(
      map((o) => o?.code),
      distinctUntilChanged()
    );
    const pers$ = this.order$.pipe(
      map((o) =>
        o?.code &&
        (o.entries ?? []).some((e) =>
          PERSONALIZATION_ADDON.test(e.product?.code ?? '')
        )
          ? o.code
          : ''
      ),
      distinctUntilChanged(),
      switchMap((code) =>
        code
          ? this.personalization
              .forOrder('current', code)
              .pipe(catchError(() => of([] as PersonalizationInfo[])))
          : of([] as PersonalizationInfo[])
      ),
      startWith([] as PersonalizationInfo[])
    );
    // "Ingresso de: Maria" nas poltronas de convidados
    const holders$ = this.order$.pipe(
      map((o) =>
        (o?.entries ?? [])
          .map((e) => e.product?.code ?? '')
          .filter((c) => TICKET.test(c))
      ),
      distinctUntilChanged((a, b) => a.join() === b.join()),
      switchMap((codes) =>
        codes.length
          ? combineLatest(
              codes.map((c) =>
                this.holders.holderOf(c).pipe(
                  map((h) => [c, h] as [string, string | undefined]),
                  startWith([c, undefined] as [string, string | undefined])
                )
              )
            )
          : of([] as [string, string | undefined][])
      ),
      map((pairs) => new Map(pairs))
    );
    const store$ = this.order$.pipe(
      map((o) => this.pickupStore(o)),
      distinctUntilChanged(),
      switchMap((name) => (name ? this.storeSearch.storeName(name) : of('')))
    );
    const earned$ = code$.pipe(
      switchMap((code) =>
        code
          ? this.points.load().pipe(
              map((p) =>
                p.enabled
                  ? (p.earnings.find((e) => e.orderCode === code)?.points ?? 0)
                  : 0
              ),
              startWith(0)
            )
          : of(0)
      )
    );
    this.me$ = this.order$.pipe(
      map((o) =>
        o?.code &&
        (o.entries ?? []).some((e) => ME_CAMISETA.test(e.product?.code ?? ''))
          ? o.code
          : ''
      ),
      distinctUntilChanged(),
      switchMap((code) =>
        code
          ? this.estudio.pedido(code).pipe(startWith(null as MePedido | null))
          : of(null)
      ),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    const plan$ = this.socio.getMembership().pipe(
      map((m: Membership) => m?.member?.name ?? undefined),
      startWith(undefined as string | undefined),
      catchError(() => of(undefined))
    );

    this.view$ = combineLatest([
      this.order$,
      paymentOf(this.order$, this.socioPayment),
      pers$,
      holders$,
      store$,
      earned$,
      plan$,
      this.me$,
      this.i18n.lang$,
    ]).pipe(
      map(([o, pay, pers, holders, store, earned, plan, me]) =>
        o ? this.toView(o, pay, pers, holders, store, earned, plan, me) : null
      )
    );
  }

  /** Fonte do pedido (chamada no construtor: subclasses usam inject() aqui) */
  protected orderSource(): Observable<Order | undefined> {
    return confirmedOrder(this.routing, this.orders);
  }

  protected toView(
    o: Order,
    pay: SocioPayment | undefined,
    pers: PersonalizationInfo[],
    holders: Map<string, string | undefined>,
    store: string,
    earned: number,
    plan: string | undefined,
    me?: MePedido | null
  ): BodyView {
    const money = (v: number) => this.i18n.money(v);
    const items: OcItem[] = (o.entries ?? []).map((entry) => {
      const code = entry.product?.code ?? '';
      // camiseta do Mengo Estúdio: "Estampa Elenco" e a configuração (modelo, tecido, cor, tamanho)
      const camiseta = me?.itens.find((x) => x.entryNumber === entry.entryNumber);
      if (camiseta) {
        return {
          entry,
          name: this.i18n.t('me.c.nome', { n: camiseta.estampa.nome }),
          details: [
            this.estudio.configuracao(camiseta),
            this.i18n.t('ci.units', { n: entry.quantity ?? 1 }),
          ],
          price:
            entry.totalPrice?.formattedValue ??
            money(entry.totalPrice?.value ?? 0),
          me: camiseta,
        };
      }
      const qualifiers =
        entry.product?.baseOptions?.[0]?.selected?.variantOptionQualifiers ??
        [];
      const details = [
        qualifiers
          .filter((q) => q.name && q.value)
          .map((q) => `${q.name}: ${q.value}`)
          .join(' · '),
      ];
      const p = pers.find((x) => x.entryNumber === entry.entryNumber);
      if (p) {
        details.push(
          this.i18n.t('pz.cartLine', {
            v: personalizationSummary(p, this.i18n.t('pz.sponsorShort')),
          })
        );
      }
      const holder = holders.get(code);
      if (holder) {
        details.push(this.i18n.t('sg.cartHolder', { name: holder }));
      }
      details.push(this.i18n.t('ci.units', { n: entry.quantity ?? 1 }));
      return {
        entry,
        name: entry.product?.name ?? code,
        details: details.filter((d) => !!d),
        price:
          entry.totalPrice?.formattedValue ??
          money(entry.totalPrice?.value ?? 0),
      };
    });

    // subtotal cheio = total − frete + todos os descontos (o subTotal do pedido na API já vem
    // sem o desconto no pedido, como o do Pix: somar a partir dele contava esse desconto 2 vezes)
    const orderDiscount = o.orderDiscounts?.value ?? 0;
    const discount = o.totalDiscounts?.value ?? 0;
    const grandTotal = o.totalPriceWithTax?.value ?? o.totalPrice?.value ?? 0;
    const net = grandTotal - (o.deliveryCost?.value ?? 0);
    // desconto do Pix (promoção "Forma de pagamento = Pix"): é desconto no pedido, em linha própria
    const pixPromo = (o.appliedOrderPromotions ?? []).some((p) =>
      /pix/i.test(`${p.promotion?.code ?? ''} ${p.description ?? ''}`)
    );
    const pixOff =
      pay?.method === 'PIX' && pixPromo && orderDiscount > 0.004
        ? orderDiscount
        : 0;
    const rest = discount - pixOff;
    const cost = o.deliveryCost;
    const free = cost?.value === 0;

    return {
      items,
      subtotal: money(net + discount),
      discount: rest > 0.004 ? money(rest) : undefined,
      discountLabel: this.i18n.t(plan ? 'cd.memberDiscount' : 'cd.discount'),
      pixOff: pixOff ? money(pixOff) : undefined,
      delivery: cost
        ? free
          ? this.i18n.t('cs.free')
          : (cost.formattedValue ?? money(cost.value ?? 0))
        : undefined,
      total:
        o.totalPriceWithTax?.formattedValue ??
        o.totalPrice?.formattedValue ??
        '',
      shipping: this.shipping(o, store),
      payment: this.payment(o, pay),
      points: earned > 0 ? { n: earned, plan } : undefined,
    };
  }

  /** Retirada na loja ou endereço de casa com a forma de entrega; só itens digitais: sem cartão */
  protected shipping(o: Order, store: string): OcCard | undefined {
    const physical = (o.entries ?? []).filter(
      (e) => !DIGITAL.test(e.product?.code ?? '')
    );
    if (!physical.length) {
      return undefined;
    }
    if (store && physical.every((e) => !!e.deliveryPointOfService)) {
      return {
        l1: this.i18n.t('cpay.pickupAt', { store }),
        l2: this.i18n.t('cdl.pickupFree'),
      };
    }
    const a = o.deliveryAddress;
    const join = (...xs: (string | undefined)[]) =>
      xs.filter((x) => !!x).join(' · ');
    return {
      l1: join(a?.line1, a?.line2),
      l2: join(
        a?.town,
        a?.region?.isocodeShort ?? a?.region?.isocode,
        a?.postalCode
      ),
      l3: join(o.deliveryMode?.name, o.deliveryMode?.description),
    };
  }

  /** Pix ou boleto (e se já foi pago) ou o cartão com o final */
  protected payment(o: Order, pay: SocioPayment | undefined): OcCard {
    const total = o.totalPriceWithTax?.formattedValue ?? '';
    if (
      pay?.method &&
      pay.method !== 'CARTAO_MENSAL' &&
      pay.method !== 'CARTAO_ANUAL'
    ) {
      const key = pay.planCode
        ? 'spay.m.' + pay.method
        : 'spay.m.store.' + pay.method;
      const name =
        this.i18n.t(key) === key
          ? this.i18n.t('spay.m.' + pay.method)
          : this.i18n.t(key);
      return {
        l1: name,
        l2: `${total} · ${this.i18n.t(pay.awaiting ? 'oc.payWait' : 'oc.payDone')}`,
      };
    }
    const digits = (o.paymentInfo?.cardNumber ?? '').replace(/\D/g, '');
    return {
      l1:
        digits.length >= 4
          ? this.i18n.t('oc.cardEnding', { n: digits.slice(-4) })
          : this.i18n.t('spay.m.store.CARTAO_MENSAL'),
      l2: total,
    };
  }

  /** Loja de retirada quando todos os itens físicos vão para a mesma loja */
  protected pickupStore(o: Order | undefined): string {
    const physical = (o?.entries ?? []).filter(
      (e) => !DIGITAL.test(e.product?.code ?? '')
    );
    const stores = [
      ...new Set(physical.map((e) => e.deliveryPointOfService?.name ?? '')),
    ];
    return physical.length && stores.length === 1 ? stores[0] : '';
  }
}
