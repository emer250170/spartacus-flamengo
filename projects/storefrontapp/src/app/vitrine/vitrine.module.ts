import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UrlModule } from '@spartacus/core';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { ExperienceShowcaseComponent } from './experience-showcase.component';
import { MatchCardsComponent } from './match-cards.component';
import {
  FlaHomeAccessoriesComponent,
  FlaHomeCategoriesComponent,
  FlaHomeHeroComponent,
  FlaHomeLinesComponent,
  FlaHomePersonalizeComponent,
} from './fla-home.components';

const HOME = [
  FlaHomeHeroComponent,
  FlaHomeCategoriesComponent,
  FlaHomePersonalizeComponent,
  FlaHomeLinesComponent,
  FlaHomeAccessoriesComponent,
];

/** Vitrines com as imagens dos jogos e das experiências (04/11): home e página Sócio-Torcedor; blocos da home no visual da loja oficial (06/11) */
@NgModule({
  imports: [CommonModule, RouterModule, UrlModule, FlaI18nModule],
  declarations: [MatchCardsComponent, ExperienceShowcaseComponent, ...HOME],
  exports: [MatchCardsComponent, ExperienceShowcaseComponent, ...HOME],
})
export class VitrineModule {}
