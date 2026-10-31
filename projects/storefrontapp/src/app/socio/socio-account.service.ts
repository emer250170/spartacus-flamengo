import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, forkJoin, of, throwError } from 'rxjs';
import { catchError, map, switchMap, take } from 'rxjs/operators';

/** Começo do assunto do chamado de cancelamento (o atendimento filtra por ele) */
export const CANCEL_SUBJECT = 'Cancelamento do plano';

/** Pedido em que o torcedor comprou (ou trocou) o plano */
export interface SocioPlanOrder {
  code: string;
  created: Date;
  planCode: string;
  /** Valor do plano no pedido (1ª mensalidade) */
  planAmount: number;
  /** Convidados contratados no pedido */
  guests: number;
  guestAmount: number;
  card?: SocioCard;
}

export interface SocioCard {
  id: string;
  /** ************1111 */
  number: string;
  /** visa, master... */
  type: string;
  typeName: string;
  expiry: string;
  holder: string;
  isDefault: boolean;
}

export interface SocioCancellation {
  id: string;
  createdAt?: Date;
  status: string;
  statusName: string;
}

export interface NewCard {
  holder: string;
  number: string;
  type: string;
  month: string;
  year: string;
  cvn: string;
}

/**
 * Dados da área do sócio-torcedor (Minha conta > Meu sócio-torcedor) pela API
 * de comércio: pedidos do plano, cartões salvos e chamados de atendimento.
 *
 * - Mensalidades: o plano é uma assinatura anual (12 mensalidades); a 1ª é paga
 *   na compra e as outras no cartão padrão da conta, no mesmo dia dos meses
 *   seguintes.
 * - Novo cartão: a API só cadastra cartão num carrinho; usa o carrinho atual
 *   (ou cria um vazio) e salva o cartão na conta, como padrão.
 * - Cancelamento: abre um chamado de atendimento "Cancelamento do plano ...",
 *   que a equipe trata no Backoffice (Customer Support).
 */
