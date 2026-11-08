import { SocioRedemption } from './socio-points.service';

/**
 * Situação do voucher de experiência (08/11, Meus vouchers e detalhe do pedido): cancelado
 * (cancelledAt), utilizado (usedAt) ou válido. Os dados vêm de GET /users/current/sociopoints
 * (redemptions), que lista os vouchers de qualquer cliente, sócio ou não.
 */
export type VoucherStatus = 'valid' | 'used' | 'cancelled';

export function voucherStatus(r: SocioRedemption): VoucherStatus {
  return r.cancelledAt ? 'cancelled' : r.usedAt ? 'used' : 'valid';
}

/** Cor do selo (fla-badge): verde válido, azul utilizado, cinza cancelado */
export const VOUCHER_TONE: Record<VoucherStatus, string> = {
  valid: 'green',
  used: 'blue',
  cancelled: 'grey',
};

/** Produto da loja de uma experiência (SocioExperienceService.PRODUCT_PREFIX) */
export const EXPERIENCE_PRODUCT_PREFIX = 'EXP-';

/** Compra na loja (pedido) ou resgate com pontos */
export function isPurchase(r: SocioRedemption): boolean {
  return !!r.orderCode || r.origin === 'Compra';
}
