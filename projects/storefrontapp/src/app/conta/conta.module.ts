import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import {
  AuthGuard,
  CmsConfig,
  GlobalMessageConfig,
  GlobalMessageType,
  I18nModule,
  UrlModule,
  provideConfig,
} from '@spartacus/core';
import {
  KeyboardFocusModule,
  ListNavigationModule,
  MediaModule,
  OutletPosition,
  provideOutlet,
} from '@spartacus/storefront';
import { EstudioSharedModule } from '../estudio/estudio-shared.module';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaAccountBandComponent } from './fla-account-band.component';
import { FlaFaqComponent } from './fla-faq.component';
import {
  FlaRegisterAsideComponent,
  FlaRegisterHeadComponent,
} from './fla-register-extras.component';
import { FlaAccountMenuComponent } from './fla-account-menu.component';
import { FlaAccountOrderComponent } from './fla-account-order.component';
import { FlaEmailCurrentComponent } from './fla-email-current.component';
import { FlaOrderHistoryComponent } from './fla-order-history.component';
import { FlaMyVouchersComponent } from './fla-my-vouchers.component';
import { FlaMyTicketsComponent } from './fla-my-tickets.component';
import {
  FlaLoginHaveComponent,
  FlaLoginPerksComponent,
} from './fla-login-extras.component';

/**
 * Minha conta e login no visual do mockup (07/11): faixa escura com trilha e título, menu lateral
 * em grupos, conteúdo em cartões brancos sobre fundo cinza (estilos em styles/visual-conta.scss).
 * Todas as telas e funções do Spartacus continuam; muda a moldura e o visual.
 * "Dados pessoais" saiu da conta (decisão do Emerson, 07/11): fora do menu e o endereço leva a
 * Meus pedidos. A página continua no CMS (nada apagado).
 */
@NgModule({
  imports: [
    CommonModule,
    RouterModule.forChild([
      {
        path: 'my-account/update-profile',
        redirectTo: 'my-account/orders',
        pathMatch: 'full',
      },
      // "Ver todas as lojas": as lojas da demo são todas do Brasil; vai direto à lista (07/11)
      {
        path: 'store-finder/view-all',
        redirectTo: 'store-finder/country/BR',
        pathMatch: 'full',
      },
    ]),
    UrlModule,
    I18nModule,
    ListNavigationModule,
    MediaModule,
    FlaI18nModule,
    KeyboardFocusModule,
    EstudioSharedModule,
  ],
  declarations: [
    FlaAccountBandComponent,
    FlaAccountMenuComponent,
    FlaLoginHaveComponent,
    FlaLoginPerksComponent,
    FlaOrderHistoryComponent,
    FlaMyVouchersComponent,
    FlaMyTicketsComponent,
    FlaAccountOrderComponent,
    FlaEmailCurrentComponent,
    FlaFaqComponent,
    FlaRegisterHeadComponent,
    FlaRegisterAsideComponent,
  ],
  providers: [
    // avisos flutuantes (07/11): erro some em 6s, sucesso e informação em 4s (dá para fechar no X)
    provideConfig(<GlobalMessageConfig>{
      globalMessages: {
        [GlobalMessageType.MSG_TYPE_ERROR]: { timeout: 6000 },
        [GlobalMessageType.MSG_TYPE_WARNING]: { timeout: 6000 },
        [GlobalMessageType.MSG_TYPE_CONFIRMATION]: { timeout: 4000 },
        [GlobalMessageType.MSG_TYPE_INFO]: { timeout: 4000 },
      },
    }),
    // Meus pedidos no visual do mockup (07/11): itens, selo da situação, "Ver pedido"
    provideConfig(<CmsConfig>{
      cmsComponents: {
        AccountOrderHistoryComponent: {
          component: FlaOrderHistoryComponent,
          guards: [AuthGuard],
        },
        // 08/11: Meus vouchers (/my-account/vouchers, CMS: scripts/meus-vouchers-cms.groovy)
        FlaMyVouchersComponent: {
          component: FlaMyVouchersComponent,
          guards: [AuthGuard],
        },
        // 09/11: Meus ingressos (/my-account/ingressos, CMS: scripts/meus-ingressos-cms.groovy)
        FlaMyTicketsComponent: {
          component: FlaMyTicketsComponent,
          guards: [AuthGuard],
        },
      },
    }),
    // detalhe do pedido no visual do mockup (só aparece em /my-account/order/...)
    provideOutlet({
      id: 'BodyContent',
      position: OutletPosition.BEFORE,
      component: FlaAccountOrderComponent,
    }),
    // E-mail: "E-mail atual" em cima do formulário
    provideOutlet({
      id: 'UpdateEmailComponent',
      position: OutletPosition.BEFORE,
      component: FlaEmailCurrentComponent,
    }),
    // perguntas frequentes (/faq) no lugar do texto de exemplo do CMS (07/11)
    provideOutlet({
      id: 'ContentPage1Template',
      position: OutletPosition.BEFORE,
      component: FlaFaqComponent,
    }),
    provideOutlet({
      id: 'BottomHeaderSlot',
      position: OutletPosition.BEFORE,
      component: FlaAccountBandComponent,
    }),
    provideOutlet({
      id: 'AccountPageTemplate',
      position: OutletPosition.BEFORE,
      component: FlaAccountMenuComponent,
    }),
    provideOutlet({
      id: 'ReturningCustomerLoginComponent',
      position: OutletPosition.BEFORE,
      component: FlaLoginHaveComponent,
    }),
    // criar conta (/login/register) no visual do mockup (07/11, quadro 42)
    provideOutlet({
      id: 'RegisterCustomerComponent',
      position: OutletPosition.BEFORE,
      component: FlaRegisterHeadComponent,
    }),
    provideOutlet({
      id: 'RegisterCustomerComponent',
      position: OutletPosition.AFTER,
      component: FlaRegisterAsideComponent,
    }),
    provideOutlet({
      id: 'ReturningCustomerRegisterComponent',
      position: OutletPosition.AFTER,
      component: FlaLoginPerksComponent,
    }),
  ],
})
export class ContaModule {}
