import { Injectable } from '@angular/core';
import { ConsignmentEntry } from '@spartacus/cart/base/root';
import { MyAccountV2OrderConsignmentsService } from '@spartacus/order/components';
import { Consignment, Order } from '@spartacus/order/root';

const CANCELLED = 'CANCELLED';

/**
 * Item 14: cancelar uma linha de uma remessa ainda não enviada, no padrão do SAP, cancela a
 * remessa inteira e cria outra só com o que sobrou. O detalhe do pedido mostrava o bloco
 * "Cancelado" com todos os itens da remessa antiga, como se o pedido inteiro tivesse sido
 * cancelado. Aqui, na hora de agrupar as remessas para a tela, a remessa cancelada fica só com
 * a quantidade que saiu do pedido (o que ela tinha menos o que foi para as remessas em aberto
 * ou enviadas); remessa cancelada vazia some. (Um normalizador do pedido não serve: o
 * normalizador padrão do Spartacus refaz as remessas a partir da resposta da API.)
 */
@Injectable()
export class FlaOrderConsignmentsService extends MyAccountV2OrderConsignmentsService {
  override getGroupedConsignments(
    order: Order,
    pickup: boolean
  ): Consignment[] | undefined {
    return super.getGroupedConsignments(
      { ...order, consignments: visibleConsignments(order.consignments) },
      pickup
    );
  }
}

export function visibleConsignments(
  consignments: Consignment[] | undefined
): Consignment[] | undefined {
  if (!consignments?.some((c) => c.status === CANCELLED)) {
    return consignments;
  }
  const active = new Map<number, number>();
  consignments
    .filter((c) => c.status !== CANCELLED)
    .forEach((c) =>
      (c.entries ?? []).forEach((e) => {
        const key = e.orderEntry?.entryNumber;
        if (key !== undefined) {
          active.set(key, (active.get(key) ?? 0) + (e.quantity ?? 0));
        }
      })
    );

  const result: Consignment[] = [];
  for (const c of consignments) {
    if (c.status !== CANCELLED) {
      result.push(c);
      continue;
    }
    const entries: ConsignmentEntry[] = [];
    for (const e of c.entries ?? []) {
      const key = e.orderEntry?.entryNumber;
      const qty = e.quantity ?? 0;
      if (key === undefined) {
        entries.push(e);
        continue;
      }
      // o que foi para outra remessa não foi cancelado
      const moved = Math.min(qty, active.get(key) ?? 0);
      active.set(key, (active.get(key) ?? 0) - moved);
      if (qty - moved > 0) {
        entries.push({ ...e, quantity: qty - moved });
      }
    }
    if (entries.length) {
      result.push({ ...c, entries });
    }
  }
  return result;
}
