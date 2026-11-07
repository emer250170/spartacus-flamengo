import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of, throwError, timer } from 'rxjs';
import {
  catchError,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { FlaMediaUrlService } from '../vitrine/fla-media-url.service';

export type MeSituacao =
  | 'ENCERRADA'
  | 'ULTIMAS_48H'
  | 'ULTIMOS_DIAS'
  | 'NOVA'
  | 'A_VENDA';
export type MePosicao = 'FRENTE_INTEIRA' | 'CENTRO_PEITO' | 'PEITO_ESQUERDO';

export interface MeEstampa {
  code: string;
  nome: string;
  drop?: string;
  linha?: string;
  numero?: number;
  lancamento?: number;
  encerramento?: number;
  situacao: MeSituacao;
  posicao: MePosicao;
  /** URL completa da arte (vazia: a loja mostra a ilustração provisória) */
  arte?: string;
  arteLarga: boolean;
  fundo: string;
  coresIndisponiveis: Record<string, string>;
  descricao?: string;
}

export interface MeCamiseta {
  code: string;
  modelo: 'MAS' | 'FEM' | 'INF';
  tecido: 'ALG' | 'POL' | 'OVS';
  preco?: number;
  tamanhos: string[];
  medidas: { tamanho: string; largura: number; comprimento: number }[];
}

export interface MeCatalogo {
  ativo: boolean;
  /** Diferença entre o relógio do servidor (com a demo) e o do navegador, em ms */
  skew: number;
  regras: {
    ultimasHoras: number;
    ultimosDias: number;
    novaDias: number;
    parcelasMax: number;
    parcelaMinima: number;
    quantidadeMax: number;
  };
  drops: { code: string; numero?: number; nome?: string; descricao?: string }[];
  linhas: { code: string; nome?: string; descricao?: string }[];
  estampas: MeEstampa[];
  camisetas: MeCamiseta[];
}

/** Uma versão da estampa na vitrine (camiseta masculina, feminina ou infantil) */
export interface MeVersao {
  modelo: 'MAS' | 'FEM' | 'INF';
  preco: number;
}

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
const VAZIO: MeCatalogo = {
  ativo: false,
  skew: 0,
  regras: {
    ultimasHoras: 48,
    ultimosDias: 7,
    novaDias: 7,
    parcelasMax: 10,
    parcelaMinima: 30,
    quantidadeMax: 10,
  },
  drops: [],
  linhas: [],
  estampas: [],
  camisetas: [],
};

/** Posição da arte sobre a foto da camiseta (frente), em % da foto */
export const ME_POSICAO: Record<MePosicao, { left: number; top: number; w: number; h: number }> = {
  FRENTE_INTEIRA: { left: 28, top: 19, w: 44, h: 50 },
  CENTRO_PEITO: { left: 40, top: 21, w: 20, h: 20 },
  PEITO_ESQUERDO: { left: 55, top: 20, w: 11, h: 11 },
};

/**
 * Mengo Estúdio (07/11): catálogo da API (/mengo-estudio/catalogo), a mesma regra de situação do servidor
 * (com o relógio do servidor, que inclui o da demo) e o "Avise-me se voltar". Lido de novo na troca de idioma.
 */
@Injectable({ providedIn: 'root' })
export class EstudioService {
  readonly catalogo$: Observable<MeCatalogo> = this.i18n.lang$.pipe(
    switchMap(() =>
      this.http.get<any>(`${this.base}/catalogo`).pipe(
        map((r) => this.convert(r)),
        catchError(() => of(VAZIO))
      )
    ),
    shareReplay({ bufferSize: 1, refCount: false })
  );

  /** Bate a cada segundo, para os contadores */
  readonly tick$: Observable<number> = timer(0, 1000).pipe(
    shareReplay({ bufferSize: 1, refCount: true })
  );

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected i18n: FlaI18nService,
    protected media: FlaMediaUrlService
  ) {}

  protected get base(): string {
    return `${this.occEndpoints.getBaseUrl()}/mengo-estudio`;
  }

  protected convert(r: any): MeCatalogo {
    if (!r?.ativo) {
      return VAZIO;
    }
    return {
      ativo: true,
      skew: (r.agora ?? Date.now()) - Date.now(),
      regras: { ...VAZIO.regras, ...(r.regras ?? {}) },
      drops: r.drops ?? [],
      linhas: r.linhas ?? [],
      estampas: (r.estampas ?? []).map((e: any) => ({
        ...e,
        arte: this.media.url(e.arte) ?? undefined,
        coresIndisponiveis: e.coresIndisponiveis ?? {},
      })),
      camisetas: r.camisetas ?? [],
    };
  }

  /** Agora pelo relógio do servidor */
  now(c: MeCatalogo): number {
    return Date.now() + c.skew;
  }

  situacao(e: MeEstampa, c: MeCatalogo, now = this.now(c)): MeSituacao {
    const rest = (e.encerramento ?? Number.MAX_SAFE_INTEGER) - now;
    if (rest <= 0) {
      return 'ENCERRADA';
    }
    if (rest <= c.regras.ultimasHoras * HOUR) {
      return 'ULTIMAS_48H';
    }
    if (rest <= c.regras.ultimosDias * DAY) {
      return 'ULTIMOS_DIAS';
    }
    if (e.lancamento && now - e.lancamento <= c.regras.novaDias * DAY) {
      return 'NOVA';
    }
    return 'A_VENDA';
  }

  /** "1d 13:59:54" */
  contador(ms: number): string {
    if (ms <= 0) {
      return '0d 00:00:00';
    }
    const p = (n: number) => String(n).padStart(2, '0');
    const d = Math.floor(ms / DAY);
    const h = Math.floor((ms % DAY) / HOUR);
    const m = Math.floor((ms % HOUR) / 60000);
    const s = Math.floor((ms % 60000) / 1000);
    return `${d}d ${p(h)}:${p(m)}:${p(s)}`;
  }

  /** "21/11, 23h59" no horário de Brasília */
  fim(ms?: number): string {
    if (!ms) {
      return '';
    }
    const o: Record<string, string> = {};
    new Intl.DateTimeFormat('pt-BR', {
      timeZone: 'America/Sao_Paulo',
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(ms))
      .forEach((x) => (o[x.type] = x.value));
    return this.i18n.lang === 'pt'
      ? `${o['day']}/${o['month']}, ${o['hour']}h${o['minute']}`
      : `${o['month']}/${o['day']}, ${o['hour']}:${o['minute']}`;
  }

  /** "10/09" */
  dia(ms?: number): string {
    return ms ? this.fim(ms).split(',')[0] : '';
  }

  /** Texto do prazo: "Sai de linha em 1d 13:59:54", "À venda até 21/11, 23h59" ou "Saiu de linha em 10/09" */
  prazo(e: MeEstampa, c: MeCatalogo, now = this.now(c)): string {
    const rest = (e.encerramento ?? Number.MAX_SAFE_INTEGER) - now;
    if (rest <= 0) {
      return this.i18n.t('me.prazo.saiu', { d: this.dia(e.encerramento) });
    }
    if (rest <= c.regras.ultimosDias * DAY) {
      return this.i18n.t('me.prazo.sai', { t: this.contador(rest) });
    }
    return this.i18n.t('me.prazo.ate', { d: this.fim(e.encerramento) });
  }

  /** Versões da estampa (masculina, feminina, infantil) com o menor preço de cada modelo */
  versoes(c: MeCatalogo): MeVersao[] {
    return (['MAS', 'FEM', 'INF'] as const)
      .map((modelo) => {
        const precos = c.camisetas
          .filter((x) => x.modelo === modelo && x.preco)
          .map((x) => x.preco as number);
        return { modelo, preco: precos.length ? Math.min(...precos) : 0 };
      })
      .filter((v) => v.preco > 0);
  }

  /** Menor preço entre todas as camisetas */
  aPartir(c: MeCatalogo): number {
    const v = this.versoes(c);
    return v.length ? Math.min(...v.map((x) => x.preco)) : 0;
  }

  /** "ou 5x de R$ 33,96" */
  parcelas(valor: number, c: MeCatalogo): string {
    const n = Math.max(
      1,
      Math.min(c.regras.parcelasMax, Math.floor(valor / c.regras.parcelaMinima))
    );
    return this.i18n.t('me.parcelas', { n, v: this.i18n.money(valor / n) });
  }

  /** Cor da camiseta para a vitrine: preta, ou a primeira que a arte permite */
  corVitrine(e: MeEstampa): 'PRT' | 'BRA' | 'CIN' {
    return (['PRT', 'BRA', 'CIN'] as const).find((c) => !e.coresIndisponiveis[c]) ?? 'PRT';
  }

  /** Avise-me se voltar */
  avisar(code: string, email: string, consentimento: boolean): Observable<unknown> {
    return this.http
      .post(`${this.base}/estampas/${encodeURIComponent(code)}/aviso`, {
        email,
        consentimento,
      })
      .pipe(
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }
}
