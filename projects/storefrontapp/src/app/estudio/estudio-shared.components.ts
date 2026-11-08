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
import { NavigationEnd, Router } from '@angular/router';
import { Observable, Subscription, of } from 'rxjs';
import { distinctUntilChanged, filter, map, shareReplay, startWith, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { EstudioService, ME_POSICAO, MeEstampa } from './estudio.service';

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
export class FlaMeSubnavComponent {
  @Input() ativo: 'estampas' | 'acervo' | '' = 'estampas';
}

/** Classe no <html> das páginas com a barra do Estúdio (tema: sem a faixa cinza, layout de borda a borda) */
export const ESTUDIO_CLASS = 'fla-estudio';

/** Páginas com a barra do Estúdio: início, drop, acervo, estampa e camiseta (não devolução nem painel) */
export const COM_BARRA =
  /\/mengo-estudio(?:\/(?:drop|acervo|estampa|camiseta)(?:[/?#]|$)|\/?(?:[?#]|$))/;

type Aba = 'estampas' | 'acervo';

const ACERVO = /\/mengo-estudio\/acervo(?:[/?#]|$)/;

/**
 * Barra do Estúdio fixa no topo das páginas do Estúdio (08/11, Emerson): fica no slot de baixo do header
 * (outlet do BottomHeaderSlot), que não é refeito na troca entre início, drop, acervo e estampa. Antes cada
 * página tinha a sua barra: na troca, a barra sumia por alguns quadros enquanto a página nova carregava e o
 * header "saltava". Também marca o <html> com fla-estudio (sem a faixa cinza "Início / Mengo Estúdio" e
 * com o layout da página do CMS solto, como no mockup). Com o Estúdio desligado, nada aparece.
 */
@Component({
  selector: 'fla-me-barra',
  template: `<fla-me-subnav *ngIf="aba$ | async as aba" [ativo]="aba"></fla-me-subnav>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMeBarraComponent implements OnInit, OnDestroy {
  protected readonly url$: Observable<string> = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map((e) => e.urlAfterRedirects),
    startWith(this.router.url),
    distinctUntilChanged(),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  /** Aba ativa ('estampas' | 'acervo') nas páginas com a barra; null nas outras */
  readonly aba$: Observable<Aba | null> = this.url$.pipe(
    map((url): Aba | null => (COM_BARRA.test(url) ? (ACERVO.test(url) ? 'acervo' : 'estampas') : null)),
    distinctUntilChanged(),
    switchMap((aba) =>
      aba
        ? this.estudio.catalogo$.pipe(
            map((c): Aba | null => (c.ativo ? aba : null)),
            startWith(aba)
          )
        : of(null)
    )
  );

  protected sub?: Subscription;

  constructor(
    protected router: Router,
    protected estudio: EstudioService,
    protected renderer: Renderer2,
    @Inject(DOCUMENT) protected document: Document
  ) {}

  ngOnInit(): void {
    this.sub = this.aba$.subscribe((aba) =>
      aba
        ? this.renderer.addClass(this.document.documentElement, ESTUDIO_CLASS)
        : this.renderer.removeClass(this.document.documentElement, ESTUDIO_CLASS)
    );
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.renderer.removeClass(this.document.documentElement, ESTUDIO_CLASS);
  }
}

/** Nome da linha pelo código: {{ e.linha | meLinha: c.linhas }} */
@Pipe({ name: 'meLinha' })
export class MeLinhaPipe implements PipeTransform {
  transform(code: string | undefined, linhas: { code: string; nome?: string }[]): string {
    return linhas?.find((l) => l.code === code)?.nome ?? '';
  }
}
