import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
} from '@angular/core';
import {
  ActiveCartFacade,
  Cart,
  CartVoucherFacade,
  OrderEntry,
} from '@spartacus/cart/base/root';
import { RoutingService } from '@spartacus/core';
import { FocusConfig, LaunchDialogService } from '@spartacus/storefront';
import { Observable, Subscription, combineLatest, interval } from 'rxjs';
import { filter, map, startWith } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from '../matchday/matchday-data.service';
import { SocioService } from '../socio/socio.service';

/** Item do carrinho como o box mostra */
export interface DrawerEntry {
  entry: OrderEntry;
  name: string;
  /** "Tamanho: M" (variantes) */
  details: string;
  /** Preço cheio (antes do desconto), só quando há desconto */
  fullPrice?: string;
  price: string;
  /** Poltrona de jogo: sem quantidade e sem remover aqui (a jornada cuida) */
  seat: boolean;
}

export interface DrawerView {
  entries: DrawerEntry[];
  count: number;
  subtotal: string;
  discount?: string;
  discountLabel: string;
  total: string;
  /** "13:42" enquanto há poltrona reservada no carrinho */
  holdLeft?: string;
}

/**
 * Carrinho em box lateral à direita (visual da loja oficial, mockup aprovado em 05/11).
 * Abre ao clicar no carrinho do header (FlaMiniCartComponent) e ao adicionar um produto
 * (no lugar do "Adicionado ao carrinho" do Spartacus: mesmo LAUNCH_CALLER.ADDED_TO_CART,
 * com dialogType SIDEBAR_END). Fecha no X, no fundo escuro, no Esc, em "Continuar
 * comprando" e ao trocar de página.
 */
@Component({
  selector: 'fla-cart-drawer',
  templateUrl: './fla-cart-drawer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCartDrawerComponent implements OnInit, OnDestroy {
  focusConfig: FocusConfig = {
    trap: true,
    block: true,
    autofocus: 'button.fla-cd-close',
    focusOnEscape: true,
  };

  readonly view$: Observable<DrawerView | null>;
  readonly loading$: Observable<boolean> = this.activeCart
    .isStable()
    .pipe(map((stable) => !stable));

  protected subscription = new Subscription();

  constructor(
    protected activeCart: ActiveCartFacade,
    protected vouchers: CartVoucherFacade,
    protected launchDialog: LaunchDialogService,
    protected routing: RoutingService,
    protected i18n: FlaI18nService,
    protected matchday: MatchdayDataService,
    protected socio: SocioService,
    protected el: ElementRef<HTMLElement>
  ) {
    const member$ = this.socio.getMembership().pipe(
      map((m) => !!(m.member || m.inCart)),
      startWith(false)
    );
    const tick$ = interval(1000).pipe(startWith(0));
    this.view$ = combineLatest([
      this.activeCart.getActive(),
      member$,
      tick$,
      this.i18n.lang$,
    ]).pipe(map(([cart, member]) => this.toView(cart, member)));
  }

  ngOnInit(): void {
    // trocar de página fecha o box (inclusive "Finalizar compra")
    this.subscription.add(
      this.routing
        .getRouterState()
        .pipe(filter((state) => !!state.nextState))
        .subscribe(() => this.close('navigation'))
    );
  }

  /** Clique no fundo escuro (fora do box) fecha */
  @HostListener('click', ['$event'])
  onHostClick(event: UIEvent): void {
    if (event.target === this.el.nativeElement) {
      this.close('backdrop');
    }
  }

  close(reason: string): void {
    this.launchDialog.closeDialog(reason);
  }

  increase(e: DrawerEntry): void {
    this.activeCart.updateEntry(
      e.entry.entryNumber as number,
      (e.entry.quantity ?? 0) + 1
    );
  }

  decrease(e: DrawerEntry): void {
    const qty = (e.entry.quantity ?? 0) - 1;
    if (qty <= 0) {
      this.remove(e);
    } else {
      this.activeCart.updateEntry(e.entry.entryNumber as number, qty);
    }
  }

  remove(e: DrawerEntry): void {
    this.activeCart.removeEntry(e.entry);
  }

  applyCoupon(input: HTMLInputElement): void {
    const code = input.value.trim();
    if (code) {
      this.vouchers.addVoucher(code);
      input.value = '';
    }
  }

  trackEntry(_i: number, e: DrawerEntry): number | undefined {
    return e.entry.entryNumber;
  }

  protected toView(cart: Cart, member: boolean): DrawerView | null {
    const entries = cart?.entries ?? [];
    if (!entries.length) {
      return null;
    }
    const money = (v: number) => this.i18n.money(v);
    const items: DrawerEntry[] = entries.map((entry) => {
      const qty = entry.quantity ?? 1;
      const total = entry.totalPrice?.value ?? 0;
      const full = (entry.basePrice?.value ?? 0) * qty;
      const qualifiers =
        entry.product?.baseOptions?.[0]?.selected?.variantOptionQualifiers ??
        [];
      return {
        entry,
        name: entry.product?.name ?? entry.product?.code ?? '',
        details: qualifiers
          .filter((q) => q.name && q.value)
          .map((q) => `${q.name}: ${q.value}`)
          .join(' · '),
        fullPrice: full > total + 0.004 ? money(full) : undefined,
        price: entry.totalPrice?.formattedValue ?? money(total),
        seat: !!this.matchday.matchCodeOfSeat(entry.product?.code ?? ''),
      };
    });
    // subTotal = itens já com o desconto de cada item; orderDiscounts = desconto no pedido
    const orderDiscount = cart.orderDiscounts?.value ?? 0;
    const total = (cart.subTotal?.value ?? 0) - orderDiscount;
    const discount = cart.totalDiscounts?.value ?? 0;
    return {
      entries: items,
      count: cart.totalUnitCount ?? items.length,
      subtotal: money(total + discount),
      discount: discount > 0.004 ? money(discount) : undefined,
      discountLabel: this.i18n.t(member ? 'cd.memberDiscount' : 'cd.discount'),
      total: money(total),
      holdLeft: this.holdLeft(entries),
    };
  }

  /** Tempo que falta da reserva da poltrona que está no carrinho */
  protected holdLeft(entries: OrderEntry[]): string | undefined {
    const hold = this.matchday.readHold();
    if (
      !hold?.expiresAt ||
      !entries.some((e) => e.product?.code === hold.code)
    ) {
      return undefined;
    }
    const ms = hold.expiresAt - Date.now();
    if (ms <= 0) {
      return undefined;
    }
    const s = Math.floor(ms / 1000);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
