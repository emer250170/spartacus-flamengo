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

/** Linha do Estúdio no carrinho ou no pedido (API /users/{u}/carts/{c}/mengo-estudio) */
export interface MeCarrinhoItem {
  entryNumber: number;
  estampa: MeEstampa;
  camiseta: string;
  modelo: 'MAS' | 'FEM' | 'INF';
  tecido: 'ALG' | 'POL' | 'OVS';
  cor: 'PRT' | 'BRA' | 'CIN';
  tamanho: string;
  quantidade: number;
  encerrada: boolean;
  numeroSerie?: string;
}

export interface MeCarrinho {
  cartCode?: string;
  itens: MeCarrinhoItem[];
  /** Mensagem do servidor quando há estampa que saiu de linha (não fecha o pedido) */
  bloqueio?: string | null;
  /** Linha incluída ou somada (resposta do POST) */
  entryNumber?: number;
  quantidade?: number;
}

/** Situação da parte Mengo Estúdio do pedido (PR 4) */
export type MeSituacaoPedido =
  | 'RECEBIDO'
  | 'EM_PRODUCAO'
  | 'ENVIADO'
  | 'ENTREGUE'
  | 'CANCELADO'
  | 'EM_DEVOLUCAO'
  | 'REEMBOLSADO';

/** Um pedido do torcedor em Meus pedidos (API /users/{u}/mengo-estudio/pedidos) */
export interface MePedidoResumo {
  code: string;
  data?: number;
  total: number;
  status?: string;
  statusDisplay?: string;
  origem: ('loja' | 'estudio')[];
  /** Situação do Estúdio (null = pedido só da loja) */
  situacao?: MeSituacaoPedido | null;
}

/** Evento da linha do tempo do pedido */
export interface MeEvento {
  situacao: MeSituacaoPedido;
  data?: number;
  nota?: string;
}

/** Parte Mengo Estúdio de um pedido (API /users/{u}/orders/{code}/mengo-estudio) */
export interface MePedido {
  code: string;
  temEstudio: boolean;
  soEstudio: boolean;
  situacao?: MeSituacaoPedido | null;
  eventos: MeEvento[];
  rastreio?: string | null;
  itens: MeCarrinhoItem[];
  podeCancelar: boolean;
  valorEstudioTexto?: string;
  /** Devolução (PR 5): null = sem devolução */
  devolucao?: MeDevolucao | null;
  podeDevolver?: boolean;
}

/** Devolução da parte Mengo Estúdio do pedido (PR 5) */
export interface MeDevolucao {
  motivo: 'DEFEITO' | 'ERRO' | 'ARREPENDIMENTO';
  solucao: 'REEMBOLSO' | 'REIMPRESSAO';
  comentario?: string | null;
  etapa: 'SOLICITADA' | 'ENVIADA' | 'RESOLVIDA';
  codigoPostagem: string;
  solicitadaEm?: number;
  enviadaEm?: number;
  recebidaEm?: number;
  valor?: number;
  valorTexto?: string;
  pedidoReimpressao?: string;
}

/** Estado do painel da demonstração (PR 5) */
export interface MeDemo {
  agora: number;
  relogioMinutos: number;
  pagamento: 'APROVAR' | 'RECUSAR_UMA_VEZ' | 'RECUSAR';
  cancelamento: 'ACEITO' | 'RECUSADO' | 'FALHA';
  estampas: { code: string; nome: string; situacao: string; fim: number; simulado: boolean }[];
  pedidos: { code: string; situacao: MeSituacaoPedido; devolucao?: string | null; proximo?: string | null }[];
  avisos: number;
  avisosPendentes: number;
}

/** Resposta da estamparia ao cancelamento */
export interface MeCancelamento {
  resultado: 'ACEITO' | 'RECUSADO' | 'FALHA';
  mensagem: string;
  pedido?: MePedido;
}

/** Cor do selo de cada situação do pedido (classes fla-badge) */
export const ME_SITUACAO_TOM: Record<MeSituacaoPedido, string> = {
  RECEBIDO: 'grey',
  EM_PRODUCAO: 'amber',
  ENVIADO: 'blue',
  ENTREGUE: 'green',
  CANCELADO: 'red',
  EM_DEVOLUCAO: 'amber',
  REEMBOLSADO: 'grey',
};

