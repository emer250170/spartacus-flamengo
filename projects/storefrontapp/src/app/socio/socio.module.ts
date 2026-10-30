import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CmsConfig, provideConfig } from '@spartacus/core';
import { SpinnerModule } from '@spartacus/storefront';
import { SocioPlansComponent } from './socio-plans.component';

/**
 * Sócio-torcedor Nação Maraca (protótipo Flamengo).
 * SocioPlansComponent -> página de conteúdo /socio-torcedor (CMSFlexComponent).
 * O SocioService também é usado pela jornada de matchday (ondas e desconto).
 */
@NgModule({
  imports: [CommonModule, RouterModule, SpinnerModule],
  declarations: [SocioPlansComponent],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        SocioPlansComponent: { component: SocioPlansComponent },
      },
    }),
  ],
})
export class SocioModule {}
