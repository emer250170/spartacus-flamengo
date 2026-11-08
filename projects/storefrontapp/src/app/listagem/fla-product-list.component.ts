import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GlobalMessageService } from '@spartacus/core';
import {
  PageLayoutService,
  ProductListComponent,
  ProductListComponentService,
  ViewConfig,
  ViewModes,
} from '@spartacus/storefront';
import { BehaviorSubject, Observable } from 'rxjs';
import { FlaHomeProduct, FlaHomeService } from '../vitrine/fla-home.service';
import { BUSCA_URL } from './fla-search-band.component';

/** Atalhos da busca sem resultado (categorias do catálogo; nomes em fla-texts: bs.cat.*) */
const ATALHOS = [
  { key: 'mantos', code: 'flamengo_c115' },
  { key: 'adidas', code: 'flamengo_c11' },
  { key: 'treino', code: 'flamengo_c145' },
  { key: 'personalizaveis', code: 'flamengo_c110' },
  { key: 'acessorios', code: 'flamengo_c52' },
  { key: 'bones', code: 'flamengo_c60' },
];

/** Modo de exibição que fica sempre em grade (o modelo de página pede lista nas categorias) */
class GridOnlySubject extends BehaviorSubject<ViewModes> {
  constructor() {
    super(ViewModes.Grid);
  }

  override next(): void {
    super.next(ViewModes.Grid);
  }
}

/**
 * Listagem de produtos no layout da loja oficial (flamengo.com.br/loja, 04/11): sempre em
 * grade (sem o ícone lista/grade), sem paginação (botão "Carregar mais produtos", configurado
 * em ListagemModule) e com o botão "Filtrar", que abre os filtros num painel pela esquerda
 * ("N itens encontrados", "Limpar filtros" e "Aplicar"). A coluna de filtros some pelo tema.
 */
@Component({
  selector: 'cx-product-list',
  templateUrl: './fla-product-list.component.html',
})
export class FlaProductListComponent extends ProductListComponent {
  override viewMode$: BehaviorSubject<ViewModes> = new GridOnlySubject();

  /** Painel de filtros aberto */
  filtersOpen = false;

  protected readonly router = inject(Router);
  protected readonly home = inject(FlaHomeService);

  /** Busca sem resultado (mockup 2b, 08/11): dicas, atalhos para as categorias e os mantos */
  readonly atalhos = ATALHOS.map((a) => ({ key: 'bs.cat.' + a.key, link: '/c/' + a.code }));
  readonly mantosLink = '/c/' + ATALHOS[0].code;
  readonly mantos$: Observable<FlaHomeProduct[]> = this.home.byCategory(ATALHOS[0].code, 4);

  /** Página de resultado da busca (/search/{termo}) */
  get busca(): boolean {
    return BUSCA_URL.test(this.router.url);
  }

  constructor(
    pageLayoutService: PageLayoutService,
    protected listService: ProductListComponentService,
    globalMessageService: GlobalMessageService,
    scrollConfig: ViewConfig
  ) {
    super(pageLayoutService, listService, globalMessageService, scrollConfig);
  }

  openFilters(): void {
    this.filtersOpen = true;
  }

  closeFilters(): void {
    this.filtersOpen = false;
    document.body.classList.remove('modal-open');
  }

  /** Tem algum filtro marcado (além da ordenação)? */
  hasFilters(query?: string): boolean {
    return (query ?? '').split(':').filter(Boolean).length > 1;
  }

  /** Os filtros já valem a cada clique; "Aplicar" só fecha o painel */
  apply(): void {
    this.closeFilters();
  }
}
