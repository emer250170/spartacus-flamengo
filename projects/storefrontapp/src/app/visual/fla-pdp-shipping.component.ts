import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { OccEndpointsService, Product } from '@spartacus/core';
import { CurrentProductService } from '@spartacus/storefront';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, filter, map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';

interface QuoteOption {
  code: string;
  name: string;
  description?: string;
  price: number;
}

interface Quote {
  place?: string;
  options: QuoteOption[];
  pickup: boolean;
}

/** Produtos sem frete: ingresso, estacionamento, comida, experiência, plano */
const NO_SHIPPING = /^(JOGO-|EXP-|NACAO-|FLA-EST-|FLA-FOOD-|FLA-PERS)/;

/**
 * "Calcule o frete" na página do produto (mockup tela 4, 06/11): CEP → bairro e cidade (ViaCEP,
 * pelo navegador) e as formas de entrega da loja com o preço do servidor (/fladelivery/quote),
 * mais a retirada grátis na loja quando ligada. Depois, o link de trocas e devoluções.
 * Entra depois do botão "Adicionar ao carrinho" (outlet AddToCartComponent).
 */
@Component({
  selector: 'fla-pdp-shipping',
  template: `
    <div class="fla-ship" *ngIf="show$ | async">
      <form class="fla-ship-form" (submit)="calc($event, cepInput.value)">
        <label for="fla-ship-cep" class="fla-ship-label">{{ 'sh.title' | flaT }}</label>
        <div class="fla-ship-field">
          <input
            #cepInput
            id="fla-ship-cep"
            type="text"
            inputmode="numeric"
            autocomplete="postal-code"
            maxlength="9"
            [placeholder]="'sh.placeholder' | flaT"
            (input)="mask(cepInput)"
            [attr.aria-describedby]="error ? 'fla-ship-error' : null"
          />
          <button type="submit" [disabled]="loading">{{ 'sh.calc' | flaT }}</button>
        </div>
      </form>
      <p class="fla-ship-error" id="fla-ship-error" *ngIf="error" role="alert">{{ error }}</p>
      <div class="fla-ship-result" *ngIf="quote" role="status">
        <p class="fla-ship-place" *ngIf="quote.place">{{ quote.place }}</p>
        <ul>
          <li *ngFor="let o of quote.options">
            <span>{{ o.name }}</span>
            <strong>{{ money(o.price) }}</strong>
          </li>
          <li *ngIf="quote.pickup">
            <span>{{ 'sh.pickup' | flaT }}</span>
            <strong>{{ 'sh.free' | flaT }}</strong>
          </li>
        </ul>
      </div>
      <a class="fla-ship-returns" routerLink="/faq">{{ 'sh.returns' | flaT }}</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPdpShippingComponent {
  quote?: Quote;
  error = '';
  loading = false;

  readonly show$: Observable<boolean> = this.currentProduct.getProduct().pipe(
    filter((p): p is Product => !!p?.code),
    map((p) => !NO_SHIPPING.test(p.code as string))
  );

  constructor(
    protected currentProduct: CurrentProductService,
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  mask(input: HTMLInputElement): void {
    const d = input.value.replace(/\D/g, '').slice(0, 8);
    input.value = d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
    this.error = '';
  }

  money(v: number): string {
    return this.i18n.money(v);
  }

  calc(event: Event, value: string): void {
    event.preventDefault();
    const cep = (value ?? '').replace(/\D/g, '');
    if (cep.length !== 8) {
      this.error = this.i18n.t('sh.invalid');
      this.quote = undefined;
      return;
    }
    this.loading = true;
    this.error = '';
    forkJoin([
      this.http
        .get<{ erro?: boolean; bairro?: string; localidade?: string; uf?: string }>(
          `https://viacep.com.br/ws/${cep}/json/`
        )
        .pipe(catchError(() => of(null))),
      this.http
        .get<{ options?: QuoteOption[]; pickup?: boolean }>(
          `${this.occEndpoints.getBaseUrl()}/fladelivery/quote`,
          { params: { postalCode: cep } }
        )
        .pipe(catchError(() => of(null))),
    ]).subscribe(([place, quote]) => {
      this.loading = false;
      if (place?.erro) {
        this.error = this.i18n.t('sh.notFound');
        this.quote = undefined;
      } else if (!quote) {
        this.error = this.i18n.t('sh.unavailable');
        this.quote = undefined;
      } else {
        this.quote = {
          place: place?.localidade
            ? [place.bairro, `${place.localidade}/${place.uf}`]
                .filter((x) => !!x)
                .join(' · ')
            : undefined,
          options: quote.options ?? [],
          pickup: !!quote.pickup,
        };
      }
      this.cd.markForCheck();
    });
  }
}
