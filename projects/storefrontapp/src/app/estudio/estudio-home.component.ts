import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { EstudioService, MeCatalogo, MeEstampa, MeSituacao } from './estudio.service';

export interface MeCardVm {
  e: MeEstampa;
  sit: MeSituacao;
  prazo: string;
  urgente: boolean;
}

/**
 * Sub-home do Mengo Estúdio (página /mengo-estudio do CMS, CMSFlexComponent MengoEstudioHomeComponent; mockup E1):
 * apresentação, como funciona, drops no ar, linhas, vitrine com filtro por linha e "Carregar mais", faixa do acervo.
 */
@Component({
  selector: 'fla-estudio-home',
  templateUrl: './estudio-home.component.html',
  styleUrls: ['./estudio.scss', './estudio-home.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioHomeComponent {
  readonly linha$ = new BehaviorSubject<string>('');
  readonly qtd$ = new BehaviorSubject<number>(8);

  readonly vm$: Observable<any> = combineLatest([
    this.estudio.catalogo$,
    this.estudio.tick$,
    this.linha$,
    this.qtd$,
    this.i18n.lang$,
  ]).pipe(map(([c, , linha, qtd]) => this.build(c, linha, qtd)));

  constructor(protected estudio: EstudioService, protected i18n: FlaI18nService) {}

  protected card(e: MeEstampa, c: MeCatalogo, now: number): MeCardVm {
    const sit = this.estudio.situacao(e, c, now);
    return {
      e,
      sit,
      prazo: this.estudio.prazo(e, c, now),
      urgente: sit === 'ULTIMAS_48H' || sit === 'ULTIMOS_DIAS',
    };
  }

  protected build(c: MeCatalogo, linha: string, qtd: number) {
    const now = this.estudio.now(c);
    const ativas = c.estampas.filter((e) => this.estudio.situacao(e, c, now) !== 'ENCERRADA');
    const vis = ativas.filter((e) => !linha || e.linha === linha);
    const prim = ativas[0];
    const drop = c.drops[0];
    return {
      c,
      ativo: c.ativo,
      drop,
      temDrop: !!drop && ativas.length > 0,
      dropResumo: this.i18n.t('me.drop.resumo', { n: ativas.length, l: c.linhas.length }),
      primeira: prim
        ? { nome: prim.nome, fim: this.estudio.fim(prim.encerramento), cont: this.estudio.contador((prim.encerramento ?? 0) - now) }
        : null,
      destaques: ativas
        .filter((e) => e.arte)
        .slice(0, 3)
        .map((e) => this.card(e, c, now)),
      linhas: c.linhas.map((l) => ({
        ...l,
        qtd: ativas.filter((e) => e.linha === l.code).length,
      })),
      chips: [{ code: '', nome: this.i18n.t('me.todas') }, ...c.linhas.map((l) => ({ code: l.code, nome: l.nome }))],
      lista: vis.slice(0, qtd).map((e) => this.card(e, c, now)),
      contagem: this.i18n.t('me.mostrando', { a: Math.min(qtd, vis.length), b: vis.length }),
      temMais: vis.length > qtd,
      aPartir: this.i18n.money(this.estudio.aPartir(c)),
      acervo: c.estampas.filter((e) => this.estudio.situacao(e, c, now) === 'ENCERRADA').slice(0, 4),
    };
  }

  filtrar(code: string): void {
    this.linha$.next(code);
    this.qtd$.next(8);
  }

  mais(): void {
    this.qtd$.next(this.qtd$.value + 8);
  }
}
