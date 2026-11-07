import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { OccEndpointsService, RoutingService } from '@spartacus/core';
import { OrderFacade } from '@spartacus/order/root';
import { Observable, of, timer } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  switchMap,
  take,
  takeWhile,
} from 'rxjs/operators';

/**
 * Confirmação do pedido (07/11): a análise do ClearSale roda logo depois do pedido. Durante ~30 s a página
 * confere o status; se o pedido foi cancelado (recusado pela análise), mostra o aviso no topo: não foi
 * aprovado, nada foi cobrado e o Pix ou boleto não deve ser pago.
 */
@Component({
  selector: 'fla-order-refused',
  template: `
    <div class="fla-refused" role="alert" *ngIf="refused$ | async">
      <strong>{{ 'cs.refusedTitle' | flaT }}</strong>
      <p>{{ 'cs.refusedText' | flaT }}</p>
      <a routerLink="/contact">{{ 'cs.refusedContact' | flaT }}</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaOrderRefusedComponent {
  // só na confirmação do pedido (rota orderConfirmation); nas outras páginas, nada
  readonly refused$: Observable<boolean> = this.routing.getRouterState().pipe(
    map((s) => s?.state?.semanticRoute === 'orderConfirmation'),
    distinctUntilChanged(),
    switchMap((on) =>
      on
        ? this.orders.getOrderDetails().pipe(map((o) => o?.code))
        : of(undefined)
    ),
    distinctUntilChanged(),
    switchMap((code) =>
      !code
        ? of(false)
        : timer(1500, 3000).pipe(
            take(10),
            switchMap(() =>
              this.http
                .get<{
                  status?: string;
                }>(`${this.occEndpoints.getBaseUrl()}/users/current/orders/${encodeURIComponent(code)}?fields=status`)
                .pipe(catchError(() => of({} as { status?: string })))
            ),
            map((o) => String(o?.status ?? '').toUpperCase() === 'CANCELLED'),
            takeWhile((cancelled) => !cancelled, true)
          )
    )
  );

  constructor(
    protected routing: RoutingService,
    protected orders: OrderFacade,
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}
}
