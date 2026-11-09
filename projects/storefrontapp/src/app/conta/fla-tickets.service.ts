import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { FlaMediaUrlService } from '../vitrine/fla-media-url.service';

/** Situação do ingresso no SAP Commerce (MatchdayTicketStatus) */
export type TicketStatus =
  | 'VALIDO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'UTILIZADO'
  | 'CANCELADO';

export interface TicketGame {
  code: string;
  opponent: string;
  kickoff?: Date;
  competition?: string;
  venue?: string;
  imageUrl?: string;
}

export interface TicketEvent {
  /** VENDA, TROCA_POLTRONA, TROCA_TITULAR, CANCELAMENTO, UTILIZACAO */
  type: string;
  date?: Date;
  from?: string;
  to?: string;
  amount?: number;
}

export interface MyTicket {
  code: string;
  status: TicketStatus;
  orderCode?: string;
  sector?: string;
  seatLabel?: string;
  holder?: string;
  holderIsBuyer: boolean;
  guest: boolean;
  /** ***.456.789-** */
  holderCpf?: string;
  holderEmail?: string;
  price?: number;
  memberDiscount?: number;
  soldAt?: Date;
  cancelledAt?: Date;
  refundAmount?: number;
  usedAt?: Date;
  game?: TicketGame;
  history: TicketEvent[];
}

export interface MyTickets {
  enabled: boolean;
  /** Hora do servidor, para separar próximos jogos dos que já aconteceram */
  now: Date;
  tickets: MyTicket[];
}

interface OccTickets {
  enabled?: boolean;
  now?: string;
  tickets?: (Omit<
    MyTicket,
    'soldAt' | 'cancelledAt' | 'usedAt' | 'game' | 'history'
  > & {
    soldAt?: string;
    cancelledAt?: string;
    usedAt?: string;
    game?: Omit<TicketGame, 'kickoff'> & { kickoff?: string };
    history?: (Omit<TicketEvent, 'date'> & { date?: string })[];
  })[];
}

/**
 * Meus ingressos (Minha conta, 09/11): GET /users/current/matchdaytickets, os ingressos que o
 * torcedor comprou, um por poltrona. Só leitura. Erro ou chave desligada: lista vazia.
 */
@Injectable({ providedIn: 'root' })
export class FlaTicketsService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected media: FlaMediaUrlService
  ) {}

  load(): Observable<MyTickets> {
    const url = `${this.occEndpoints.getBaseUrl()}/users/current/matchdaytickets`;
    return this.http.get<OccTickets>(url).pipe(
      map((r) => this.convert(r)),
      catchError(() => of({ enabled: false, now: new Date(), tickets: [] }))
    );
  }

  protected convert(r: OccTickets): MyTickets {
    const date = (v?: string) => (v ? new Date(v) : undefined);
    return {
      enabled: !!r?.enabled,
      now: date(r?.now) ?? new Date(),
      tickets: (r?.tickets ?? []).map((t) => ({
        ...t,
        code: t.code ?? '',
        status: (t.status ?? 'VALIDO') as TicketStatus,
        holderIsBuyer: !!t.holderIsBuyer,
        guest: !!t.guest,
        holderCpf: t.holderCpf || undefined,
        holderEmail: t.holderEmail || undefined,
        memberDiscount: t.memberDiscount ?? undefined,
        refundAmount: t.refundAmount ?? undefined,
        soldAt: date(t.soldAt),
        cancelledAt: date(t.cancelledAt),
        usedAt: date(t.usedAt),
        game: t.game
          ? {
              ...t.game,
              kickoff: date(t.game.kickoff),
              imageUrl: this.media.url(t.game.imageUrl),
            }
          : undefined,
        history: (t.history ?? []).map((e) => ({
          ...e,
          from: e.from || undefined,
          to: e.to || undefined,
          amount: e.amount ?? undefined,
          date: date(e.date),
        })),
      })),
    };
  }
}
