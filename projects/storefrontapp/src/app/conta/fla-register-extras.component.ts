import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Criar conta no visual do mockup (07/11, quadro 42): "Seus dados" em cima do formulário do
 * Spartacus (outlet antes do RegisterCustomerComponent). O cartão branco vem do tema; só aparece
 * com a classe fla-register no <html> (/login/register).
 */
@Component({
  selector: 'fla-register-head',
  template: `<h2 class="frg-head">{{ 'rg.head' | flaT }}</h2>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaRegisterHeadComponent {}

/**
 * Criar conta (quadro 42): cartão vermelho à direita com os motivos para criar a conta e o
 * "Já tem conta? Entrar" (no lugar do link do formulário, que o tema esconde). Outlet depois do
 * RegisterCustomerComponent; só com a classe fla-register no <html>.
 */
@Component({
  selector: 'fla-register-aside',
  template: `
    <section class="frg-aside" aria-labelledby="frg-why">
      <h2 id="frg-why" class="frg-title">{{ 'rg.why' | flaT }}</h2>
      <p>{{ 'rg.whyText' | flaT }}</p>
      <ul>
        <li *ngFor="let k of perks">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="3"
            aria-hidden="true"
            focusable="false"
          >
            <path d="M5 12l5 5 9-10" />
          </svg>
          {{ k | flaT }}
        </li>
      </ul>
      <div class="frg-have">
        <span>{{ 'rg.have' | flaT }}</span>
        <a class="frg-login" routerLink="/login">{{ 'rg.signIn' | flaT }}</a>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaRegisterAsideComponent {
  readonly perks = ['lg.perk1', 'lg.perk2', 'lg.perk3'];
}
