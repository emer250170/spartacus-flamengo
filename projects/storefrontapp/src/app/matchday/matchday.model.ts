/**
 * Modelos da jornada de matchday (protótipo Flamengo).
 * Os dados vêm do catálogo flamengoProductCatalog pelas APIs OCC da loja.
 */

export interface MatchdaySector {
  /** Código da variante (ex.: FLA-PAL-20261019-NORTE) — é o que vai para o carrinho */
  code: string;
  name: string;
  description: string;
  price: number;
  stock?: number;
}

export interface MatchdayMatch {
  /** Código do produto base do jogo (ex.: FLA-PAL-20261019) */
  code: string;
  title: string;
  opponent: string;
  weekday: string;
  dateLabel: string;
  timeLabel: string;
  competition: string;
  venue: string;
  minPrice: number;
  /** Selo do card: últimos ingressos, pré-venda etc. */
  tag?: string;
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
