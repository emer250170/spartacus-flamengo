import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  Optional,
} from '@angular/core';
import { CmsService, WindowRef } from '@spartacus/core';
import { CmsComponentData } from '@spartacus/storefront';
import { Observable, combineLatest, of } from 'rxjs';
import { distinctUntilChanged, filter, map, startWith, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { FlaHomeHeroRegistry, FlaHomeProduct, FlaHomeService } from './fla-home.service';

/**
 * Conteúdo da home (mockup "Home", 06/11). Tudo vem do SAP Commerce: o produto da promoção, os
 * banners e carrosséis do CMS (componentes que já estavam na home) e as categorias do catálogo.
 * Trocar um código aqui muda o bloco; trocar o banner ou os produtos do carrossel no CMS também.
 */
export const FLA_HOME = {
  /**
   * Destaques de reserva, usados só sem o carrossel "Destaques da home" no CMS (o normal é o
   * CMS: ImpEx 12-home-destaques, editável no Backoffice e no SmartEdit). Mesmo conteúdo dos
   * banners cadastrados.
   */
  hero: [
    // promoção do Manto 3: preço e parcelas do produto
    { product: 'flamengo_5248', badgeKey: 'home.promoBadge', titleKey: 'home.promoTitle', accentKey: 'home.promoAccent', price: true },
    // campanha da coleção (o conteúdo do banner "Coleção Adidas 2026"): foto do Manto Jogo 1
    {
      product: 'flamengo_4899',
      badgeKey: 'home.colBadge',
      titleKey: 'home.colTitle',
      accentKey: 'home.colAccent',
      textKey: 'home.colText',
      link: '/c/flamengo_c115',
      price: false,
    },
  ] as { product: string; badgeKey: string; titleKey: string; accentKey: string; textKey?: string; link?: string; price: boolean }[],
  /** "Compre por categoria": carrosséis do CMS (decisão do Emerson: viram abas) e categorias */
  tabs: [
    { carousel: 'ElectronicsHomepageProductCarouselComponent', link: '/c/flamengo_c115' },
    { carousel: 'NewElectronicsHomepageProductCarouselComponent', link: '/c/flamengo_c11' },
    { category: 'flamengo_c145', titleKey: 'home.tabTraining', link: '/c/flamengo_c145' },
    { category: 'flamengo_c52', titleKey: 'home.tabAccessories', link: '/c/flamengo_c52' },
  ] as { carousel?: string; category?: string; titleKey?: string; link: string }[],
  /** Personalize o seu Manto Sagrado: leva à categoria Mantos (pedido do Emerson, 07/11) */
  personalizeLink: '/c/flamengo_c115',
  /** Linhas (3 blocos com a foto do primeiro produto) */
  lines: [
    { category: 'flamengo_c11', titleKey: 'home.lineAdidas' },
    { category: 'flamengo_c145', titleKey: 'home.lineTraining' },
    { category: 'flamengo_c110', titleKey: 'home.lineCustom' },
  ],
  /** Acessórios (2 banners) */
  accessories: [
    { category: 'flamengo_c60', titleKey: 'home.capsTitle', textKey: 'home.capsText', dark: true },
    { category: 'flamengo_c52', titleKey: 'home.detailsTitle', textKey: '', dark: false },
  ],
};

interface HeroSlide {
  badge: string;
  title: string;
  accent: string;
  text?: string;
  link: string;
  img?: string;
  /** link de produto: nome, preço e parcelas */
  product?: FlaHomeProduct;
}

/** Código do produto num link da loja (/product/<código> ou .../p/<código>) */
function productCodeOf(link: string): string | undefined {
  const m = /\/(?:product|p)\/([^/?#\s]+)\/?(?:[?#].*)?$/.exec(link ?? '');
  return m ? decodeURIComponent(m[1]) : undefined;
}

/** Parágrafos de um texto do CMS (HTML), sem as tags */
function paragraphsOf(html: string): string[] {
  const clean = (t: string) =>
    t
      .replace(/<br\s*\/?>/gi, ' ')
      .replace(/<[^>]+>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, '&')
      .replace(/\s+/g, ' ')
      .trim();
  const ps = [...String(html ?? '').matchAll(/<p[^>]*>([\s\S]*?)<\/p>/gi)].map((m) => clean(m[1]));
  const list = ps.length ? ps : [clean(String(html ?? ''))];
  return list.filter((t) => !!t);
}

/**
 * Destaques no topo da home, todos no mesmo modelo (selo, título com a última palavra em
 * vermelho, preço ou frase, botão e foto à direita); setas, marcadores e troca automática (pausa
 * com o mouse ou o foco em cima, e sem troca automática para quem pede menos movimento).
 *
 * Gerenciado no CMS (06/11): é o componente do carrossel "Destaques da home"
 * (RotatingImagesComponent FlaHomeHeroComponent, ImpEx 12-home-destaques), editável no Backoffice
 * e no SmartEdit. Cada banner do carrossel é um destaque: título (headline), 1º parágrafo do
 * conteúdo = selo, demais = frase, link e imagem (se vazia, a foto do produto do link). Link de
 * produto mostra nome, preço e parcelas atuais. Sem o componente no CMS, a home mostra os
 * destaques de FLA_HOME.hero (instância dentro do MatchdayHomeBannerComponent).
 */
@Component({
  selector: 'fla-home-hero',
  template: `
    <section class="fh-hero" *ngIf="slides$ | async as slides" [attr.aria-label]="'home.heroLabel' | flaT"
      (mouseenter)="paused = true" (mouseleave)="paused = false" (focusin)="paused = true" (focusout)="paused = false">
      <ng-container *ngFor="let s of slides; let i = index">
        <div class="fh-slide" [class.is-active]="i === current" [attr.aria-hidden]="i !== current"
          role="group" [attr.aria-roledescription]="'home.slide' | flaT" [attr.aria-label]="(i + 1) + ' / ' + slides.length">
          <div class="fh-promo" [class.is-stacked]="!s.product">
            <div class="fh-promo-text">
              <span class="fh-badge" *ngIf="s.badge">{{ s.badge }}</span>
              <h1 *ngIf="i === 0; else h2" class="fh-promo-title">{{ s.title }} <span>{{ s.accent }}</span></h1>
              <ng-template #h2><h2 class="fh-promo-title">{{ s.title }} <span>{{ s.accent }}</span></h2></ng-template>
              <ng-container *ngIf="s.product as p; else text">
                <p class="fh-promo-name">{{ p.name }}</p>
                <p class="fh-promo-price"><span>{{ 'home.for' | flaT }}</span> {{ p.price }}</p>
                <p class="fh-promo-inst" *ngIf="p.installments">{{ p.installments }}</p>
              </ng-container>
              <ng-template #text><p class="fh-promo-lead" *ngIf="s.text">{{ s.text }}</p></ng-template>
              <a class="fh-btn" [routerLink]="s.link" [attr.tabindex]="i === current ? null : -1">
                {{ 'home.buyNow' | flaT }}
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
              </a>
            </div>
            <img *ngIf="s.img" class="fh-promo-img" [src]="s.img" alt="" />
          </div>
        </div>
      </ng-container>
      <ng-container *ngIf="slides.length > 1">
        <button type="button" class="fh-arrow is-prev" (click)="go(current - 1, slides.length)" [attr.aria-label]="'home.prev' | flaT">‹</button>
        <button type="button" class="fh-arrow is-next" (click)="go(current + 1, slides.length)" [attr.aria-label]="'home.next' | flaT">›</button>
        <div class="fh-dots">
          <button type="button" *ngFor="let s of slides; let i = index" [class.is-active]="i === current"
            (click)="go(i, slides.length)" [attr.aria-label]="('home.slide' | flaT) + ' ' + (i + 1)" [attr.aria-current]="i === current"></button>
        </div>
      </ng-container>
    </section>
  `,
  styleUrls: ['./fla-home.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaHomeHeroComponent implements OnInit, OnDestroy {
  /** desenhado pelo CMS (componente do slot) ou a reserva dentro do bloco da home */
  protected readonly fromCms = !!this.cmsData;

  readonly slides$: Observable<HeroSlide[] | null> = (
    this.fromCms
      ? this.cmsSlides()
      : this.registry.cms$.pipe(switchMap((cms) => (cms ? of(null) : this.defaultSlides())))
  ).pipe(
    map((slides) => {
      this.count = slides?.length ?? 0;
      if (this.current >= this.count) {
        this.current = 0;
      }
      return slides?.length ? slides : null;
    })
  );

  current = 0;
  paused = false;
  protected count = 0;
  protected timer?: ReturnType<typeof setInterval>;

  constructor(
    @Optional() protected cmsData: CmsComponentData<any> | null,
    protected home: FlaHomeService,
    protected registry: FlaHomeHeroRegistry,
    protected cms: CmsService,
    protected i18n: FlaI18nService,
    protected winRef: WindowRef,
    protected cd: ChangeDetectorRef
  ) {
    if (this.fromCms) {
      this.registry.cms$.next(true);
    }
  }

  /** Banners do carrossel do CMS, na ordem cadastrada */
  protected cmsSlides(): Observable<HeroSlide[]> {
    return (this.cmsData as CmsComponentData<any>).data$.pipe(
      map((c) =>
        (Array.isArray(c?.banners) ? c.banners : String(c?.banners ?? '').split(/\s+/)).filter(
          (uid: string) => !!uid
        ) as string[]
      ),
      distinctUntilChanged((a, b) => a.join() === b.join()),
      switchMap((uids) =>
        uids.length
          ? combineLatest(
              uids.map((uid) =>
                this.cms.getComponentData<any>(uid).pipe(
                  filter((b) => !!b),
                  switchMap((b) => this.fromBanner(b)),
                  startWith(null as HeroSlide | null)
                )
              )
            ).pipe(map((slides) => slides.filter((s): s is HeroSlide => !!s)))
          : of([] as HeroSlide[])
      )
    );
  }

  /** Um banner do CMS vira um destaque */
  protected fromBanner(b: any): Observable<HeroSlide | null> {
    const words = String(b.headline ?? '').trim().split(/\s+/).filter((w) => !!w);
    const accent = words.length > 1 ? (words.pop() as string) : '';
    const [badge = '', ...rest] = paragraphsOf(b.content ?? '');
    const link: string = b.urlLink || '/';
    const media = b.media?.url ?? b.media?.desktop?.url ?? b.media?.widescreen?.url;
    const base: HeroSlide = {
      badge,
      title: words.join(' '),
      accent,
      text: rest.join(' '),
      link,
      img: this.home.mediaUrl(media),
    };
    if (!base.title && !accent) {
      return of(null);
    }
    const code = productCodeOf(link);
    return code
      ? this.home.byCodes([code], 1).pipe(
          map((ps) => (ps[0] ? { ...base, product: ps[0], img: base.img ?? ps[0].img } : base))
        )
      : of(base);
  }

  /** Destaques do código (sem o carrossel no CMS) */
  protected defaultSlides(): Observable<HeroSlide[]> {
    return this.i18n.lang$.pipe(
      switchMap(() =>
        combineLatest(
          FLA_HOME.hero.map((h) =>
            this.home.byCodes([h.product], 1).pipe(
              map((ps): HeroSlide | null =>
                ps[0]
                  ? {
                      badge: this.i18n.t(h.badgeKey),
                      title: this.i18n.t(h.titleKey),
                      accent: this.i18n.t(h.accentKey),
                      text: h.textKey ? this.i18n.t(h.textKey) : '',
                      link: h.link ?? ps[0].link,
                      img: ps[0].img,
                      product: h.price ? ps[0] : undefined,
                    }
                  : null
              ),
              startWith(null as HeroSlide | null)
            )
          )
        )
      ),
      map((slides) => slides.filter((s): s is HeroSlide => !!s))
    );
  }

  ngOnInit(): void {
    const win = this.winRef.nativeWindow;
    const reduce = win?.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (win && !reduce) {
      this.timer = setInterval(() => {
        if (!this.paused && this.count > 1) {
          this.go(this.current + 1, this.count);
        }
      }, 7000);
    }
  }

  go(i: number, n: number): void {
    this.current = ((i % n) + n) % n;
    this.cd.markForCheck();
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
    if (this.fromCms) {
      this.registry.cms$.next(false);
    }
  }
}

interface CategoryTab {
  title: string;
  link: string;
  products$: Observable<FlaHomeProduct[]>;
}

/**
 * "Compre por categoria" (fundo preto): abas com os carrosséis do CMS (Mantos 2026, Novidades da
 * loja; decisão do Emerson, 06/11) e categorias do catálogo; em cada aba, o bloco vermelho
 * "Rubro-negro além do campo" e 3 produtos com preço e parcelas; "Ver tudo" leva à categoria.
 */
@Component({
  selector: 'fla-home-categories',
  template: `
    <section class="fh-dark" aria-labelledby="fh-cat-title" *ngIf="tabs$ | async as tabs">
      <div class="fh-wrap">
        <div class="fh-head">
          <h2 id="fh-cat-title" class="fh-cat-title">{{ 'home.shopBy' | flaT }} <strong>{{ 'home.category' | flaT }}</strong></h2>
          <a class="fh-btn is-small" [routerLink]="tabs[selected]?.link">
            {{ 'home.seeAll' | flaT }}
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
          </a>
        </div>
        <div class="fh-tabs" role="tablist" [attr.aria-label]="'home.categories' | flaT" (keydown)="keys($event, tabs.length)">
          <button type="button" role="tab" *ngFor="let t of tabs; let i = index" [id]="'fh-tab-' + i"
            [attr.aria-selected]="i === selected" [attr.aria-controls]="'fh-panel'" [attr.tabindex]="i === selected ? 0 : -1"
            [class.is-active]="i === selected" (click)="select(i)">{{ t.title }}</button>
        </div>
        <div id="fh-panel" role="tabpanel" class="fh-cat-grid" [attr.aria-labelledby]="'fh-tab-' + selected">
          <div class="fh-cat-promo">
            <strong>{{ 'home.beyond' | flaT }}</strong>
            <a [routerLink]="tabs[selected]?.link">{{ 'home.buyNow' | flaT }}</a>
          </div>
          <ng-container *ngIf="tabs[selected]?.products$ | async as products">
            <a class="fh-card" *ngFor="let p of products" [routerLink]="p.link">
              <span class="fh-card-img"><img *ngIf="p.img" [src]="p.img" alt="" loading="lazy" /></span>
              <span class="fh-card-name">{{ p.name }}</span>
              <span class="fh-card-price"><strong>{{ p.price }}</strong> <span *ngIf="p.installments">{{ p.installments }}</span></span>
            </a>
          </ng-container>
        </div>
      </div>
    </section>
  `,
  styleUrls: ['./fla-home.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaHomeCategoriesComponent {
  selected = 0;

  readonly tabs$: Observable<CategoryTab[]> = this.i18n.lang$.pipe(
    switchMap(() =>
      combineLatest(
        FLA_HOME.tabs.map((t) =>
          t.carousel
            ? this.home.carousel(t.carousel).pipe(
                map((c) => ({
                  title: c?.title ?? '',
                  link: t.link,
                  products$: this.home.byCodes(c?.codes ?? [], 3),
                }))
              )
            : of({
                title: this.i18n.t(t.titleKey ?? ''),
                link: t.link,
                products$: this.home.byCategory(t.category ?? '', 3),
              })
        )
      )
    ),
    map((tabs) => tabs.filter((t) => !!t.title))
  );

  constructor(
    protected home: FlaHomeService,
    protected i18n: FlaI18nService,
    protected host: ElementRef<HTMLElement>,
    protected cd: ChangeDetectorRef
  ) {}

  select(i: number): void {
    this.selected = i;
    this.cd.markForCheck();
  }

  /** Setas, Home e End trocam a aba (padrão de abas acessíveis) */
  keys(event: KeyboardEvent, n: number): void {
    const moves: Record<string, number> = {
      ArrowRight: this.selected + 1,
      ArrowLeft: this.selected - 1,
      Home: 0,
      End: n - 1,
    };
    if (!(event.key in moves)) {
      return;
    }
    event.preventDefault();
    this.select((moves[event.key] + n) % n);
    this.host.nativeElement.querySelector<HTMLElement>('#fh-tab-' + this.selected)?.focus();
  }
}

/** "Personalize o seu Manto Sagrado" (faixa vermelha) */
@Component({
  selector: 'fla-home-personalize',
  template: `
    <section class="fh-wrap fh-section" [attr.aria-label]="'home.personalize' | flaT">
      <a class="fh-personalize" [routerLink]="link">
        <strong>{{ 'home.personalize' | flaT }}</strong>
        <span>{{ 'home.enjoy' | flaT }}</span>
      </a>
    </section>
  `,
  styleUrls: ['./fla-home.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaHomePersonalizeComponent {
  readonly link = FLA_HOME.personalizeLink;
}

/** Linhas: 3 blocos com o nome da linha e a foto do primeiro produto da categoria */
@Component({
  selector: 'fla-home-lines',
  template: `
    <section class="fh-wrap fh-section fh-lines" [attr.aria-label]="'home.lines' | flaT">
      <a class="fh-line" *ngFor="let l of lines$ | async" [routerLink]="l.link">
        <img *ngIf="l.img" [src]="l.img" alt="" loading="lazy" />
        <strong>{{ l.title }}</strong>
      </a>
    </section>
  `,
  styleUrls: ['./fla-home.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaHomeLinesComponent {
  readonly lines$ = this.i18n.lang$.pipe(
    switchMap(() =>
      combineLatest(
        FLA_HOME.lines.map((l) =>
          this.home.byCategory(l.category, 1).pipe(
            map((ps) => ({
              title: this.i18n.t(l.titleKey),
              link: '/c/' + l.category,
              img: ps[0]?.img,
            }))
          )
        )
      )
    )
  );

  constructor(protected home: FlaHomeService, protected i18n: FlaI18nService) {}
}

/** Acessórios: 2 banners (bonés e detalhes) com a foto do primeiro produto da categoria */
@Component({
  selector: 'fla-home-accessories',
  template: `
    <section class="fh-wrap fh-section fh-acc" [attr.aria-label]="'home.accessories' | flaT">
      <a class="fh-acc-item" *ngFor="let a of items$ | async" [routerLink]="a.link" [class.is-red]="!a.dark">
        <img *ngIf="a.img" [src]="a.img" alt="" loading="lazy" />
        <span class="fh-acc-text">
          <strong>{{ a.title }}</strong>
          <span *ngIf="a.text">{{ a.text }}</span>
          <span class="fh-acc-cta">{{ 'home.check' | flaT }}</span>
        </span>
      </a>
    </section>
  `,
  styleUrls: ['./fla-home.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaHomeAccessoriesComponent {
  readonly items$ = this.i18n.lang$.pipe(
    switchMap(() =>
      combineLatest(
        FLA_HOME.accessories.map((a) =>
          this.home.byCategory(a.category, 1).pipe(
            map((ps) => ({
              title: this.i18n.t(a.titleKey),
              text: a.textKey ? this.i18n.t(a.textKey) : '',
              link: '/c/' + a.category,
              img: ps[0]?.img,
              dark: a.dark,
            }))
          )
        )
      )
    )
  );

  constructor(protected home: FlaHomeService, protected i18n: FlaI18nService) {}
}
