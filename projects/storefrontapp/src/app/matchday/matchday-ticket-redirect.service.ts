import { Injectable } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import {
  TICKET_MODE_PARAM,
  TICKET_MODE_VALUE,
} from './matchday-journey.component';

/**
 * Compra só de ingresso pelo stepper (pedido do Emerson, 04/11): a compra começa na lista
 * "Jogos em casa" (botão "Escolher poltrona", MatchdayTicketActionComponent) ou na vitrine
 * da home; a página de um jogo ou de uma poltrona (JOGO-AAAAMMDD-ADV...) vai para a jornada
 * no modo ingresso (/matchday?modo=ingresso&jogo=...: Setor e poltrona > Estacionamento >
 * Comidas e bebidas > Camisa > Revisão) e, na poltrona, abre o setor dela.
 * Desligar: FLA_TICKET_REDIRECT = false (a página padrão do produto volta a valer).
 */
export const FLA_TICKET_REDIRECT = true;

const PRODUCT =
  /\/product\/(JOGO-\d{8}-[A-Za-z0-9]+)((?:-[A-Za-z0-9]+)*)(?:[/?#]|$)/;
const CODE = /^(JOGO-\d{8}-[A-Za-z0-9]+)((?:-[A-Za-z0-9]+)*)$/;

/**
 * Parâmetros da jornada (modo ingresso) para um jogo (JOGO-AAAAMMDD-ADV) ou uma poltrona
 * dele; null quando o código não é de ingresso.
 */
export function ticketQueryParams(
  code: string | undefined
): Record<string, string> | null {
  const m = CODE.exec(code ?? '');
  if (!m) {
    return null;
  }
  const params: Record<string, string> = {
    [TICKET_MODE_PARAM]: TICKET_MODE_VALUE,
    jogo: m[1],
  };
  if (m[2]) {
    params['poltrona'] = m[1] + m[2];
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class MatchdayTicketRedirectService {
  protected started = false;

  constructor(protected router: Router) {}

  start(): void {
    if (this.started || !FLA_TICKET_REDIRECT) {
      return;
    }
    this.started = true;
    this.router.events
      .pipe(filter((e): e is NavigationStart => e instanceof NavigationStart))
      .subscribe((e) => {
        const queryParams = this.target(e.url);
        if (queryParams) {
          // depois do evento: a navegação nova substitui a que estava começando
          Promise.resolve().then(() =>
            this.router.navigate(['/matchday'], {
              queryParams,
              replaceUrl: true,
            })
          );
        }
      });
  }

  /** Parâmetros da jornada para a URL, ou null quando a URL não é de ingresso */
  target(url: string): Record<string, string> | null {
    const m = PRODUCT.exec(url);
    return m ? ticketQueryParams(m[1] + m[2]) : null;
  }
}
