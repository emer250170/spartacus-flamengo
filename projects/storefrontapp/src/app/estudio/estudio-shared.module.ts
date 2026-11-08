import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import {
  FlaMeArteComponent,
  FlaMeCamisetaComponent,
  FlaMeSubnavComponent,
  MeLinhaPipe,
} from './estudio-shared.components';

/**
 * Peças do Mengo Estúdio usadas fora das telas do Estúdio também (08/11, PR 3): arte, camiseta com a estampa
 * (miniatura do carrinho e do box lateral), barra do Estúdio e o nome da linha.
 */
@NgModule({
  imports: [CommonModule, RouterModule, FlaI18nModule],
  declarations: [FlaMeArteComponent, FlaMeCamisetaComponent, FlaMeSubnavComponent, MeLinhaPipe],
  exports: [FlaMeArteComponent, FlaMeCamisetaComponent, FlaMeSubnavComponent, MeLinhaPipe],
})
export class EstudioSharedModule {}
