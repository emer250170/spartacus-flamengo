/**
 * Sócio-torcedor Nação Maraca (protótipo Flamengo).
 *
 * Os planos são produtos de assinatura do SAP Commerce (categoria
 * socio_torcedor, preço no plano de preço da assinatura). Quem compra um
 * plano entra no grupo de clientes nacao-maraca-N (passo completeOrder do
 * order-process) e as promoções do SAP Commerce dão o desconto no ingresso.
 */

export interface SocioPlan {
  /** NACAO-MARACA-1 */
  code: string;
  /** 1, 2 ou 3 */
  level: number;
  name: string;
  summary: string;
  /** Itens do card (vêm da descrição do produto) */
  benefits: string[];
  /** Mensalidade (R$) */
  price: number;
  /** Plano esgotado (Maraca 1 tem vagas limitadas) */
  soldOut: boolean;
  guest?: SocioGuest;
  maxGuests: number;
  /** % de desconto na inteira */
  discount: number;
  /** Onda de vendas do titular sem estrelas */
  wave: number;
  /** Ondas do plano (texto) */
  wavesLabel: string;
}

export interface SocioGuest {
  code: string;
  name: string;
  price: number;
}

/**
 * Regras de cada plano que a loja precisa conhecer para montar a tela.
 * O desconto em si é aplicado pelo SAP Commerce (promoções
 * nacao_maraca_N_desconto_ingresso); aqui ele só aparece como informação.
 */
export const SOCIO_RULES: Record<
  number,
  {
    discount: number;
    wave: number;
    wavesLabel: string;
    maxGuests: number;
    guestCode: string;
  }
> = {
  1: {
    discount: 75,
    wave: 1,
    wavesLabel: 'Onda 1',
    maxGuests: 3,
    guestCode: 'NACAO-CONVIDADO-60',
  },
  2: {
    discount: 65,
    wave: 2,
    wavesLabel: 'Onda 2',
    maxGuests: 2,
    guestCode: 'NACAO-CONVIDADO-60',
  },
  3: {
    discount: 60,
    wave: 3,
    wavesLabel: 'Onda 3',
    maxGuests: 1,
    guestCode: 'NACAO-CONVIDADO-35',
  },
};

/** Onda 4 = venda geral (quem não é sócio) */
export const GENERAL_SALE_WAVE = 4;

export const SOCIO_PLAN_PREFIX = 'NACAO-MARACA-';
export const SOCIO_GROUP_PREFIX = 'nacao-maraca-';
export const SOCIO_CATEGORY = 'socio_torcedor';

/**
 * Ondas de venda de cada jogo (datas cadastradas no Backoffice, tela Jogos).
 * Cada onda vale até o jogo: quando a onda 2 abre, o Maraca 1 continua comprando.
 */
export const WAVE_LABELS: Record<number, string> = {
  1: 'Onda 1 · Nação Maraca 1',
  2: 'Onda 2 · Nação Maraca 2',
  3: 'Onda 3 · Nação Maraca 3',
  4: 'Venda geral',
};

export interface WaveStatus {
  /** Onda aberta agora (0 = vendas ainda não abriram, 4 = venda geral) */
  current: number;
  /** Onda do torcedor (4 se não é sócio) */
  mine: number;
  /** Pode comprar agora */
  open: boolean;
  /** Quando a onda do torcedor abre */
  opensAt?: Date;
}

/**
 * Situação do torcedor. O plano no carrinho já vale para ingressos (o SAP
 * Commerce põe o torcedor no grupo do plano enquanto ele estiver no carrinho).
 */
export interface Membership {
  /** Plano comprado (1 a 3) ou null */
  member: number | null;
  /** Plano no carrinho (1 a 3) ou null */
  inCart: number | null;
  /** O que vale para ingressos: o melhor dos dois */
  level: number | null;
}

export const SOCIO_PENDING_PREFIX = 'nacao-pendente-';
