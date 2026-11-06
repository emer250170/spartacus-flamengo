import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DrawerEntry,
  DrawerView,
  FlaCartViewService,
} from './fla-cart-view.service';

/**
 * Página do carrinho no visual da loja oficial (mockup "Checkout — Meu carrinho", 06/11): caixa
 * branca "Meu carrinho" (Produto · Valor · Quantidade · remover) e caixa "Resumo" (cupom, aviso do
 * desconto de sócio, subtotal, desconto, frete a calcular, total, Finalizar compra e Continuar
 * comprando). No lugar do CartComponent do CMS (cx-cart-details); o tema esconde os outros
 * componentes da página. Itens e totais: FlaCartViewService (o mesmo do carrinho lateral).
 */
@Component({
  selector: 'fla-cart-page',
  templateUrl: './fla-cart-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCartPageComponent {
  readonly view$: Observable<DrawerView | null> = this.cartView.view$;
  readonly loading$: Observable<boolean> = this.cartView.loading$;

  constructor(protected cartView: FlaCartViewService) {}

  increase(e: DrawerEntry): void {
    this.cartView.increase(e);
  }

  decrease(e: DrawerEntry): void {
    this.cartView.decrease(e);
  }

  remove(e: DrawerEntry): void {
    this.cartView.remove(e);
  }

  applyCoupon(input: HTMLInputElement): void {
    this.cartView.applyCoupon(input);
  }

  removeCoupon(code: string): void {
    this.cartView.removeCoupon(code);
  }

  trackEntry(_i: number, e: DrawerEntry): number | undefined {
    return e.entry.entryNumber;
  }
}
