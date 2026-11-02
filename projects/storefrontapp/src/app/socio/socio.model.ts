/**
 * Sócio-torcedor Flamengo (protótipo).
 *
 * Os planos são produtos de assinatura do SAP Commerce (categoria
 * socio_torcedor, preço no plano de preço da assinatura). Os usuários de
 * negócio criam e editam quantos planos quiserem no Backoffice: as regras de
 * cada plano (onda, descontos, convidados, grupos de clientes, família e ordem)
 * vêm num marcador no fim da descrição do produto:
 *
 *   <ul><li>benefício</li></ul><!--socio-plan {"wave":1,"discount":75,"storeDiscount":10,...}-->
 *
 * Quem compra um plano entra no grupo de clientes do plano (passo
 * completeOrder do order-process) e as promoções do SAP Commerce dão o
 * desconto no ingresso e nos produtos da loja. Produto da categoria sem o marcador é complemento de
 * convidado (NACAO-CONVIDADO-60/35), não plano.
 */

export interface SocioPlan {
  /** Código do produto (NACAO-MARACA-1, NACAO-SEM-FRONTEIRAS-ANUAL...) */
  code: string;
  name: string;
  summary: string;
  /** Itens do card (vêm da descrição do produto) */
  benefits: string[];
  /** Mensalidade (R$) */
  price: number;
  /** Plano esgotado (sem estoque no SAP Commerce) */
  soldOut: boolean;
  guest?: SocioGuest;
  /** Convidados permitidos (0 = plano sem convidados) */
  maxGuests: number;
  /** % de desconto na inteira (0 = sem desconto) */
  discount: number;
  /**
   * % de desconto nos produtos da loja (0 = sem desconto); não vale para
   * alimentos e bebidas, estacionamento, planos e ingressos
   */
  storeDiscount: number;
  /** Onda de vendas do titular: 1 a 3, ou 0 = sem prioridade (venda geral) */
  wave: number;
  /** Grupo de clientes de quem tem o plano */
  group: string;
  /** Grupo marcador de plano só no carrinho (ainda não comprado) */
  pending: string;
  /** Família do plano ("Nação Maraca", "Nação Sem Fronteiras") */
  family: string;
  /** Ordem dos cards */
  order: number;
}

export interface SocioGuest {
  code: string;
  name: string;
  price: number;
}

/** Regras do marcador <!--socio-plan {...}--> da descrição do produto */
export interface SocioPlanRules {
  wave: number;
  discount: number;
  storeDiscount: number;
  maxGuests: number;
  guest: string;
  group: string;
  pending: string;
  family: string;
  order: number;
}

/**
 * Compatibilidade: enquanto a migração do backend não roda, as descrições não
 * têm o marcador. Só para produtos NACAO-MARACA-N sem marcador, valem estas
 * regras antigas (grupo nacao-maraca-N, marcador nacao-pendente-N, família
 * "Nação Maraca", ordem N). Pode ser apagado depois da migração.
 */
export const SOCIO_RULES: Record<
  number,
  {
    discount: number;
    wave: number;
    maxGuests: number;
    guestCode: string;
  }
> = {
  1: {
    discount: 75,
    wave: 1,
    maxGuests: 3,
    guestCode: 'NACAO-CONVIDADO-60',
  },
  2: {
    discount: 65,
    wave: 2,
    maxGuests: 2,
    guestCode: 'NACAO-CONVIDADO-60',
  },
  3: {
    discount: 60,
    wave: 3,
    maxGuests: 1,
    guestCode: 'NACAO-CONVIDADO-35',
  },
};

/** Onda 4 = venda geral (quem não é sócio ou tem plano sem prioridade) */
export const GENERAL_SALE_WAVE = 4;

export const SOCIO_CATEGORY = 'socio_torcedor';

/*
 * Ondas de venda de cada jogo: datas cadastradas no Backoffice (tela Jogos).
 * Cada onda vale até o jogo: quando a onda 2 abre, a onda 1 continua
 * comprando. Nomes das ondas: fla-texts (wave.*) com os planos de cada onda.
 */

export interface WaveStatus {
  /** Onda aberta agora (0 = vendas ainda não abriram, 4 = venda geral) */
  current: number;
  /** Onda do torcedor (4 se não é sócio ou o plano não tem prioridade) */
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
  /** Plano comprado ou null */
  member: SocioPlan | null;
  /** Plano no carrinho ou null */
  inCart: SocioPlan | null;
  /**
   * O que vale para ingressos: o de melhor onda entre os dois (onda 0 conta
   * como venda geral; no empate, o comprado). Sócio com os benefícios
   * suspensos: null (compra na venda geral, sem desconto).
   */
  plan: SocioPlan | null;
  /**
   * Benefícios suspensos por inadimplência (grupo socio-suspenso no SAP
   * Commerce): continua sócio, mas sem onda de sócio e sem descontos até
   * regularizar.
   */
  suspended: boolean;
  /** Biometria facial validada (grupo biometria-validada no SAP Commerce) */
  biometry: boolean;
}

/** Grupo de clientes de quem está com os benefícios suspensos (inadimplência) */
export const SUSPENDED_GROUP = 'socio-suspenso';

/** Grupo de clientes de quem já validou a biometria facial */
export const BIOMETRY_GROUP = 'biometria-validada';

/**
 * Elegibilidade para comprar ingresso (cartão "Sua elegibilidade para este
 * jogo" na jornada). A mesma regra roda no SAP Commerce
 * (SocioEligibilityService), que grava cada tentativa; estes valores espelham
 * as chaves do servidor:
 * - biometryRequired: flamengo.socio.biometria.exigida
 * - generalLimit: flamengo.ingressos.limite.geral (sócio: titular + convidados do plano)
 */
export const ELIGIBILITY = {
  biometryRequired: false,
  generalLimit: 4,
};

/** Uma condição conferida no cartão de elegibilidade */
export interface EligibilityCheck {
  /** plan, standing, window, biometry, limit */
  key: string;
  /** ok = atendida, warn = aviso que não impede, no = não atendida */
  state: 'ok' | 'warn' | 'no';
  text: string;
}
