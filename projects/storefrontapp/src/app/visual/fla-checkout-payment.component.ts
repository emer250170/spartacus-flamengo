import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
  OnInit,
  Renderer2,
} from '@angular/core';
import { ActiveCartFacade, Cart, OrderEntry } from '@spartacus/cart/base/root';
import {
  CheckoutDeliveryAddressFacade,
  CheckoutDeliveryModesFacade,
} from '@spartacus/checkout/base/root';
import { Address, RoutingService } from '@spartacus/core';
import { UserAccountFacade } from '@spartacus/user/account/root';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import {
  distinctUntilChanged,
  map,
  startWith,
  switchMap,
} from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioPaymentService } from '../socio/socio-payment.service';
import { FlaCartViewService } from './fla-cart-view.service';
import { FlaStoreSearchService } from './fla-store-search.service';

/** Itens sem entrega física (ingresso, experiência, plano, estacionamento, comida, personalização) */
const DIGITAL = /^(JOGO-|EXP-|NACAO-|FLA-EST-|FLA-FOOD-|FLA-PERS-|FLA-PERSONALIZACAO)/;

/** Uma etapa já feita: título com ✓, duas linhas e "Alterar" */
interface DoneStep {
  title: string;
  l1: string;
  l2?: string;
  route: string;
}

/** Página do pagamento (escolha da forma) ou revisão (cartão, aceites e finalizar) */
function payPage(url: string): 'payment' | 'review' | null {
  if (/checkout\/review-order/.test(url)) {
    return 'review';
  }
  if (/checkout\/(payment-details|payment-type)/.test(url)) {
    return 'payment';
  }
  return null;
}

/**
 * Pagamento (mockup "Checkout — Pagamento", 06/11), no topo da coluna principal da escolha da
 * forma e da revisão: as etapas feitas (Dados pessoais, Entrega e, na revisão, a forma de
 * pagamento), cada uma com "Alterar", e o título "Pagamento" do bloco que segue (a escolha da
 * forma ou, na revisão, o cartão, os aceites e o finalizar). Põe no <html> as classes que o tema
 * usa para a disposição: fla-co-pay (as duas páginas) e fla-co-review (revisão).
 * Entra pelo outlet do slot BodyContent (antes).
 */
