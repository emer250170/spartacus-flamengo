import { Injectable, NgModule, Pipe, PipeTransform } from '@angular/core';
import { LanguageService } from '@spartacus/core';
import { Observable } from 'rxjs';
import { distinctUntilChanged, map, shareReplay } from 'rxjs/operators';
import { FLA_TEXTS, FlaLang } from './fla-texts';

const TZ = 'America/Sao_Paulo';

/**
 * Idioma das telas próprias do Flamengo: português quando o site está em
 * português, inglês nos demais idiomas. Acompanha o seletor de idioma do site.
 */
@Injectable({ providedIn: 'root' })
export class FlaI18nService {
  /** Idioma atual (síncrono, para os templates) */
  lang: FlaLang = 'pt';
  /** Emite o idioma atual e cada troca */
  readonly lang$: Observable<FlaLang>;

  constructor(languageService: LanguageService) {
    this.lang$ = languageService.getActive().pipe(
      map((iso): FlaLang => (iso?.startsWith('pt') ? 'pt' : 'en')),
      distinctUntilChanged(),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.lang$.subscribe((lang) => (this.lang = lang));
  }

  /** Texto traduzido, com {parâmetros} trocados */
  t(key: string, params?: Record<string, unknown>): string {
    const text = FLA_TEXTS[this.lang][key] ?? FLA_TEXTS.pt[key] ?? key;
    return params
      ? text.replace(/\{(\w+)\}/g, (all, name) =>
          params[name] === undefined ? all : String(params[name])
        )
      : text;
  }

  /** R$ 1.234,56 (pt) ou R$1,234.56 (en) */
  money(value: number): string {
    return value.toLocaleString(this.lang === 'pt' ? 'pt-BR' : 'en-US', {
      style: 'currency',
      currency: 'BRL',
    });
  }

  /** Data e hora curtas no horário de Brasília: "09/11 10h00" ou "Nov 9, 10:00 AM" */
  shortDate(date?: Date): string {
    if (!date) {
      return '';
    }
    if (this.lang === 'en') {
      return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        timeZone: TZ,
      });
    }
    const d = date.toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      timeZone: TZ,
    });
    const t = date
      .toLocaleTimeString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: TZ,
      })
      .replace(':', 'h');
    return `${d} ${t}`;
  }

  /** Dia da semana (0 = domingo), curto ("SEG"/"MON") ou por extenso */
  weekday(day: number, full = false): string {
    return this.t(full ? 'weekdaysFull' : 'weekdays').split(',')[day] ?? '';
  }

  /**
   * Nome da onda: "Onda 1 · {planos}" (planos que compram nela, separados por
   * vírgula), "Onda 1" sem planos, "Venda geral" (onda 4).
   */
  waveLabel(wave: number, plans?: string): string {
    if (wave <= 0) {
      return this.t('wave.notOpen');
    }
    if (wave >= 4) {
      return this.t('wave.general');
    }
    return plans
      ? this.t('wave.plans', { n: wave, plans })
      : this.t('wave.n', { n: wave });
  }
}

/**
 * {{ 'md.title' | flaT }} ou {{ 'md.left.many' | flaT: { n: 3 } }}.
 * Impuro: acompanha a troca de idioma (os componentes OnPush pedem a
 * verificação quando o idioma muda).
 */
@Pipe({ name: 'flaT', pure: false })
export class FlaTranslatePipe implements PipeTransform {
  constructor(protected i18n: FlaI18nService) {}

  transform(key: string, params?: Record<string, unknown>): string {
    return this.i18n.t(key, params);
  }
}

@NgModule({
  declarations: [FlaTranslatePipe],
  exports: [FlaTranslatePipe],
})
export class FlaI18nModule {}
