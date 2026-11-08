import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  Input,
  OnDestroy,
  OnInit,
  Pipe,
  PipeTransform,
  Renderer2,
} from '@angular/core';
import { Router } from '@angular/router';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { ME_POSICAO, MeEstampa } from './estudio.service';

/**
 * Arte da estampa (imagem do Commerce). Sem arte cadastrada (ex.: estampas com fotos de jogadores, que esperam
 * a arte oficial), mostra uma ilustração provisória com o nome, no fundo da estampa.
 */
@Component({
  selector: 'fla-me-arte',
  template: `
    <div class="arte" [style.background]="estampa.fundo || '#111'" [class.larga]="estampa.arteLarga">
      <img *ngIf="estampa.arte; else ilustra" [src]="estampa.arte" [alt]="alt" loading="lazy" />
      <ng-template #ilustra>
        <svg viewBox="0 0 120 150" role="img" [attr.aria-label]="alt" preserveAspectRatio="xMidYMid meet">
          <rect x="9" y="9" width="102" height="132" fill="none" stroke="#fff" stroke-opacity=".6"></rect>
          <circle cx="60" cy="58" r="28" fill="#d70000"></circle>
          <path d="M20 62 L50 66 L60 53 L70 66 L100 62 L77 77 L64 77 L60 88 L56 77 L43 77 Z" fill="#fff"></path>
        </svg>
        <span *ngIf="!semNome" class="nome">{{ estampa.nome }}</span>
      </ng-template>
    </div>
  `,
  styles: [
    `
      :host { display: block; width: 100%; height: 100%; }
      .arte { container-type: inline-size; position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; overflow: hidden; }
      img { width: 100%; height: 100%; object-fit: contain; padding: 8%; box-sizing: border-box; }
      svg { width: 76%; height: 76%; }
      .nome { position: absolute; left: 10%; right: 10%; bottom: 9%; text-align: center; color: #fff; font-family: var(--fla-fonte-titulo); font-weight: 800; text-transform: uppercase; font-size: clamp(7px, 9cqw, 15px); line-height: 1.05; overflow-wrap: anywhere; text-shadow: 0 1px 2px rgba(0,0,0,.4); }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMeArteComponent {
  @Input() estampa!: MeEstampa;
  /** Sem o nome na ilustração provisória (quando o card já mostra o nome por cima) */
  @Input() semNome = false;
  constructor(protected i18n: FlaI18nService) {}
  get alt(): string {
    return this.i18n.t('me.arteAlt', { n: this.estampa?.nome });
  }
}

/**
 * Camiseta com a estampa aplicada na posição cadastrada (foto da camiseta sem modelo, frente), preservando a
 * proporção da arte. Branca e cinza usam "multiply" para a arte assentar no tecido.
 */
@Component({
  selector: 'fla-me-camiseta',
  template: `
    <div class="tee" [class.escura]="cor === 'PRT'">
      <img class="base" [src]="'assets/estudio/camiseta-flat-' + cor.toLowerCase() + '.webp'" alt="" />
      <div class="pos" [style.left.%]="pos.left" [style.top.%]="pos.top" [style.width.%]="pos.w" [style.height.%]="pos.h">
        <img *ngIf="estampa.arte; else ilustra" [src]="estampa.arte" alt="" loading="lazy" />
        <ng-template #ilustra><fla-me-arte [estampa]="estampa"></fla-me-arte></ng-template>
      </div>
      <span class="me-sr">{{ alt }}</span>
    </div>
  `,
  styles: [
    `
      :host { display: block; }
      .tee { position: relative; width: 100%; aspect-ratio: 948 / 993; }
      .base { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
      .pos { position: absolute; display: flex; align-items: center; justify-content: center; }
      .pos img { max-width: 100%; max-height: 100%; object-fit: contain; mix-blend-mode: multiply; }
      .escura .pos img { mix-blend-mode: normal; }
      .me-sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMeCamisetaComponent {
  @Input() estampa!: MeEstampa;
  @Input() cor: 'PRT' | 'BRA' | 'CIN' = 'PRT';
  constructor(protected i18n: FlaI18nService) {}
  get pos() {
    return ME_POSICAO[this.estampa?.posicao] ?? ME_POSICAO.FRENTE_INTEIRA;
  }
  get alt(): string {
    return this.i18n.t('me.camisetaAlt', {
      n: this.estampa?.nome,
      c: this.i18n.t('me.cor.' + this.cor).toLowerCase(),
    });
  }
}

/** Barra branca do Mengo Estúdio (marca, Estampas, Acervo; Meus pedidos e Carrinho à direita) */
@Component({
  selector: 'fla-me-subnav',
  template: `
    <div class="me-subnav">
      <nav class="me-wrap" [attr.aria-label]="'me.nome' | flaT">
        <a class="me-marca" routerLink="/mengo-estudio">
          <svg width="56" height="20" viewBox="0 0 56 20" aria-hidden="true"><path d="M0 6 L22 9 L28 4 L34 9 L56 6 L40 13 L30 13 L28 18 L26 13 L16 13 Z" fill="#D70000"></path></svg>
          <strong>{{ 'me.nome' | flaT }}</strong>
        </a>
        <a class="me-aba" routerLink="/mengo-estudio" [class.ativo]="ativo === 'estampas'" [attr.aria-current]="ativo === 'estampas' ? 'page' : null">{{ 'me.nav.estampas' | flaT }}</a>
        <a class="me-aba" routerLink="/mengo-estudio/acervo" [class.ativo]="ativo === 'acervo'" [attr.aria-current]="ativo === 'acervo' ? 'page' : null">{{ 'me.nav.acervo' | flaT }}</a>
        <span class="me-sp"></span>
        <a class="me-aba" routerLink="/my-account/orders">{{ 'me.nav.pedidos' | flaT }}</a>
        <a class="me-aba" routerLink="/cart">{{ 'me.nav.carrinho' | flaT }}</a>
      </nav>
    </div>
  `,
  styleUrls: ['./estudio.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMeSubnavComponent implements OnInit, OnDestroy {
  /** Subnavs na tela (na troca de rota a nova pode nascer antes de a antiga sair) */
  protected static abertas = 0;

  @Input() ativo: 'estampas' | 'acervo' | '' = 'estampas';

  constructor(
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document,
    protected router: Router
  ) {}

  /**
   * Páginas do Estúdio (início, drop, acervo, estampa) começam na barra branca, como no mockup: o <html>
   * ganha fla-estudio, que tira a faixa cinza "Início / Mengo Estúdio" e solta o layout da página do CMS
   * (no início, a barra e o hero ficavam presos em 1140px e 30px abaixo das outras páginas). 08/11, Emerson.
   */
  ngOnInit(): void {
    FlaMeSubnavComponent.abertas++;
    this.renderer.addClass(this.document.documentElement, ESTUDIO_CLASS);
  }

  ngOnDestroy(): void {
    FlaMeSubnavComponent.abertas = Math.max(0, FlaMeSubnavComponent.abertas - 1);
    // Indo para outra página com a barra (ex.: acervo -> início, que vem do CMS e nasce depois), a
    // classe fica: sem isso a faixa cinza aparecia por um instante e a barra "saltava" (08/11)
    const destino =
      this.router.getCurrentNavigation()?.finalUrl?.toString() ??
      this.router.getCurrentNavigation()?.extractedUrl?.toString() ??
      '';
    if (!FlaMeSubnavComponent.abertas && !COM_BARRA.test(destino)) {
      this.renderer.removeClass(this.document.documentElement, ESTUDIO_CLASS);
    }
  }
}

/** Classe no <html> das páginas com a barra do Estúdio (tema: sem a faixa cinza, layout de borda a borda) */
export const ESTUDIO_CLASS = 'fla-estudio';

/** Páginas com a barra do Estúdio: início, drop, acervo, estampa e camiseta (não devolução nem painel) */
const COM_BARRA = /\/mengo-estudio(?:\/(?:drop|acervo|estampa|camiseta)(?:[/?#]|$)|\/?(?:[?#]|$))/;

/** Nome da linha pelo código: {{ e.linha | meLinha: c.linhas }} */
@Pipe({ name: 'meLinha' })
export class MeLinhaPipe implements PipeTransform {
  transform(code: string | undefined, linhas: { code: string; nome?: string }[]): string {
    return linhas?.find((l) => l.code === code)?.nome ?? '';
  }
}
