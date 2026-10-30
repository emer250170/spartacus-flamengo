import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MatchdayDataService } from './matchday-data.service';
import { MatchdayMatch } from './matchday.model';

const WEEKDAY_FULL: Record<string, string> = {
  DOM: 'Domingo',
  SEG: 'Segunda',
  TER: 'Terça',
  QUA: 'Quarta',
  QUI: 'Quinta',
  SEX: 'Sexta',
  SÁB: 'Sábado',
};

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
  // só a lista de jogos: a home não precisa das poltronas
  next$: Observable<MatchdayMatch | undefined> = this.data
    .getMatches()
    .pipe(map((matches) => matches[0]));

  constructor(protected data: MatchdayDataService) {}

  weekday(match: MatchdayMatch): string {
    return WEEKDAY_FULL[match.weekday] ?? match.weekday;
  }
}
