import { Component } from '@angular/core';
import { GlobalMessageService } from '@spartacus/core';
import {
  PageLayoutService,
  ProductListComponent,
  ProductListComponentService,
  ViewConfig,
  ViewModes,
} from '@spartacus/storefront';
import { BehaviorSubject } from 'rxjs';

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
