import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CmsConfig, PageMetaResolver, provideConfig } from '@spartacus/core';
import {
  OutletPosition,
  ProductListOutlets,
  SpinnerModule,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { VitrineModule } from '../vitrine/vitrine.module';
import { MatchdayHomeBannerComponent } from './matchday-home-banner.component';
import { MatchdayDeliveryTextInterceptor } from './matchday-delivery-text.interceptor';
import { MatchdayPageMetaResolver } from './matchday-page-meta.resolver';
import { MatchdayTicketActionComponent } from './matchday-ticket-action.component';
import { MatchdayTicketRedirectService } from './matchday-ticket-redirect.service';
import { MatchdayHoldWatcherService } from './matchday-hold-watcher.service';
import { MatchdayJourneyComponent } from './matchday-journey.component';
import { MatchdayOrderConfirmationComponent } from './matchday-order-confirmation.component';
import { MatchdayStadiumMapComponent } from './matchday-stadium-map.component';
import { OrderNumberHeaderComponent } from './order-number-header.component';

/**
 * Protótipo "jornada única de matchday" do Flamengo.
 *
 * Componentes ligados ao CMS por CMSFlexComponent (flexType):
 * - MatchdayJourneyComponent           -> página de conteúdo /matchday
 * - MatchdayHomeBannerComponent        -> home (próximo jogo)
 * - MatchdayOrderConfirmationComponent -> confirmação do pedido
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    SpinnerModule,
    FlaI18nModule,
    VitrineModule,
  ],
  declarations: [
    MatchdayJourneyComponent,
    MatchdayHomeBannerComponent,
    MatchdayOrderConfirmationComponent,
    MatchdayStadiumMapComponent,
    OrderNumberHeaderComponent,
    MatchdayTicketActionComponent,
  ],
  providers: [
    // cabeçalho da página /matchday: título e breadcrumb do modo só ingresso (04/11)
    {
      provide: PageMetaResolver,
      useExisting: MatchdayPageMetaResolver,
      multi: true,
    },
    // detalhe do pedido: "Número: 00025001" abaixo do título, no cabeçalho cinza
    provideOutlet({
      id: 'BreadcrumbComponent',
      position: OutletPosition.AFTER,
      component: OrderNumberHeaderComponent,
    }),
    // lista "Jogos em casa": "Escolher poltrona" no card do jogo (abre o stepper, 04/11)
    provideOutlet({
      id: ProductListOutlets.ITEM_ACTIONS,
      position: OutletPosition.BEFORE,
      component: MatchdayTicketActionComponent,
    }),
    // descrição da entrega "Ingresso digital e retirada no estádio" conforme o carrinho
    {
      provide: HTTP_INTERCEPTORS,
      useClass: MatchdayDeliveryTextInterceptor,
      multi: true,
    },
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MatchdayJourneyComponent: { component: MatchdayJourneyComponent },
        MatchdayHomeBannerComponent: { component: MatchdayHomeBannerComponent },
        MatchdayOrderConfirmationComponent: {
          component: MatchdayOrderConfirmationComponent,
        },
      },
    }),
  ],
})
export class MatchdayModule {
  // vigia a poltrona reservada em qualquer página (aviso se ela sair do carrinho) e leva a
  // página de um jogo ou de uma poltrona para a compra de ingresso no stepper (04/11)
  constructor(
    holdWatcher: MatchdayHoldWatcherService,
    ticketRedirect: MatchdayTicketRedirectService
  ) {
    holdWatcher.start();
    ticketRedirect.start();
  }
}
