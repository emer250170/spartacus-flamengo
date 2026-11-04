import { ChangeDetectionStrategy, Component, Optional } from '@angular/core';
import { Product } from '@spartacus/core';
import { ProductListItemContext } from '@spartacus/storefront';
import { EMPTY, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ticketQueryParams } from './matchday-ticket-redirect.service';

/**
 * Lista "Jogos em casa" (04/11): no card de um jogo, "Escolher poltrona" no lugar de
 * "Adicionar ao carrinho"; abre a compra de ingresso (stepper) já com o jogo escolhido.
 * Entra antes das ações do item (ProductListOutlets.ITEM_ACTIONS); o botão padrão do
 * carrinho some pelo tema só nos itens que têm este botão. Outros produtos não mudam.
 */
@Component({
  selector: 'fla-matchday-ticket-action',
  template: `
    <ng-container *ngIf="params$ | async as params">
      <a
        class="btn btn-primary btn-block fla-ticket-btn"
        [routerLink]="'/matchday'"
        [queryParams]="params"
        >{{ 'md.chooseSeat' | flaT }}</a
      >
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchdayTicketActionComponent {
  readonly params$: Observable<Record<string, string> | null> = this.context
    ? this.context.product$.pipe(
        map((product: Product) => ticketQueryParams(product?.code))
      )
    : EMPTY;

  constructor(@Optional() protected context?: ProductListItemContext) {}
}
