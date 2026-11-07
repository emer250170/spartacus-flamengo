import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UserAccountFacade } from '@spartacus/user/account/root';
import { map } from 'rxjs/operators';

/**
 * Minha conta › E-mail (mockup, 07/11): "E-mail atual: fulano@..." e o aviso de que o novo e-mail
 * passa a ser o login, em cima do formulário do Spartacus (outlet antes do UpdateEmailComponent;
 * o tema junta os dois no mesmo cartão).
 */
@Component({
  selector: 'fla-email-current',
  template: `
    <ng-container *ngIf="email$ | async as email">
      <p class="fec-now">
        {{ 'acc.em.now' | flaT }} <strong>{{ email }}</strong>
      </p>
      <p class="fec-note">{{ 'acc.em.note' | flaT }}</p>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaEmailCurrentComponent {
  readonly email$ = this.userAccount.get().pipe(map((u) => u?.uid ?? ''));

  constructor(protected userAccount: UserAccountFacade) {}
}
