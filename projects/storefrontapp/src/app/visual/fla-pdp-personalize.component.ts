import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActiveCartFacade, CartAddEntrySuccessEvent } from '@spartacus/cart/base/root';
import {
  AuthService,
  EventService,
  GlobalMessageService,
  GlobalMessageType,
  Product,
} from '@spartacus/core';
import { CurrentProductService } from '@spartacus/storefront';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import { distinctUntilChanged, filter, map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  FlaPersonalizationService,
  PersonalizationOptions,
  cleanPersonalizationName,
  cleanPersonalizationNumber,
} from './fla-personalization.service';

interface PersonalizeView {
  productCode: string;
  options: PersonalizationOptions;
  loggedIn: boolean;
}

/**
 * "Personalizar" na página do produto (mockup tela 4, 06/11): nome e número, kit de patrocínio
 * e patch, para os produtos que o servidor diz que podem ser personalizados (Mantos). A
 * escolha vale para o próximo "Adicionar ao carrinho" deste produto: quando o carrinho
 * confirma a inclusão, grava a personalização na linha do manto (as linhas de preço entram
 * pelo servidor) e recarrega o carrinho. Personalizar exige estar logado.
 * Entra depois do seletor de tamanho (outlet ProductMultiDimensionalSelectorComponent).
 */
@Component({
  selector: 'fla-pdp-personalize',
  templateUrl: './fla-pdp-personalize.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPdpPersonalizeComponent implements OnInit, OnDestroy {
  name = '';
  number = '';
  sponsor = false;
  patchCode = '';
  open = { nameNumber: true, sponsor: false, patch: false };
  error = '';
  saving = false;

  readonly view$: Observable<PersonalizeView | null>;
  protected productCode = '';
  protected subscription = new Subscription();

  constructor(
    protected currentProduct: CurrentProductService,
    protected personalization: FlaPersonalizationService,
    protected auth: AuthService,
    protected events: EventService,
    protected activeCart: ActiveCartFacade,
    protected messages: GlobalMessageService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {
    const code$ = this.currentProduct.getProduct().pipe(
      filter((p): p is Product => !!p?.code),
      map((p) => p.code as string),
      distinctUntilChanged()
    );
    this.view$ = combineLatest([
      code$.pipe(
        switchMap((productCode) =>
          /^JOGO-/.test(productCode)
            ? of({ productCode, options: { available: false } })
            : this.personalization
                .options(productCode)
                .pipe(map((options) => ({ productCode, options })))
        )
      ),
      this.auth.isUserLoggedIn(),
    ]).pipe(
      map(([v, loggedIn]) => {
        this.productCode = v.options.available ? v.productCode : '';
        return v.options.available ? { ...v, loggedIn } : null;
      })
    );
  }

  ngOnInit(): void {
    // outro tamanho (outra variante) mantém o que foi digitado; o código muda no view$
    this.subscription.add(
      this.events
        .get(CartAddEntrySuccessEvent)
        .pipe(
          filter(
            (e) =>
              !!this.productCode &&
              e.productCode === this.productCode &&
              this.hasChoice
          )
        )
        .subscribe((e) => this.apply(e))
    );
  }

  get hasChoice(): boolean {
    return !!(this.name.trim() || this.number || this.sponsor || this.patchCode);
  }

  onName(value: string, input: HTMLInputElement): void {
    this.name = cleanPersonalizationName(value);
    input.value = this.name;
    this.error = '';
  }

  onNumber(value: string, input: HTMLInputElement): void {
    this.number = cleanPersonalizationNumber(value);
    input.value = this.number;
    this.error = '';
  }

  toggle(section: 'nameNumber' | 'sponsor' | 'patch'): void {
    this.open[section] = !this.open[section];
  }

  price(value?: number): string {
    return value === undefined ? '' : this.i18n.money(value);
  }

  /** Soma das opções escolhidas (por unidade) */
  total(o: PersonalizationOptions): number {
    let t = 0;
    if ((this.name.trim() || this.number) && o.nameNumber?.price) {
      t += o.nameNumber.price;
    }
    if (this.sponsor && o.sponsor?.price) {
      t += o.sponsor.price;
    }
    const patch = o.patches?.find((p) => p.code === this.patchCode);
    if (patch?.price) {
      t += patch.price;
    }
    return t;
  }

  clear(): void {
    this.name = '';
    this.number = '';
    this.sponsor = false;
    this.patchCode = '';
    this.error = '';
  }

  protected apply(e: CartAddEntrySuccessEvent): void {
    const cartId = e.cartCode || e.cartId;
    if (!cartId) {
      return;
    }
    this.saving = true;
    this.cd.markForCheck();
    this.personalization
      .save('current', cartId, e.productCode, {
        name: this.name.trim(),
        number: this.number,
        sponsor: this.sponsor,
        patchCode: this.patchCode,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.clear();
          this.activeCart.reloadActiveCart();
          this.cd.markForCheck();
        },
        error: (err: Error) => {
          this.saving = false;
          this.error = err.message || this.i18n.t('pz.error');
          this.messages.add(
            this.i18n.t('pz.notApplied', { m: this.error }),
            GlobalMessageType.MSG_TYPE_ERROR
          );
          this.cd.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
