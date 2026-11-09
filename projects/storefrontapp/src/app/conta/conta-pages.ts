/**
 * Páginas de Minha conta no visual do mockup (07/11): título e frase da faixa escura e os grupos
 * do menu lateral. A chave vem do endereço (/my-account/<segmento>/<código>); os textos ficam em
 * fla-texts (acc.t.<chave> título, acc.c.<chave> trilha quando difere, acc.l.<chave> frase).
 */

/** Segmento da URL depois de /my-account/ → chave da página */
const SEGMENT_KEYS: Record<string, string> = {
  'socio-torcedor': 'socio',
  orders: 'orders',
  vouchers: 'vouchers',
  ingressos: 'mytickets',
  order: 'order',
  'return-request': 'return',
  wishlist: 'wishlist',
  'saved-carts': 'savedCarts',
  'saved-cart': 'savedCart',
  coupons: 'coupons',
  'my-interests': 'interests',
  'update-email': 'email',
  'update-password': 'password',
  'address-book': 'addresses',
  'payment-details': 'payments',
  consents: 'consents',
  'notification-preference': 'notifications',
  'close-account': 'close',
  'support-tickets': 'tickets',
  'support-ticket': 'ticket',
};

/** Páginas de detalhe: item do menu que fica marcado */
const MENU_OF: Record<string, string> = {
  order: 'orders',
  return: 'orders',
  savedCart: 'savedCarts',
  ticket: 'tickets',
};

/** Páginas com frase embaixo do título (mockup) */
export const ACCOUNT_LEADS = [
  'socio',
  'orders',
  'vouchers',
  'mytickets',
  'email',
  'addresses',
  'consents',
  'close',
  'tickets',
];

/** Detalhes com o código no título ("Pedido nº 00045000") */
export const ACCOUNT_WITH_CODE = ['order', 'return', 'ticket'];

/** Trilha diferente do título ("Notificações", "Pedido 00045000") */
export const ACCOUNT_SHORT_CRUMB = ['order', 'return', 'notifications'];

export interface AccountMenuItem {
  key: string;
  url: string;
  /** Só para quem é ou já foi sócio (SocioNavigationService) */
  socio?: boolean;
}

export interface AccountMenuGroup {
  key: string;
  items: AccountMenuItem[];
}

/** Menu lateral (mockup ContaMenu). "Dados pessoais" saiu da conta em 07/11. */
export const ACCOUNT_MENU: AccountMenuGroup[] = [
  {
    key: 'socio',
    items: [{ key: 'socio', url: '/my-account/socio-torcedor', socio: true }],
  },
  {
    key: 'shop',
    items: [
      { key: 'orders', url: '/my-account/orders' },
      // 08/11: vouchers de experiência (sócio ou não)
      { key: 'vouchers', url: '/my-account/vouchers' },
      // 09/11: ingressos comprados, por jogo
      { key: 'mytickets', url: '/my-account/ingressos' },
      { key: 'wishlist', url: '/my-account/wishlist' },
      { key: 'savedCarts', url: '/my-account/saved-carts' },
      { key: 'coupons', url: '/my-account/coupons' },
      { key: 'interests', url: '/my-account/my-interests' },
    ],
  },
  {
    key: 'profile',
    items: [
      { key: 'email', url: '/my-account/update-email' },
      { key: 'password', url: '/my-account/update-password' },
      { key: 'addresses', url: '/my-account/address-book' },
      { key: 'payments', url: '/my-account/payment-details' },
    ],
  },
  {
    key: 'privacy',
    items: [
      { key: 'consents', url: '/my-account/consents' },
      { key: 'notifications', url: '/my-account/notification-preference' },
      { key: 'close', url: '/my-account/close-account' },
    ],
  },
  {
    key: 'help',
    items: [{ key: 'tickets', url: '/my-account/support-tickets' }],
  },
];

export interface AccountPage {
  /** Chave da página (acc.t.<key>); vazia quando a página não está no mapa */
  key: string;
  /** Código do pedido, devolução ou chamado na URL */
  code?: string;
  /** Item do menu marcado */
  menu: string;
}

const ACCOUNT_URL = /\/my-account\/([^/?#]+)(?:\/([^/?#]+))?/;
const LOGIN_URL = /\/login\/?(?:[?#]|$)/;

/** Página de Minha conta do endereço, ou null fora da conta */
export function accountPageOf(url: string): AccountPage | null {
  const match = ACCOUNT_URL.exec(url ?? '');
  if (!match) {
    return null;
  }
  const key = SEGMENT_KEYS[match[1]] ?? '';
  const code = match[2] ? decodeURIComponent(match[2]) : undefined;
  return { key, code, menu: MENU_OF[key] ?? key };
}

/** /login (sem /login/register, /login/forgot-password...) */
export function isLoginUrl(url: string): boolean {
  return LOGIN_URL.test(url ?? '');
}

/**
 * Cor do selo de situação (mockup): âmbar aguardando, azul em andamento, verde concluído,
 * cinza cancelado/fechado. Aceita o código (PENDING, statusDisplay "pending") ou o texto
 * em português ("Em andamento").
 */
export function statusTone(status: string): string {
  const s = (status ?? '').toLowerCase();
  if (/cancel|fechad|closed|rejeit|recusad/.test(s)) {
    return 'grey';
  }
  if (/complet|conclu|entregu|deliver|shipped|enviad|pago|paid/.test(s)) {
    return 'green';
  }
  if (
    /pend|aguard|wait|created|criad|payment_not|check_on_hold|on_hold/.test(s)
  ) {
    return 'amber';
  }
  return 'blue';
}
