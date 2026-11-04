import { ChangeDetectionStrategy, Component, Optional } from '@angular/core';
import { Product } from '@spartacus/core';
import { ProductListItemContext } from '@spartacus/storefront';
import { EMPTY, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';

/** Parcelas como na loja oficial: até 10x, parcela de pelo menos R$ 40 */
export const FLA_MAX_INSTALLMENTS = 10;
export const FLA_MIN_INSTALLMENT = 40;

export interface FlaCardPrice {
  price: string;
  installments: string | null;
  /** produto com tamanhos (ou sem compra direta): a lista leva à página do produto */
  hasVariants: boolean;
}

/**
 * Card da listagem (04/11): preço e parcelas ("ou 4x de R$ 44,99") abaixo do nome, como na
 * loja oficial, também nos produtos com tamanho (o padrão mostra vazio). Marca o card dos
 * produtos com tamanho (data-fla-variants) para o tema esconder "Adicionar ao carrinho": o card
 * abre a página do produto, onde o tamanho é escolhido. Jogos (JOGO-...) não mudam.
 */
@Component({
  selector: 'fla-product-card-price',
  template: `
    <ng-container *ngIf="card$ | async as card">
      <div class="fla-card-price">{{ card.price }}</div>
      <div class="fla-card-installments" *ngIf="card.installments">
        {{ card.installments }}
      </div>
      <span *ngIf="card.hasVariants" hidden data-fla-variants></span>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaProductCardPriceComponent {
  readonly card$: Observable<FlaCardPrice | null> = this.context
    ? this.context.product$.pipe(map((p) => this.card(p)))
    : EMPTY;

  constructor(
    protected i18n: FlaI18nService,
    @Optional() protected context?: ProductListItemContext
  ) {}

  protected card(product?: Product): FlaCardPrice | null {
    if (!product || /^JOGO-/.test(product.code ?? '')) {
      return null;
    }
    const value =
      product.price?.value ?? product.priceRange?.minPrice?.value ?? undefined;
    if (value === undefined || value <= 0) {
      return null;
    }
    const n = installments(value);
    return {
      price: this.i18n.money(value),
      installments:
        n > 1
          ? this.i18n.t('list.installments', {
              n,
              v: this.i18n.money(Math.floor((value * 100) / n) / 100),
            })
          : null,
      hasVariants: !!product.multidimensional || product.purchasable === false,
    };
  }
}

export function installments(value: number): number {
  return Math.max(
    1,
    Math.min(FLA_MAX_INSTALLMENTS, Math.floor(value / FLA_MIN_INSTALLMENT))
  );
}
