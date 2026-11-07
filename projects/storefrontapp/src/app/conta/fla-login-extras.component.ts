import { ChangeDetectionStrategy, Component } from '@angular/core';
import {
  GlobalMessageService,
  GlobalMessageType,
  Translatable,
} from '@spartacus/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

/**
 * Login no visual do mockup (07/11): "Já tenho conta" em cima do formulário (outlet antes do
 * ReturningCustomerLoginComponent) e, logo abaixo, os erros do login (senha errada...) dentro do
 * próprio cartão; no login o aviso flutuante de erro fica escondido no tema. Some no login a
 * caminho do checkout (o tema só mostra com a classe fla-login no <html>).
 */
@Component({
  selector: 'fla-login-have',
  template: `
    <h2 class="flg-have">{{ 'lg.have' | flaT }}</h2>
    <div class="flg-error" role="alert" *ngIf="errors$ | async as errors">
      <ng-container *ngIf="errors.length">
        <p *ngFor="let e of errors">{{ e | cxTranslate }}</p>
      </ng-container>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaLoginHaveComponent {
  readonly errors$: Observable<Translatable[]> = this.messages
    .get()
    .pipe(map((m) => m?.[GlobalMessageType.MSG_TYPE_ERROR] ?? []));

  constructor(protected messages: GlobalMessageService) {}
}

/**
 * Login no visual do mockup (07/11): no cartão vermelho "Ainda não tem conta?", a frase e os três
 * motivos para criar a conta, entre o título e o botão Criar conta do Spartacus (outlet depois do
 * ReturningCustomerRegisterComponent; o tema põe na ordem). Só com a classe fla-login no <html>.
 */
@Component({
  selector: 'fla-login-perks',
  template: `
    <div class="flg-perks">
      <p>{{ 'lg.newText' | flaT }}</p>
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
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaLoginPerksComponent {
  readonly perks = ['lg.perk1', 'lg.perk2', 'lg.perk3'];
}