@Injectable({ providedIn: 'root' })
export class SocioAccountService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected activeCart: ActiveCartFacade
  ) {}

  protected get base(): string {
    return this.occEndpoints.getBaseUrl();
  }

  /** Pedidos com plano de sócio, do mais novo para o mais antigo */
  planOrders(
    planCodes: string[],
    guestCodes: string[]
  ): Observable<SocioPlanOrder[]> {
    return this.http
      .get<{ orders?: { code?: string }[] }>(
        `${this.base}/users/current/orders`,
        { params: { fields: 'orders(code)', pageSize: '20' } }
      )
      .pipe(
        switchMap((res) => {
          const codes = (res.orders ?? [])
            .map((o) => o.code)
            .filter((c): c is string => !!c);
          if (!codes.length) {
            return of([] as (OccOrder | null)[]);
          }
          return forkJoin(
            codes.map((code) =>
              this.http
                .get<OccOrder>(
                  `${this.base}/users/current/orders/${encodeURIComponent(code)}`,
                  { params: { fields: 'FULL' } }
                )
                .pipe(catchError(() => of(null)))
            )
          );
        }),
        map((orders) =>
          orders
            .map((o) => (o ? this.toPlanOrder(o, planCodes, guestCodes) : null))
            .filter((o): o is SocioPlanOrder => !!o)
            .sort((a, b) => b.created.getTime() - a.created.getTime())
        ),
        catchError(() => of([] as SocioPlanOrder[]))
      );
  }

  /** Cartões salvos na conta */
  cards(): Observable<SocioCard[]> {
    return this.http
      .get<{ payments?: OccPayment[] }>(
        `${this.base}/users/current/paymentdetails`,
        { params: { saved: 'true', fields: 'FULL' } }
      )
      .pipe(
        map((res) => (res.payments ?? []).map((p) => this.toCard(p))),
        catchError(() => of([] as SocioCard[]))
      );
  }

  /** Cartão das mensalidades = cartão padrão da conta */
  setDefaultCard(id: string): Observable<unknown> {
    return this.http.patch(
      `${this.base}/users/current/paymentdetails/${encodeURIComponent(id)}`,
      { billingAddress: { titleCode: 'mr' }, defaultPayment: true }
    );
  }

  /** Cadastra um cartão novo (endereço de cobrança = endereço padrão) e o deixa como padrão */
  addCard(card: NewCard): Observable<unknown> {
    return this.http
      .get<{ addresses?: OccAddress[] }>(
        `${this.base}/users/current/addresses`,
        { params: { fields: 'FULL' } }
      )
      .pipe(
        map((res) => {
          const list = res.addresses ?? [];
          const address = list.find((a) => a.defaultAddress) ?? list[0];
          if (!address) {
            throw new Error('no-address');
          }
          return address;
        }),
        switchMap((address) =>
          this.activeCart.requireLoadedCart().pipe(
            take(1),
            map((cart) => ({ address, cartId: cart.code }))
          )
        ),
        switchMap(({ address, cartId }) => {
          if (!cartId) {
            return throwError(() => new Error('no-cart'));
          }
          return this.http.post<OccPayment>(
            `${this.base}/users/current/carts/${encodeURIComponent(cartId)}/paymentdetails`,
            {
              accountHolderName: card.holder,
              cardNumber: card.number.replace(/\D/g, ''),
              cardType: { code: card.type },
              expiryMonth: card.month,
              expiryYear: card.year,
              cvn: card.cvn,
              saved: true,
              defaultPayment: true,
              billingAddress: {
                firstName: address.firstName,
                lastName: address.lastName,
                line1: address.line1,
                line2: address.line2,
                town: address.town,
                postalCode: address.postalCode,
                country: { isocode: address.country?.isocode },
                region: address.region?.isocode
                  ? { isocode: address.region.isocode }
                  : undefined,
              },
            },
            { params: { fields: 'FULL' } }
          );
        }),
        switchMap((payment) =>
          payment?.id ? this.setDefaultCard(payment.id) : of(payment)
        )
      );
  }

  /** Pedido de cancelamento ainda aberto (chamado de atendimento) ou null */
  cancellation(): Observable<SocioCancellation | null> {
    return this.http
      .get<{ tickets?: OccTicket[] }>(`${this.base}/users/current/tickets`, {
        params: { fields: 'FULL', pageSize: '50' },
      })
      .pipe(
        map((res) => {
          const ticket = (res.tickets ?? []).find(
            (t) =>
              (t.subject ?? '').startsWith(CANCEL_SUBJECT) &&
              t.status?.id !== 'CLOSED'
          );
          return ticket ? this.toCancellation(ticket) : null;
        }),
        catchError(() => of(null))
      );
  }

  /** Abre o chamado "Cancelamento do plano X" */
  requestCancellation(
    planName: string,
    message: string
  ): Observable<SocioCancellation> {
    return this.http
      .post<OccTicket>(`${this.base}/users/current/tickets`, {
        subject: `${CANCEL_SUBJECT} ${planName}`,
        message,
        ticketCategory: { id: 'ENQUIRY' },
      })
      .pipe(
        map((ticket) =>
          this.toCancellation({
            ...ticket,
            status: ticket.status ?? { id: 'OPEN', name: 'Open' },
          })
        )
      );
  }

  protected toPlanOrder(
    order: OccOrder,
    planCodes: string[],
    guestCodes: string[]
  ): SocioPlanOrder | null {
    const entries = order.entries ?? [];
    const plan = entries.find((e) =>
      planCodes.includes(e.product?.code ?? '')
    );
    if (!plan || !order.code || !order.created) {
      return null;
    }
    const guests = entries.filter((e) =>
      guestCodes.includes(e.product?.code ?? '')
    );
    return {
      code: order.code,
      created: new Date(order.created),
      planCode: plan.product?.code ?? '',
      planAmount: plan.totalPrice?.value ?? 0,
      guests: guests.reduce((n, e) => n + (e.quantity ?? 0), 0),
      guestAmount: guests.reduce((n, e) => n + (e.totalPrice?.value ?? 0), 0),
      card: order.paymentInfo ? this.toCard(order.paymentInfo) : undefined,
    };
  }

  protected toCard(p: OccPayment): SocioCard {
    const month = p.expiryMonth ?? '';
    return {
      id: p.id ?? '',
      number: p.cardNumber ?? '',
      type: p.cardType?.code ?? '',
      typeName: p.cardType?.name ?? p.cardType?.code ?? '',
      expiry: `${month.length === 1 ? '0' + month : month}/${p.expiryYear ?? ''}`,
      holder: p.accountHolderName ?? '',
      isDefault: !!p.defaultPayment,
    };
  }

  protected toCancellation(ticket: OccTicket): SocioCancellation {
    return {
      id: ticket.id ?? '',
      createdAt: ticket.createdAt ? new Date(ticket.createdAt) : undefined,
      status: ticket.status?.id ?? 'OPEN',
      statusName: ticket.status?.name ?? '',
    };
  }
}

interface OccPrice {
  value?: number;
}

interface OccPayment {
  id?: string;
  accountHolderName?: string;
  cardNumber?: string;
  cardType?: { code?: string; name?: string };
  expiryMonth?: string;
  expiryYear?: string;
  defaultPayment?: boolean;
}

interface OccOrder {
  code?: string;
  created?: string;
  entries?: {
    product?: { code?: string };
    quantity?: number;
    totalPrice?: OccPrice;
  }[];
  paymentInfo?: OccPayment;
}

interface OccAddress {
  defaultAddress?: boolean;
  firstName?: string;
  lastName?: string;
  line1?: string;
  line2?: string;
  town?: string;
  postalCode?: string;
  country?: { isocode?: string };
  region?: { isocode?: string };
}

interface OccTicket {
  id?: string;
  subject?: string;
  createdAt?: string;
  status?: { id?: string; name?: string };
}
