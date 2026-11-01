/**
 * Modelos da jornada de matchday (protótipo Flamengo).
 * Os dados vêm do catálogo flamengoProductCatalog pelas APIs OCC da loja.
 */

export interface MatchdaySeat {
  /** Código da variante da poltrona (ex.: JOGO-20261019-PAL-LESTE-D04) — vai para o carrinho */
  code: string;
  /** Rótulo da poltrona (ex.: D4) */
  label: string;
  row: string;
  number: number;
  /** Tem estoque (1) = livre; sem estoque = vendida */
  available: boolean;
}

export interface MatchdaySector {
  /** Chave do setor (ex.: LESTE) ou, no modelo antigo por setor, o código da variante */
  code: string;
  name: string;
  description: string;
  price: number;
  /** Lugares livres no setor */
  stock?: number;
  /** Poltronas do setor (modelo "poltrona como produto"); vazio no modelo por setor */
  seats: MatchdaySeat[];
}

export interface MatchdayMatch {
  /** Código do produto base do jogo (ex.: FLA-PAL-20261019) */
  code: string;
  title: string;
  opponent: string;
  /** Dia da semana curto no idioma do site ("SEG" / "MON") */
  weekday: string;
  /** 0 = domingo; -1 se o código não tem data */
  weekdayIndex: number;
  dateLabel: string;
  timeLabel: string;
  competition: string;
  venue: string;
  minPrice: number;
  /** Selo do card: últimos ingressos, pré-venda de sócio ou vendas abertas */
  tag?: 'low' | 'presale' | 'open';
  /**
   * Abertura das ondas de venda (ISO): onda 1, 2, 3 e venda geral.
   * Vazio = jogo sem ondas cadastradas (venda geral).
   */
  waveStarts?: string[];
  /** Setores e poltronas já carregados (a lista de jogos chega antes) */
  seatsLoaded?: boolean;
  sectors: MatchdaySector[];
}

export interface MatchdayProduct {
  code: string;
  name: string;
  summary: string;
  price: number;
  /** Destaque "Recomendado para você" */
  recommended?: boolean;
}

export interface MatchdayShirt {
  code: string;
  name: string;
  summary: string;
  price: number;
  sizes: { label: string; code: string }[];
  personalization?: MatchdayProduct;
}

export interface MatchdayCatalog {
  matches: MatchdayMatch[];
  parking: MatchdayProduct[];
  food: MatchdayProduct[];
  shirt?: MatchdayShirt;
}

/**
 * Detalhes da jornada que não cabem numa linha de carrinho padrão
 * (poltrona, placa, forma de entrega da comida, nome e número da camisa).
 * No protótipo ficam no navegador e aparecem na confirmação do pedido.
 */
export interface MatchdaySelection {
  matchCode: string;
  matchTitle: string;
  matchLine: string;
  sectorCode: string;
  sectorName: string;
  seat: string;
  /** Código do item de ingresso que foi para o carrinho (poltrona ou setor) */
  ticketCode?: string;
  parkingCode?: string;
  parkingName?: string;
  plate?: string;
  food: { code: string; name: string; quantity: number }[];
  foodDelivery: 'seat' | 'pickup';
  shirtCode?: string;
  shirtSize?: string;
  shirtName?: string;
  shirtNumber?: string;
  createdAt: string;
}

export const MATCHDAY_STORAGE_KEY = 'fla-matchday-selection';

/**
 * Poltrona segura no carrinho do torcedor. O servidor (extensão
 * flamengomatchday) mantém a reserva pelo mesmo prazo e depois a libera.
 */
export interface MatchdayHold {
  code: string;
  /** Instante (ms) em que a reserva vence; ausente quando não se sabe */
  expiresAt?: number;
}

export const MATCHDAY_HOLD_KEY = 'fla-matchday-hold';

/**
 * Escolha feita sem login: ao clicar em "Entrar" no aviso da poltrona, a
 * jornada guarda jogo, setor e poltrona e, na volta do login, reabre o setor
 * e reserva aquela poltrona.
 */
export interface MatchdayLoginIntent {
  matchCode: string;
  sectorCode?: string;
  seatCode?: string;
  /** Instante (ms) em que foi guardada; vale por MATCHDAY_HOLD_MINUTES */
  savedAt: number;
}

export const MATCHDAY_LOGIN_KEY = 'fla-matchday-login';
/** Igual a flamengomatchday.seathold.minutes no servidor */
export const MATCHDAY_HOLD_MINUTES = 15;
