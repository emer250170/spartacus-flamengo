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
  FlaLanguageComponent,
  FlaPortalLinksComponent,
  FlaPortalNavComponent,
  FlaStoreHomeLinkComponent,
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
import { FlaPdpPersonalizeComponent } from './fla-pdp-personalize.component';
import { FlaPdpShippingComponent } from './fla-pdp-shipping.component';
import { FlaCartPageComponent } from './fla-cart-page.component';
import {
  FlaCheckoutFooterComponent,
  FlaCheckoutHeaderComponent,
} from './fla-checkout-header.component';

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
    FlaLanguageComponent,
    FlaStoreHomeLinkComponent,
    FlaFooterInfoComponent,
    FlaFooterBottomComponent,
    FlaLogoLabelComponent,
    FlaMiniCartComponent,
    FlaCartDrawerComponent,
    FlaPdpHeaderComponent,
    FlaSizeChipsComponent,
    FlaPdpPersonalizeComponent,
    FlaPdpShippingComponent,
    FlaCartPageComponent,
    FlaCheckoutHeaderComponent,
    FlaCheckoutFooterComponent,
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
    // página do produto: "Personalizar" (nome e número, patrocínio, patch) depois dos tamanhos
    provideOutlet({
      id: 'ProductMultiDimensionalSelectorComponent',
      position: OutletPosition.AFTER,
      component: FlaPdpPersonalizeComponent,
    }),
    // página do produto: "Calcule o frete" e trocas e devoluções depois do botão do carrinho
    provideOutlet({
      id: 'AddToCartComponent',
      position: OutletPosition.AFTER,
      component: FlaPdpShippingComponent,
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
        // página do carrinho no formato do mockup (lista + Resumo)
        CartComponent: { component: FlaCartPageComponent },
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
    // barra vermelha: "Ingressos ▾" (lista dos próximos jogos) antes da busca
    provideOutlet({
      id: 'SearchBox',
      position: OutletPosition.BEFORE,
      component: FlaTicketsButtonComponent,
    }),
    // faixa de cima: idioma compacto (globo + sigla) no lugar do seletor do CMS, no desktop
    provideOutlet({
      id: 'SiteContext',
      position: OutletPosition.BEFORE,
      component: FlaLanguageComponent,
    }),
    // faixa preta: "Loja Oficial" antes das categorias
    provideOutlet({
      id: 'NavigationBar',
      position: OutletPosition.BEFORE,
      component: FlaStoreHomeLinkComponent,
    }),
    // carrinho, checkout e confirmação: cabeçalho com as etapas da compra e rodapé curto
    provideOutlet({
      id: 'cx-header',
      position: OutletPosition.BEFORE,
      component: FlaCheckoutHeaderComponent,
    }),
    provideOutlet({
      id: 'cx-footer',
      position: OutletPosition.AFTER,
      component: FlaCheckoutFooterComponent,
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
