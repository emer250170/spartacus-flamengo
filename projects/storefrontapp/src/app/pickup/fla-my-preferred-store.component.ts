import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  inject,
} from '@angular/core';
import {
  CmsService,
  PointOfService,
  RoutingService,
  TranslationService,
} from '@spartacus/core';
import { MyPreferredStoreComponent } from '@spartacus/pickup-in-store/components';
import {
  PickupLocationsSearchFacade,
  PreferredStoreFacade,
} from '@spartacus/pickup-in-store/root';
import { StoreFinderFacade } from '@spartacus/storefinder/root';
import { Subscription, combineLatest } from 'rxjs';

type CardContent = MyPreferredStoreComponent['content'];

/** Nomes fixos em inglês no componente do Spartacus -> chave de tradução */
const LABELS: Record<string, string> = {
  'My Store': 'setPreferredStore.myStore',
  'Get Directions': 'storeFinder.getDirections',
  'Change Store': 'pickupOptions.changeStore',
};

/**
 * "Minha loja" (catálogo de endereços e localizador de lojas) traduzido.
 *
 * O componente do Spartacus monta o cartão com "My Store", "Get Directions"
 * e "Change Store" fixos no código, fora da tradução. Aqui o cartão recebe os
 * mesmos textos pelas chaves de tradução (Minha loja, Como chegar, Trocar
 * loja); o resto do comportamento é o do Spartacus.
 *
 * "Como chegar": o Spartacus monta o link com o serviço do localizador de
 * lojas, que é carregado sob demanda e devolve um Observable (o link saía
 * "[object Object]"), e antes de a loja carregar. Aqui o link é montado com as
 * coordenadas da loja (Google Maps, como no Spartacus) ou, sem coordenadas,
 * com o endereço.
 */
@Component({
  selector: 'cx-my-preferred-store',
  templateUrl: './fla-my-preferred-store.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMyPreferredStoreComponent
  extends MyPreferredStoreComponent
  implements OnDestroy
{
  private readonly translation = inject(TranslationService);
  private labels: Record<string, string> = {};
  private lastContent?: CardContent;
  private lastStore?: PointOfService;
  private lastTranslated?: CardContent;
  private readonly subscription: Subscription;

  constructor() {
    super(
      inject(PreferredStoreFacade),
      inject(PickupLocationsSearchFacade),
      inject(RoutingService),
      inject(StoreFinderFacade),
      inject(CmsService)
    );
    const names = Object.keys(LABELS);
    this.subscription = combineLatest(
      names.map((name) => this.translation.translate(LABELS[name]))
    ).subscribe((texts) => {
      this.labels = Object.fromEntries(names.map((n, i) => [n, texts[i]]));
      this.lastContent = undefined;
      this.cdr?.markForCheck();
    });
  }

  /** O mesmo objeto enquanto o conteúdo e os textos não mudam (o cartão não redesenha à toa) */
  translated(content: CardContent): CardContent {
    const store = this.pointOfService;
    if (
      content !== this.lastContent ||
      store !== this.lastStore ||
      !this.lastTranslated
    ) {
      const label = (text: string) => this.labels[text] || text;
      const directions = directionsUrl(store);
      this.lastContent = content;
      this.lastStore = store;
      this.lastTranslated = {
        ...content,
        header: label(content.header),
        actions: content.actions.map((action) => ({
          ...action,
          name: label(action.name),
          ...('link' in action && directions ? { link: directions } : {}),
        })),
      };
    }
    return this.lastTranslated;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

/** Rota até a loja no Google Maps: pelas coordenadas ou, sem elas, pelo endereço */
function directionsUrl(store?: PointOfService): string | undefined {
  const lat = store?.geoPoint?.latitude;
  const lng = store?.geoPoint?.longitude;
  if (lat !== undefined && lng !== undefined && (lat !== 0 || lng !== 0)) {
    return `https://www.google.com/maps/dir/Current+Location/${lat},${lng}`;
  }
  const address = store?.address;
  const text = [
    store?.displayName,
    address?.line1,
    address?.line2,
    address?.town,
    address?.postalCode,
    address?.country?.name,
  ]
    .filter(Boolean)
    .join(', ');
  return text
    ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(text)}`
    : undefined;
}
