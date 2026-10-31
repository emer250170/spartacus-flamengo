import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CmsConfig, provideConfig } from '@spartacus/core';
import {
  OutletPosition,
  SpinnerModule,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { SocioBadgeComponent } from './socio-badge.component';
import { SocioPlansComponent } from './socio-plans.component';

/**
 * Sócio-torcedor Nação Maraca (protótipo Flamengo).
 * SocioPlansComponent -> página de conteúdo /socio-torcedor (CMSFlexComponent).
 * SocioBadgeComponent -> selo de sócio no header (antes do "Olá, Fulano").
 * O SocioService também é usado pela jornada de matchday (ondas e desconto).
 */
@NgModule({
  imports: [CommonModule, RouterModule, SpinnerModule, FlaI18nModule],
  declarations: [SocioPlansComponent, SocioBadgeComponent],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        SocioPlansComponent: { component: SocioPlansComponent },
      },
    }),
    provideOutlet({
      id: 'SiteLogin',
      position: OutletPosition.BEFORE,
      component: SocioBadgeComponent,
    }),
  ],
})
export class SocioModule {}
