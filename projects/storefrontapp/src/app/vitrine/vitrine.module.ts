import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CmsConfig, UrlModule, provideConfig } from '@spartacus/core';
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
  providers: [
    // destaques da home gerenciados no CMS (06/11): o carrossel "Destaques da home"
    // (RotatingImagesComponent) é desenhado no visual do mockup, editável no SmartEdit
    provideConfig(<CmsConfig>{
      cmsComponents: {
        RotatingImagesComponent: { component: FlaHomeHeroComponent },
      },
    }),
  ],
})
export class VitrineModule {}
