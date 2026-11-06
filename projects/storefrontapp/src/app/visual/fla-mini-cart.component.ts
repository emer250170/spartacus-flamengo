import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
} from '@angular/core';
import {
  MiniCartComponent,
  MiniCartComponentService,
} from '@spartacus/cart/base/components/mini-cart';
import { LAUNCH_CALLER, LaunchDialogService } from '@spartacus/storefront';
import { take } from 'rxjs/operators';

/**
 * Carrinho do header (visual da loja oficial): o clique abre o carrinho em box lateral
 * à direita (FlaCartDrawerComponent) em vez de ir para a página do carrinho. Ctrl/⌘+clique
 * ou clique do meio continuam abrindo a página /cart. Substitui o MiniCartComponent do CMS.
 */
@Component({
  selector: 'cx-mini-cart',
  template: `
    <a
      #link
      *ngIf="{ quantity: quantity$ | async } as data"
      [attr.aria-label]="'miniCart.item' | cxTranslate: { count: data.quantity }"
      [title]="'miniCart.item' | cxTranslate: { count: data.quantity }"
      [routerLink]="{ cxRoute: 'cart' } | cxUrl"
      aria-haspopup="dialog"
      (click)="open($event)"
    >
      <cx-icon [type]="iconTypes.CART"></cx-icon>
      <span class="total">
        {{ 'miniCart.total' | cxTranslate: { total: total$ | async } }}
      </span>
      <span class="count">
        {{ 'miniCart.count' | cxTranslate: { count: quantity$ | async } }}
      </span>
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMiniCartComponent extends MiniCartComponent {
  @ViewChild('link') link?: ElementRef<HTMLElement>;

  constructor(
    miniCartComponentService: MiniCartComponentService,
    protected launchDialog: LaunchDialogService
  ) {
    super(miniCartComponentService);
  }

  open(event: MouseEvent): void {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0) {
      return;
    }
    event.preventDefault();
    this.launchDialog
      .openDialog(LAUNCH_CALLER.ADDED_TO_CART, this.link, undefined, {})
      ?.pipe(take(1))
      .subscribe();
  }
}
