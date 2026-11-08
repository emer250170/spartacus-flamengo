import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthGuard, CmsConfig, provideConfig } from '@spartacus/core';
import { CmsPageGuard, KeyboardFocusModule } from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { EstudioAcervoComponent } from './estudio-acervo.component';
import { EstudioDemoComponent } from './estudio-demo.component';
import { EstudioDevolucaoComponent } from './estudio-devolucao.component';
import { EstudioDropComponent } from './estudio-drop.component';
import { EstudioGaleriaComponent } from './estudio-galeria.component';
import { EstudioHomeComponent } from './estudio-home.component';
import { EstudioProdutoComponent } from './estudio-produto.component';
import { EstudioSharedModule } from './estudio-shared.module';
import { VisualModule } from '../visual/visual.module';

/**
 * Mengo Estúdio (07/11): camisetas sob demanda com estampas que saem de linha.
 * - EstudioHomeComponent  -> página /mengo-estudio do CMS (CMSFlexComponent MengoEstudioHomeComponent)
 * - EstudioDropComponent  -> /mengo-estudio/drop/:code (vitrines por linha, filtros)
 * - EstudioAcervoComponent -> /mengo-estudio/acervo (estampas encerradas e "Avise-me se voltar")
 * - EstudioProdutoComponent -> /mengo-estudio/estampa/:code e /mengo-estudio/camiseta (configurador, PR 3)
 * - EstudioDevolucaoComponent -> /mengo-estudio/devolucao/:code (devolução em 4 etapas, PR 5)
 * - EstudioDemoComponent -> /mengo-estudio/demo (painel do apresentador, só usuários de teste, PR 5)
 * As rotas usam o cabeçalho e o rodapé da página /mengo-estudio. Dados: EstudioService (/mengo-estudio/catalogo).
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    FlaI18nModule,
    KeyboardFocusModule,
    EstudioSharedModule,
    VisualModule,
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
      {
        path: 'mengo-estudio/estampa/:code',
        component: EstudioProdutoComponent,
        canActivate: [CmsPageGuard],
        data: { pageLabel: '/mengo-estudio' },
      },
      {
        path: 'mengo-estudio/camiseta',
        component: EstudioProdutoComponent,
        canActivate: [CmsPageGuard],
        data: { pageLabel: '/mengo-estudio' },
      },
      // PR 5: devolução (do detalhe do pedido) e painel da demonstração (fora dos menus)
      {
        path: 'mengo-estudio/devolucao/:code',
        component: EstudioDevolucaoComponent,
        canActivate: [AuthGuard, CmsPageGuard],
        data: { pageLabel: '/mengo-estudio' },
      },
      {
        path: 'mengo-estudio/demo',
        component: EstudioDemoComponent,
        canActivate: [AuthGuard, CmsPageGuard],
        data: { pageLabel: '/mengo-estudio' },
      },
    ]),
  ],
  declarations: [
    EstudioHomeComponent,
    EstudioDropComponent,
    EstudioAcervoComponent,
    EstudioProdutoComponent,
    EstudioGaleriaComponent,
    EstudioDevolucaoComponent,
    EstudioDemoComponent,
  ],
  exports: [EstudioSharedModule],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MengoEstudioHomeComponent: { component: EstudioHomeComponent },
      },
    }),
  ],
})
export class EstudioModule {}
