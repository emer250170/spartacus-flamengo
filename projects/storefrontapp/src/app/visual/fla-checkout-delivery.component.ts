import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Inject,
  OnDestroy,
  OnInit,
  Renderer2,
} from '@angular/core';
import { ActiveCartFacade, Cart, OrderEntry } from '@spartacus/cart/base/root';
import { CheckoutDeliveryModesFacade } from '@spartacus/checkout/base/root';
import { GlobalMessageService, GlobalMessageType, RoutingService } from '@spartacus/core';
import { PreferredStoreFacade } from '@spartacus/pickup-in-store/root';
import { UserAccountFacade } from '@spartacus/user/account/root';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import { filter, map, startWith, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { FlaPersonalizationService, PERSONALIZATION_ADDON } from './fla-personalization.service';
import { FlaStoreSearchService, GeoPoint, PickupStore, RIO } from './fla-store-search.service';

/** Itens sem frete nem retirada: ingresso, estacionamento, comida, experiência, plano, poltrona */
const DIGITAL = /^(JOGO-|EXP-|NACAO-|FLA-EST-|FLA-FOOD-|FLA-PERSONALIZACAO)/;

interface DeliveryView {
  name: string;
  email: string;
  /** itens físicos que podem ir para retirada (sem as linhas de preço da personalização) */
  physical: OrderEntry[];
  /** há ingresso/estacionamento/comida no carrinho */
  hasDigital: boolean;
  /** todos os itens físicos podem ser retirados */
  canPickup: boolean;
  /** loja em que os itens físicos estão marcados para retirada (todos na mesma) */
  pickupStore?: string;
  pickupStoreName?: string;
  /** só há itens físicos (e linhas de preço): retirada dispensa endereço e forma de entrega */
  onlyPhysical: boolean;
}

/**
 * Entrega no visual da loja oficial (mockup "Checkout — Entrega", 06/11), no topo da etapa de
 * endereço: dados pessoais, aviso dos itens digitais e a escolha "Receber em casa" ou "Retirar
 * na loja". Na retirada, a busca de lojas (CEP, cidade ou endereço, ou a localização do
 * navegador) com estoque por loja (de todos os itens), ocultar lojas sem estoque, horários,
 * distância e "Definir como minha loja"; "Retirar aqui" marca os itens físicos para aquela loja
 * (as linhas de preço da personalização vão junto, pelo servidor). Com só itens físicos, a
 * retirada dispensa endereço e forma de entrega: "Ir para o pagamento" grava a forma "pickup" e
 * segue. Em casa, ficam o endereço e a forma de entrega do Spartacus, como antes.
 * Entra pelo outlet CheckoutDeliveryAddress (antes).
 */
@Component({
  selector: 'fla-checkout-delivery',
  templateUrl: './fla-checkout-delivery.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutDeliveryComponent implements OnInit, OnDestroy {
  mode: 'home' | 'pickup' = 'home';
  stores?: PickupStore[];
  point: GeoPoint = RIO;
  hideOutOfStock = false;
  searching = false;
  applying = false;
  searchError = '';
  openHours = '';
  preferred?: string;

  readonly view$: Observable<DeliveryView | null>;
  protected subscription = new Subscription();
  protected cart?: Cart;
  protected productCodes: string[] = [];

  constructor(
    protected activeCart: ActiveCartFacade,
    protected userAccount: UserAccountFacade,
    protected storeSearch: FlaStoreSearchService,
    protected preferredStore: PreferredStoreFacade,
    protected personalization: FlaPersonalizationService,
    protected deliveryModes: CheckoutDeliveryModesFacade,
    protected routing: RoutingService,
    protected messages: GlobalMessageService,
    protected i18n: FlaI18nService,
    protected renderer: Renderer2,
    protected cd: ChangeDetectorRef,
    @Inject(DOCUMENT) protected document: Document
  ) {
    this.view$ = combineLatest([
      this.activeCart.getActive(),
      this.userAccount.get().pipe(startWith(undefined)),
    ]).pipe(
      map(([cart, user]) => {
        this.cart = cart;
        const entries = cart?.entries ?? [];
        if (!entries.length) {
          return null;
        }
        const code = (e: OrderEntry) => e.product?.code ?? '';
        const physical = entries.filter(
          (e) => !DIGITAL.test(code(e)) && !PERSONALIZATION_ADDON.test(code(e)) && !/^JOGO-/.test(code(e))
        );
        const stores = [...new Set(physical.map((e) => e.deliveryPointOfService?.name ?? ''))];
        const pickupStore = physical.length && stores.length === 1 && stores[0] ? stores[0] : undefined;
        this.productCodes = physical.map(code);
        return {
          name: [user?.firstName, user?.lastName].filter((x) => !!x).join(' ') || user?.name || '',
          email: user?.uid ?? '',
          physical,
          hasDigital: entries.some((e) => DIGITAL.test(code(e))),
          canPickup: physical.length > 0 && physical.every((e) => e.product?.availableForPickup !== false),
          pickupStore,
          pickupStoreName: physical.find((e) => e.deliveryPointOfService)?.deliveryPointOfService?.displayName,
          onlyPhysical: entries.every((e) => !DIGITAL.test(code(e))),
        };
      })
    );
  }

  ngOnInit(): void {
    // o modo inicial segue o carrinho (já marcado para retirada = "Retirar na loja")
    this.subscription.add(
      this.view$.pipe(filter((v): v is DeliveryView => !!v), take(1)).subscribe((v) => {
        this.setMode(v.pickupStore ? 'pickup' : 'home', v, false);
      })
    );
    this.subscription.add(this.clearStaleMode().subscribe({ error: () => undefined }));
    this.subscription.add(
      this.preferredStore.getPreferredStore$().subscribe((p) => {
        this.preferred = p?.name;
        this.cd.markForCheck();
      })
    );
  }

  /**
   * Forma de entrega que deixou de valer (08/11): o carrinho guarda a forma escolhida antes (ex.:
   * frete de R$ 19,90 de um produto que saiu) mesmo quando ela não está mais entre as permitidas
   * (carrinho só com experiência = só a entrega digital, R$ 0). O resumo desta tela mostrava esse
   * frete. Aqui a forma é apagada; o carrinho recarrega sem frete e o torcedor escolhe no passo
   * seguinte. Retirada na loja ("pickup") não vem na lista; sem lista (sem endereço ou erro), nada muda.
   */
  protected clearStaleMode(): Observable<unknown> {
    return combineLatest([
      this.deliveryModes.getSelectedDeliveryModeState().pipe(filter((st) => !st.loading)),
      this.deliveryModes.getSupportedDeliveryModesState().pipe(filter((st) => !st.loading)),
    ]).pipe(
      take(1),
      map(([selected, supported]) => {
        const code = selected.data?.code;
        const modes = supported.data ?? [];
        return !!code && code !== 'pickup' && modes.length > 0 && !modes.some((m) => m.code === code);
      }),
      filter((stale) => stale),
      switchMap(() => this.deliveryModes.clearCheckoutDeliveryMode())
    );
  }

  setMode(mode: 'home' | 'pickup', v: DeliveryView, apply = true): void {
    this.mode = mode;
    this.toggleAddress(v);
    if (mode === 'pickup' && !this.stores) {
      this.search(RIO);
    }
    if (apply && mode === 'home' && v.pickupStore) {
      // volta os itens para entrega em casa
      this.applying = true;
      v.physical.forEach((e) =>
        this.activeCart.updateEntry(e.entryNumber as number, e.quantity ?? 1, undefined, true)
      );
      // a forma "pickup" gravada no "Ir para o pagamento" não vale mais: o torcedor escolhe o frete
      // (o carrinho ativo não traz a forma; ela vem do estado do checkout)
      this.deliveryModes
        .getSelectedDeliveryModeState()
        .pipe(
          filter((st) => !st.loading),
          take(1),
          filter((st) => st.data?.code === 'pickup'),
          switchMap(() => this.deliveryModes.clearCheckoutDeliveryMode())
        )
        .subscribe({ error: () => undefined });
      this.afterChange();
    }
    this.cd.markForCheck();
  }

  /** Na retirada só de itens físicos, o endereço do Spartacus sai (não é preciso) */
  protected toggleAddress(v: DeliveryView): void {
    const html = this.document?.documentElement;
    if (!html) {
      return;
    }
    if (this.mode === 'pickup' && v.onlyPhysical) {
      this.renderer.addClass(html, 'fla-co-pickup');
    } else {
      this.renderer.removeClass(html, 'fla-co-pickup');
    }
  }

  searchText(event: Event, value: string): void {
    event.preventDefault();
    if (!value.trim()) {
      return;
    }
    this.searching = true;
    this.searchError = '';
    this.storeSearch.geocode(value).subscribe((p) => {
      if (!p) {
        this.searching = false;
        this.searchError = this.i18n.t('cdl.notFound');
        this.cd.markForCheck();
        return;
      }
      this.search(p);
    });
  }

  useLocation(): void {
    if (!navigator?.geolocation) {
      this.searchError = this.i18n.t('cdl.noGeo');
      return;
    }
    this.searching = true;
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        this.search({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          label: this.i18n.t('cdl.myLocation'),
        }),
      () => {
        this.searching = false;
        this.searchError = this.i18n.t('cdl.noGeo');
        this.cd.markForCheck();
      },
      { timeout: 10000 }
    );
  }

  protected search(point: GeoPoint): void {
    this.point = point;
    this.searching = true;
    this.cd.markForCheck();
    this.storeSearch.stores(point, this.productCodes).subscribe((stores) => {
      this.stores = stores;
      this.searching = false;
      this.cd.markForCheck();
    });
  }

  /** Nome da loja escolhida (o carrinho só traz o código; o nome vem da busca) */
  storeLabel(v: DeliveryView): string {
    return (
      this.stores?.find((s) => s.name === v.pickupStore)?.displayName ||
      v.pickupStoreName ||
      v.pickupStore ||
      ''
    );
  }

  visible(stores: PickupStore[]): PickupStore[] {
    return this.hideOutOfStock ? stores.filter((s) => s.inStock) : stores;
  }

  stockLabel(s: PickupStore): string {
    if (!s.inStock) {
      return this.i18n.t('cdl.outOfStock');
    }
    if (s.stock === undefined) {
      return this.i18n.t('cdl.available');
    }
    return s.stock <= 2 ? this.i18n.t('cdl.last', { n: s.stock }) : this.i18n.t('cdl.stock', { n: s.stock });
  }

  todayLabel(s: PickupStore): string {
    if (!s.today) {
      return '';
    }
    return s.today === 'closed' ? this.i18n.t('cdl.closedToday') : this.i18n.t('cdl.openToday', { h: s.today });
  }

  toggleHours(s: PickupStore): void {
    this.openHours = this.openHours === s.name ? '' : s.name;
  }

  setPreferred(s: PickupStore): void {
    this.preferredStore.setPreferredStore({ name: s.name, displayName: s.displayName });
  }

  /** "Retirar aqui": todos os itens físicos para a loja; as linhas de preço vão junto */
  choose(s: PickupStore, v: DeliveryView): void {
    if (!s.inStock || this.applying) {
      return;
    }
    this.applying = true;
    v.physical.forEach((e) =>
      this.activeCart.updateEntry(e.entryNumber as number, e.quantity ?? 1, s.name, false)
    );
    this.afterChange();
  }

  /** Depois de marcar ou desmarcar a retirada: linhas de preço com a loja do manto e recarrega */
  protected afterChange(): void {
    this.activeCart
      .isStable()
      .pipe(filter((stable) => stable), take(1))
      .subscribe(() => {
        const code = this.cart?.code;
        const hasAddOns = (this.cart?.entries ?? []).some((e) =>
          PERSONALIZATION_ADDON.test(e.product?.code ?? '')
        );
        (code && hasAddOns ? this.personalization.sync('current', code) : of([])).subscribe(() => {
          if (hasAddOns) {
            this.activeCart.reloadActiveCart();
          }
          this.applying = false;
          this.cd.markForCheck();
        });
      });
  }

  /** Retirada só de itens físicos: forma "pickup" e segue para o pagamento */
  goToPayment(v: DeliveryView): void {
    if (!v.pickupStore) {
      return;
    }
    if (!v.onlyPhysical) {
      this.routing.go({ cxRoute: 'checkoutDeliveryMode' });
      return;
    }
    this.applying = true;
    this.deliveryModes.setDeliveryMode('pickup').subscribe({
      next: () => this.routing.go({ cxRoute: 'checkoutPaymentDetails' }),
      error: () => {
        this.applying = false;
        this.messages.add(this.i18n.t('cdl.modeError'), GlobalMessageType.MSG_TYPE_ERROR);
        this.cd.markForCheck();
      },
    });
  }

  trackStore(_i: number, s: PickupStore): string {
    return s.name;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.renderer.removeClass(this.document.documentElement, 'fla-co-pickup');
  }
}
