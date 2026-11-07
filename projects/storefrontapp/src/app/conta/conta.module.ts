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
  ListNavigationModule,
  MediaModule,
  OutletPosition,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaAccountBandComponent } from './fla-account-band.component';
import { FlaAccountMenuComponent } from './fla-account-menu.component';
import { FlaAccountOrderComponent } from './fla-account-order.component';
import { FlaEmailCurrentComponent } from './fla-email-current.component';
import { FlaOrderHistoryComponent } from './fla-order-history.component';
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
  ],
  declarations: [
    FlaAccountBandComponent,
    FlaAccountMenuComponent,
    FlaLoginHaveComponent,
    FlaLoginPerksComponent,
    FlaOrderHistoryComponent,
    FlaAccountOrderComponent,
    FlaEmailCurrentComponent,
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
    provideOutlet({
      id: 'ReturningCustomerRegisterComponent',
      position: OutletPosition.AFTER,
      component: FlaLoginPerksComponent,
    }),
  ],
})
export class ContaModule {}
