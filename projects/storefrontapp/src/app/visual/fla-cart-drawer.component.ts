import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { RoutingService } from '@spartacus/core';
import { FocusConfig, LaunchDialogService } from '@spartacus/storefront';
import { Observable, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import {
  DrawerEntry,
  DrawerView,
  FlaCartViewService,
} from './fla-cart-view.service';

export { DrawerEntry, DrawerView } from './fla-cart-view.service';

/**
 * Carrinho em box lateral à direita (visual da loja oficial, mockup aprovado em 05/11).
 * Abre ao clicar no carrinho do header (FlaMiniCartComponent) e ao adicionar um produto
 * (no lugar do "Adicionado ao carrinho" do Spartacus: mesmo LAUNCH_CALLER.ADDED_TO_CART,
 * com dialogType SIDEBAR_END). Fecha no X, no fundo escuro, no Esc, em "Continuar
 * comprando" e ao trocar de página. Itens e totais: FlaCartViewService (o mesmo da página
 * do carrinho).
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

  readonly view$: Observable<DrawerView | null> = this.cartView.view$;
  readonly loading$: Observable<boolean> = this.cartView.loading$;

  protected subscription = new Subscription();

  constructor(
    protected cartView: FlaCartViewService,
    protected launchDialog: LaunchDialogService,
    protected routing: RoutingService,
    protected el: ElementRef<HTMLElement>
  ) {}

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

  trackEntry(_i: number, e: DrawerEntry): number | undefined {
    return e.entry.entryNumber;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
