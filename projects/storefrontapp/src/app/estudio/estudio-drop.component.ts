import { ChangeDetectionStrategy, Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { EstudioService, MeCatalogo, MeEstampa, MeVersao, meTrack } from './estudio.service';

/** Filtros do drop: linha, faixas de disponibilidade e faixa de preço (preço inicial de cada versão) */
export interface MeFiltros {
  linha: string;
  venda: boolean;
  dias: boolean;
  h48: boolean;
  pmin: string;
  pmax: string;
}
const F0: MeFiltros = { linha: '', venda: true, dias: true, h48: true, pmin: '', pmax: '' };
type Ordem = 'fim' | 'menor' | 'maior' | 'nome';

/**
 * Página do drop (/mengo-estudio/drop/:code; mockup E2–E4 e E12): vitrines por linha com abas e setas, a
 * estampa em destaque e as versões masculina, feminina e infantil. Filtros num painel com seleção provisória
 * (Aplicar confirma; fechar ou Esc descarta), filtros aplicados com remover/limpar e estado sem resultados.
 */
@Component({
  selector: 'fla-estudio-drop',
  templateUrl: './estudio-drop.component.html',
  styleUrls: ['./estudio.scss', './estudio-drop.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioDropComponent {
  readonly tb = meTrack;
  @ViewChild('painelRef') painelRef?: ElementRef<HTMLElement>;
  readonly ap$ = new BehaviorSubject<MeFiltros>({ ...F0 });
  readonly ordem$ = new BehaviorSubject<Ordem>('fim');
  readonly tab$ = new BehaviorSubject<Record<string, number>>({});
  /** Rascunho do painel (null = painel fechado) */
  ra: MeFiltros | null = null;
  protected abridor?: HTMLElement | null;
  protected escolhida?: string;

  readonly vm$: Observable<any> = combineLatest([
    this.estudio.catalogo$,
    this.estudio.tick$,
    this.route.paramMap,
    this.ap$,
    this.ordem$,
    this.tab$,
    this.i18n.lang$,
  ]).pipe(map(([c, , params, ap, ordem, tab]) => this.build(c, params.get('code') ?? '', ap, ordem, tab)));

  constructor(
    protected estudio: EstudioService,
    protected i18n: FlaI18nService,
    protected route: ActivatedRoute
  ) {
    const q = this.route.snapshot.queryParamMap;
    if (q.get('linha')) {
      this.ap$.next({ ...F0, linha: q.get('linha') as string });
    }
    this.escolhida = q.get('estampa') ?? undefined;
  }

  protected cat(e: MeEstampa, c: MeCatalogo, now: number): 'venda' | 'dias' | 'h48' | 'x' {
    const s = this.estudio.situacao(e, c, now);
    return s === 'ENCERRADA' ? 'x' : s === 'ULTIMAS_48H' ? 'h48' : s === 'ULTIMOS_DIAS' ? 'dias' : 'venda';
  }

  protected versoes(c: MeCatalogo, f: MeFiltros): MeVersao[] {
    const mn = parseFloat(String(f.pmin).replace(',', '.'));
    const mx = parseFloat(String(f.pmax).replace(',', '.'));
    return this.estudio
      .versoes(c)
      .filter((v) => (isNaN(mn) || v.preco >= mn) && (isNaN(mx) || v.preco <= mx));
  }

  protected passa(e: MeEstampa, f: MeFiltros, c: MeCatalogo, now: number, semLinha = false): boolean {
    const k = this.cat(e, c, now);
    if (k === 'x' || !f[k]) {
      return false;
    }
    if (!semLinha && f.linha && e.linha !== f.linha) {
      return false;
    }
    return this.versoes(c, f).length > 0;
  }

  protected build(c: MeCatalogo, code: string, ap: MeFiltros, ordem: Ordem, tab: Record<string, number>) {
    const now = this.estudio.now(c);
    const drop = c.drops.find((d) => d.code === code) ?? c.drops[0];
    const doDrop = c.estampas.filter((e) => !drop || e.drop === drop.code);
    const lista = doDrop.filter((e) => this.passa(e, ap, c, now));
    const vs = this.versoes(c, ap).slice();
    if (ordem === 'menor') {
      vs.sort((a, b) => a.preco - b.preco);
    } else if (ordem === 'maior') {
      vs.sort((a, b) => b.preco - a.preco);
    }
    const ord = (l: MeEstampa[]) =>
      l.slice().sort((a, b) =>
        ordem === 'nome'
          ? a.nome.localeCompare(b.nome, 'pt-BR')
          : (a.encerramento ?? 0) - (b.encerramento ?? 0)
      );
    const grupos = c.linhas
      .map((l) => {
        const es = ord(lista.filter((e) => e.linha === l.code));
        if (!es.length) {
          return null;
        }
        let i = tab[l.code];
        if (i === undefined && this.escolhida) {
          const j = es.findIndex((e) => e.code === this.escolhida);
          i = j >= 0 ? j : 0;
        }
        i = Math.min(i ?? 0, es.length - 1);
        const e = es[i];
        const sit = this.estudio.situacao(e, c, now);
        return {
          code: l.code,
          nome: l.nome,
          descricao: l.descricao,
          i,
          n: es.length,
          tabs: es,
          e,
          sit,
          prazo: this.estudio.prazo(e, c, now),
          cor: this.estudio.corVitrine(e),
          versoes: vs.map((v) => ({
            ...v,
            precoTxt: this.i18n.money(v.preco),
            parc: this.estudio.parcelas(v.preco, c),
          })),
        };
      })
      .filter((g) => !!g);
    const ativas = doDrop.filter((e) => this.cat(e, c, now) !== 'x').sort((a, b) => (a.encerramento ?? 0) - (b.encerramento ?? 0));
    const prim = ativas[0];
    const chips: { txt: string; mud: Partial<MeFiltros> }[] = [];
    if (ap.linha) {
      chips.push({ txt: this.i18n.t('me.linhaDe', { l: c.linhas.find((l) => l.code === ap.linha)?.nome }), mud: { linha: '' } });
    }
    if (!(ap.venda && ap.dias && ap.h48)) {
      (['venda', 'dias', 'h48'] as const).forEach((k) => {
        if (ap[k]) {
          chips.push({ txt: this.i18n.t('me.f.' + k), mud: { [k]: false } });
        }
      });
    }
    if (ap.pmin) {
      chips.push({ txt: this.i18n.t('me.f.de', { v: ap.pmin }), mud: { pmin: '' } });
    }
    if (ap.pmax) {
      chips.push({ txt: this.i18n.t('me.f.ate', { v: ap.pmax }), mud: { pmax: '' } });
    }
    const ra = this.ra;
    const contaRa = (f: MeFiltros) => doDrop.filter((e) => this.passa(e, f, c, now)).length;
    return {
      c,
      ativo: c.ativo,
      drop,
      total: lista.length,
      grupos,
      vazio: c.ativo && lista.length === 0,
      chips,
      primeira: prim
        ? { nome: prim.nome, fim: this.estudio.fim(prim.encerramento), cont: this.estudio.contador((prim.encerramento ?? 0) - now) }
        : null,
      ordem,
      painel: !!ra,
      ra,
      raTotal: ra ? contaRa(ra) : 0,
      fLinhas: ra
        ? [{ code: '', nome: this.i18n.t('me.todasLinhas') }, ...c.linhas].map((l) => ({
            code: l.code,
            nome: l.nome,
            n: contaRa({ ...ra, linha: l.code }),
          }))
        : [],
      fDisp: ra
        ? (['venda', 'dias', 'h48'] as const).map((k) => ({
            k,
            n: doDrop.filter((e) => this.cat(e, c, now) === k && (!ra.linha || e.linha === ra.linha)).length,
          }))
        : [],
    };
  }

  alternar(k: 'venda' | 'dias' | 'h48'): void {
    if (this.ra) {
      this.mudar({ [k]: !this.ra[k] } as Partial<MeFiltros>);
    }
  }

  ir(grupo: string, i: number): void {
    this.tab$.next({ ...this.tab$.value, [grupo]: i });
  }

  ordenar(v: string): void {
    this.ordem$.next(v as Ordem);
  }

  remover(mud: Partial<MeFiltros>): void {
    this.ap$.next({ ...this.ap$.value, ...mud });
  }

  limpar(): void {
    this.ra = null;
    this.ap$.next({ ...F0 });
  }

  abrir(ev?: Event): void {
    this.abridor = (ev?.currentTarget as HTMLElement) ?? null;
    this.ra = { ...this.ap$.value };
    this.ap$.next(this.ap$.value);
    setTimeout(() => this.painelRef?.nativeElement.querySelector<HTMLElement>('button, input')?.focus());
  }

  /** Fechar sem aplicar: descarta o rascunho */
  fechar(): void {
    this.ra = null;
    this.ap$.next(this.ap$.value);
    this.abridor?.focus();
  }

  aplicar(): void {
    if (this.ra) {
      const f = this.ra;
      this.ra = null;
      this.ap$.next({ ...f });
      this.abridor?.focus();
    }
  }

  limparRascunho(): void {
    this.ra = { ...F0 };
    this.ap$.next(this.ap$.value);
  }

  mudar(mud: Partial<MeFiltros>): void {
    if (this.ra) {
      this.ra = { ...this.ra, ...mud };
      this.ap$.next(this.ap$.value);
    }
  }

  @HostListener('document:keydown.escape')
  esc(): void {
    if (this.ra) {
      this.fechar();
    }
  }
}
