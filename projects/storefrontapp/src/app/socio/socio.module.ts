import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CartOutlets } from '@spartacus/cart/base/root';
import { AuthGuard, CmsConfig, provideConfig } from '@spartacus/core';
import {
  CmsPageGuard,
  NavigationService,
  OutletPosition,
  SpinnerModule,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { SocioAccountComponent } from './socio-account.component';
import { SocioBadgeComponent } from './socio-badge.component';
import { SocioCardComponent } from './socio-card.component';
import { SocioCardPageComponent } from './socio-card-page.component';
import { SocioGuestsComponent } from './socio-guests.component';
import { SocioNavigationService } from './socio-navigation.service';
import { SocioPaymentChoiceComponent } from './socio-payment-choice.component';
import { SocioPaymentReviewComponent } from './socio-payment-review.component';
import { SocioPaymentStatusComponent } from './socio-payment-status.component';
import { SocioPlansComponent } from './socio-plans.component';
import { SocioPointsComponent } from './socio-points.component';
import { SocioTicketHolderComponent } from './socio-ticket-holder.component';
import { SocioTermCheckoutComponent } from './socio-term-checkout.component';
import { SocioTermNoticeComponent } from './socio-term-notice.component';
import { SocioTermPageComponent } from './socio-term-page.component';

/**
 * Sócio-torcedor Nação Maraca (protótipo Flamengo).
 * SocioPlansComponent -> página de conteúdo /socio-torcedor (CMSFlexComponent).
 * SocioAccountComponent -> Minha conta > Meu sócio-torcedor
 *   (/my-account/socio-torcedor, CMSFlexComponent, só logado).
 * SocioNavigationService -> esconde o link do menu de quem nunca foi sócio.
 * SocioBadgeComponent -> selo de sócio no header (antes do "Olá, Fulano").
 * SocioGuestsComponent -> Meus convidados, dentro de Meu sócio-torcedor.
 * Termo do Nação: SocioTermCheckoutComponent (antes do Finalizar pedido),
 *   SocioTermNoticeComponent (Meu sócio-torcedor) e SocioTermPageComponent
 *   (/socio-torcedor/termo).
 * Pix e boleto (item 7): SocioPaymentChoiceComponent (pagamento do checkout),
 *   SocioPaymentReviewComponent (revisão) e SocioPaymentStatusComponent (QR Code
 *   ou boleto na confirmação e em Meu sócio-torcedor).
 * Pontos e carteirinha (item 8): SocioPointsComponent (saldo, vitrine e vouchers)
 *   e SocioCardComponent (carteirinha) em Meu sócio-torcedor; SocioCardPageComponent
 *   (/socio-torcedor/carteirinha, para o celular).
 * O SocioService também é usado pela jornada de matchday (ondas e desconto).
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    // Termo do Nação: rota própria com o cabeçalho e o rodapé da página /socio-torcedor do CMS
    RouterModule.forChild([
      {
        path: 'socio-torcedor/termo',
        component: SocioTermPageComponent,
        canActivate: [CmsPageGuard],
        data: { pageLabel: '/socio-torcedor' },
      },
      // Carteirinha digital (item 8): tela cheia para o celular, só logado
      {
        path: 'socio-torcedor/carteirinha',
        component: SocioCardPageComponent,
        canActivate: [AuthGuard, CmsPageGuard],
        data: { pageLabel: '/socio-torcedor' },
      },
    ]),
    SpinnerModule,
    FlaI18nModule,
  ],
  declarations: [
    SocioPlansComponent,
    SocioBadgeComponent,
    SocioAccountComponent,
    SocioGuestsComponent,
    SocioTicketHolderComponent,
    SocioTermCheckoutComponent,
    SocioTermNoticeComponent,
    SocioTermPageComponent,
    SocioPaymentChoiceComponent,
    SocioPaymentReviewComponent,
    SocioPaymentStatusComponent,
    SocioPointsComponent,
    SocioCardComponent,
    SocioCardPageComponent,
  ],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        SocioPlansComponent: { component: SocioPlansComponent },
        SocioAccountComponent: {
          component: SocioAccountComponent,
          guards: [AuthGuard],
        },
      },
    }),
    // menu Minha conta: "Meu sócio-torcedor" só para quem é ou já foi sócio
    { provide: NavigationService, useExisting: SocioNavigationService },
    provideOutlet({
      id: 'SiteLogin',
      position: OutletPosition.BEFORE,
      component: SocioBadgeComponent,
    }),
    // "Ingresso de: Maria" na poltrona do convidado (carrinho, checkout e pedido)
    provideOutlet({
      id: CartOutlets.ITEM_DETAILS,
      position: OutletPosition.AFTER,
      component: SocioTicketHolderComponent,
    }),
    // Termo do Nação: "Li e aceito" antes do Finalizar pedido (só com plano no carrinho)
    provideOutlet({
      id: 'CheckoutPlaceOrder',
      position: OutletPosition.BEFORE,
      component: SocioTermCheckoutComponent,
    }),
    // Pix e boleto (item 7): forma de pagamento do plano no checkout, na revisão e na confirmação
    provideOutlet({
      id: 'CheckoutPaymentDetails',
      position: OutletPosition.BEFORE,
      component: SocioPaymentChoiceComponent,
    }),
    provideOutlet({
      id: 'CheckoutPlaceOrder',
      position: OutletPosition.BEFORE,
      component: SocioPaymentReviewComponent,
    }),
    provideOutlet({
      id: 'OrderConfirmationThankMessageComponent',
      position: OutletPosition.AFTER,
      component: SocioPaymentStatusComponent,
    }),
  ],
})
export class SocioModule {}
