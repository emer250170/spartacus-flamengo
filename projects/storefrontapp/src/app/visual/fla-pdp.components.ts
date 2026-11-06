import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Product, ProductScope, RoutingService } from '@spartacus/core';
import { CurrentProductService } from '@spartacus/storefront';
import { Observable, combineLatest, of } from 'rxjs';
import { catchError, filter, map, startWith } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { installments } from '../listagem/fla-product-card-price.component';
import { SocioService } from '../socio/socio.service';

/** Produtos da loja (os que têm o desconto de sócio na loja): código flamengo_… */
const STORE_PRODUCT = /^flamengo_/;

export interface PdpHeader {
  code: string;
  name: string;
  price: string;
  installments?: string;
  /** "Sócio Nação Maraca 1 paga R$ 404,99 (10% off)" */
  member?: string;
  /** quem ainda não tem plano com desconto na loja vê "Seja sócio" */
  join: boolean;
}

/**
 * Página do produto no visual da loja oficial (mockup tela 4): código, nome grande em caixa
 * alta (sem o " - P" do tamanho), preço em vermelho com parcelas e a faixa preta do preço de
 * sócio. Entra antes do ProductIntroComponent; o tema esconde a faixa do título, o "Ainda sem
 * avaliações" e o preço padrão quando este bloco aparece. Jogos (JOGO-…) não mudam.
 */
@Component({
  selector: 'fla-pdp-header',
  template: `
    <div class="fla-pdp" *ngIf="header$ | async as h">
      <nav class="fla-pdp-crumbs" [attr.aria-label]="'md.crumbs' | flaT">
        <a routerLink="/">{{ 'md.home' | flaT }}</a>
        <span aria-hidden="true">—</span>
        <span>{{ h.name }}</span>
      </nav>
      <span class="fla-pdp-code">{{ h.code }}</span>
      <h1 class="fla-pdp-name">{{ h.name }}</h1>
      <div class="fla-pdp-price">
        <strong>{{ h.price }}</strong>
        <span *ngIf="h.installments">{{ h.installments }}</span>
      </div>
      <div class="fla-pdp-member" *ngIf="h.member">
        <span>{{ h.member }}</span>
        <a *ngIf="h.join" routerLink="/socio-torcedor">{{ 'pdp.join' | flaT }}</a>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPdpHeaderComponent {
  readonly header$: Observable<PdpHeader | null> = combineLatest([
    this.currentProduct
      .getProduct([ProductScope.DETAILS, ProductScope.PRICE])
      .pipe(filter((p): p is Product => !!p)),
    combineLatest([this.socio.getMembership(), this.socio.getPlans()]).pipe(
      catchError(() => of(null)),
      startWith(null)
    ),
    this.i18n.lang$,
  ]).pipe(map(([product, socio]) => this.toHeader(product, socio)));

  constructor(
    protected currentProduct: CurrentProductService,
    protected socio: SocioService,
    protected i18n: FlaI18nService
  ) {}

  protected toHeader(
    product: Product,
    socio: [any, any[]] | null
  ): PdpHeader | null {
    const code = product.code ?? '';
    if (/^JOGO-/.test(code)) {
      return null;
    }
    const value = product.price?.value;
    if (value === undefined) {
      return null;
    }
    // variante de tamanho: "Manto Flamengo Jogo 3 Adidas 2026 - P" → sem o " - P"
    const name = (product.name ?? code).replace(
      product.baseProduct ? /\s+-\s+[^-]+$/ : /$^/,
      ''
    );
    const n = installments(value);
    let member: string | undefined;
    let join = false;
    if (socio && STORE_PRODUCT.test(code)) {
      const [membership, plans] = socio;
      const own = membership?.member ?? membership?.inCart;
      const best = own?.storeDiscount
        ? own
        : [...(plans ?? [])].sort(
            (a, b) => b.storeDiscount - a.storeDiscount
          )[0];
      if (best?.storeDiscount) {
        const v = this.i18n.money(
          Math.round(value * (100 - best.storeDiscount)) / 100
        );
        member = this.i18n.t(own?.storeDiscount ? 'pdp.memberMine' : 'pdp.member', {
          plan: best.name,
          v,
          d: best.storeDiscount,
        });
        join = !own?.storeDiscount;
      }
    }
    return {
      code,
      name,
      price: this.i18n.money(value),
      installments:
        n > 1
          ? this.i18n.t('list.installments', {
              n,
              v: this.i18n.money(Math.floor((value * 100) / n) / 100),
            })
          : undefined,
      member,
      join,
    };
  }
}

/**
 * Escopo próprio da matriz de tamanhos (o do seletor padrão só existe depois que o módulo dele
 * carrega). Configurado no VisualModule.
 */
export const FLA_SIZES_SCOPE = 'fla_sizes';
export const FLA_SIZES_ENDPOINT =
  'products/${productCode}?fields=code,multidimensional,' +
  'variantMatrix(variantOption(code),variantValueCategory(name),parentVariantCategory(name),elements)';

interface SizeChip {
  code: string;
  label: string;
  selected: boolean;
}

interface SizeGroup {
  name: string;
  chips: SizeChip[];
}

/**
 * Tamanhos em botões (P, M, G, GG...) no lugar da lista suspensa, como na loja oficial.
 * Lê a matriz de variantes do produto (uma dimensão, como Tamanho) e, ao clicar, abre a
 * variante. Com mais de uma dimensão (ex.: cor e tamanho) não aparece e fica o seletor padrão.
 * Entra depois do ProductMultiDimensionalSelectorComponent; o tema esconde o seletor padrão
 * quando estes botões aparecem.
 */
@Component({
  selector: 'fla-size-chips',
  template: `
    <div class="fla-sizes" *ngIf="group$ | async as g" role="group" [attr.aria-label]="g.name">
      <span class="fla-sizes-label">{{ g.name }}:</span>
      <div class="fla-sizes-list">
        <button
          type="button"
          *ngFor="let c of g.chips"
          [class.is-selected]="c.selected"
          [attr.aria-pressed]="c.selected"
          (click)="select(c)"
        >
          {{ c.label }}
        </button>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaSizeChipsComponent {
  readonly group$: Observable<SizeGroup | null> = this.currentProduct
    .getProduct(FLA_SIZES_SCOPE)
    .pipe(
      filter((p): p is Product => !!p),
      map((p) => this.toGroup(p))
    );

  constructor(
    protected currentProduct: CurrentProductService,
    protected routing: RoutingService
  ) {}

  select(chip: SizeChip): void {
    if (!chip.selected) {
      this.routing.go({ cxRoute: 'product', params: { code: chip.code } });
    }
  }

  protected toGroup(product: Product): SizeGroup | null {
    const matrix = product.variantMatrix ?? [];
    if (
      !product.multidimensional ||
      !matrix.length ||
      matrix.some((e) => (e.elements ?? []).length > 0)
    ) {
      return null;
    }
    const chips = matrix
      .filter((e) => !!e.variantOption?.code)
      .map((e) => ({
        code: e.variantOption?.code as string,
        label: e.variantValueCategory?.name ?? '',
        selected: e.variantOption?.code === product.code,
      }));
    return chips.length
      ? {
          name: matrix[0].parentVariantCategory?.name ?? '',
          chips,
        }
      : null;
  }
}
