import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
  OnInit,
  Renderer2,
} from '@angular/core';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from './matchday-data.service';
import { MatchdayMatch } from './matchday.model';

/**
 * Faixa "Próximo jogo em casa" da home, com acesso à jornada de matchday, seguida das
 * vitrines de jogos e de experiências com as imagens cadastradas no Backoffice (04/11).
 * Visual da loja oficial (mockup "Home", 06/11): monta a home inteira na ordem do mockup
 * (destaques, próximo jogo, compre por categoria, jogos, personalize, linhas, experiências e
 * acessórios) e marca o <html> com fla-home para o tema esconder os banners e carrosséis
 * antigos do CMS (os carrosséis viraram abas; os banners de campanha, destaques).
 * CMSFlexComponent com flexType MatchdayHomeBannerComponent.
 */
@Component({
  selector: 'fla-matchday-home-banner',
  templateUrl: './matchday-home-banner.component.html',
  styleUrls: ['./matchday-home-banner.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchdayHomeBannerComponent implements OnInit, OnDestroy {
  // só a lista de jogos (a home não precisa das poltronas), no idioma do site
  next$: Observable<MatchdayMatch | undefined> = this.i18n.lang$.pipe(
    switchMap(() => this.data.getMatches()),
    map((matches) => matches[0])
  );

  constructor(
    protected data: MatchdayDataService,
    protected i18n: FlaI18nService,
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  ngOnInit(): void {
    this.renderer.addClass(this.document.documentElement, 'fla-home');
  }

  ngOnDestroy(): void {
    this.renderer.removeClass(this.document.documentElement, 'fla-home');
  }

  weekday(match: MatchdayMatch): string {
    return match.weekdayIndex < 0
      ? match.weekday
      : this.i18n.weekday(match.weekdayIndex, true);
  }
}
