import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Observable, combineLatest, of } from 'rxjs';
import { distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  EstudioService,
  MeDevolucao,
  MePedido,
  meTrack,
} from './estudio.service';

type Motivo = 'DEFEITO' | 'ERRO' | 'ARREPENDIMENTO';
type Solucao = 'REEMBOLSO' | 'REIMPRESSAO';

interface DvPasso {
  n: number;
  nome: string;
  estado: 'feito' | 'atual' | 'pendente';
}

interface DvAcomp {
  nome: string;
  quando: string;
  feito: boolean;
}

interface DvVm {
  code: string;
  p: MePedido | null;
  d: MeDevolucao | null;
  etapa: 1 | 2 | 3 | 4;
  passos: DvPasso[];
  acomp: DvAcomp[];
}

/**
 * Devolução da parte Mengo Estúdio (08/11, PR 5; mockup E21–E24): /mengo-estudio/devolucao/:code, em 4 etapas
 * conforme o que está gravado no pedido: Motivo (defeito, erro de produção ou arrependimento; reembolso ou
 * reimpressão), Envio (código de postagem demonstrativo e "Já enviei a peça"), Acompanhamento (aguardando a
 * estamparia) e Resultado (reembolso aprovado ou reimpressão a caminho, com o pedido novo).
 */
@Component({
  selector: 'fla-estudio-devolucao',
  templateUrl: './estudio-devolucao.component.html',
  styleUrls: ['./estudio.scss', './estudio-devolucao.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioDevolucaoComponent {
  readonly tb = meTrack;
  readonly motivos: Motivo[] = ['DEFEITO', 'ERRO', 'ARREPENDIMENTO'];
  readonly solucoes: Solucao[] = ['REEMBOLSO', 'REIMPRESSAO'];

  motivo: Motivo | '' = '';
  solucao: Solucao | '' = '';
  comentario = '';
  tentou = false;
  enviando = false;
  erro = '';

  /** Pedido devolvido pelas ações (pedir, enviar) */
  protected readonly novo$ = new BehaviorSubject<MePedido | null>(null);

  readonly vm$: Observable<DvVm> = this.route.paramMap.pipe(
    map((p) => p.get('code') ?? ''),
    distinctUntilChanged(),
    switchMap((code) =>
      combineLatest([
        code ? this.estudio.pedido(code) : of(null),
        this.novo$,
        this.i18n.lang$,
      ]).pipe(
        map(([p, novo]) => this.toVm(code, novo && novo.code === code ? novo : p))
      )
    )
  );

  constructor(
    protected route: ActivatedRoute,
    public estudio: EstudioService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef,
    protected host: ElementRef<HTMLElement>
  ) {}

  get temSolucao(): boolean {
    return this.motivo === 'DEFEITO' || this.motivo === 'ERRO';
  }

  get erroForm(): string {
    if (!this.tentou) {
      return '';
    }
    if (!this.motivo) {
      return this.i18n.t('me.d.erroMotivo');
    }
    return this.temSolucao && !this.solucao ? this.i18n.t('me.d.erroSolucao') : '';
  }

  escolherMotivo(m: Motivo): void {
    this.motivo = m;
    if (m === 'ARREPENDIMENTO') {
      this.solucao = 'REEMBOLSO';
    }
  }

  continuar(ev: Event, code: string): void {
    ev.preventDefault();
    this.tentou = true;
    this.erro = '';
    if (this.erroForm) {
      this.foco('.dv-erro');
      return;
    }
    this.enviando = true;
    this.estudio
      .pedirDevolucao(code, {
        motivo: this.motivo,
        solucao: this.temSolucao ? this.solucao : 'REEMBOLSO',
        comentario: this.comentario.trim() || undefined,
      })
      .subscribe({
        next: (p) => this.depois(p),
        error: (e: Error) => this.falhou(e),
      });
  }

  enviei(code: string): void {
    this.erro = '';
    this.enviando = true;
    this.estudio.enviarDevolucao(code).subscribe({
      next: (p) => this.depois(p),
      error: (e: Error) => this.falhou(e),
    });
  }

  protected depois(p: MePedido | null): void {
    this.enviando = false;
    this.tentou = false;
    this.novo$.next(p);
    this.cd.markForCheck();
    this.foco('h2.dv-h');
  }

  protected falhou(e: Error): void {
    this.enviando = false;
    this.erro = e.message || this.i18n.t('me.d.erro');
    this.cd.markForCheck();
    this.foco('.dv-falha');
  }

  /** Leva o foco ao título da etapa nova (ou à mensagem de erro) */
  protected foco(sel: string): void {
    setTimeout(() =>
      this.host.nativeElement.querySelector<HTMLElement>(sel)?.focus()
    );
  }

  protected toVm(code: string, p: MePedido | null): DvVm {
    const d = p?.devolucao ?? null;
    const etapa: 1 | 2 | 3 | 4 = !d
      ? 1
      : d.etapa === 'SOLICITADA'
        ? 2
        : d.etapa === 'ENVIADA'
          ? 3
          : 4;
    const nomes = ['motivo', 'envio', 'acomp', 'resultado'];
    return {
      code,
      p,
      d,
      etapa,
      passos: nomes.map((n, i) => ({
        n: i + 1,
        nome: this.i18n.t('me.d.passo.' + n),
        estado: i + 1 < etapa ? 'feito' : i + 1 === etapa ? 'atual' : 'pendente',
      })),
      acomp: d ? this.acomp(d) : [],
    };
  }

  protected acomp(d: MeDevolucao): DvAcomp[] {
    const reimp = d.solucao === 'REIMPRESSAO';
    return [
      {
        nome: this.i18n.t('me.d.a.solicitada'),
        quando: `${this.dia(d.solicitadaEm)} · ${this.i18n.t('me.d.m.' + d.motivo).toLowerCase()}`,
        feito: true,
      },
      {
        nome: this.i18n.t('me.d.a.enviada'),
        quando: d.enviadaEm ? this.dia(d.enviadaEm) : this.i18n.t('me.d.a.pendente'),
        feito: !!d.enviadaEm,
      },
      {
        nome: this.i18n.t('me.d.a.recebida'),
        quando: d.recebidaEm ? this.dia(d.recebidaEm) : this.i18n.t('me.d.a.aguardando'),
        feito: !!d.recebidaEm,
      },
      {
        nome: this.i18n.t(reimp ? 'me.d.a.reimpressao' : 'me.d.a.reembolso'),
        quando: d.recebidaEm ? this.dia(d.recebidaEm) : this.i18n.t('me.d.a.depois'),
        feito: !!d.recebidaEm,
      },
    ];
  }

  /** "17/11" (horário de Brasília) */
  protected dia(ms?: number): string {
    if (!ms) {
      return '';
    }
    const o: Record<string, string> = {};
    new Intl.DateTimeFormat(this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US', {
      timeZone: 'America/Sao_Paulo',
      day: '2-digit',
      month: '2-digit',
    })
      .formatToParts(new Date(ms))
      .forEach((x) => (o[x.type] = x.value));
    return this.i18n.lang === 'pt'
      ? `${o['day']}/${o['month']}`
      : `${o['month']}/${o['day']}`;
  }
}
