import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  Injectable,
  NgModule,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { RouterModule } from '@angular/router';
import { UrlModule, WindowRef } from '@spartacus/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { FlaCustomerDataService } from '../clearsale/clearsale.service';
import { FlaClearSaleModule } from '../clearsale/clearsale.module';
import { FlaI18nModule } from '../i18n/fla-i18n.service';

export interface FlaAcceptState {
  terms: boolean;
  privacy: boolean;
  /** o torcedor tentou finalizar sem os aceites: mostra o aviso */
  touched: boolean;
}

/** Rota da página de Política de Privacidade (ContentPage no CMS, ImpEx 11-politica-privacidade) */
export const PRIVACY_PAGE = '/politica-de-privacidade';

/**
 * Aceites obrigatórios do pagamento (mockup "Checkout — Pagamento", 06/11): Termos e
 * Condições de Compra e Política de Privacidade. Um estado só para os dois caminhos de
 * finalizar: o "Pagar e finalizar" do cartão pela Adyen (AdyenCardComponent) e o
 * "Finalizar pedido" do Spartacus (Pix, boleto, planos). No do Spartacus, a caixa de termos
 * dele fica escondida e é marcada daqui quando os dois aceites estão dados.
 */
@Injectable({ providedIn: 'root' })
export class FlaCheckoutAcceptService {
  readonly state$ = new BehaviorSubject<FlaAcceptState>({
    terms: false,
    privacy: false,
    touched: false,
  });

  constructor(
    protected winRef: WindowRef,
    protected customerData: FlaCustomerDataService
  ) {
    // CPF e telefone gravados (contas antigas, ClearSale): reavalia a caixa do "Finalizar pedido"
    this.customerData.state$.subscribe(() => this.syncSpartacus());
  }

  get state(): FlaAcceptState {
    return this.state$.value;
  }

  /** Termos e Privacidade marcados e, nas contas antigas, CPF e telefone gravados (07/11, ClearSale) */
  ok(): boolean {
    return this.state.terms && this.state.privacy && this.customerData.complete;
  }

  set(key: 'terms' | 'privacy', value: boolean): void {
    this.state$.next({ ...this.state, [key]: value });
    this.syncSpartacus();
  }

  /** Tentou finalizar sem os aceites */
  touch(): void {
    this.state$.next({ ...this.state, touched: true });
  }

  /** Nova visita à revisão: começa sem aceites (e confere de novo CPF e telefone do cadastro) */
  reset(): void {
    this.customerData.load();
    this.state$.next({ terms: false, privacy: false, touched: false });
    this.syncSpartacus();
  }

  /** A caixa de termos do "Finalizar pedido" do Spartacus segue os dois aceites */
  protected syncSpartacus(): void {
    const want = this.ok();
    this.winRef.document
      ?.querySelectorAll<HTMLInputElement>(
        'cx-place-order input[formcontrolname="termsAndConditions"]'
      )
      .forEach((input) => {
        if (input.checked !== want) {
          input.click();
        }
      });
  }
}

/**
 * Bloco "Aceites" (Termos e Privacidade, obrigatórios). Duas instâncias com o mesmo estado:
 * antes do "Finalizar pedido" do Spartacus (outlet CheckoutPlaceOrder; some quando o cartão é
 * pela Adyen) e dentro do formulário de cartão da Adyen, antes do "Pagar e finalizar".
 */
@Component({
  selector: 'fla-checkout-accept',
  template: `
    <fla-checkout-dados></fla-checkout-dados>
    <fieldset
      class="fla-acc"
      *ngIf="state$ | async as s"
      [class.is-error]="s.touched && !(s.terms && s.privacy)"
    >
      <legend>{{ 'acc.title' | flaT }}</legend>
      <label>
        <input
          type="checkbox"
          [checked]="s.terms"
          (change)="set('terms', $event)"
        />
        <span>
          {{ 'acc.terms1' | flaT }}
          <a
            [routerLink]="{ cxRoute: 'termsAndConditions' } | cxUrl"
            target="_blank"
            rel="noopener"
            >{{ 'acc.termsLink' | flaT }}</a
          >{{ 'acc.terms2' | flaT }}
          <a routerLink="/faq" target="_blank" rel="noopener">{{
            'acc.returnsLink' | flaT
          }}</a>
          {{ 'acc.terms3' | flaT }}
          <strong>{{ 'acc.required' | flaT }}</strong>
        </span>
      </label>
      <label>
        <input
          type="checkbox"
          [checked]="s.privacy"
          (change)="set('privacy', $event)"
        />
        <span>
          {{ 'acc.privacy1' | flaT }}
          <a [routerLink]="privacyPage" target="_blank" rel="noopener">{{
            'acc.privacyLink' | flaT
          }}</a>
          {{ 'acc.privacy2' | flaT }}
          <strong>{{ 'acc.required' | flaT }}</strong>
        </span>
      </label>
      <p
        class="fla-acc-hint"
        *ngIf="!(s.terms && s.privacy)"
        [attr.role]="s.touched ? 'alert' : 'status'"
      >
        {{ 'acc.missing' | flaT }}
      </p>
    </fieldset>
  `,
  host: { '[class.fla-acc-inline]': 'inline' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutAcceptComponent implements OnInit, OnDestroy {
  readonly state$ = this.accept.state$;
  readonly privacyPage = PRIVACY_PAGE;
  /** dentro do formulário da Adyen (a instância do outlet some quando a Adyen está ativa) */
  inline = false;

  protected subscription = new Subscription();

  constructor(
    protected accept: FlaCheckoutAcceptService,
    protected host: ElementRef<HTMLElement>,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.inline = !!this.host.nativeElement.closest('fla-adyen-card');
    if (!this.inline) {
      // a instância do outlet nasce com a revisão: cada visita começa sem aceites
      this.accept.reset();
    }
    this.subscription.add(
      this.accept.state$.subscribe(() => this.cd.markForCheck())
    );
  }

  set(key: 'terms' | 'privacy', event: Event): void {
    this.accept.set(key, (event.target as HTMLInputElement).checked);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    UrlModule,
    FlaI18nModule,
    FlaClearSaleModule,
  ],
  declarations: [FlaCheckoutAcceptComponent],
  exports: [FlaCheckoutAcceptComponent],
})
export class FlaCheckoutAcceptModule {}
