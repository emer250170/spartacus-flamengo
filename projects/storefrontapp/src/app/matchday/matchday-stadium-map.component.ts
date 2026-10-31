import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
} from '@angular/core';

/** Estado de um setor no mapa */
export type StadiumSectorState = 'free' | 'low' | 'soldout' | 'selected';

/** Setor já pronto para o mapa (a jornada calcula preço, estoque e textos) */
export interface StadiumSectorView {
  /** Código usado em selectSector (o mesmo do cartão) */
  code: string;
  /** NORTE | SUL | LESTE | OESTE: decide onde o setor fica no desenho */
  key: string;
  name: string;
  /** Preço que o torcedor paga (com o desconto do sócio, se houver) */
  price: string;
  /** "128 lugares livres", "Restam 3 lugares", "Esgotado" */
  status: string;
  state: StadiumSectorState;
}

interface Arc {
  /** Ângulos em graus (0 = direita, 90 = baixo, sentido horário) */
  from: number;
  to: number;
}

/**
 * Posição de cada setor no anel do Maracanã visto de cima, com o campo na
 * horizontal: Oeste (tribuna coberta) em cima, Leste embaixo, Norte e Sul
 * atrás dos gols.
 */
const ARCS: Record<string, Arc> = {
  SUL: { from: -38, to: 38 },
  LESTE: { from: 46, to: 134 },
  NORTE: { from: 142, to: 218 },
  OESTE: { from: 226, to: 314 },
};

const CX = 200;
const CY = 150;
/** Elipse de fora (borda do estádio) e de dentro (borda do gramado) */
const OUTER = { rx: 192, ry: 142 };
const INNER = { rx: 128, ry: 84 };

interface SectorShape extends StadiumSectorView {
  path: string;
  labelX: number;
  labelY: number;
  aria: string;
}

/**
 * Mapa do Maracanã para escolher o setor na jornada de matchday.
 * Só desenha: dados e regras (preço, estoque, desconto) vêm da jornada.
 */
@Component({
  selector: 'fla-matchday-stadium-map',
  templateUrl: './matchday-stadium-map.component.html',
  styleUrls: ['./matchday-stadium-map.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchdayStadiumMapComponent implements OnChanges {
  @Input() sectors: StadiumSectorView[] = [];
  /** Rótulo do campo ("CAMPO" / "PITCH") */
  @Input() fieldLabel = '';
  @Output() pick = new EventEmitter<string>();

  shapes: SectorShape[] = [];

  ngOnChanges(): void {
    this.shapes = this.sectors
      .filter((s) => !!ARCS[s.key])
      .map((s) => {
        const arc = ARCS[s.key];
        const mid = (arc.from + arc.to) / 2;
        const label = this.point(mid, {
          rx: (OUTER.rx + INNER.rx) / 2,
          ry: (OUTER.ry + INNER.ry) / 2,
        });
        return {
          ...s,
          path: this.ringPath(arc),
          labelX: label.x,
          labelY: label.y,
          aria: [s.name, s.price, s.status].filter(Boolean).join(', '),
        };
      });
  }

  choose(s: SectorShape): void {
    if (s.state !== 'soldout') {
      this.pick.emit(s.code);
    }
  }

  onKey(event: KeyboardEvent, s: SectorShape): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.choose(s);
    }
  }

  trackByCode(_: number, s: SectorShape): string {
    return s.code;
  }

  private point(deg: number, e: { rx: number; ry: number }) {
    const a = (deg * Math.PI) / 180;
    return {
      x: Math.round((CX + e.rx * Math.cos(a)) * 10) / 10,
      y: Math.round((CY + e.ry * Math.sin(a)) * 10) / 10,
    };
  }

  /** Fatia do anel entre a borda do estádio e a do gramado */
  private ringPath(arc: Arc): string {
    const o1 = this.point(arc.from, OUTER);
    const o2 = this.point(arc.to, OUTER);
    const i2 = this.point(arc.to, INNER);
    const i1 = this.point(arc.from, INNER);
    return [
      `M ${o1.x} ${o1.y}`,
      `A ${OUTER.rx} ${OUTER.ry} 0 0 1 ${o2.x} ${o2.y}`,
      `L ${i2.x} ${i2.y}`,
      `A ${INNER.rx} ${INNER.ry} 0 0 0 ${i1.x} ${i1.y}`,
      'Z',
    ].join(' ');
  }
}
