import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { Language, LanguageService, RoutingService } from '@spartacus/core';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import { catchError, filter, map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from '../matchday/matchday-data.service';
import { MatchdayMatch } from '../matchday/matchday.model';

/** Portal do clube: os links que não são da loja abrem o flamengo.com.br em nova aba */
const PORTAL = 'https://www.flamengo.com.br';

/**
 * Faixa de cima do header (visual da loja oficial, 06/11): links do portal do Flamengo.
 * Entra pelo outlet do slot SiteLinks, que no desktop deixa de mostrar os links do CMS
 * (Pedidos, Encontre uma loja...). No celular os links do CMS continuam no menu.
 */
@Component({
  selector: 'fla-portal-links',
  template: `
    <nav class="fla-portal-links" [attr.aria-label]="'hd.portal' | flaT">
      <a routerLink="/socio-torcedor">{{ 'hd.socio' | flaT }}</a>
      <a routerLink="/c/experiencias">{{ 'hd.experience' | flaT }}</a>
      <a [href]="portal" target="_blank" rel="noopener"
        >{{ 'hd.uruhub' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
      <a [href]="portal" target="_blank" rel="noopener"
        >{{ 'hd.tv' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPortalLinksComponent {
  readonly portal = PORTAL;
}

/**
 * Menu principal do portal ao lado do logo (Notícias, Futebol... Loja, Clube), como no
 * flamengo.com.br; "Loja" fica marcado. Entra pelo outlet do slot SiteLogo (depois do logo).
 */
@Component({
  selector: 'fla-portal-nav',
  template: `
    <nav class="fla-portal-nav" [attr.aria-label]="'hd.main' | flaT">
      <a [href]="portal + '/noticias'" target="_blank" rel="noopener"
        >{{ 'hd.news' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
      <a [href]="portal" target="_blank" rel="noopener"
        >{{ 'hd.football' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
      <a [href]="portal" target="_blank" rel="noopener"
        >{{ 'hd.olympic' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
      <a [href]="portal" target="_blank" rel="noopener"
        >{{ 'hd.relations' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
      <a routerLink="/" class="fla-ativo" aria-current="page">{{
        'hd.store' | flaT
      }}</a>
      <a [href]="portal" target="_blank" rel="noopener"
        >{{ 'hd.club' | flaT
        }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
      >
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPortalNavComponent {
  readonly portal = PORTAL;
}

/**
 * Botão preto "Ingressos ▾" na barra vermelha, antes da busca (mockup do header, 06/11): abre
 * a lista dos próximos jogos em casa (data, competição, preço inicial), cada um levando à
 * jornada de matchday já com o jogo escolhido, e "Ver todos os jogos". Os jogos só são
 * buscados quando a lista abre. Fecha no Esc (foco volta ao botão), no clique fora e ao
 * trocar de página. Entra pelo outlet do slot SearchBox.
 */
@Component({
  selector: 'fla-tickets-button',
  template: `
    <div class="fla-tk">
      <button
        type="button"
        class="fla-tickets-btn"
        #trigger
        [attr.aria-expanded]="open"
        aria-controls="fla-tk-panel"
        (click)="toggle()"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="2" aria-hidden="true" focusable="false">
          <path d="M3 8V6h18v2a2 2 0 0 0 0 4v2a2 2 0 0 0 0 4H3a2 2 0 0 0 0-4v-2a2 2 0 0 0 0-4z" />
        </svg>
        {{ 'hd.tickets' | flaT }}
        <svg class="fla-tk-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <div class="fla-tk-panel" id="fla-tk-panel" *ngIf="open">
        <p class="fla-tk-title">{{ 'hd.ticketsNext' | flaT }}</p>
        <ng-container *ngIf="matches$ | async as matches; else loading">
          <ul *ngIf="matches.length; else empty">
            <li *ngFor="let m of matches">
              <a routerLink="/matchday" [queryParams]="{ jogo: m.code }" (click)="close()">
                <span class="fla-tk-name">{{ m.title }}</span>
                <span class="fla-tk-meta"
                  >{{ weekday(m) }} {{ m.dateLabel }} · {{ m.timeLabel }} · {{ m.competition }}</span
                >
                <span class="fla-tk-price">{{ 'hd.ticketsFrom' | flaT: { v: price(m.minPrice) } }}</span>
              </a>
            </li>
          </ul>
        </ng-container>
        <ng-template #loading>
          <p class="fla-tk-note" role="status">{{ 'hd.ticketsLoading' | flaT }}</p>
        </ng-template>
        <ng-template #empty>
          <p class="fla-tk-note">{{ 'hd.ticketsEmpty' | flaT }}</p>
        </ng-template>
        <a class="fla-tk-all" routerLink="/matchday" (click)="close()">{{
          'hd.ticketsAll' | flaT
        }}</a>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaTicketsButtonComponent implements OnInit, OnDestroy {
  open = false;
  matches$?: Observable<MatchdayMatch[]>;
  @ViewChild('trigger') trigger?: ElementRef<HTMLButtonElement>;
  protected subscription = new Subscription();

  constructor(
    protected data: MatchdayDataService,
    protected i18n: FlaI18nService,
    protected routing: RoutingService,
    protected el: ElementRef<HTMLElement>,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.routing
        .getRouterState()
        .pipe(filter((state) => !!state.nextState))
        .subscribe(() => this.close())
    );
  }

  toggle(): void {
    if (this.open) {
      this.close();
      return;
    }
    this.matches$ ??= this.data.getMatches().pipe(
      map((list) => list.slice(0, 4)),
      catchError(() => of([] as MatchdayMatch[]))
    );
    this.open = true;
  }

  close(focusTrigger = false): void {
    if (this.open) {
      this.open = false;
      this.cd.markForCheck();
      if (focusTrigger) {
        this.trigger?.nativeElement.focus();
      }
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.el.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('keydown.escape')
  onEscape(): void {
    this.close(true);
  }

  weekday(m: MatchdayMatch): string {
    return m.weekdayIndex < 0 ? m.weekday : this.i18n.weekday(m.weekdayIndex);
  }

  price(value: number): string {
    return this.i18n.money(value);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

/**
 * Idioma compacto na faixa de cima (mockup do header: globo + "PT"). Mostra a sigla de cada
 * idioma do site; o nome completo vai para o leitor de tela. No desktop fica no lugar do
 * seletor do CMS (o tema esconde o do Spartacus); no celular o seletor do CMS continua no menu.
 * Entra pelo outlet do slot SiteContext.
 */
@Component({
  selector: 'fla-language',
  template: `
    <label class="fla-lang" *ngIf="view$ | async as v">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="2" aria-hidden="true" focusable="false">
        <circle cx="12" cy="12" r="9" />
        <path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" />
      </svg>
      <span class="cx-visually-hidden">{{ 'hd.language' | flaT }}</span>
      <select (change)="select($any($event.target).value)">
        <option
          *ngFor="let l of v.languages"
          [value]="l.isocode"
          [selected]="l.isocode === v.active"
          [attr.aria-label]="l.nativeName"
        >
          {{ short(l.isocode) }}
        </option>
      </select>
    </label>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaLanguageComponent {
  readonly view$: Observable<{ languages: Language[]; active: string }> =
    combineLatest([this.languages.getAll(), this.languages.getActive()]).pipe(
      map(([languages, active]) => ({ languages, active }))
    );

  constructor(protected languages: LanguageService) {}

  short(isocode: string): string {
    return (isocode ?? '').split(/[_-]/)[0].toUpperCase();
  }

  select(isocode: string): void {
    this.languages.setActive(isocode);
  }
}

/**
 * "Loja Oficial" no começo da faixa preta de categorias (mockup do header), sublinhado na
 * página inicial da loja. Entra pelo outlet do slot NavigationBar (antes das categorias).
 */
@Component({
  selector: 'fla-store-home-link',
  template: `
    <a
      class="fla-store-home"
      routerLink="/"
      routerLinkActive="is-active"
      [routerLinkActiveOptions]="{ exact: true }"
      ariaCurrentWhenActive="page"
      >{{ 'hd.storeHome' | flaT }}</a
    >
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaStoreHomeLinkComponent {}

/**
 * Rodapé (visual da loja oficial, como no mockup aprovado): quatro colunas — Sede Gávea,
 * CT George Helal, Atendimento e Nação. Os links do CMS (SAP Commerce Cloud, SAP Customer
 * Experience, Siga a gente) ficam escondidos no tema. Entra pelo outlet do slot Footer (BEFORE).
 */
@Component({
  selector: 'fla-footer-info',
  template: `
    <div class="fla-footer-info">
      <address>
        <strong>{{ 'ft.hq' | flaT }}</strong>
        <span>{{ 'ft.hqStreet' | flaT }}</span>
        <span>{{ 'ft.hqCity' | flaT }}</span>
        <span>{{ 'ft.hqZip' | flaT }}</span>
      </address>
      <address>
        <strong>{{ 'ft.ct' | flaT }}</strong>
        <span>{{ 'ft.ctStreet' | flaT }}</span>
        <span>{{ 'ft.ctCity' | flaT }}</span>
        <span>{{ 'ft.ctZip' | flaT }}</span>
      </address>
      <nav [attr.aria-label]="'ft.service' | flaT">
        <strong>{{ 'ft.service' | flaT }}</strong>
        <a routerLink="/my-account/orders">{{ 'ft.orders' | flaT }}</a>
        <a routerLink="/faq">{{ 'ft.returns' | flaT }}</a>
        <a routerLink="/store-finder">{{ 'ft.stores' | flaT }}</a>
        <a routerLink="/contact">{{ 'ft.contact' | flaT }}</a>
      </nav>
      <nav [attr.aria-label]="'ft.nation' | flaT">
        <strong>{{ 'ft.nation' | flaT }}</strong>
        <a routerLink="/socio-torcedor">{{ 'ft.join' | flaT }}</a>
        <a routerLink="/matchday">{{ 'hd.tickets' | flaT }}</a>
        <a routerLink="/c/experiencias">{{ 'ft.experiences' | flaT }}</a>
        <a [href]="portal" target="_blank" rel="noopener"
          >{{ 'ft.schools' | flaT
          }}<span class="cx-visually-hidden">{{ 'hd.newTab' | flaT }}</span></a
        >
      </nav>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaFooterInfoComponent {
  readonly portal = PORTAL;
}

/**
 * Linha final do rodapé: "Flamengo © 2026 Todos os direitos reservados" e links. Substitui
 * o aviso de copyright da SAP (parágrafo do CMS, escondido no tema). Outlet Footer (AFTER).
 */
@Component({
  selector: 'fla-footer-bottom',
  template: `
    <div class="fla-footer-bottom">
      <div>
        <span>{{ 'ft.rights' | flaT: { y: year } }}</span>
        <span class="fla-footer-links">
          <a [href]="portal" target="_blank" rel="noopener">{{
            'ft.privacy' | flaT
          }}</a>
          <a [href]="portal" target="_blank" rel="noopener">{{
            'ft.jobs' | flaT
          }}</a>
          <a routerLink="/contact">{{ 'ft.contact' | flaT }}</a>
        </span>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaFooterBottomComponent {
  readonly portal = PORTAL;
  readonly year = new Date().getFullYear();
}
