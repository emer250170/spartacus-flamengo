import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from '../matchday/matchday-data.service';
import { MatchdayMatch } from '../matchday/matchday.model';
import { FLA_DEFAULT_MATCH_IMAGE } from './fla-media-url.service';

/**
 * Vitrine "Próximos jogos" (04/11): um card por jogo à venda com a imagem cadastrada no
 * Backoffice (ou a imagem padrão), data, competição, preço inicial e acesso à jornada já
 * com o jogo escolhido (/matchday?jogo=...).
 */
@Component({
  selector: 'fla-match-cards',
  templateUrl: './match-cards.component.html',
  styleUrls: ['./vitrine.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchCardsComponent {
  /** Quantos jogos mostrar (0 = todos) */
  @Input() limit = 0;

  readonly defaultImage = FLA_DEFAULT_MATCH_IMAGE;

  matches$: Observable<MatchdayMatch[]> = this.i18n.lang$.pipe(
    switchMap(() => this.data.getMatches()),
    map((list) => (this.limit > 0 ? list.slice(0, this.limit) : list))
  );

  constructor(
    protected data: MatchdayDataService,
    protected i18n: FlaI18nService
  ) {}

  weekday(m: MatchdayMatch): string {
    return m.weekdayIndex < 0 ? m.weekday : this.i18n.weekday(m.weekdayIndex);
  }

  price(value: number): string {
    return this.i18n.money(value);
  }

  /** Foto do jogo que não carregou: volta para a imagem padrão */
  fallback(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.src.endsWith(this.defaultImage)) {
      img.src = this.defaultImage;
    }
  }
}
