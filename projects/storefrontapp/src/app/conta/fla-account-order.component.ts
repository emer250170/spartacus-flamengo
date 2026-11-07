import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { OrderEntry } from '@spartacus/cart/base/root';
import { OrderDetailsService } from '@spartacus/order/components';
import { Order } from '@spartacus/order/root';
import { Observable, combineLatest, of } from 'rxjs';
import {
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import {
  BodyView,
  DIGITAL,
  FlaOcBodyComponent,
  OcItem,
} from '../visual/fla-order-confirmation.component';
import { statusTone } from './conta-pages';

interface OrderLine {
  entry: OrderEntry;
  name: string;
  code: string;
  details: string;
  qty: number;
  price: string;
  /** Preço sem desconto, riscado */
  was?: string;
  /** "Comprar de novo" (produto da loja, não ingresso, plano ou serviço) */
  again: boolean;
}

interface OrderGroup {
  key: 'ship' | 'pickup';
  sub: string;
  lines: OrderLine[];
}

interface AccountOrderView {
  order: Order;
  tone: string;
  groups: OrderGroup[];
  body: BodyView;
  saved?: string;
}

/**
 * Detalhe do pedido em Minha conta no visual do mockup (07/11): número, data e situação em linha;
 * itens a entregar e itens para retirar (miniatura, código, detalhes, quantidade, preço com o
 * valor cheio riscado, "Comprar de novo"); "Entrega e pagamento" ao lado do Resumo. Os valores,
 * a entrega, o pagamento e os detalhes dos itens (personalização, titular do ingresso) vêm da
 * mesma lógica da confirmação do pedido (FlaOcBodyComponent), com o pedido da página. Ficam os
 * componentes do Spartacus para o Pix/boleto, exportar CSV e Voltar/Cancelar/Devolver; os outros
 * somem no tema. Entra pelo outlet do slot BodyContent (antes) e só aparece no detalhe do pedido.
 */
@Component({
  selector: 'fla-account-order',
  templateUrl: './fla-account-order.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaAccountOrderComponent extends FlaOcBodyComponent {
  readonly account$: Observable<AccountOrderView | null> = combineLatest([
    this.order$,
    this.view$,
  ]).pipe(
    map(([order, body]) =>
      order && body ? this.toAccountView(order, body) : null
    ),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  /** Pedido da página /my-account/order/{code} (no lugar do pedido da confirmação) */
  protected orderSource(): Observable<Order | undefined> {
    const details = inject(OrderDetailsService);
    // só no detalhe do pedido (o componente entra em toda página pelo outlet do BodyContent)
    return this.routing.getRouterState().pipe(
      map((s) => s?.state?.semanticRoute === 'orderDetails'),
      distinctUntilChanged(),
      switchMap((on) => (on ? details.getOrderDetails() : of(undefined))),
      map((o) => (o?.code ? o : undefined)),
      distinctUntilChanged(),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }

  protected toAccountView(order: Order, body: BodyView): AccountOrderView {
    const money = (v: number) => this.i18n.money(v);
    const lines = body.items.map((item) => this.toLine(order, item, money));
    const ship = lines.filter((l) => !l.entry.deliveryPointOfService);
    const pickup = lines.filter((l) => !!l.entry.deliveryPointOfService);
    const groups: OrderGroup[] = [];
    if (ship.length) {
      groups.push({
        key: 'ship',
        sub: [order.deliveryMode?.name, order.deliveryMode?.description]
          .filter((x) => !!x)
          .join(' · '),
        lines: ship,
      });
    }
    if (pickup.length) {
      const pos = pickup[0].entry.deliveryPointOfService;
      const a = pos?.address;
      groups.push({
        key: 'pickup',
        sub: [
          pos?.displayName ?? pos?.name,
          [a?.line1, a?.line2].filter((x) => !!x).join(', '),
          a?.town,
        ]
          .filter((x) => !!x)
          .join(' · '),
        lines: pickup,
      });
    }
    const saved = order.totalDiscounts?.value ?? 0;
    return {
      order,
      tone: statusTone(order.status ?? order.statusDisplay ?? ''),
      groups,
      body,
      saved: saved > 0.004 ? money(saved) : undefined,
    };
  }

  protected toLine(
    order: Order,
    item: OcItem,
    money: (v: number) => string
  ): OrderLine {
    const entry = item.entry;
    const code = entry.product?.code ?? '';
    const qty = entry.quantity ?? 1;
    const full = (entry.basePrice?.value ?? 0) * qty;
    const paid = entry.totalPrice?.value ?? full;
    // promoção do item (ex.: "Sócio Nação Maraca 3: 60% de desconto no ingresso")
    const promo = (order.appliedProductPromotions ?? [])
      .filter((p) =>
        (p.consumedEntries ?? []).some(
          (c) => c.orderEntryNumber === entry.entryNumber
        )
      )
      .map((p) => p.description ?? '')
      .filter((d) => !!d);
    // o último detalhe da confirmação é "N unidade(s)": aqui a quantidade tem coluna própria
    const details = [...item.details.slice(0, -1), ...promo].join(' · ');
    return {
      entry,
      name: item.name,
      code,
      details,
      qty,
      price: item.price,
      was: full - paid > 0.004 ? money(full) : undefined,
      again:
        !!code && !DIGITAL.test(code) && entry.product?.purchasable !== false,
    };
  }
}
