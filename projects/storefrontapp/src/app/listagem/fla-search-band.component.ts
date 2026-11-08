import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
  OnInit,
  Renderer2,
} from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { ProductSearchPage, ProductSearchService, RoutingService } from '@spartacus/core';
import { Observable, Subscription, of } from 'rxjs';
import {
  distinctUntilChanged,
  filter,
  map,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs/operators';

/** Classe no <html> do resultado da busca (tema: sem a trilha cinza do Spartacus nem o banner do CMS) */
export const BUSCA_CLASS = 'fla-busca';

/** Resultado da busca: /search/{termo} */
export const BUSCA_URL = /\/search\/[^/?#]+/;

export interface FlaSearchVm {
  term: string;
  total: number;
  /** "Você quis dizer" (Solr), quando difere do termo */
  suggestion?: string;
  /** Atalhos "Ver em:" (faceta de categoria do resultado, sem os agrupadores) */
  cats: { name: string; count: number; query: string }[];
}

/** Valores da faceta de categoria que não são categorias de verdade (agrupadores do catálogo) */
const NAO_CATEGORIA = /^(tamanho|roupas)$/i;

/** Busca atual (termo, total, sugestão e categorias) a partir do modelo da listagem */
export function toSearchVm(model: ProductSearchPage): FlaSearchVm {
  const term = model.freeTextSearch ?? '';
  const sug = model.spellingSuggestion?.suggestion;
  const facet = (model.facets ?? []).find(
    (f) => /categ/i.test(f.name ?? '') || f.name === 'allCategories'
  );
  const seen = new Set<string>();
  const cats = (facet?.values ?? [])
    .filter((v) => v.name && !NAO_CATEGORIA.test(v.name) && !v.selected)
    .filter((v) => (seen.has(v.name ?? '') ? false : (seen.add(v.name ?? ''), true)))
    .slice(0, 5)
    .map((v) => ({
      name: v.name ?? '',
      count: v.count ?? 0,
      query: v.query?.query?.value ?? '',
    }))
    .filter((c) => !!c.query);
  return {
    term,
    total: model.pagination?.totalResults ?? 0,
    suggestion: sug && sug.toLowerCase() !== term.toLowerCase() ? sug : undefined,
    cats,
  };
}

/**
 * Faixa escura do resultado da busca (mockup 2a/2b, 08/11): trilha "Loja Oficial — Busca", "Você buscou
 * por", o termo grande, quantos produtos, "Você quis dizer" quando o Solr sugere, a busca de novo e os
 * atalhos "Ver em:" pelas categorias do resultado. Sem resultado: "Nenhum produto encontrado". Fica no
 * lugar da trilha cinza do Spartacus (outlet do BottomHeaderSlot; o tema esconde o resto com fla-busca).
 */
@Component({
  selector: 'fla-search-band',
  template: `
    <section class="fsb" *ngIf="vm$ | async as v" [attr.aria-label]="'bs.search' | flaT">
      <div class="fsb-in">
        <nav class="fsb-crumbs" [attr.aria-label]="'bs.crumbs' | flaT">
          <a routerLink="/">{{ 'bs.store' | flaT }}</a><span aria-hidden="true">—</span><span aria-current="page">{{ 'bs.search' | flaT }}</span>
        </nav>
        <div class="fsb-row">
          <div class="fsb-txt">
            <span class="fsb-kicker">{{ (v.total ? 'bs.kicker' : 'bs.kickerNone') | flaT }}</span>
            <h1 class="fsb-h">“{{ v.term }}”</h1>
            <span class="fsb-sub">{{
              v.total ? ((v.total === 1 ? 'bs.foundOne' : 'bs.found') | flaT: { n: v.total }) : ('bs.noneText' | flaT)
            }}</span>
            <span class="fsb-sug" *ngIf="v.suggestion as s">
              {{ 'bs.didYouMean' | flaT }} <a [routerLink]="link(s)">“{{ s }}”</a>?
            </span>
          </div>
          <form class="fsb-form" role="search" (submit)="buscar($event, q.value)">
            <label class="fsb-sr" for="fsb-q">{{ 'bs.label' | flaT }}</label>
            <input #q id="fsb-q" type="search" [value]="v.term" autocomplete="off" />
            <button type="submit" [attr.aria-label]="'bs.submit' | flaT">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true" focusable="false"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg>
            </button>
          </form>
        </div>
        <div class="fsb-chips" *ngIf="v.cats.length">
          <span>{{ 'bs.seeIn' | flaT }}</span>
          <a *ngFor="let c of v.cats" [routerLink]="[]" [queryParams]="{ query: c.query, currentPage: null }" queryParamsHandling="merge">
            {{ c.name }} <span class="fsb-n">{{ c.count }}</span>
          </a>
        </div>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaSearchBandComponent implements OnInit, OnDestroy {
  protected readonly busca$: Observable<boolean> = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map((e) => e.urlAfterRedirects),
    startWith(this.router.url),
    map((url) => BUSCA_URL.test(url)),
    distinctUntilChanged(),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  readonly vm$: Observable<FlaSearchVm | null> = this.busca$.pipe(
    // resultados já carregados pela listagem (só lê o estado; não dispara outra busca)
    switchMap((busca) =>
      busca
        ? this.search.getResults().pipe(
            filter((r) => !!r?.pagination),
            map(toSearchVm)
          )
        : of(null)
    )
  );

  protected sub?: Subscription;

  constructor(
    protected router: Router,
    protected routing: RoutingService,
    protected search: ProductSearchService,
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  ngOnInit(): void {
    this.sub = this.busca$.subscribe((b) =>
      b
        ? this.renderer.addClass(this.document.documentElement, BUSCA_CLASS)
        : this.renderer.removeClass(this.document.documentElement, BUSCA_CLASS)
    );
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.renderer.removeClass(this.document.documentElement, BUSCA_CLASS);
  }

  link(term: string): unknown[] {
    return ['/search', term];
  }

  buscar(ev: Event, term: string): void {
    ev.preventDefault();
    const t = term.trim();
    if (t) {
      this.routing.go({ cxRoute: 'search', params: { query: t } });
    }
  }
}
