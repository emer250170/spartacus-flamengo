import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CmsConfig, I18nModule, provideConfig } from '@spartacus/core';
import {
  FacetListModule,
  ListNavigationModule,
  OutletPosition,
  ProductListModule,
  ProductListOutlets,
  ViewConfig,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaProductCardPriceComponent } from './fla-product-card-price.component';
import { FlaProductListComponent } from './fla-product-list.component';
import { FlaSearchBandComponent } from './fla-search-band.component';

/**
 * Listagem de produtos no layout da loja oficial (04/11): grade sempre, "Filtrar" em painel,
 * "Carregar mais produtos" no lugar da paginação, preço e parcelas no card. Desligar: tirar
 * ListagemModule do AppModule (volta a listagem padrão do Spartacus).
 */
@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    I18nModule,
    FlaI18nModule,
    ProductListModule,
    FacetListModule,
    ListNavigationModule,
  ],
  declarations: [FlaProductListComponent, FlaProductCardPriceComponent, FlaSearchBandComponent],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        CMSProductListComponent: { component: FlaProductListComponent },
        ProductGridComponent: { component: FlaProductListComponent },
        SearchResultsListComponent: { component: FlaProductListComponent },
      },
    }),
    provideConfig(<ViewConfig>{
      view: {
        infiniteScroll: { active: true, productLimit: 0, showMoreButton: true },
      },
    }),
    // resultado da busca (mockup 2a/2b, 08/11): faixa escura no lugar da trilha cinza
    provideOutlet({
      id: 'BottomHeaderSlot',
      position: OutletPosition.BEFORE,
      component: FlaSearchBandComponent,
    }),
    provideOutlet({
      id: ProductListOutlets.ITEM_DETAILS,
      position: OutletPosition.AFTER,
      component: FlaProductCardPriceComponent,
    }),
  ],
})
export class ListagemModule {}
