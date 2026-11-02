import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/** Plano menor que o sócio pode agendar para a próxima vigência */
export interface SocioContractOption {
  code: string;
  name: string;
  monthlyPrice: number;
}

/** Vigência do sócio (GET /users/current/sociocontract) */
export interface SocioContract {
  /** Chave flamengo.socio.vigencia.ativo */
  enabled: boolean;
  member: boolean;
  planCode?: string;
  planName?: string;
  start?: Date;
  end?: Date;
  autoRenew: boolean;
  nextPlanCode?: string;
  nextPlanName?: string;
  /** Último dia para desligar a renovação ou agendar a mudança */
  changeDeadline?: Date;
  /** Prazo passou: travado até a virada */
  locked: boolean;
  /** Vigência atual veio de uma renovação */
  renewed: boolean;
  renewals: number;
  options: SocioContractOption[];
  /** Resultado da última mudança (POST) */
  message?: string;
}

const NONE: SocioContract = {
  enabled: false,
  member: false,
  autoRenew: true,
  locked: false,
  renewed: false,
  renewals: 0,
  options: [],
};

/**
 * Vigência do sócio (item 6 do plano de evolução): contrato de 12 meses com
 * renovação automática. O sócio desliga a renovação ou agenda a mudança para um
 * plano menor até o prazo; o servidor grava a escolha e a linha do tempo.
 */
@Injectable({ providedIn: 'root' })
export class SocioContractService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  protected get url(): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/sociocontract`;
  }

  /** Vigência atual; com a chave desligada ou erro: enabled = false (a tela segue como antes) */
  load(): Observable<SocioContract> {
    return this.http.get<OccSocioContract>(this.url).pipe(
      map((r) => (r?.enabled ? toContract(r) : NONE)),
      catchError(() => of(NONE))
    );
  }

  /** Liga ou desliga a renovação automática */
  setAutoRenew(autoRenew: boolean): Observable<SocioContract> {
    return this.post('autorenew', { autoRenew });
  }

  /** Agenda a mudança para um plano menor na renovação (null cancela) */
  setNextPlan(planCode: string | null): Observable<SocioContract> {
    return this.post('nextplan', { nextPlanCode: planCode ?? '' });
  }

  protected post(path: string, body: object): Observable<SocioContract> {
    return this.http.post<OccSocioContract>(`${this.url}/${path}`, body).pipe(
      map((r) => toContract(r)),
      catchError((e: HttpErrorResponse) =>
        throwError(() => new Error(e?.error?.errors?.[0]?.message ?? ''))
      )
    );
  }
}

function toContract(r: OccSocioContract): SocioContract {
  const date = (v?: string) => (v ? new Date(v) : undefined);
  return {
    enabled: !!r.enabled,
    member: !!r.member,
    planCode: r.planCode,
    planName: r.planName,
    start: date(r.start),
    end: date(r.end),
    autoRenew: r.autoRenew !== false,
    nextPlanCode: r.nextPlanCode || undefined,
    nextPlanName: r.nextPlanName || undefined,
    changeDeadline: date(r.changeDeadline),
    locked: !!r.locked,
    renewed: !!r.renewed,
    renewals: r.renewals ?? 0,
    options: (r.options ?? []).map((o) => ({
      code: o.code ?? '',
      name: o.name ?? o.code ?? '',
      monthlyPrice: o.monthlyPrice ?? 0,
    })),
    message: r.message,
  };
}

interface OccSocioContract {
  enabled?: boolean;
  member?: boolean;
  planCode?: string;
  planName?: string;
  start?: string;
  end?: string;
  autoRenew?: boolean;
  nextPlanCode?: string;
  nextPlanName?: string;
  changeDeadline?: string;
  locked?: boolean;
  renewed?: boolean;
  renewals?: number;
  origin?: string;
  options?: { code?: string; name?: string; monthlyPrice?: number }[];
  message?: string;
}
