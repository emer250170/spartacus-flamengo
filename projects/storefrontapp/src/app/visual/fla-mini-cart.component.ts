import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { SemanticPathService } from '@spartacus/core';
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
 *
 * O link é um href simples, sem routerLink: o routerLink navega mesmo com preventDefault, e a
 * troca de página fechava o box logo depois de abrir (06/11).
 */
@Component({
  selector: 'cx-mini-cart',
  template: `
    <a
      #link
      *ngIf="{ quantity: quantity$ | async } as data"
      [attr.aria-label]="'miniCart.item' | cxTranslate: { count: data.quantity }"
      [title]="'miniCart.item' | cxTranslate: { count: data.quantity }"
      [href]="cartUrl"
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

  /** /electronics-spa/pt/BRL/cart (com o site, o idioma e a moeda atuais) */
  get cartUrl(): string {
    return this.router.serializeUrl(
      this.router.createUrlTree(this.semanticPath.transform({ cxRoute: 'cart' }))
    );
  }

  constructor(
    miniCartComponentService: MiniCartComponentService,
    protected launchDialog: LaunchDialogService,
    protected router: Router,
    protected semanticPath: SemanticPathService
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
