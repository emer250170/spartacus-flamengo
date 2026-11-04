import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FlaMediaUrlService } from '../vitrine/fla-media-url.service';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/** Pedido que deu pontos (ou o bônus de adesão) */
export interface SocioPointsEarning {
  orderCode?: string;
  bonus: boolean;
  date?: Date;
  tickets: number;
  productsAmount: number;
  points: number;
}

/** Experiência resgatada, com o voucher */
export interface SocioRedemption {
  voucher: string;
  experienceCode?: string;
  experienceName?: string;
  points: number;
  date?: Date;
  origin?: string;
  cancelledAt?: Date;
  /** Compra na loja (item 9): pedido e valor pago; vazio = resgate com pontos */
  orderCode?: string;
  amount?: number;
  /** Voucher já usado na portaria ou no atendimento */
  usedAt?: Date;
}

/** Experiência da vitrine */
export interface SocioExperience {
  code: string;
  name: string;
  description?: string;
  points: number;
  /** Vagas que sobram (undefined = sem limite) */
  available?: number;
  minPlanName?: string;
  redeemable: boolean;
  /** Por que não pode resgatar (saldo, plano, esgotada, situação) */
  reason?: string;
  /** Preço na loja (item 9; undefined = só resgate) */
  price?: number;
  /** Produto da loja (undefined = não está à venda) */
  productCode?: string;
  /** Dá para comprar agora (à venda e com vaga) */
  buyable: boolean;
  /** Foto da experiência (endereço completo; 04/11) */
  imageUrl?: string;
}

/** Pontos do sócio (GET /users/current/sociopoints) */
export interface SocioPoints {
  /** Chave flamengo.socio.pontos.ativo */
  enabled: boolean;
  /** Já aderiu (tem pontos) */
  member: boolean;
  canRedeem: boolean;
  reason?: string;
  since?: Date;
  perTicket: number;
  perReal: number;
  bonus: number;
  earned: number;
  redeemed: number;
  balance: number;
  tickets: number;
  productsAmount: number;
  earnings: SocioPointsEarning[];
  redemptions: SocioRedemption[];
  experiences: SocioExperience[];
}

/** Resultado do resgate */
export interface SocioRedeemResult {
  voucher: string;
  points: number;
  status: SocioPoints;
}

const NONE: SocioPoints = {
  enabled: false,
  member: false,
  canRedeem: false,
  perTicket: 0,
  perReal: 0,
  bonus: 0,
  earned: 0,
  redeemed: 0,
  balance: 0,
  tickets: 0,
  productsAmount: 0,
  earnings: [],
  redemptions: [],
  experiences: [],
};

/**
 * Pontos, experiências e resgates do sócio (item 8 do plano de evolução). O
 * saldo e as regras ficam no SAP Commerce; a loja só mostra e pede o resgate.
 */
@Injectable({ providedIn: 'root' })
export class SocioPointsService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected media: FlaMediaUrlService
  ) {}

  protected get url(): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/sociopoints`;
  }

  /** Saldo e vitrine; com a chave desligada ou erro: enabled = false (o cartão some) */
  load(): Observable<SocioPoints> {
    return this.http.get<OccSocioPoints>(this.url).pipe(
      map((r) => (r?.enabled ? toPoints(r, (u) => this.media.url(u)) : NONE)),
      catchError(() => of(NONE))
    );
  }

  /** Resgata a experiência; a recusa do servidor vem na mensagem do erro */
  redeem(experience: string): Observable<SocioRedeemResult> {
    return this.http
      .post<OccRedeem>(`${this.url}/redemptions`, { experience, origin: 'Loja' })
      .pipe(
        map((r) => ({
          voucher: r.voucher ?? '',
          points: r.points ?? 0,
          status: r.status
            ? toPoints(r.status, (u) => this.media.url(u))
            : NONE,
        })),
        catchError((e: HttpErrorResponse) =>
          throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
        )
      );
  }
}

function toPoints(
  r: OccSocioPoints,
  mediaUrl: (path?: string) => string | undefined
): SocioPoints {
  const date = (v?: string) => (v ? new Date(v) : undefined);
  return {
    enabled: !!r.enabled,
    member: !!r.member,
    canRedeem: !!r.canRedeem,
    reason: r.reason || undefined,
    since: date(r.since),
    perTicket: r.perTicket ?? 0,
    perReal: r.perReal ?? 0,
    bonus: r.bonus ?? 0,
    earned: r.earned ?? 0,
    redeemed: r.redeemed ?? 0,
    balance: r.balance ?? 0,
    tickets: r.tickets ?? 0,
    productsAmount: r.productsAmount ?? 0,
    earnings: (r.earnings ?? []).map((e) => ({
      orderCode: e.orderCode || undefined,
      bonus: !!e.bonus,
      date: date(e.date),
      tickets: e.tickets ?? 0,
      productsAmount: e.productsAmount ?? 0,
      points: e.points ?? 0,
    })),
    redemptions: (r.redemptions ?? []).map((x) => ({
      voucher: x.voucher ?? '',
      experienceCode: x.experienceCode,
      experienceName: x.experienceName,
      points: x.points ?? 0,
      date: date(x.date),
      origin: x.origin,
      cancelledAt: date(x.cancelledAt),
      orderCode: x.orderCode || undefined,
      amount: x.amount ?? undefined,
      usedAt: date(x.usedAt),
    })),
    experiences: (r.experiences ?? []).map((x) => ({
      code: x.code ?? '',
      name: x.name ?? x.code ?? '',
      description: x.description || undefined,
      points: x.points ?? 0,
      available: x.available ?? undefined,
      minPlanName: x.minPlanName || undefined,
      redeemable: !!x.redeemable,
      reason: x.reason || undefined,
      price: x.price ?? undefined,
      productCode: x.productCode || undefined,
      buyable: !!x.buyable && !!x.productCode,
      imageUrl: mediaUrl(x.imageUrl),
    })),
  };
}

interface OccSocioPoints {
  enabled?: boolean;
  member?: boolean;
  canRedeem?: boolean;
  reason?: string;
  since?: string;
  perTicket?: number;
  perReal?: number;
  bonus?: number;
  earned?: number;
  redeemed?: number;
  balance?: number;
  tickets?: number;
  productsAmount?: number;
  earnings?: {
    orderCode?: string;
    bonus?: boolean;
    date?: string;
    tickets?: number;
    productsAmount?: number;
    points?: number;
  }[];
  redemptions?: {
    voucher?: string;
    experienceCode?: string;
    experienceName?: string;
    points?: number;
    date?: string;
    origin?: string;
    cancelledAt?: string;
    orderCode?: string;
    amount?: number | null;
    usedAt?: string;
  }[];
  experiences?: {
    code?: string;
    name?: string;
    description?: string;
    points?: number;
    available?: number | null;
    minPlanName?: string;
    redeemable?: boolean;
    reason?: string;
    price?: number | null;
    productCode?: string;
    imageUrl?: string;
    buyable?: boolean;
  }[];
}

interface OccRedeem {
  voucher?: string;
  points?: number;
  status?: OccSocioPoints;
}
