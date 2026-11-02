import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/** Simulação ou valor confirmado da saída do sócio (GET /users/current/socioexit) */
export interface SocioExit {
  planCode?: string;
  planName?: string;
  /** Início do contrato e data que vale para o cálculo (pedido de cancelamento ou hoje) */
  start?: Date;
  basis?: Date;
  withdrawalDays: number;
  withdrawalUntil?: Date;
  withinWithdrawal: boolean;
  installments: number;
  paidInstallments: number;
  remainingInstallments: number;
  /** Mensalidade com convidados */
  monthly: number;
  guests: number;
  /** Pago na adesão (plano + convidados) */
  paidAtJoin: number;
  finePercent: number;
  refund: number;
  fine: number;
  /** Uma linha em português (vai na mensagem do chamado) */
  summary?: string;
  /** Valor de cancelamento confirmado (ex-sócio) */
  confirmed: boolean;
  openTicket?: string;
  /** A saída foi o fim da vigência sem renovação (sem multa) */
  endOfTerm: boolean;
}

/** Regras de saída na área do sócio: simulação e o último cancelamento confirmado */
export interface SocioExitInfo {
  /** Chave flamengo.socio.saida.ativo */
  enabled: boolean;
  /** Simulação (só para quem é sócio) */
  quote: SocioExit | null;
  /** Último cancelamento confirmado (ex-sócio) */
  settlement: SocioExit | null;
}

const NONE: SocioExitInfo = { enabled: false, quote: null, settlement: null };

/**
 * Regras de saída do sócio (item 5 do plano de evolução): no prazo de
 * arrependimento o cancelamento devolve o que foi pago na adesão; depois dele há
 * multa sobre as mensalidades restantes. O cálculo é do servidor; com pedido de
 * cancelamento aberto, vale a data do pedido.
 */
@Injectable({ providedIn: 'root' })
export class SocioExitService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  load(): Observable<SocioExitInfo> {
    return this.http
      .get<OccSocioExit>(
        `${this.occEndpoints.getBaseUrl()}/users/current/socioexit`
      )
      .pipe(
        map((r) =>
          r?.enabled
            ? {
                enabled: true,
                quote: r.member ? toExit(r) : null,
                settlement: r.settlement ? toExit(r.settlement) : null,
              }
            : NONE
        ),
        catchError(() => of(NONE))
      );
  }
}

function toExit(r: OccSocioExit): SocioExit {
  const date = (v?: string) => (v ? new Date(v) : undefined);
  return {
    planCode: r.planCode,
    planName: r.planName,
    start: date(r.start),
    basis: date(r.basis),
    withdrawalDays: r.withdrawalDays ?? 0,
    withdrawalUntil: date(r.withdrawalUntil),
    withinWithdrawal: !!r.withinWithdrawal,
    installments: r.installments ?? 12,
    paidInstallments: r.paidInstallments ?? 0,
    remainingInstallments: r.remainingInstallments ?? 0,
    monthly: r.monthly ?? 0,
    guests: r.guests ?? 0,
    paidAtJoin: r.paidAtJoin ?? 0,
    finePercent: r.finePercent ?? 0,
    refund: r.refund ?? 0,
    fine: r.fine ?? 0,
    summary: r.summary,
    confirmed: !!r.confirmed,
    openTicket: r.openTicket,
    endOfTerm: !!r.endOfTerm,
  };
}

interface OccSocioExit {
  enabled?: boolean;
  member?: boolean;
  planCode?: string;
  planName?: string;
  start?: string;
  basis?: string;
  withdrawalDays?: number;
  withdrawalUntil?: string;
  withinWithdrawal?: boolean;
  installments?: number;
  paidInstallments?: number;
  remainingInstallments?: number;
  monthly?: number;
  guests?: number;
  paidAtJoin?: number;
  finePercent?: number;
  refund?: number;
  fine?: number;
  summary?: string;
  confirmed?: boolean;
  openTicket?: string;
  endOfTerm?: boolean;
  settlement?: OccSocioExit;
}
