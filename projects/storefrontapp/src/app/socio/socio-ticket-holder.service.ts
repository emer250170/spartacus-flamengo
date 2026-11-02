import { Injectable } from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { AuthService } from '@spartacus/core';
import { Observable, combineLatest, of } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import { SocioGuestsService } from './socio-guests.service';

/**
 * Nome do convidado de cada poltrona (código da poltrona -> nome), lido de
 * GET /socioguests (ingressos dos convidados no carrinho e nos pedidos de jogos
 * futuros). Uma leitura para a página toda, refeita quando o carrinho muda.
 */
@Injectable({ providedIn: 'root' })
export class SocioTicketHolderService {
  readonly holders$: Observable<Map<string, string>> = combineLatest([
    this.auth.isUserLoggedIn(),
    this.activeCart.getActive().pipe(
      map((cart) => `${cart?.code ?? ''}:${cart?.entries?.length ?? 0}`),
      distinctUntilChanged()
    ),
  ]).pipe(
    debounceTime(300),
    switchMap(([loggedIn]) =>
      loggedIn
        ? this.guests.list().pipe(
            map(
              (list) =>
                new Map(
                  (list.tickets ?? []).map((t) => [t.seatCode, t.holder])
                )
            ),
            catchError(() => of(new Map<string, string>()))
          )
        : of(new Map<string, string>())
    ),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  constructor(
    protected auth: AuthService,
    protected activeCart: ActiveCartFacade,
    protected guests: SocioGuestsService
  ) {}

  holderOf(seatCode: string | undefined): Observable<string | undefined> {
    return seatCode
      ? this.holders$.pipe(
          map((holders) => holders.get(seatCode)),
          distinctUntilChanged()
        )
      : of(undefined);
  }
}
