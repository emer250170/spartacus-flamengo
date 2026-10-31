import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CmsConfig, provideConfig } from '@spartacus/core';
import { SpinnerModule } from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { MatchdayHomeBannerComponent } from './matchday-home-banner.component';
import { MatchdayJourneyComponent } from './matchday-journey.component';
import { MatchdayOrderConfirmationComponent } from './matchday-order-confirmation.component';
import { MatchdayStadiumMapComponent } from './matchday-stadium-map.component';

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
  ],
  declarations: [
    MatchdayJourneyComponent,
    MatchdayHomeBannerComponent,
    MatchdayOrderConfirmationComponent,
    MatchdayStadiumMapComponent,
  ],
  providers: [
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
export class MatchdayModule {}
