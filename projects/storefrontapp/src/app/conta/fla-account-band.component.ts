import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
  OnInit,
  Renderer2,
} from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { CmsService } from '@spartacus/core';
import { Observable, Subscription, combineLatest } from 'rxjs';
import {
  distinctUntilChanged,
  filter,
  map,
  shareReplay,
  startWith,
} from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { FlaCheckoutStepService } from '../visual/fla-checkout-header.component';
import { FlaDomLabelsService } from './fla-dom-labels.service';
import {
  ACCOUNT_LEADS,
  ACCOUNT_SHORT_CRUMB,
  ACCOUNT_WITH_CODE,
  accountPageOf,
  isLoginUrl,
} from './conta-pages';

/** Classes no <html> para o tema (fundo cinza, faixa escura, menu lateral, cartões) */
export const ACCOUNT_CLASS = 'fla-account';
export const LOGIN_CLASS = 'fla-login';
export const TERM_CLASS = 'fla-term';

/** Página do Termo do Nação (/socio-torcedor/termo) */
const TERM_URL = /\/socio-torcedor\/termo\/?(?:[?#]|$)/;

type Mode = 'account' | 'login' | 'term' | null;

interface Band {
  login: boolean;
  /** Item do meio da trilha (Minha conta, Sócio-torcedor) */
  parent?: { label: string; url: string };
  crumb: string;
  title: string;
  titleRed?: string;
  lead?: string;
}

/**
 * Faixa escura do topo de Minha conta e do login (mockup, 07/11): trilha "Início › Minha conta ›
 * página", título e a frase da página. Fica no lugar do breadcrumb cinza (o tema esconde o
 * breadcrumb com as classes fla-account / fla-login no <html>). Login a caminho do checkout
 * (cabeçalho da compra) fica como está. Entra pelo outlet do slot BottomHeaderSlot.
 */
@Component({
  selector: 'fla-account-band',
  template: `
    <div class="fab" *ngIf="band$ | async as b">
      <div class="fab-in">
        <nav class="fab-crumbs" [attr.aria-label]="'acc.crumbs' | flaT">
          <a routerLink="/">{{ 'acc.home' | flaT }}</a>
          <span aria-hidden="true">›</span>
          <ng-container *ngIf="b.parent as parent">
            <a [routerLink]="parent.url">{{ parent.label }}</a>
            <span aria-hidden="true">›</span>
          </ng-container>
          <span class="fab-here" aria-current="page">{{ b.crumb }}</span>
        </nav>
        <h1 class="fab-title">
          {{ b.title }}
          <span *ngIf="b.titleRed">{{ b.titleRed }}</span>
        </h1>
        <!-- sempre presente: a faixa tem a mesma altura com ou sem frase -->
        <p class="fab-lead" [attr.aria-hidden]="b.lead ? null : 'true'">
          {{ b.lead || ' ' }}
        </p>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaAccountBandComponent implements OnInit, OnDestroy {
  protected readonly url$: Observable<string> = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map((e) => e.urlAfterRedirects),
    startWith(this.router.url),
    distinctUntilChanged()
  );

  protected readonly mode$: Observable<Mode> = combineLatest([
    this.url$,
    this.steps.step$.pipe(startWith(0)),
  ]).pipe(
    map(
      ([url, step]): Mode =>
        accountPageOf(url)
          ? 'account'
          : TERM_URL.test(url ?? '')
            ? 'term'
            : isLoginUrl(url) && !step
              ? 'login'
              : null
    ),
    distinctUntilChanged(),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  readonly band$: Observable<Band | null> = combineLatest([
    this.mode$,
    this.url$,
    this.cms.getCurrentPage().pipe(startWith(undefined)),
    this.i18n.lang$,
  ]).pipe(
    map(([mode, url, page]) => {
      if (mode === 'term') {
        return {
          login: false,
          parent: {
            label: this.i18n.t('st.band.crumbParent'),
            url: '/socio-torcedor',
          },
          crumb: this.i18n.t('st.band.crumb'),
          title: this.i18n.t('st.band.title'),
          titleRed: this.i18n.t('st.band.titleRed'),
          lead: this.i18n.t('st.band.lead'),
        };
      }
      if (mode === 'login') {
        return {
          login: true,
          crumb: this.i18n.t('lg.crumb'),
          title: this.i18n.t('lg.title'),
          titleRed: this.i18n.t('lg.titleRed'),
          lead: this.i18n.t('lg.lead'),
        };
      }
      const acc = mode === 'account' ? accountPageOf(url) : null;
      if (!acc) {
        return null;
      }
      const params = { code: acc.code ?? '' };
      const title = acc.key
        ? ACCOUNT_WITH_CODE.includes(acc.key) && !acc.code
          ? (page?.title ?? '')
          : this.i18n.t('acc.t.' + acc.key, params)
        : (page?.title ?? '');
      const crumb =
        acc.key && ACCOUNT_SHORT_CRUMB.includes(acc.key)
          ? this.i18n.t('acc.c.' + acc.key, params)
          : title;
      return {
        login: false,
        parent: {
          label: this.i18n.t('acc.account'),
          url: '/my-account/orders',
        },
        crumb,
        title,
        lead: ACCOUNT_LEADS.includes(acc.key)
          ? this.i18n.t('acc.l.' + acc.key)
          : undefined,
      };
    })
  );

  protected subscription = new Subscription();

  constructor(
    protected router: Router,
    protected cms: CmsService,
    protected steps: FlaCheckoutStepService,
    protected i18n: FlaI18nService,
    protected renderer: Renderer2,
    protected domLabels: FlaDomLabelsService,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  ngOnInit(): void {
    // paginação em português e cor do selo nas listas do Spartacus (todas as páginas)
    this.domLabels.start(this.document);
    this.subscription.add(
      this.mode$.subscribe((mode) => {
        const html = this.document?.documentElement;
        if (!html) {
          return;
        }
        this.toggle(html, ACCOUNT_CLASS, mode === 'account');
        this.toggle(html, LOGIN_CLASS, mode === 'login');
        this.toggle(html, TERM_CLASS, mode === 'term');
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    const html = this.document?.documentElement;
    if (html) {
      this.renderer.removeClass(html, ACCOUNT_CLASS);
      this.renderer.removeClass(html, LOGIN_CLASS);
      this.renderer.removeClass(html, TERM_CLASS);
    }
  }

  protected toggle(html: HTMLElement, name: string, on: boolean): void {
    if (on) {
      this.renderer.addClass(html, name);
    } else {
      this.renderer.removeClass(html, name);
    }
  }
}
