import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/** Convidado nominal do sócio (API /users/current/socioguests do servidor) */
export interface SocioGuest {
  id: string;
  name: string;
  /** 123.456.789-09 */
  cpf: string;
  email?: string;
  phone?: string;
  /** aaaa-mm-dd */
  birthDate?: string;
  active: boolean;
  /** ISO com fuso */
  since?: string;
  until?: string;
  endReason?: string;
}

/** Situação dos convidados do titular */
export interface SocioGuestList {
  /** Chave flamengo.socio.convidados.ativo */
  enabled: boolean;
  planCode?: string;
  planName?: string;
  /** Máximo de convidados do plano */
  planMaxGuests: number;
  /** Vagas contratadas no pedido do plano */
  contracted: number;
  used: number;
  /** Mensalidade de cada vaga (já cobrada no plano) */
  guestPrice: number;
  suspended: boolean;
  canAdd: boolean;
  /** Motivo do servidor quando não pode incluir (português) */
  reason?: string;
  guests: SocioGuest[];
  ended: SocioGuest[];
  /** Ingressos dos convidados para jogos ainda não realizados */
  tickets?: SocioGuestTicket[];
}

/** Ingresso de convidado (poltrona do titular marcada para ele) */
export interface SocioGuestTicket {
  guestId: string;
  holder: string;
  seatCode: string;
  seatName?: string;
  gameCode: string;
  gameName?: string;
  /** ISO com fuso */
  kickoff?: string;
  /** Pedido (quando já foi comprado) */
  orderCode?: string;
  /** Carrinho (quando ainda não foi comprado) */
  cartId?: string;
}

export interface NewSocioGuest {
  name: string;
  cpf: string;
  email: string;
  phone?: string;
  /** aaaa-mm-dd */
  birthDate: string;
}

/**
 * Meus convidados: lista, inclui e tira convidados pela API de comércio
 * (SocioGuestsController na extensão flamengomatchday). Recusas do servidor
 * voltam como erro com a mensagem em português (errors[0].message).
 */
@Injectable({ providedIn: 'root' })
export class SocioGuestsService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  protected get url(): string {
    return `${this.occEndpoints.getBaseUrl()}/users/current/socioguests`;
  }

  list(): Observable<SocioGuestList> {
    return this.http
      .get<SocioGuestList>(this.url)
      .pipe(catchError((e) => this.fail(e)));
  }

  add(guest: NewSocioGuest): Observable<SocioGuestList> {
    return this.http
      .post<SocioGuestList>(this.url, guest)
      .pipe(catchError((e) => this.fail(e)));
  }

  /** Marca a poltrona do carrinho para o convidado (guestId vazio = o titular) */
  assignTicket(ticket: {
    cartId: string;
    seatCode: string;
    guestId?: string;
  }): Observable<SocioGuestList> {
    return this.http
      .put<SocioGuestList>(`${this.url}/tickets`, ticket)
      .pipe(catchError((e) => this.fail(e)));
  }

  remove(id: string): Observable<SocioGuestList> {
    return this.http
      .delete<SocioGuestList>(`${this.url}/${encodeURIComponent(id)}`)
      .pipe(catchError((e) => this.fail(e)));
  }

  /** Erro com a mensagem do servidor (ou vazio, para a tela usar o texto padrão) */
  protected fail(e: HttpErrorResponse): Observable<never> {
    const message: string = e?.error?.errors?.[0]?.message ?? '';
    return throwError(() => new Error(message));
  }
}

/** CPF com 11 dígitos e dígitos verificadores corretos (mesma regra do servidor) */
export function isValidCpf(value: string): boolean {
  const d = (value ?? '').replace(/\D/g, '');
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) {
    return false;
  }
  for (const check of [9, 10]) {
    let sum = 0;
    for (let i = 0; i < check; i++) {
      sum += Number(d[i]) * (check + 1 - i);
    }
    if (((sum * 10) % 11) % 10 !== Number(d[check])) {
      return false;
    }
  }
  return true;
}

/** 12345678909 -> 123.456.789-09, enquanto digita */
export function maskCpf(value: string): string {
  const d = (value ?? '').replace(/\D/g, '').slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2');
}

/** 21999990000 -> (21) 99999-0000, enquanto digita */
export function maskPhone(value: string): string {
  const d = (value ?? '').replace(/\D/g, '').slice(0, 11);
  if (d.length <= 2) {
    return d;
  }
  const rest = d.slice(2);
  const split = rest.length > 8 ? 5 : 4;
  return rest.length > split
    ? `(${d.slice(0, 2)}) ${rest.slice(0, split)}-${rest.slice(split)}`
    : `(${d.slice(0, 2)}) ${rest}`;
}

/**
 * Validações simples do cadastro do convidado (as mesmas do servidor), para
 * avisar antes de enviar. Devolve a chave do texto do erro (fla-texts) ou ''.
 */
export function guestFormErrorKey(f: NewSocioGuest, today: string): string {
  const name = (f.name ?? '').trim().replace(/\s+/g, ' ');
  if (name.length < 3 || !name.includes(' ')) {
    return 'sg.err.name';
  }
  if (!isValidCpf(f.cpf)) {
    return 'sg.err.cpf';
  }
  if (!f.birthDate || f.birthDate > today) {
    return 'sg.err.birth';
  }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test((f.email ?? '').trim())) {
    return 'sg.err.email';
  }
  const phone = (f.phone ?? '').replace(/\D/g, '');
  if (phone && (phone.length < 10 || phone.length > 11)) {
    return 'sg.err.phone';
  }
  return '';
}
