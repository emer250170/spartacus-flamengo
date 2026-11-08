import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { OutletPosition, provideOutlet } from '@spartacus/storefront';
import { RouterModule } from '@angular/router';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import {
  FlaMeArteComponent,
  FlaMeBarraComponent,
  FlaMeCamisetaComponent,
  FlaMeSubnavComponent,
  MeLinhaPipe,
} from './estudio-shared.components';

/**
 * Peças do Mengo Estúdio usadas fora das telas do Estúdio também (08/11, PR 3): arte, camiseta com a estampa
 * (miniatura do carrinho e do box lateral), barra do Estúdio (no topo das páginas do Estúdio) e o nome da linha.
 */
@NgModule({
  imports: [CommonModule, RouterModule, FlaI18nModule],
  declarations: [FlaMeArteComponent, FlaMeCamisetaComponent, FlaMeSubnavComponent, FlaMeBarraComponent, MeLinhaPipe],
  exports: [FlaMeArteComponent, FlaMeCamisetaComponent, FlaMeSubnavComponent, MeLinhaPipe],
  providers: [
    // barra do Estúdio no slot de baixo do header (fica parada na troca entre as páginas do Estúdio, 08/11)
    provideOutlet({
      id: 'BottomHeaderSlot',
      position: OutletPosition.BEFORE,
      component: FlaMeBarraComponent,
    }),
  ],
})
export class EstudioSharedModule {}
