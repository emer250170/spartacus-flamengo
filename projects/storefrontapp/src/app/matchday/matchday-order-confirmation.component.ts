import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Order, OrderFacade } from '@spartacus/order/root';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from './matchday-data.service';
import { MatchdaySelection } from './matchday.model';

interface MatchdayConfirmation {
  orderCode?: string;
  selection: MatchdaySelection;
  qr: boolean[][];
}

/**
 * "Tudo pronto para o jogo": cartões de ingresso, vaga, comida e camisa
 * na confirmação do pedido. Só aparece quando o pedido tem o ingresso
 * montado na jornada de matchday.
 * CMSFlexComponent com flexType MatchdayOrderConfirmationComponent.
 */
@Component({
  selector: 'fla-matchday-order-confirmation',
  templateUrl: './matchday-order-confirmation.component.html',
  styleUrls: ['./matchday-order-confirmation.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchdayOrderConfirmationComponent {
  // emite de novo na troca de idioma (os textos do cartão acompanham o site)
  confirmation$: Observable<MatchdayConfirmation | undefined> =
    this.i18n.lang$.pipe(
      switchMap(() => this.orderFacade.getOrderDetails()),
      map((order) => this.build(order))
    );

  constructor(
    protected orderFacade: OrderFacade,
    protected data: MatchdayDataService,
    protected i18n: FlaI18nService
  ) {}

  foodTotal(selection: MatchdaySelection): number {
    return selection.food.reduce((sum, f) => sum + f.quantity, 0);
  }

  protected build(order: Order | undefined): MatchdayConfirmation | undefined {
    const selection = this.data.readSelection();
    const codes = (order?.entries ?? []).map((e) => e.product?.code);
    const ticket = selection?.ticketCode ?? selection?.sectorCode;
    if (!order || !selection || !ticket || !codes.includes(ticket)) {
      return undefined;
    }
    // vaga, comida e camisa vêm dos itens do pedido (fonte da verdade);
    // do navegador só os detalhes que a linha de pedido não guarda
    // (placa, forma de entrega, nome e número da camisa)
    const entries = order.entries ?? [];
    const parking = entries.find((e) =>
      e.product?.code?.startsWith('FLA-EST-')
    );
    const shirt = entries.find((e) =>
      e.product?.code?.startsWith('FLA-CAMISA-J2-')
    );
    const merged: MatchdaySelection = {
      ...selection,
      parkingCode: parking?.product?.code,
      parkingName: parking?.product?.name,
      plate:
        parking && parking.product?.code === selection.parkingCode
          ? selection.plate
          : undefined,
      food: entries
        .filter((e) => e.product?.code?.startsWith('FLA-FOOD-'))
        .map((e) => ({
          code: e.product?.code ?? '',
          name: e.product?.name ?? '',
          quantity: e.quantity ?? 1,
        })),
      shirtCode: shirt?.product?.code,
      shirtSize: shirt?.product?.code?.split('-').pop(),
      shirtName:
        shirt && shirt.product?.code === selection.shirtCode
          ? selection.shirtName
          : undefined,
      shirtNumber:
        shirt && shirt.product?.code === selection.shirtCode
          ? selection.shirtNumber
          : undefined,
    };
    return {
      orderCode: order.code,
      selection: merged,
      qr: this.qrPattern(`${order.code}-${ticket}`),
    };
  }

  /** Padrão ilustrativo de QR (o código real viria do sistema de ingressos) */
  protected qrPattern(seed: string): boolean[][] {
    // gerador Park-Miller: deterministico e sem operadores bit a bit
    const modulus = 2147483647;
    let h = 1;
    for (const ch of seed) {
      h = (h * 31 + ch.charCodeAt(0)) % modulus;
    }
    h = h || 1;
    const size = 21;
    const finder = (r: number, c: number) => {
      const inBox = (r0: number, c0: number) =>
        r >= r0 && r < r0 + 7 && c >= c0 && c < c0 + 7;
      if (!(inBox(0, 0) || inBox(0, size - 7) || inBox(size - 7, 0))) {
        return undefined;
      }
      const rr = r < 7 ? r : r - (size - 7);
      const cc = c < 7 ? c : c - (size - 7);
      const edge = rr === 0 || rr === 6 || cc === 0 || cc === 6;
      const core = rr >= 2 && rr <= 4 && cc >= 2 && cc <= 4;
      return edge || core;
    };
    return Array.from({ length: size }, (_, r) =>
      Array.from({ length: size }, (_, c) => {
        const f = finder(r, c);
        if (f !== undefined) {
          return f;
        }
        h = (h * 48271) % modulus;
        return Math.floor(h / 65536) % 2 === 0;
      })
    );
  }
}
