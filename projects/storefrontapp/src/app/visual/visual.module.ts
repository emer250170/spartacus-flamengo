import {
  FlaCheckoutDoneComponent,
  FlaCheckoutSummaryComponent,
} from './fla-checkout-payment.component';
import {
  FlaCheckoutAcceptComponent,
  FlaCheckoutAcceptModule,
} from './fla-checkout-accept';
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
  OutletModule,
  OutletPosition,
  PageComponentModule,
  TabModule,
  provideOutlet,
} from '@spartacus/storefront';
import {
  FlaPdpGalleryComponent,
  FlaPdpTabsComponent,
} from './fla-pdp-extra.components';
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
import {
  FlaCheckoutIdentityExtraComponent,
  FlaCheckoutIdentityIntroComponent,
} from './fla-checkout-identity.component';
import { FlaCheckoutDeliveryComponent } from './fla-checkout-delivery.component';
import {
  FlaOcBodyComponent,
  FlaOcHeroComponent,
} from './fla-order-confirmation.component';
import { FlaItemPersonalizationComponent } from './fla-item-personalization.component';
import { CartOutlets } from '@spartacus/cart/base/root';
import { EstudioSharedModule } from '../estudio/estudio-shared.module';

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
    FlaCheckoutAcceptModule,
    OutletModule,
    PageComponentModule,
    TabModule,
    EstudioSharedModule,
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
    FlaPdpGalleryComponent,
    FlaPdpTabsComponent,
    FlaPdpPersonalizeComponent,
    FlaPdpShippingComponent,
    FlaCartPageComponent,
    FlaCheckoutHeaderComponent,
    FlaCheckoutFooterComponent,
    FlaCheckoutIdentityIntroComponent,
    FlaCheckoutIdentityExtraComponent,
    FlaCheckoutDeliveryComponent,
    FlaCheckoutDoneComponent,
    FlaCheckoutSummaryComponent,
    FlaOcHeroComponent,
    FlaOcBodyComponent,
    FlaItemPersonalizationComponent,
  ],
  // "Calcule o frete" também no configurador do Mengo Estúdio
  exports: [FlaPdpShippingComponent],
  providers: [
    // "Personalizado: JUNIOR · 9" nas listas de itens padrão (detalhe do pedido em Meus pedidos,
    // itens da entrega no checkout)
    provideOutlet({
      id: CartOutlets.ITEM_DETAILS,
      position: OutletPosition.AFTER,
      component: FlaItemPersonalizationComponent,
    }),
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
      id: 'ProductAddToCartComponent',
      position: OutletPosition.AFTER,
      component: FlaPdpShippingComponent,
    }),
    provideConfig(<OccConfig>{
      backend: {
        occ: {
          endpoints: { product: { [FLA_SIZES_SCOPE]: FLA_SIZES_ENDPOINT } },
        },
      },
    }),
    // carrinho em box lateral à direita: ao clicar no carrinho do header e ao adicionar
    // um produto (no lugar do "Adicionado ao carrinho" do Spartacus)
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MiniCartComponent: { component: FlaMiniCartComponent },
        // página do carrinho no formato do mockup (lista + Resumo)
        CartComponent: { component: FlaCartPageComponent },
        // página do produto (07/11, mockup tela 4): fotos em grade com ampliar no clique e
        // abas como sanfonas ("Sobre o produto" aberta) na coluna da direita
        ProductImagesComponent: { component: FlaPdpGalleryComponent },
        CMSTabParagraphContainer: { component: FlaPdpTabsComponent },
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
    // login a caminho do checkout (Identificação): título antes do formulário; motivos,
    // próximas etapas e Resumo depois
    provideOutlet({
      id: 'LeftContentSlot',
      position: OutletPosition.BEFORE,
      component: FlaCheckoutIdentityIntroComponent,
    }),
    provideOutlet({
      id: 'LeftContentSlot',
      position: OutletPosition.AFTER,
      component: FlaCheckoutIdentityExtraComponent,
    }),
    // Entrega: dados pessoais, receber em casa ou retirar na loja (com a busca de lojas)
    provideOutlet({
      id: 'CheckoutDeliveryAddress',
      position: OutletPosition.BEFORE,
      component: FlaCheckoutDeliveryComponent,
    }),
    // Pagamento e revisão: etapas feitas e o título "Pagamento" no topo da coluna principal,
    // e o resumo com os itens no lugar do resumo do Spartacus
    provideOutlet({
      id: 'BodyContent',
      position: OutletPosition.BEFORE,
      component: FlaCheckoutDoneComponent,
    }),
    provideOutlet({
      id: 'SideContent',
      position: OutletPosition.BEFORE,
      component: FlaCheckoutSummaryComponent,
    }),
    // Confirmação do pedido (mockup, 06/11): topo (antes do Pix/boleto e do "Seu dia de jogo")
    // e corpo com itens, entrega, pagamento e pontos (depois); os componentes padrão somem no tema
    provideOutlet({
      id: 'BodyContent',
      position: OutletPosition.BEFORE,
      component: FlaOcHeroComponent,
    }),
    provideOutlet({
      id: 'BodyContent',
      position: OutletPosition.AFTER,
      component: FlaOcBodyComponent,
    }),
    // Pagamento: Aceites (Termos e Privacidade) antes do "Finalizar pedido" do Spartacus
    provideOutlet({
      id: 'CheckoutPlaceOrder',
      position: OutletPosition.BEFORE,
      component: FlaCheckoutAcceptComponent,
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
