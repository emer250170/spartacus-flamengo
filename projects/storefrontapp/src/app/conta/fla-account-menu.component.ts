import { ChangeDetectionStrategy, Component } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { AuthService } from '@spartacus/core';
import { UserAccountFacade } from '@spartacus/user/account/root';
import { Observable, combineLatest, of } from 'rxjs';
import {
  distinctUntilChanged,
  filter,
  map,
  startWith,
  switchMap,
} from 'rxjs/operators';
import { SocioNavigationService } from '../socio/socio-navigation.service';
import { SocioService } from '../socio/socio.service';
import { ACCOUNT_MENU, AccountMenuGroup, accountPageOf } from './conta-pages';

interface MenuView {
  initials: string;
  first: string;
  email: string;
  plan?: string;
  current: string;
  groups: AccountMenuGroup[];
}

/**
 * Menu lateral de Minha conta (mockup ContaMenu, 07/11): quem está logado (iniciais, nome,
 * e-mail e o plano do Nação), os itens em grupos (Sócio-torcedor, Compras, Cadastro,
 * Privacidade, Ajuda) com a página atual marcada, e Sair. "Meu sócio-torcedor" só para quem é
 * ou já foi sócio, como no menu do header. No celular o menu fecha num botão com o nome da
 * página. Entra pelo outlet do template AccountPageTemplate (coluna da esquerda no tema).
 */
@Component({
  selector: 'fla-account-menu',
  template: `
    <nav
      class="fam"
      *ngIf="view$ | async as v"
      [class.is-open]="open"
      [attr.aria-label]="'acc.menu' | flaT"
    >
      <div class="fam-me">
        <span class="fam-avatar" aria-hidden="true">{{ v.initials }}</span>
        <div class="fam-me-text">
          <strong>{{ 'acc.hello' | flaT: { name: v.first } }}</strong>
          <span class="fam-mail">{{ v.email }}</span>
          <span class="fam-plan" *ngIf="v.plan">{{ v.plan }}</span>
        </div>
      </div>
      <button
        type="button"
        class="fam-toggle"
        aria-controls="fam-groups"
        [attr.aria-expanded]="open"
        (click)="open = !open"
      >
        <span>{{
          v.current ? ('acc.t.' + v.current | flaT) : ('acc.menuToggle' | flaT)
        }}</span>
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          aria-hidden="true"
          focusable="false"
        >
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <div class="fam-groups" id="fam-groups">
        <div class="fam-group" *ngFor="let g of v.groups">
          <span class="fam-group-title">{{ 'acc.g.' + g.key | flaT }}</span>
          <a
            *ngFor="let i of g.items"
            [routerLink]="i.url"
            [class.is-current]="i.key === v.current"
            [attr.aria-current]="i.key === v.current ? 'page' : null"
            (click)="open = false"
            >{{ 'acc.t.' + i.key | flaT }}</a
          >
        </div>
        <div class="fam-out">
          <a [routerLink]="{ cxRoute: 'logout' } | cxUrl">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
              focusable="false"
            >
              <path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10" />
            </svg>
            {{ 'acc.logout' | flaT }}
          </a>
        </div>
      </div>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaAccountMenuComponent {
  /** Menu aberto no celular */
  open = false;

  protected readonly current$: Observable<string> = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map((e) => e.urlAfterRedirects),
    startWith(this.router.url),
    map((url) => accountPageOf(url)?.menu ?? ''),
    distinctUntilChanged()
  );

  readonly view$: Observable<MenuView | null> = this.auth.isUserLoggedIn().pipe(
    distinctUntilChanged(),
    switchMap((loggedIn) =>
      !loggedIn
        ? of(null)
        : combineLatest([
            this.userAccount.get().pipe(startWith(undefined)),
            this.socio.getMembership().pipe(
              map((m) => m.member?.name),
              startWith(undefined)
            ),
            this.socioNav.showSocio().pipe(startWith(false)),
            this.current$,
          ]).pipe(
            map(([user, plan, showSocio, current]): MenuView => {
              const first = user?.firstName || user?.name?.split(' ')[0] || '';
              const last = user?.lastName || '';
              return {
                initials: initials(first, last),
                first,
                email: user?.uid ?? '',
                plan: plan || undefined,
                current,
                groups: ACCOUNT_MENU.map((g) => ({
                  ...g,
                  items: g.items.filter((i) => !i.socio || showSocio),
                })).filter((g) => g.items.length > 0),
              };
            })
          )
    )
  );

  constructor(
    protected router: Router,
    protected auth: AuthService,
    protected userAccount: UserAccountFacade,
    protected socio: SocioService,
    protected socioNav: SocioNavigationService
  ) {}
}

/** "Torcedor 2" → "T2"; "Emerson Oliveira" → "EO" */
function initials(first: string, last: string): string {
  const letters = (first.charAt(0) + (last.charAt(0) || first.charAt(1)))
    .trim()
    .toUpperCase();
  return letters || '?';
}
