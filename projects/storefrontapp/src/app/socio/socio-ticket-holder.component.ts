import { ChangeDetectionStrategy, Component, Optional } from '@angular/core';
import { CartItemContext } from '@spartacus/cart/base/root';
import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { SocioTicketHolderService } from './socio-ticket-holder.service';

/**
 * "Ingresso de: Maria" embaixo da poltrona do convidado nas listas de itens do
 * Spartacus (carrinho, revisão do checkout, confirmação e detalhe do pedido),
 * pelo outlet CartOutlets.ITEM_DETAILS.
 */
@Component({
  selector: 'fla-socio-ticket-holder',
  template: `
    <div class="fla-ticket-holder" *ngIf="holder$ | async as holder">
      {{ 'sg.cartHolder' | flaT: { name: holder } }}
    </div>
  `,
  styles: [
    `
      .fla-ticket-holder {
        margin: 4px 0;
        font-size: 0.875rem;
        font-weight: 700;
        color: var(--cx-color-primary, #c8102e);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioTicketHolderComponent {
  readonly holder$: Observable<string | undefined> = this.context
    ? this.context.item$.pipe(
        switchMap((item) => this.holders.holderOf(item?.product?.code))
      )
    : of(undefined);

  constructor(
    protected holders: SocioTicketHolderService,
    @Optional() protected context: CartItemContext
  ) {}
}
