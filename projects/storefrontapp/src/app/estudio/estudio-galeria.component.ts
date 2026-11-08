import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { ME_POSICAO, MeCor, MeEstampa } from './estudio.service';

type Vista = '360' | 'arte' | 'modelo' | 'detalhe';
/** 0 frente, 1 lado, 2 costas, 3 o outro lado */
type Angulo = 0 | 1 | 2 | 3;

/**
 * Galeria da camiseta no configurador (08/11, PR 3): 360° com frente, lado e costas (arrastar, setas ← → do
 * teclado, botões e "Girar", 90° por vez; sem animação com movimento reduzido), arte na proporção real, foto no
 * modelo e detalhe da arte. Sem estampa escolhida, a frente mostra onde a arte entra e o botão "Escolher estampa".
 * O lado é a foto da frente vista de perfil (sem a arte), até as fotos de lado chegarem da estamparia.
 */
@Component({
  selector: 'fla-me-galeria',
  template: `
    <div class="gal">
      <div class="palco" [class.cinza]="vista !== '360'">
        <ng-container [ngSwitch]="vista">
          <div
            *ngSwitchCase="'360'"
            class="giro"
            tabindex="0"
            role="group"
            [attr.aria-roledescription]="'me.g.giro' | flaT"
            [attr.aria-label]="'me.g.angulo' | flaT: { a: nomeAngulo }"
            (keydown.arrowleft)="passo(-1); $event.preventDefault()"
            (keydown.arrowright)="passo(1); $event.preventDefault()"
            (pointerdown)="inicio($event)"
            (pointermove)="arrasto($event)"
            (pointerup)="fim()"
            (pointercancel)="fim()"
            (pointerleave)="fim()"
          >
            <span class="dica">{{ 'me.g.dica' | flaT: { a: nomeAngulo } }}</span>
            <div class="tee" [class.anima]="!reduzido" [style.transform]="'scaleX(' + escala + ')'">
              <img class="base" [src]="foto" alt="" draggable="false" />
              <div
                *ngIf="angulo === 0"
                class="pos"
                [style.left.%]="pos.left"
                [style.top.%]="pos.top"
                [style.width.%]="pos.w"
                [style.height.%]="pos.h"
              >
                <ng-container *ngIf="estampa; else vazio">
                  <img *ngIf="estampa.arte; else ilustra" [src]="estampa.arte" alt="" draggable="false" [class.mult]="cor !== 'PRT'" />
                  <ng-template #ilustra><fla-me-arte [estampa]="estampa" [semNome]="false"></fla-me-arte></ng-template>
                </ng-container>
                <ng-template #vazio>
                  <div class="slot">
                    <strong>{{ 'me.g.slot' | flaT }}</strong>
                    <button type="button" class="me-btn" (click)="escolher.emit()">{{ 'me.p.escolher' | flaT }}</button>
                  </div>
                </ng-template>
              </div>
            </div>
            <span class="me-sr">{{ alt }}</span>
          </div>

          <div *ngSwitchCase="'arte'" class="quadro">
            <fla-me-arte *ngIf="estampa; else semArte" [estampa]="estampa"></fla-me-arte>
          </div>

          <figure *ngSwitchCase="'modelo'" class="modelo">
            <img [src]="fotoModelo" [alt]="'me.g.modeloAlt' | flaT: { m: ('me.modelo.' + modelo | flaT) }" />
            <figcaption>{{ 'me.g.modeloNota' | flaT }}</figcaption>
          </figure>

          <div *ngSwitchCase="'detalhe'" class="detalhe">
            <img *ngIf="estampa?.arte; else semArte" [src]="estampa?.arte" [alt]="'me.arteAlt' | flaT: { n: estampa?.nome }" />
          </div>
        </ng-container>
        <ng-template #semArte>
          <p class="sem">{{ 'me.g.semArte' | flaT }}</p>
        </ng-template>

        <div class="angulos" *ngIf="vista === '360'">
          <button type="button" [attr.aria-pressed]="angulo === 0" (click)="ir(0)">{{ 'me.g.frente' | flaT }}</button>
          <button type="button" [attr.aria-pressed]="angulo === 1 || angulo === 3" (click)="ir(1)">{{ 'me.g.lado' | flaT }}</button>
          <button type="button" [attr.aria-pressed]="angulo === 2" (click)="ir(2)">{{ 'me.g.costas' | flaT }}</button>
          <button type="button" (click)="passo(1)">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5" /></svg>
            {{ 'me.g.girar' | flaT }}
          </button>
        </div>
      </div>

      <div class="vistas" role="group" [attr.aria-label]="'me.g.vistas' | flaT">
        <button *ngFor="let v of vistas" type="button" [attr.aria-pressed]="vista === v" (click)="vista = v">
          <strong>{{ 'me.g.v.' + v | flaT }}</strong>
          <span>{{ 'me.g.v.' + v + '.d' | flaT }}</span>
        </button>
      </div>
    </div>
  `,
  styleUrls: ['./estudio.scss', './estudio-galeria.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioGaleriaComponent {
  @Input() estampa?: MeEstampa;
  @Input() cor: MeCor = 'PRT';
  @Input() modelo: 'MAS' | 'FEM' | 'INF' = 'MAS';
  @Output() escolher = new EventEmitter<void>();

  readonly vistas: Vista[] = ['360', 'arte', 'modelo', 'detalhe'];
  vista: Vista = '360';
  angulo: Angulo = 0;
  readonly reduzido =
    typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  protected x0?: number;

  constructor(protected i18n: FlaI18nService) {}

  get pos() {
    return ME_POSICAO[this.estampa?.posicao ?? 'FRENTE_INTEIRA'] ?? ME_POSICAO.FRENTE_INTEIRA;
  }

  get foto(): string {
    const c = this.cor.toLowerCase();
    return this.angulo === 2
      ? `assets/estudio/camiseta-costas-${c}.webp`
      : `assets/estudio/camiseta-flat-${c}.webp`;
  }

  /** Lado = a foto vista de perfil (estreita); o outro lado espelhado */
  get escala(): number {
    return this.angulo === 1 ? 0.42 : this.angulo === 3 ? -0.42 : 1;
  }

  get fotoModelo(): string {
    return this.modelo === 'FEM'
      ? 'assets/estudio/modelo-camiseta-branca-lisa-feminina.webp'
      : this.modelo === 'INF'
      ? 'assets/estudio/modelo-camiseta-branca-lisa-infantil.webp'
      : 'assets/estudio/modelo-camiseta-branca-lisa.webp';
  }

  get nomeAngulo(): string {
    return this.i18n.t(
      this.angulo === 0 ? 'me.g.frente' : this.angulo === 2 ? 'me.g.costas' : 'me.g.lado'
    ).toLowerCase();
  }

  get alt(): string {
    return this.estampa
      ? this.i18n.t('me.camisetaAlt', {
          n: this.estampa.nome,
          c: this.i18n.t('me.cor.' + this.cor).toLowerCase(),
        })
      : this.i18n.t('me.g.semEstampa', { c: this.i18n.t('me.cor.' + this.cor).toLowerCase() });
  }

  ir(a: Angulo): void {
    this.angulo = a;
  }

  passo(d: 1 | -1): void {
    this.angulo = (((this.angulo + d) % 4) + 4) % 4 as Angulo;
  }

  inicio(e: PointerEvent): void {
    this.x0 = e.clientX;
  }

  arrasto(e: PointerEvent): void {
    if (this.x0 === undefined) {
      return;
    }
    const dx = e.clientX - this.x0;
    if (Math.abs(dx) > 48) {
      this.passo(dx > 0 ? -1 : 1);
      this.x0 = e.clientX;
    }
  }

  fim(): void {
    this.x0 = undefined;
  }
}
