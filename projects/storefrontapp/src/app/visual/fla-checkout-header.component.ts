import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  Injectable,
  OnDestroy,
  OnInit,
  Renderer2,
} from '@angular/core';
import { AuthRedirectStorageService, RoutingService } from '@spartacus/core';
import { Observable, Subscription, combineLatest } from 'rxjs';
import { distinctUntilChanged, map, shareReplay } from 'rxjs/operators';

/** Etapas do checkout no visual da loja oficial */
export const CHECKOUT_STEPS = ['cart', 'identification', 'delivery', 'payment'];

/**
 * Em que etapa da compra o torcedor está (1 a 4), 5 na confirmação do pedido e null fora do
 * checkout: carrinho = 1; login a caminho do checkout = 2 (Identificação); endereço e forma de
 * entrega = 3; pagamento e revisão = 4.
 */
@Injectable({ providedIn: 'root' })
export class FlaCheckoutStepService {
  readonly step$: Observable<number | null>;

  constructor(
    protected routing: RoutingService,
    protected authRedirect: AuthRedirectStorageService
  ) {
    this.step$ = combineLatest([
      this.routing.getRouterState(),
      this.authRedirect.getRedirectUrl(),
    ]).pipe(
      map(([state, redirect]) => {
        const route = state?.state?.semanticRoute ?? '';
        const url = state?.state?.url ?? '';
        if (route === 'cart') {
          return 1;
        }
        if (route === 'login' && /\/checkout/.test(redirect ?? '')) {
          return 2;
        }
        if (/checkout\/(delivery-address|delivery-mode)/.test(url)) {
          return 3;
        }
        if (/checkout\/(payment-details|review-order|payment-type)/.test(url)) {
          return 4;
        }
        if (route === 'orderConfirmation') {
          return 5;
        }
        return null;
      }),
      distinctUntilChanged(),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }
}

/**
 * Cabeçalho da compra (mockup "Checkout", 06/11): fundo vermelho com o logo (volta para a loja),
 * as etapas Carrinho — Identificação — Entrega — Pagamento (feitas com ✓, atual sublinhada) e
 * "Ambiente seguro". No carrinho, no checkout e na confirmação fica no lugar do header da loja:
 * marca o <html> com .fla-co-mode e o tema esconde o header, a faixa de baixo e o rodapé padrão
 * (no lugar dele entra a linha do FlaCheckoutFooterComponent). Entra pelo outlet cx-header.
 */
@Component({
  selector: 'fla-checkout-header',
  template: `
    <div class="fla-coh" *ngIf="step$ | async as step">
      <div class="fla-coh-inner">
        <a class="fla-coh-logo" routerLink="/" [attr.aria-label]="'co.backToStore' | flaT">
          <img src="/assets/logo-flamengo.svg" alt="" width="100" height="48" />
        </a>
        <ol class="fla-coh-steps" [attr.aria-label]="'co.steps' | flaT">
          <li *ngFor="let s of steps; let i = index" [class.is-current]="i + 1 === step" [class.is-done]="i + 1 < step">
            <span class="fla-coh-dash" *ngIf="i > 0" aria-hidden="true">—</span>
            <a *ngIf="i === 0 && step > 1 && step < 5; else label" routerLink="/cart">
              <ng-container *ngTemplateOutlet="content; context: { i: i, s: s }"></ng-container>
            </a>
            <ng-template #label>
              <span [attr.aria-current]="i + 1 === step ? 'step' : null">
                <ng-container *ngTemplateOutlet="content; context: { i: i, s: s }"></ng-container>
              </span>
            </ng-template>
          </li>
        </ol>
        <span class="fla-coh-secure">
          {{ 'co.secure' | flaT }}
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false">
            <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
          </svg>
        </span>
      </div>
      <ng-template #content let-i="i" let-s="s">
        <svg *ngIf="i + 1 < step" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true" focusable="false">
          <path d="M5 12l5 5L20 7" />
        </svg>
        {{ 'co.step.' + s | flaT }}
        <span class="cx-visually-hidden" *ngIf="i + 1 < step">{{ 'co.done' | flaT }}</span>
      </ng-template>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutHeaderComponent implements OnInit, OnDestroy {
  readonly steps = CHECKOUT_STEPS;
  readonly step$ = this.stepService.step$;
  protected subscription = new Subscription();

  constructor(
    protected stepService: FlaCheckoutStepService,
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.step$.subscribe((step) => {
        const html = this.document?.documentElement;
        if (!html) {
          return;
        }
        if (step) {
          this.renderer.addClass(html, 'fla-co-mode');
        } else {
          this.renderer.removeClass(html, 'fla-co-mode');
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.renderer.removeClass(this.document.documentElement, 'fla-co-mode');
  }
}

/**
 * Rodapé curto da compra (mockup "Checkout"): "Flamengo © ano" e os links de termos, privacidade,
 * trocas e contato. Só no carrinho, no checkout e na confirmação. Entra pelo outlet cx-footer.
 */
@Component({
  selector: 'fla-checkout-footer',
  template: `
    <div class="fla-cof" *ngIf="step$ | async">
      <div class="fla-cof-inner">
        <span>{{ 'co.copyright' | flaT: { y: year } }}</span>
        <nav [attr.aria-label]="'co.legal' | flaT">
          <a [routerLink]="{ cxRoute: 'termsAndConditions' } | cxUrl">{{ 'co.terms' | flaT }}</a>
          <a routerLink="/politica-de-privacidade">{{ 'co.privacy' | flaT }}</a>
          <a routerLink="/faq">{{ 'co.returns' | flaT }}</a>
          <a routerLink="/contact">{{ 'ft.contact' | flaT }}</a>
        </nav>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutFooterComponent {
  readonly step$ = this.stepService.step$;
  readonly year = new Date().getFullYear();

  constructor(protected stepService: FlaCheckoutStepService) {}
}
