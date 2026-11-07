import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UrlModule } from '@spartacus/core';
import { OutletPosition, provideOutlet } from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaAccountBandComponent } from './fla-account-band.component';
import { FlaAccountMenuComponent } from './fla-account-menu.component';
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
    ]),
    UrlModule,
    FlaI18nModule,
  ],
  declarations: [
    FlaAccountBandComponent,
    FlaAccountMenuComponent,
    FlaLoginHaveComponent,
    FlaLoginPerksComponent,
  ],
  providers: [
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
