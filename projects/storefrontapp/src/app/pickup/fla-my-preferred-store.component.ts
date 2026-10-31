import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  inject,
} from '@angular/core';
import {
  CmsService,
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
    if (content !== this.lastContent || !this.lastTranslated) {
      const label = (text: string) => this.labels[text] || text;
      this.lastContent = content;
      this.lastTranslated = {
        ...content,
        header: label(content.header),
        actions: content.actions.map((action) => ({
          ...action,
          name: label(action.name),
        })),
      };
    }
    return this.lastTranslated;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