@Component({
  selector: 'fla-checkout-done',
  template: `
    <ng-container *ngIf="view$ | async as v">
      <section class="fla-cdn" *ngFor="let s of v.steps">
        <div class="fla-cdn-text">
          <h2>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true" focusable="false"><path d="M5 12l5 5 9-10" /></svg>
            {{ s.title | flaT }}
          </h2>
          <span>{{ s.l1 }}</span>
          <span class="fla-cdn-muted" *ngIf="s.l2">{{ s.l2 }}</span>
        </div>
        <a [routerLink]="{ cxRoute: s.route } | cxUrl">{{ 'cpay.change' | flaT }}</a>
      </section>
      <div class="fla-cpay-head">
        <h1 class="fla-co-title">{{ 'cpay.title' | flaT }}</h1>
      </div>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutDoneComponent implements OnInit, OnDestroy {
  readonly view$: Observable<{ steps: DoneStep[] } | null>;

  protected page$: Observable<'payment' | 'review' | null>;
  protected subscription = new Subscription();

  constructor(
    protected routing: RoutingService,
    protected activeCart: ActiveCartFacade,
    protected userAccount: UserAccountFacade,
    protected deliveryAddress: CheckoutDeliveryAddressFacade,
    protected deliveryModes: CheckoutDeliveryModesFacade,
    protected socioPayment: SocioPaymentService,
    protected storeSearch: FlaStoreSearchService,
    protected i18n: FlaI18nService,
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document
  ) {
    this.page$ = this.routing.getRouterState().pipe(
      map((s) => payPage(s?.state?.url ?? '')),
      distinctUntilChanged()
    );

    const cart$ = this.activeCart.getActive();
    const store$ = cart$.pipe(
      map((c) => this.pickupStore(c)),
      distinctUntilChanged(),
      switchMap((name) => (name ? this.storeSearch.storeName(name) : of('')))
    );
    const payment$ = combineLatest([this.page$, this.activeCart.getActiveCartId()]).pipe(
      switchMap(([page, id]) =>
        page === 'review' && id ? this.socioPayment.options(id).pipe(startWith(undefined)) : of(undefined)
      )
    );

    this.view$ = this.page$.pipe(
      switchMap((page) =>
        !page
          ? of(null)
          : combineLatest([
              this.userAccount.get().pipe(startWith(undefined)),
              cart$,
              store$,
              this.deliveryAddress.getDeliveryAddressState().pipe(map((s) => s.data)),
              this.deliveryModes.getSelectedDeliveryModeState().pipe(map((s) => s.data)),
              payment$,
            ]).pipe(
              map(([user, cart, storeName, address, mode, pay]) => {
                const steps: DoneStep[] = [
                  {
                    title: 'cpay.personal',
                    l1: [[user?.firstName, user?.lastName].filter((x) => !!x).join(' ') || user?.name, user?.uid]
                      .filter((x) => !!x)
                      .join(' · '),
                    route: 'updateProfile',
                  },
                  this.deliveryStep(cart, storeName, address, mode),
                ];
                if (page === 'review' && pay?.method) {
                  const key = pay.planCode ? 'spay.m.' + pay.method : 'spay.m.store.' + pay.method;
                  const name = this.i18n.t(key) === key ? this.i18n.t('spay.m.' + pay.method) : this.i18n.t(key);
                  steps.push({
                    title: 'cpay.payment',
                    l1: name,
                    l2: this.i18n.money(pay.total),
                    route: 'checkoutPaymentDetails',
                  });
                }
                return { steps };
              })
            )
      )
    );
  }

  ngOnInit(): void {
    this.subscription.add(
      this.page$.subscribe((page) => {
        const html = this.document.documentElement;
        const set = (cls: string, on: boolean) =>
          on ? this.renderer.addClass(html, cls) : this.renderer.removeClass(html, cls);
        set('fla-co-pay', !!page);
        set('fla-co-review', page === 'review');
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.renderer.removeClass(this.document.documentElement, 'fla-co-pay');
    this.renderer.removeClass(this.document.documentElement, 'fla-co-review');
  }

  /** Loja de retirada quando todos os itens físicos vão para a mesma loja */
  protected pickupStore(cart: Cart): string {
    const physical = this.physical(cart);
    const stores = [...new Set(physical.map((e) => e.deliveryPointOfService?.name ?? ''))];
    return physical.length && stores.length === 1 ? stores[0] : '';
  }

  protected physical(cart: Cart): OrderEntry[] {
    return (cart?.entries ?? []).filter((e) => !DIGITAL.test(e.product?.code ?? ''));
  }

  protected deliveryStep(cart: Cart, storeName: string, address?: Address, mode?: any): DoneStep {
    const physical = this.physical(cart);
    const hasDigital = (cart?.entries ?? []).some((e) => DIGITAL.test(e.product?.code ?? '') && !/^FLA-PERS-/.test(e.product?.code ?? ''));
    const digitalNote = hasDigital ? this.i18n.t('cpay.digitalNote') : '';
    const join = (...xs: (string | undefined)[]) => xs.filter((x) => !!x).join(' · ');
    const step: DoneStep = { title: 'cpay.delivery', l1: '', route: 'checkoutDeliveryAddress' };
    if (physical.length && storeName && physical.every((e) => !!e.deliveryPointOfService)) {
      step.l1 = this.i18n.t('cpay.pickupAt', { store: storeName });
      step.l2 = join(this.i18n.t('cdl.pickupFree'), digitalNote);
      return step;
    }
    if (physical.length && address) {
      step.l1 = join(this.i18n.t('cpay.home'), address.line1, address.line2, address.town, address.postalCode);
      step.l2 = join(mode?.name, mode?.description, digitalNote);
      return step;
    }
    step.l1 = mode?.name || this.i18n.t('cpay.digitalOnly');
    step.l2 = join(mode?.description, digitalNote);
    return step;
  }
}

/**
 * Resumo do pedido no pagamento (mockup): itens com miniatura (manto com a personalização),
 * cupons, subtotal, desconto, frete com o nome da forma de entrega, total e o tempo da poltrona
 * reservada. No pagamento e na revisão fica no lugar do resumo do Spartacus.
 * Entra pelo outlet do slot SideContent (antes).
 */
@Component({
  selector: 'fla-checkout-summary',
  template: `
    <aside class="fla-co-box fla-ci-summary fla-cs" *ngIf="view$ | async as v" aria-labelledby="fla-cs-title">
      <div class="fla-ci-summary-head">
        <h2 id="fla-cs-title" class="fla-co-title">{{ 'cp.summary' | flaT }}</h2>
        <a [routerLink]="{ cxRoute: 'cart' } | cxUrl">{{ 'ci.changeCart' | flaT }}</a>
      </div>
      <ul class="fla-ci-items">
        <li *ngFor="let e of v.cart.entries">
          <span class="fla-ci-thumb">
            <cx-media [container]="e.entry.product?.images?.PRIMARY" format="thumbnail" [alt]="''"></cx-media>
          </span>
          <span class="fla-ci-item">
            <strong>{{ e.name }}</strong>
            <span *ngIf="e.details">{{ e.details }}</span>
            <span *ngIf="e.personalization" class="fla-cp-pers">{{ 'pz.cartLine' | flaT: { v: e.personalization } }}</span>
            <span>{{ 'ci.units' | flaT: { n: e.entry.quantity } }}</span>
          </span>
          <strong>{{ e.price }}</strong>
        </li>
      </ul>
      <ul class="fla-cp-vouchers" *ngIf="v.cart.vouchers.length">
        <li *ngFor="let c of v.cart.vouchers"><span>{{ c.name }}</span></li>
      </ul>
      <div class="fla-cp-line"><span>{{ 'cd.subtotal' | flaT }}</span><span>{{ v.cart.subtotal }}</span></div>
      <div class="fla-cp-line fla-cp-discount" *ngIf="v.discount"><span>{{ v.cart.discountLabel }}</span><span>− {{ v.discount }}</span></div>
      <div class="fla-cp-line fla-cp-discount" *ngIf="v.pixOff"><span>{{ 'cs.pixDiscount' | flaT: { p: v.pixPercent } }}</span><span>− {{ v.pixOff }}</span></div>
      <div class="fla-cp-line" *ngIf="v.delivery">
        <span>{{ 'cp.shipping' | flaT }}<ng-container *ngIf="v.mode"> · {{ v.mode }}</ng-container></span>
        <span>{{ v.delivery }}</span>
      </div>
      <div class="fla-cp-line fla-cp-total"><span>{{ 'cd.total' | flaT }}</span><span>{{ v.total }}</span></div>
      <p class="fla-cs-hold" *ngIf="v.cart.holdLeft">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
        {{ 'cp.hold' | flaT: { t: v.cart.holdLeft } }}
      </p>
    </aside>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutSummaryComponent {
  // frete e total do próprio carrinho (o carrinho da loja traz só o valor formatado do frete:
  // somar no resumo deixava o frete de fora); "Grátis" com frete zero
  readonly view$ = combineLatest([
    this.routing.getRouterState().pipe(map((s) => payPage(s?.state?.url ?? ''))),
    this.cartView.view$,
    this.activeCart.getActive(),
    this.deliveryModes.getSelectedDeliveryModeState().pipe(map((s) => s.data?.name)),
    this.pixDiscount$(),
  ]).pipe(
    map(([page, cart, active, mode, pix]) => {
      if (!page || !cart) {
        return null;
      }
      const cost = active?.deliveryCost;
      const free = cost?.value === 0 || /^\D*0,00$/.test(cost?.formattedValue ?? '');
      // desconto do Pix em linha própria (06/11): sai da linha de desconto de sócio/cupom
      const totalOff = active?.totalDiscounts?.value ?? 0;
      const pixOff = pix && pix.off > 0.004 && pix.off <= totalOff + 0.004 ? pix.off : 0;
      const rest = totalOff - pixOff;
      return {
        cart,
        mode,
        discount: pixOff ? (rest > 0.004 ? this.i18n.money(rest) : undefined) : cart.discount,
        pixOff: pixOff ? this.i18n.money(pixOff) : undefined,
        pixPercent: pix?.percent,
        delivery: cost ? (free ? this.i18n.t('cs.free') : cost.formattedValue) : undefined,
        total: active?.totalPriceWithTax?.formattedValue || active?.totalPrice?.formattedValue || cart.total,
      };
    })
  );

  constructor(
    protected routing: RoutingService,
    protected cartView: FlaCartViewService,
    protected activeCart: ActiveCartFacade,
    protected deliveryModes: CheckoutDeliveryModesFacade,
    protected i18n: FlaI18nService,
    protected payment: SocioPaymentService
  ) {}

  /**
   * Desconto do Pix já aplicado no carrinho: com o Pix escolhido, o servidor devolve o total sem o
   * desconto (baseTotal) e com ele (pixTotal). Consulta de novo quando o total do carrinho muda.
   */
  protected pixDiscount$(): Observable<{ off: number; percent: number } | null> {
    return this.activeCart.getActive().pipe(
      map((c) => (c?.code ? `${c.code}|${c.totalPriceWithTax?.value ?? c.totalPrice?.value ?? ''}` : '')),
      distinctUntilChanged(),
      switchMap((key) =>
        key
          ? this.payment.options(key.split('|')[0]).pipe(
              map((p) =>
                p.method === 'PIX' && p.pixPercent && p.baseTotal && p.pixTotal
                  ? { off: Math.round((p.baseTotal - p.pixTotal) * 100) / 100, percent: p.pixPercent }
                  : null
              )
            )
          : of(null)
      ),
      startWith(null)
    );
  }
}
