import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { FlaCheckoutStepService } from './fla-checkout-header.component';
import { DrawerView, FlaCartViewService } from './fla-cart-view.service';

/**
 * Identificação da compra (mockup "Checkout — Identificação", 06/11): quando o login vem do
 * "Finalizar compra" (etapa 2 do FlaCheckoutStepService), o título e a frase antes do
 * formulário de login. No login normal não aparece. Entra pelo outlet LeftContentSlot (antes).
 */
@Component({
  selector: 'fla-checkout-identity-intro',
  template: `
    <div class="fla-ci-intro" *ngIf="(step$ | async) === 2">
      <h1 class="fla-co-title">{{ 'ci.title' | flaT }}</h1>
      <p>{{ 'ci.intro' | flaT }}</p>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutIdentityIntroComponent {
  readonly step$ = this.steps.step$;
  constructor(protected steps: FlaCheckoutStepService) {}
}

/**
 * Depois do formulário de login, na Identificação: "Por que pedimos login" (4 motivos), as
 * etapas seguintes fechadas e, à direita (pelo tema), o Resumo do carrinho com "Alterar
 * carrinho". Entra pelo outlet LeftContentSlot (depois).
 */
@Component({
  selector: 'fla-checkout-identity-extra',
  template: `
    <ng-container *ngIf="view$ | async as v">
      <div class="fla-ci-why">
        <strong>{{ 'ci.why' | flaT }}</strong>
        <ul>
          <li *ngFor="let r of reasons">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true" focusable="false">
              <path d="M5 12l5 5L20 7" />
            </svg>
            {{ r | flaT }}
          </li>
        </ul>
      </div>
      <div class="fla-ci-next" *ngFor="let n of next">
        <h2>{{ n | flaT }}</h2>
      </div>

      <aside class="fla-co-box fla-ci-summary" *ngIf="v.cart as c" aria-labelledby="fla-ci-summary-title">
        <div class="fla-ci-summary-head">
          <h2 id="fla-ci-summary-title" class="fla-co-title">{{ 'cp.summary' | flaT }}</h2>
          <a [routerLink]="{ cxRoute: 'cart' } | cxUrl">{{ 'ci.changeCart' | flaT }}</a>
        </div>
        <ul class="fla-ci-items">
          <li *ngFor="let e of c.entries">
            <span class="fla-ci-thumb" [class.fla-ci-thumb-me]="!!e.estudio">
              <!-- camiseta do Mengo Estúdio: sem foto no catálogo, a miniatura com a estampa (como no carrinho) -->
              <fla-me-camiseta *ngIf="e.estudio as me; else foto" [estampa]="me.estampa" [cor]="me.cor"></fla-me-camiseta>
              <ng-template #foto><cx-media [container]="e.entry.product?.images?.PRIMARY" format="thumbnail" [alt]="''"></cx-media></ng-template>
            </span>
            <span class="fla-ci-item">
              <strong>{{ e.name }}</strong>
              <span *ngIf="e.details">{{ e.details }}</span>
              <span *ngIf="e.personalization" class="fla-cp-pers">{{ 'pz.cartLine' | flaT: { v: e.personalization } }}</span>
              <span>{{ 'ci.units' | flaT: { n: e.entry.quantity } }}</span>
            </span>
            <strong>{{ e.price }}</strong>
          </li>
        </ul>
        <div class="fla-cp-line"><span>{{ 'cd.subtotal' | flaT }}</span><span>{{ c.subtotal }}</span></div>
        <div class="fla-cp-line fla-cp-discount" *ngIf="c.discount"><span>{{ c.discountLabel }}</span><span>− {{ c.discount }}</span></div>
        <div class="fla-cp-line fla-cp-total"><span>{{ 'cd.total' | flaT }}</span><span>{{ c.total }}</span></div>
      </aside>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutIdentityExtraComponent {
  readonly reasons = ['ci.why1', 'ci.why2', 'ci.why3', 'ci.why4'];
  readonly next = ['ci.nextDelivery', 'ci.nextPayment'];

  /** Só na etapa 2; o Resumo só quando há carrinho */
  readonly view$: Observable<{ cart: DrawerView | null } | null> = combineLatest([
    this.steps.step$,
    this.cartView.view$,
  ]).pipe(map(([step, cart]) => (step === 2 ? { cart } : null)));

  constructor(
    protected steps: FlaCheckoutStepService,
    protected cartView: FlaCartViewService
  ) {}
}
