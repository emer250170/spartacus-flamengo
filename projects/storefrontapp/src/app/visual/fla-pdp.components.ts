import { ChangeDetectionStrategy, Component } from '@angular/core';
import { OrderEntry } from '@spartacus/cart/base/root';
import { WishListFacade } from '@spartacus/cart/wish-list/root';
import {
  AuthService,
  GlobalMessageService,
  GlobalMessageType,
  Product,
  ProductScope,
  RoutingService,
  WindowRef,
} from '@spartacus/core';
import { CurrentProductService } from '@spartacus/storefront';
import { Observable, combineLatest, of } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  filter,
  map,
  startWith,
  switchMap,
} from 'rxjs/operators';
import { FlaPersonalizationService } from './fla-personalization.service';
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

/** Estado do coração (lista de desejos) e o selo "Personalização disponível" */
interface PdpExtras {
  personalizable: boolean;
  loggedIn: boolean;
  /** Linha do produto na lista de desejos (vazio = não está) */
  wish?: OrderEntry;
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
      <ng-container *ngIf="extras$ | async as x">
        <span class="fla-pdp-pill" *ngIf="x.personalizable">{{
          'pdp.personalizable' | flaT
        }}</span>
      </ng-container>
      <div class="fla-pdp-title">
        <h1 class="fla-pdp-name">{{ h.name }}</h1>
        <div class="fla-pdp-actions" *ngIf="extras$ | async as x">
          <button
            type="button"
            class="fla-pdp-round"
            [class.is-on]="!!x.wish"
            [attr.aria-pressed]="!!x.wish"
            [attr.aria-label]="
              (x.wish ? 'pdp.wishRemove' : 'pdp.wishAdd') | flaT
            "
            [attr.title]="(x.wish ? 'pdp.wishRemove' : 'pdp.wishAdd') | flaT"
            (click)="toggleWish(h.code, x)"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"
                stroke-width="2"
              ></path>
            </svg>
          </button>
          <button
            type="button"
            class="fla-pdp-round"
            [attr.aria-label]="'pdp.share' | flaT"
            [attr.title]="'pdp.share' | flaT"
            (click)="share(h.name)"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <circle cx="6" cy="12" r="2.5"></circle>
              <circle cx="18" cy="6" r="2.5"></circle>
              <circle cx="18" cy="18" r="2.5"></circle>
              <path d="M8 11l8-4M8 13l8 4"></path>
            </svg>
          </button>
        </div>
      </div>
      <div class="fla-pdp-price">
        <strong>{{ h.price }}</strong>
        <span *ngIf="h.installments">{{ h.installments }}</span>
      </div>
      <div class="fla-pdp-member" *ngIf="h.member">
        <span>{{ h.member }}</span>
        <a *ngIf="h.join" routerLink="/socio-torcedor">{{
          'pdp.join' | flaT
        }}</a>
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

  /** Selo de personalização e coração da lista de desejos (07/11, mockup tela 4) */
  readonly extras$: Observable<PdpExtras> = this.currentProduct
    .getProduct()
    .pipe(
      map((p) => p?.code ?? ''),
      filter((code) => !!code && !/^JOGO-/.test(code)),
      distinctUntilChanged(),
      switchMap((code) =>
        combineLatest([
          this.personalization.options(code).pipe(
            map((o) => !!o?.available),
            startWith(false)
          ),
          this.auth.isUserLoggedIn(),
        ]).pipe(
          switchMap(([personalizable, loggedIn]) =>
            (loggedIn
              ? this.wishList.getWishList().pipe(
                  map((cart) =>
                    (cart?.entries ?? []).find((e) => e.product?.code === code)
                  ),
                  catchError(() => of(undefined)),
                  startWith(undefined)
                )
              : of(undefined)
            ).pipe(map((wish) => ({ personalizable, loggedIn, wish })))
          )
        )
      )
    );

  constructor(
    protected currentProduct: CurrentProductService,
    protected socio: SocioService,
    protected i18n: FlaI18nService,
    protected personalization: FlaPersonalizationService,
    protected auth: AuthService,
    protected wishList: WishListFacade,
    protected routing: RoutingService,
    protected messages: GlobalMessageService,
    protected winRef: WindowRef
  ) {}

  /** Coração: entra/sai da lista de desejos; sem login, vai para Entrar */
  toggleWish(code: string, x: PdpExtras): void {
    if (!x.loggedIn) {
      this.routing.go({ cxRoute: 'login' });
      return;
    }
    if (x.wish) {
      this.wishList.removeEntry(x.wish);
      this.messages.add(
        { key: 'addToWishList.removedFromWishList' },
        GlobalMessageType.MSG_TYPE_CONFIRMATION
      );
    } else {
      this.wishList.addEntry(code);
      this.messages.add(
        { key: 'addToWishList.addedToWishList' },
        GlobalMessageType.MSG_TYPE_CONFIRMATION
      );
    }
  }

  /** Compartilhar: o menu do aparelho (celular) ou copia o link */
  share(name: string): void {
    const win = this.winRef.nativeWindow;
    const url = win?.location?.href ?? '';
    const nav = win?.navigator as Navigator | undefined;
    if (nav?.share) {
      nav.share({ title: name, url }).catch(() => undefined);
      return;
    }
    if (nav?.clipboard) {
      nav.clipboard
        .writeText(url)
        .then(() =>
          this.messages.add(
            this.i18n.t('pdp.linkCopied'),
            GlobalMessageType.MSG_TYPE_CONFIRMATION
          )
        )
        .catch(() => undefined);
    }
  }

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
        member = this.i18n.t(
          own?.storeDiscount ? 'pdp.memberMine' : 'pdp.member',
          {
            plan: best.name,
            v,
            d: best.storeDiscount,
          }
        );
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
    <div
      class="fla-sizes"
      *ngIf="group$ | async as g"
      role="group"
      [attr.aria-label]="g.name"
    >
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
