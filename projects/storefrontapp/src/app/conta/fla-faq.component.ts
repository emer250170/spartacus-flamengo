import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { distinctUntilChanged, filter, map, startWith } from 'rxjs/operators';

/** Página de perguntas frequentes (/faq) */
export const FAQ_URL = /\/faq\/?(?:[?#]|$)/;

/** Temas e quantas perguntas cada um tem (textos em fla-texts: faq.s.*, faq.q.*, faq.a.*) */
const FAQ_SECTIONS: { key: string; count: number }[] = [
  { key: 'trocas', count: 3 },
  { key: 'entrega', count: 3 },
  { key: 'retirada', count: 2 },
  { key: 'pagamento', count: 3 },
  { key: 'personalizacao', count: 2 },
  { key: 'socio', count: 3 },
  { key: 'ingressos', count: 3 },
  { key: 'conta', count: 2 },
];

interface FaqSection {
  key: string;
  questions: number[];
}

/**
 * Perguntas frequentes no visual do mockup (07/11, quadro 41), em português e inglês: temas à
 * esquerda (levam ao tema), perguntas em cartões brancos que abrem e fecham (a primeira
 * aberta) e, no fim, "Não encontrou a sua resposta?" com o chamado e as lojas. No lugar do
 * texto de exemplo do CMS (em inglês), que o tema esconde nesta página (classe fla-faq).
 * Entra pelo outlet do ContentPage1Template e só aparece em /faq.
 */
@Component({
  selector: 'fla-faq',
  template: `
    <div class="ffq" *ngIf="show$ | async">
      <nav class="ffq-nav" [attr.aria-label]="'faq.topics' | flaT">
        <span class="ffq-nav-title">{{ 'faq.topics' | flaT }}</span>
        <button
          type="button"
          *ngFor="let s of sections; let first = first"
          [class.is-first]="first"
          (click)="goTo(s.key)"
        >
          {{ 'faq.s.' + s.key | flaT }}
        </button>
      </nav>

      <div class="ffq-main">
        <section
          *ngFor="let s of sections; let si = index"
          class="ffq-card"
          [id]="'faq-' + s.key"
          [attr.aria-labelledby]="'faq-h-' + s.key"
        >
          <h2 class="ffq-h2" [id]="'faq-h-' + s.key">
            {{ 'faq.s.' + s.key | flaT }}
          </h2>
          <details
            *ngFor="let i of s.questions"
            class="ffq-item"
            [open]="si === 0 && i === 0"
          >
            <summary>
              <span>{{ 'faq.q.' + s.key + '.' + i | flaT }}</span>
              <span class="ffq-sign" aria-hidden="true"></span>
            </summary>
            <p>{{ 'faq.a.' + s.key + '.' + i | flaT }}</p>
          </details>
        </section>

        <section class="ffq-help" aria-labelledby="faq-help">
          <div>
            <h2 id="faq-help" class="ffq-h2">{{ 'faq.help.title' | flaT }}</h2>
            <p>{{ 'faq.help.text' | flaT }}</p>
          </div>
          <div class="ffq-help-actions">
            <a
              class="btn btn-primary"
              routerLink="/my-account/support-tickets"
              >{{ 'faq.help.ticket' | flaT }}</a
            >
            <a class="ffq-ghost" routerLink="/store-finder">{{
              'faq.help.stores' | flaT
            }}</a>
          </div>
        </section>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaFaqComponent {
  readonly sections: FaqSection[] = FAQ_SECTIONS.map((s) => ({
    key: s.key,
    questions: Array.from({ length: s.count }, (_, i) => i),
  }));

  readonly show$: Observable<boolean> = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map((e) => e.urlAfterRedirects),
    startWith(this.router.url),
    map((url) => FAQ_URL.test(url ?? '')),
    distinctUntilChanged()
  );

  constructor(
    protected router: Router,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  /** Tema na lista da esquerda: rola até ele (sem mudar o endereço) */
  goTo(key: string): void {
    this.document
      .getElementById('faq-' + key)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
