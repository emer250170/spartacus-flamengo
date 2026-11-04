import { ChangeDetectionStrategy, Component, Optional } from '@angular/core';
import { Consignment } from '@spartacus/order/root';
import { OutletContextData } from '@spartacus/storefront';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

/** Situação de uma consignação de retirada: concluída ou ainda a retirar */
export type FlaPickupState = 'done' | 'open' | null;

/**
 * Detalhes do pedido (04/11): marca cada consignação de retirada na loja como concluída ou
 * ainda a retirar (atributo data-fla-pickup, sem nada visível). O tema usa a marca para não
 * mostrar ao mesmo tempo o título "Itens para retirar" e o bloco "Retirada concluída": com
 * toda a retirada concluída, o título some. Entregas em casa não são marcadas.
 */
@Component({
  selector: 'fla-pickup-consignment-mark',
  template: `<span
    *ngIf="state$ | async as state"
    hidden
    [attr.data-fla-pickup]="state"
  ></span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPickupConsignmentMarkComponent {
  readonly state$: Observable<FlaPickupState> = this.outlet?.context$
    ? this.outlet.context$.pipe(map((context) => pickupState(context?.item)))
    : of(null);

  constructor(
    @Optional() protected outlet?: OutletContextData<{ item?: Consignment }>
  ) {}
}

export function pickupState(
  consignment: Consignment | undefined
): FlaPickupState {
  if (!consignment?.deliveryPointOfService) {
    return null;
  }
  return consignment.status === 'PICKUP_COMPLETE' ? 'done' : 'open';
}
