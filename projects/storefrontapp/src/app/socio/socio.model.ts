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
    wave: 2,
    wavesLabel: 'Ondas 1 e 2',
    maxGuests: 3,
    guestCode: 'NACAO-CONVIDADO-60',
  },
  2: {
    discount: 65,
    wave: 5,
    wavesLabel: 'Ondas 3, 4 e 5',
    maxGuests: 2,
    guestCode: 'NACAO-CONVIDADO-60',
  },
  3: {
    discount: 60,
    wave: 6,
    wavesLabel: 'Ondas 5 e 6',
    maxGuests: 1,
    guestCode: 'NACAO-CONVIDADO-35',
  },
};

/** Onda 7 = venda geral (quem não é sócio) */
export const GENERAL_SALE_WAVE = 7;

export const SOCIO_PLAN_PREFIX = 'NACAO-MARACA-';
export const SOCIO_GROUP_PREFIX = 'nacao-maraca-';
export const SOCIO_CATEGORY = 'socio_torcedor';

/** Rótulos das ondas, como no site do Nação */
export const WAVE_LABELS: Record<number, string> = {
  1: 'Onda 1 · Nação Maraca 1 com estrela',
  2: 'Onda 2 · Nação Maraca 1',
  3: 'Onda 3 · Nação Maraca 2 com 2 estrelas',
  4: 'Onda 4 · Nação Maraca 2 com 1 estrela',
  5: 'Onda 5 · Nação Maraca 2 e Nação Maraca 3 com estrela',
  6: 'Onda 6 · Nação Maraca 3',
  7: 'Venda geral',
};

/**
 * Simulação da abertura de vendas (só para a demonstração): força a onda
 * aberta em todos os jogos. null = pela data do jogo.
 */
export const SOCIO_SIMULATION_KEY = 'fla-socio-onda-simulada';

export interface WaveStatus {
  /** Onda aberta agora (0 = vendas ainda não abriram, 7 = venda geral) */
  current: number;
  /** Onda do torcedor (7 se não é sócio) */
  mine: number;
  /** Pode comprar agora */
  open: boolean;
  /** Quando a onda do torcedor abre (pela data do jogo) */
  opensAt?: Date;
  simulated: boolean;
}
