import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CmsConfig, provideConfig } from '@spartacus/core';
import { CmsPageGuard } from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { EstudioAcervoComponent } from './estudio-acervo.component';
import { EstudioDropComponent } from './estudio-drop.component';
import { EstudioHomeComponent } from './estudio-home.component';
import {
  FlaMeArteComponent,
  FlaMeCamisetaComponent,
  FlaMeSubnavComponent,
  MeLinhaPipe,
} from './estudio-shared.components';

/**
 * Mengo Estúdio (07/11, PR 2 de 5): camisetas sob demanda com estampas que saem de linha.
 * - EstudioHomeComponent  -> página /mengo-estudio do CMS (CMSFlexComponent MengoEstudioHomeComponent)
 * - EstudioDropComponent  -> /mengo-estudio/drop/:code (vitrines por linha, filtros)
 * - EstudioAcervoComponent -> /mengo-estudio/acervo (estampas encerradas e "Avise-me se voltar")
 * As rotas usam o cabeçalho e o rodapé da página /mengo-estudio. Dados: EstudioService (/mengo-estudio/catalogo).
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    FlaI18nModule,
    RouterModule.forChild([
      {
        path: 'mengo-estudio/drop/:code',
        component: EstudioDropComponent,
        canActivate: [CmsPageGuard],
        data: { pageLabel: '/mengo-estudio' },
      },
      {
        path: 'mengo-estudio/acervo',
        component: EstudioAcervoComponent,
        canActivate: [CmsPageGuard],
        data: { pageLabel: '/mengo-estudio' },
      },
    ]),
  ],
  declarations: [
    EstudioHomeComponent,
    EstudioDropComponent,
    EstudioAcervoComponent,
    FlaMeArteComponent,
    FlaMeCamisetaComponent,
    FlaMeSubnavComponent,
    MeLinhaPipe,
  ],
  exports: [FlaMeArteComponent, FlaMeCamisetaComponent, FlaMeSubnavComponent, MeLinhaPipe],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MengoEstudioHomeComponent: { component: EstudioHomeComponent },
      },
    }),
  ],
})
export class EstudioModule {}