/** O que o configurador manda para o carrinho */
export interface MePedidoItem {
  estampa: string;
  camiseta: string;
  cor: string;
  tamanho: string;
  quantidade: number;
  /** Linha do carrinho que está sendo editada */
  substituir?: number;
}

export type MeCor = 'PRT' | 'BRA' | 'CIN';
export const ME_CORES: MeCor[] = ['PRT', 'BRA', 'CIN'];
export const ME_MODELOS: ('MAS' | 'FEM' | 'INF')[] = ['MAS', 'FEM', 'INF'];
export const ME_TECIDOS: ('ALG' | 'POL' | 'OVS')[] = ['ALG', 'POL', 'OVS'];
/** Cor da bolinha de cada cor de camiseta */
export const ME_COR_HEX: Record<MeCor, string> = { PRT: '#1b1b1b', BRA: '#ffffff', CIN: '#9a9a9a' };

/** Uma versão da estampa na vitrine (camiseta masculina, feminina ou infantil) */
export interface MeVersao {
  modelo: 'MAS' | 'FEM' | 'INF';
  preco: number;
}

/**
 * trackBy dos *ngFor do estúdio: o vm é recalculado a cada segundo (contadores); sem isto o Angular recria
 * os cards (pisca, perde o foco dos filtros e do diálogo "Avise-me").
 */
