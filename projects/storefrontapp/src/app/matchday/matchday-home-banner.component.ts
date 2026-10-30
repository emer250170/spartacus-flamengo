import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from './matchday-data.service';
import { MatchdayMatch } from './matchday.model';

/**
 * Faixa "Próximo jogo em casa" da home, com acesso à jornada de matchday.
 * CMSFlexComponent com flexType MatchdayHomeBannerComponent.
 */
@Component({
  selector: 'fla-matchday-home-banner',
  templateUrl: './matchday-home-banner.component.html',
  styleUrls: ['./matchday-home-banner.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchdayHomeBannerComponent {
  // só a lista de jogos (a home não precisa das poltronas), no idioma do site
  next$: Observable<MatchdayMatch | undefined> = this.i18n.lang$.pipe(
    switchMap(() => this.data.getMatches()),
    map((matches) => matches[0])
  );

  constructor(
    protected data: MatchdayDataService,
    protected i18n: FlaI18nService
  ) {}

  weekday(match: MatchdayMatch): string {
    return match.weekdayIndex < 0
      ? match.weekday
      : this.i18n.weekday(match.weekdayIndex, true);
  }
}
