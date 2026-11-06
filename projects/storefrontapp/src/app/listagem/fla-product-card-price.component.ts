import { ChangeDetectionStrategy, Component, Optional } from '@angular/core';
import { Product } from '@spartacus/core';
import { ProductListItemContext } from '@spartacus/storefront';
import { EMPTY, Observable, combineLatest, of } from 'rxjs';
import { catchError, map, startWith } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioService } from '../socio/socio.service';

/** Parcelas como na loja oficial: até 10x, parcela de pelo menos R$ 40 */
export const FLA_MAX_INSTALLMENTS = 10;
export const FLA_MIN_INSTALLMENT = 40;

export interface FlaCardPrice {
  price: string;
  installments: string | null;
  /** produto com tamanhos (ou sem compra direta): a lista leva à página do produto */
  hasVariants: boolean;
  /** "Sócio Nação Maraca 1 paga R$ 404,99" / "Seu preço de sócio: R$ 404,99" */
  member?: string;
}

/** Desconto de sócio que o card mostra: o do plano do torcedor ou o maior dos planos */
interface CardMemberDiscount {
  pct: number;
  plan: string;
  mine: boolean;
}

/** Produtos da loja (os que têm o desconto de sócio na loja): código flamengo_NNNN */
const STORE_PRODUCT = /^flamengo_/;

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
      <div class="fla-card-member" *ngIf="card.member">{{ card.member }}</div>
      <span *ngIf="card.hasVariants" hidden data-fla-variants></span>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaProductCardPriceComponent {
  readonly card$: Observable<FlaCardPrice | null> = this.context
    ? combineLatest([this.context.product$, this.memberDiscount()]).pipe(
        map(([p, member]) => this.card(p, member))
      )
    : EMPTY;

  constructor(
    protected i18n: FlaI18nService,
    protected socio: SocioService,
    @Optional() protected context?: ProductListItemContext
  ) {}

  /**
   * Visual da loja oficial (06/11): preço de sócio no card. Quem é sócio (ou tem plano no
   * carrinho) vê o preço do seu plano; os demais, o do plano com maior desconto na loja.
   */
  protected memberDiscount(): Observable<CardMemberDiscount | null> {
    return combineLatest([this.socio.getMembership(), this.socio.getPlans()]).pipe(
      map(([membership, plans]) => {
        const own = membership.member ?? membership.inCart;
        if (own?.storeDiscount) {
          return { pct: own.storeDiscount, plan: own.name, mine: true };
        }
        const best = [...plans].sort((a, b) => b.storeDiscount - a.storeDiscount)[0];
        return best?.storeDiscount
          ? { pct: best.storeDiscount, plan: best.name, mine: false }
          : null;
      }),
      catchError(() => of(null)),
      startWith(null)
    );
  }

  protected card(
    product?: Product,
    member?: CardMemberDiscount | null
  ): FlaCardPrice | null {
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
      member:
        member && STORE_PRODUCT.test(product.code ?? '')
          ? this.i18n.t(member.mine ? 'list.memberMine' : 'list.member', {
              plan: member.plan,
              v: this.i18n.money(
                Math.round(value * (100 - member.pct)) / 100
              ),
            })
          : undefined,
    };
  }
}

export function installments(value: number): number {
  return Math.max(
    1,
    Math.min(FLA_MAX_INSTALLMENTS, Math.floor(value / FLA_MIN_INSTALLMENT))
  );
}