export function meTrack(i: number, x: any): unknown {
  return x?.e?.code ?? x?.code ?? x?.modelo ?? x?.k ?? x?.txt ?? x?.nome ?? i;
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

  /** Camiseta (produto) do modelo e tecido */
  camiseta(c: MeCatalogo, modelo?: string, tecido?: string): MeCamiseta | undefined {
    return c.camisetas.find((x) => x.modelo === modelo && x.tecido === tecido);
  }

  /** Tecidos vendidos para o modelo (Infantil não tem Oversized) */
  tecidos(c: MeCatalogo, modelo?: string): MeCamiseta[] {
    return ME_TECIDOS.map((t) => this.camiseta(c, modelo, t)).filter(
      (x): x is MeCamiseta => !!x && !!x.preco && x.tamanhos.length > 0
    );
  }

  /** "Camiseta Masculina · Algodão · Preta · M" */
  configuracao(i: { modelo?: string; tecido?: string; cor?: string; tamanho?: string }): string {
    return [
      this.i18n.t('me.camisetaDe', { n: this.i18n.t('me.modelo.' + i.modelo) }),
      i.tecido ? this.i18n.t('me.tecido.' + i.tecido) : '',
      i.cor ? this.i18n.t('me.cor.' + i.cor) : '',
      i.tamanho ?? '',
    ]
      .filter((x) => !!x)
      .join(' · ');
  }

  /** Linhas do Estúdio no carrinho */
  carrinho(cartCode: string): Observable<MeCarrinho> {
    return this.http
      .get<any>(`${this.occEndpoints.getBaseUrl()}/users/current/carts/${encodeURIComponent(cartCode)}/mengo-estudio`)
      .pipe(
        map((r) => this.convertCarrinho(r)),
        catchError(() => of({ itens: [] } as MeCarrinho))
      );
  }

  /** Inclui a camiseta com a estampa no carrinho (ou soma a quantidade da mesma combinação) */
  adicionar(cartCode: string, item: MePedidoItem): Observable<MeCarrinho> {
    return this.http
      .post<any>(
        `${this.occEndpoints.getBaseUrl()}/users/current/carts/${encodeURIComponent(cartCode)}/mengo-estudio`,
        item
      )
      .pipe(
        map((r) => this.convertCarrinho(r)),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }

  protected convertCarrinho(r: any): MeCarrinho {
    return {
      cartCode: r?.cartCode,
      entryNumber: r?.entryNumber,
      quantidade: r?.quantidade,
      bloqueio: r?.bloqueio ?? null,
      itens: (r?.itens ?? []).map((i: any) => ({
        ...i,
        estampa: {
          ...i.estampa,
          arte: this.media.url(i.estampa?.arte) ?? undefined,
          coresIndisponiveis: i.estampa?.coresIndisponiveis ?? {},
        },
      })),
    };
  }

  /** Pedidos do torcedor com a origem (loja, Mengo Estúdio) e a situação do Estúdio (PR 4) */
  pedidos(): Observable<MePedidoResumo[]> {
    return this.http
      .get<any>(`${this.occEndpoints.getBaseUrl()}/users/current/mengo-estudio/pedidos`)
      .pipe(
        map((r) => (r?.ativo ? ((r?.pedidos ?? []) as MePedidoResumo[]) : [])),
        catchError(() => of([] as MePedidoResumo[]))
      );
  }

  /** Parte Mengo Estúdio do pedido: situação, linha do tempo, rastreio, série e se ainda dá para cancelar */
  pedido(code: string): Observable<MePedido | null> {
    return this.http
      .get<any>(`${this.occEndpoints.getBaseUrl()}/users/current/orders/${encodeURIComponent(code)}/mengo-estudio`)
      .pipe(
        map((r) => this.convertPedido(r)),
        catchError(() => of(null))
      );
  }

  /** Pede o cancelamento à estamparia (só em Recebido) */
  cancelar(code: string): Observable<MeCancelamento> {
    return this.http
      .post<any>(
        `${this.occEndpoints.getBaseUrl()}/users/current/orders/${encodeURIComponent(code)}/mengo-estudio/cancelamento`,
        {}
      )
      .pipe(
        map((r) => ({
          resultado: r?.resultado,
          mensagem: r?.mensagem ?? '',
          pedido: this.convertPedido(r?.pedido) ?? undefined,
        })),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }

  protected convertPedido(r: any): MePedido | null {
    if (!r?.code) {
      return null;
    }
    return {
      code: r.code,
      temEstudio: !!r.temEstudio,
      soEstudio: !!r.soEstudio,
      situacao: r.situacao ?? null,
      eventos: r.eventos ?? [],
      rastreio: r.rastreio ?? null,
      itens: this.convertCarrinho(r).itens,
      podeCancelar: !!r.podeCancelar,
      valorEstudioTexto: r.valorEstudioTexto,
      devolucao: r.devolucao ?? null,
      podeDevolver: !!r.podeDevolver,
    };
  }

  /** Pede a devolução da parte do Estúdio (PR 5) */
  pedirDevolucao(
    code: string,
    body: { motivo: string; solucao: string; comentario?: string }
  ): Observable<MePedido | null> {
    return this.http
      .post<any>(
        `${this.occEndpoints.getBaseUrl()}/users/current/orders/${encodeURIComponent(code)}/mengo-estudio/devolucao`,
        body
      )
      .pipe(
        map((r) => this.convertPedido(r)),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }

  /** O torcedor informa que postou a peça */
  enviarDevolucao(code: string): Observable<MePedido | null> {
    return this.http
      .post<any>(
        `${this.occEndpoints.getBaseUrl()}/users/current/orders/${encodeURIComponent(code)}/mengo-estudio/devolucao/envio`,
        {}
      )
      .pipe(
        map((r) => this.convertPedido(r)),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }

  /** Painel da demonstração: estado (403 = não é usuário de teste) */
  demo(): Observable<MeDemo> {
    return this.http
      .get<MeDemo>(`${this.occEndpoints.getBaseUrl()}/users/current/mengo-estudio/demo`)
      .pipe(
        catchError((e: HttpErrorResponse) =>
          throwError(() => Object.assign(new Error(e?.error?.errors?.[0]?.message ?? ''), { status: e?.status }))
        )
      );
  }

  /** Painel da demonstração: uma ação ({acao, ...}); devolve a mensagem e o estado novo */
  demoAcao(body: Record<string, unknown>): Observable<{ mensagem: string; estado: MeDemo }> {
    return this.http
      .post<{ mensagem: string; estado: MeDemo }>(
        `${this.occEndpoints.getBaseUrl()}/users/current/mengo-estudio/demo`,
        body
      )
      .pipe(
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
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
