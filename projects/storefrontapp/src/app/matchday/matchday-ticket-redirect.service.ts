import { Injectable } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import {
  TICKET_MODE_PARAM,
  TICKET_MODE_VALUE,
} from './matchday-journey.component';

/**
 * Compra só de ingresso pelo stepper (pedido do Emerson, 04/11): em vez da lista
 * "Jogos em casa" e da página da poltrona (listas Setor e Poltrona), o torcedor vai para
 * a jornada no modo ingresso (/matchday?modo=ingresso: Jogo > Setor e poltrona > Revisão).
 * - categoria Ingressos ou Jogos em casa: abre a escolha do jogo;
 * - página de um jogo ou de uma poltrona (JOGO-AAAAMMDD-ADV...): abre o jogo e, na
 *   poltrona, o setor dela.
 * Desligar: FLA_TICKET_REDIRECT = false (as telas padrão voltam a valer).
 */
export const FLA_TICKET_REDIRECT = true;

const CATEGORY = /\/c\/(flamengo_jogos_casa|flamengo_ingressos)(?:[/?#]|$)/;
const PRODUCT =
  /\/product\/(JOGO-\d{8}-[A-Za-z0-9]+)((?:-[A-Za-z0-9]+)*)(?:[/?#]|$)/;

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
    if (CATEGORY.test(url)) {
      return { [TICKET_MODE_PARAM]: TICKET_MODE_VALUE };
    }
    const m = PRODUCT.exec(url);
    if (m) {
      const params: Record<string, string> = {
        [TICKET_MODE_PARAM]: TICKET_MODE_VALUE,
        jogo: m[1],
      };
      if (m[2]) {
        params['poltrona'] = m[1] + m[2];
      }
      return params;
    }
    return null;
  }
}
