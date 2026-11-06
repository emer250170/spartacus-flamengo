import { ChangeDetectionStrategy, Component } from '@angular/core';

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
 * Botão preto "Ingressos" na barra vermelha, antes da busca (leva aos jogos em casa).
 * Entra pelo outlet do slot SearchBox.
 */
@Component({
  selector: 'fla-tickets-button',
  template: `
    <a class="fla-tickets-btn" routerLink="/matchday">
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
        <path
          d="M3 8V6h18v2a2 2 0 0 0 0 4v2a2 2 0 0 0 0 4H3a2 2 0 0 0 0-4v-2a2 2 0 0 0 0-4z"
        />
      </svg>
      {{ 'hd.tickets' | flaT }}
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaTicketsButtonComponent {}

/**
 * Rodapé (visual da loja oficial): endereços da Sede Gávea e do CT, antes dos links do
 * CMS. Entra pelo outlet do slot Footer (BEFORE).
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
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaFooterInfoComponent {}

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
