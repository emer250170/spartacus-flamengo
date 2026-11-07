import { isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, OnDestroy, PLATFORM_ID } from '@angular/core';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { statusTone } from './conta-pages';

/** "first page", "page 2"... que o cx-pagination monta em inglês dentro do "Ir para {{location}}" */
const PAGE_WORDS: [RegExp, string][] = [
  [/\bfirst page\b/i, 'a primeira página'],
  [/\blast page\b/i, 'a última página'],
  [/\bprevious page\b/i, 'a página anterior'],
  [/\bnext page\b/i, 'a próxima página'],
  [/\bpage (\d+)\b/i, 'a página $1'],
];

/** Situações em listas do Spartacus que viram selo colorido (data-tone; cores no tema) */
/** Serviços da loja que vêm do Commerce em inglês (Encontre uma loja) */
const STORE_FEATURES: Record<string, string> = {
  'buy online pick up in store': 'sf.feature.pickup',
};

const STATUS_CELLS = [
  'cx-customer-ticketing-list .cx-ticketing-list-table td:last-child .cx-ticketing-list-value',
];

/**
 * Ajustes no DOM de componentes do Spartacus que não têm tradução nem classe para isso (07/11):
 * - rótulos de acessibilidade da paginação em português ("Ir para a primeira página");
 * - situação dos chamados de atendimento com a cor do selo (data-tone);
 * - serviços da loja em português ("Retirada na loja") em Encontre uma loja.
 * Só no navegador; olha as mudanças da página e só mexe quando o valor muda.
 */
@Injectable({ providedIn: 'root' })
export class FlaDomLabelsService implements OnDestroy {
  protected observer?: MutationObserver;
  protected scheduled = false;

  constructor(
    protected i18n: FlaI18nService,
    @Inject(PLATFORM_ID) protected platformId: object
  ) {}

  start(doc: Document): void {
    if (this.observer || !isPlatformBrowser(this.platformId) || !doc?.body) {
      return;
    }
    this.observer = new MutationObserver(() => this.schedule(doc));
    this.observer.observe(doc.body, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['aria-label', 'title'],
    });
    this.schedule(doc);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  protected schedule(doc: Document): void {
    if (this.scheduled) {
      return;
    }
    this.scheduled = true;
    const run = () => {
      this.scheduled = false;
      this.apply(doc);
    };
    const win = doc.defaultView;
    if (win?.requestAnimationFrame) {
      win.requestAnimationFrame(run);
    } else {
      setTimeout(run, 50);
    }
  }

  protected apply(doc: Document): void {
    if (this.i18n.lang === 'pt') {
      doc.querySelectorAll('cx-pagination a').forEach((a) => {
        for (const attr of ['aria-label', 'title']) {
          const value = a.getAttribute(attr);
          const next = value ? toPortuguese(value) : value;
          if (value && next !== value) {
            a.setAttribute(attr, next as string);
          }
        }
      });
    }
    doc.querySelectorAll('.cx-feature-item').forEach((item) => {
      const key = STORE_FEATURES[(item.textContent ?? '').trim().toLowerCase()];
      const text = key ? this.i18n.t(key) : '';
      if (text && item.textContent !== text) {
        item.textContent = text;
      }
    });
    doc.querySelectorAll(STATUS_CELLS.join(',')).forEach((cell) => {
      const tone = statusTone(cell.textContent ?? '');
      if (cell.getAttribute('data-tone') !== tone) {
        cell.setAttribute('data-tone', tone);
      }
    });
  }
}

function toPortuguese(label: string): string {
  return PAGE_WORDS.reduce((text, [re, pt]) => text.replace(re, pt), label);
}
