import { ChangeDetectionStrategy, Component, Optional } from '@angular/core';
import { Router } from '@angular/router';
import { ActiveCartFacade, CartItemContext } from '@spartacus/cart/base/root';
import { Observable, combineLatest, of } from 'rxjs';
import { map, shareReplay, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  FlaPersonalizationService,
  PersonalizationInfo,
  personalizationSummary,
} from './fla-personalization.service';

/** /my-account/order/00045000 -> 00045000 */
const ORDER_URL = /\/my-account\/order\/([^/?#]+)/;

/** Uma busca por pedido para todas as linhas da lista (o pedido não muda) */
const ORDER_CACHE = new Map<string, Observable<PersonalizationInfo[]>>();

/**
 * "Personalizado: JUNIOR · 9" embaixo do manto (ou da camisa do dia de jogo) nas listas de itens
 * padrão do Spartacus, pelo outlet CartOutlets.ITEM_DETAILS (07/11): detalhe do pedido em Minha
 * conta › Meus pedidos e lista de itens da entrega no checkout. No pedido lê a personalização do
 * pedido; nas outras telas, a do carrinho. Carrinho, resumo do checkout e confirmação têm a linha
 * própria e não usam este componente.
 */
@Component({
  selector: 'fla-item-personalization',
  template: `
    <div class="fla-item-pers" *ngIf="summary$ | async as summary">
      {{ 'pz.cartLine' | flaT: { v: summary } }}
    </div>
  `,
  styles: [
    `
      .fla-item-pers {
        margin: 4px 0;
        font-size: 0.875rem;
        font-weight: 700;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaItemPersonalizationComponent {
  readonly summary$: Observable<string | undefined> = this.context
    ? combineLatest([this.context.item$, this.items(), this.i18n.lang$]).pipe(
        map(([item, items]) => {
          const p = items.find(
            (i) =>
              i.productCode === item?.product?.code &&
              (item?.entryNumber === undefined ||
                i.entryNumber === item.entryNumber)
          );
          return p
            ? personalizationSummary(p, this.i18n.t('pz.sponsorShort')) ||
                undefined
            : undefined;
        })
      )
    : of(undefined);

  constructor(
    protected personalization: FlaPersonalizationService,
    protected activeCart: ActiveCartFacade,
    protected router: Router,
    protected i18n: FlaI18nService,
    @Optional() protected context: CartItemContext
  ) {}

  /** Personalizações do pedido aberto ou do carrinho (uma busca por lista) */
  protected items(): Observable<PersonalizationInfo[]> {
    const order = ORDER_URL.exec(this.router.url)?.[1];
    if (order) {
      let cached = ORDER_CACHE.get(order);
      if (!cached) {
        cached = this.personalization
          .forOrder('current', order)
          .pipe(shareReplay({ bufferSize: 1, refCount: false }));
        ORDER_CACHE.set(order, cached);
      }
      return cached;
    }
    return this.activeCart.getActive().pipe(
      take(1),
      switchMap((cart) =>
        cart?.code && cart.user?.uid !== 'anonymous'
          ? this.personalization.forCart('current', cart.code)
          : of([])
      ),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }
}
