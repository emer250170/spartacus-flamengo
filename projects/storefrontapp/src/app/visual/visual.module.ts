import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import {
  CmsConfig,
  I18nModule,
  OccConfig,
  UrlModule,
  provideConfig,
} from '@spartacus/core';
import {
  DIALOG_TYPE,
  IconModule,
  KeyboardFocusModule,
  LayoutConfig,
  MediaModule,
  OutletPosition,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import {
  FlaFooterBottomComponent,
  FlaFooterInfoComponent,
  FlaPortalLinksComponent,
  FlaPortalNavComponent,
  FlaTicketsButtonComponent,
} from './fla-header.components';
import { FlaCartDrawerComponent } from './fla-cart-drawer.component';
import { FlaLogoLabelComponent } from './fla-logo-label.component';
import { FlaMiniCartComponent } from './fla-mini-cart.component';
import {
  FLA_SIZES_ENDPOINT,
  FLA_SIZES_SCOPE,
  FlaPdpHeaderComponent,
  FlaSizeChipsComponent,
} from './fla-pdp.components';

/**
 * Visual da loja oficial (flamengo.com.br/loja, mockup aprovado em 05/11): header em três
 * faixas e rodapé escuro. Os componentes entram pelos outlets dos slots do header e do
 * rodapé; a disposição fica no tema (tema-flamengo.scss, seção "Visual da loja oficial").
 * Desligar: tirar VisualModule do AppModule e a seção do tema.
 */
@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    I18nModule,
    UrlModule,
    IconModule,
    MediaModule,
    KeyboardFocusModule,
    FlaI18nModule,
  ],
  declarations: [
    FlaPortalLinksComponent,
    FlaPortalNavComponent,
    FlaTicketsButtonComponent,
    FlaFooterInfoComponent,
    FlaFooterBottomComponent,
    FlaLogoLabelComponent,
    FlaMiniCartComponent,
    FlaCartDrawerComponent,
    FlaPdpHeaderComponent,
    FlaSizeChipsComponent,
  ],
  providers: [
    // página do produto: código, nome, preço com parcelas e preço de sócio no topo do
    // resumo; tamanhos em botões
    provideOutlet({
      id: 'ProductIntroComponent',
      position: OutletPosition.BEFORE,
      component: FlaPdpHeaderComponent,
    }),
    provideOutlet({
      id: 'ProductMultiDimensionalSelectorComponent',
      position: OutletPosition.AFTER,
      component: FlaSizeChipsComponent,
    }),
    provideConfig(<OccConfig>{
      backend: {
        occ: { endpoints: { product: { [FLA_SIZES_SCOPE]: FLA_SIZES_ENDPOINT } } },
      },
    }),
    // carrinho em box lateral à direita: ao clicar no carrinho do header e ao adicionar
    // um produto (no lugar do "Adicionado ao carrinho" do Spartacus)
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MiniCartComponent: { component: FlaMiniCartComponent },
      },
    }),
    provideConfig(<LayoutConfig>{
      launch: {
        ADDED_TO_CART: {
          inlineRoot: true,
          component: FlaCartDrawerComponent,
          dialogType: DIALOG_TYPE.SIDEBAR_END,
        },
      },
    }),
    // faixa de cima: links do portal (no lugar dos links do CMS, no desktop)
    provideOutlet({
      id: 'SiteLinks',
      position: OutletPosition.BEFORE,
      component: FlaPortalLinksComponent,
    }),
    // barra vermelha: menu do portal depois do logo
    provideOutlet({
      id: 'SiteLogo',
      position: OutletPosition.AFTER,
      component: FlaPortalNavComponent,
    }),
    // logo sem o tooltip "SAP Commerce" do CMS (tira os title, nome para leitor de tela)
    provideOutlet({
      id: 'SiteLogo',
      position: OutletPosition.AFTER,
      component: FlaLogoLabelComponent,
    }),
    // barra vermelha: "Ingressos" antes da busca
    provideOutlet({
      id: 'SearchBox',
      position: OutletPosition.BEFORE,
      component: FlaTicketsButtonComponent,
    }),
    // rodapé: endereços antes dos links do CMS e a linha final depois
    provideOutlet({
      id: 'Footer',
      position: OutletPosition.BEFORE,
      component: FlaFooterInfoComponent,
    }),
    provideOutlet({
      id: 'Footer',
      position: OutletPosition.AFTER,
      component: FlaFooterBottomComponent,
    }),
  ],
})
export class VisualModule {}
