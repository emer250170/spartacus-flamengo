import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthGuard, CmsConfig, provideConfig } from '@spartacus/core';
import {
  NavigationService,
  OutletPosition,
  SpinnerModule,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { SocioAccountComponent } from './socio-account.component';
import { SocioBadgeComponent } from './socio-badge.component';
import { SocioNavigationService } from './socio-navigation.service';
import { SocioPlansComponent } from './socio-plans.component';

/**
 * Sócio-torcedor Nação Maraca (protótipo Flamengo).
 * SocioPlansComponent -> página de conteúdo /socio-torcedor (CMSFlexComponent).
 * SocioAccountComponent -> Minha conta > Meu sócio-torcedor
 *   (/my-account/socio-torcedor, CMSFlexComponent, só logado).
 * SocioNavigationService -> esconde o link do menu de quem nunca foi sócio.
 * SocioBadgeComponent -> selo de sócio no header (antes do "Olá, Fulano").
 * O SocioService também é usado pela jornada de matchday (ondas e desconto).
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
    SocioPlansComponent,
    SocioBadgeComponent,
    SocioAccountComponent,
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
  ],
})
export class SocioModule {}
