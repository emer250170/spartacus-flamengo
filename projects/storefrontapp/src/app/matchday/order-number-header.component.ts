import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RoutingService } from '@spartacus/core';
import { OrderReturnRequestFacade } from '@spartacus/order/root';
import { Observable, of } from 'rxjs';
import { distinctUntilChanged, map, switchMap } from 'rxjs/operators';

interface HeaderLine {
  key: string;
  code: string;
}

/**
 * Linha abaixo do título, no mesmo cabeçalho cinza, como na confirmação do pedido (pedidos do
 * Emerson, 04/11). Entra pelo outlet do componente de breadcrumb:
 * - detalhe do pedido (orderDetails): "Número: 00025001";
 * - detalhe da devolução (returnRequestDetails): "Pedido nº 00024010", o pedido de origem,
 *   lido da devolução que a própria tela já carregou (só usa o que bate com o código da rota).
 * Nas outras páginas não aparece nada.
 */
@Component({
  selector: 'fla-order-number-header',
  template: `
    <div class="fla-order-number" *ngIf="line$ | async as line">
      {{ line.key | flaT: { code: line.code } }}
    </div>
  `,
  styles: [
    `
      /* sem caixa própria: vazio (fora dessas telas) não ocupa espaço na barra do
         breadcrumb, que é uma linha flexível sem quebra */
      :host {
        display: contents;
      }
      .fla-order-number {
        flex: 0 0 100%;
        background: rgb(244, 244, 244);
        text-align: center;
        font-family: 'Zalando Sans Expanded', 'Archivo Narrow', sans-serif;
        font-size: 1.125rem;
        font-weight: 600;
        color: var(--cx-color-text, #1d2d3e);
        padding: 0 1rem 1.5rem;
        margin-top: -0.5rem;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderNumberHeaderComponent {
  readonly line$: Observable<HeaderLine | undefined> = this.routing
    .getRouterState()
    .pipe(
      map((router) => ({
        route: router?.state?.semanticRoute,
        params: router?.state?.params ?? {},
      })),
      distinctUntilChanged(
        (a, b) =>
          a.route === b.route &&
          a.params['orderCode'] === b.params['orderCode'] &&
          a.params['returnCode'] === b.params['returnCode']
      ),
      switchMap(({ route, params }) => {
        if (route === 'orderDetails' && params['orderCode']) {
          return of({ key: 'od.number', code: params['orderCode'] as string });
        }
        if (route === 'returnRequestDetails' && params['returnCode']) {
          return this.returnRequests
            .getOrderReturnRequest()
            .pipe(
              map((request) =>
                request?.rma === params['returnCode'] && request?.order?.code
                  ? { key: 'rr.order', code: request.order.code }
                  : undefined
              )
            );
        }
        return of(undefined);
      }),
      distinctUntilChanged((a, b) => a?.key === b?.key && a?.code === b?.code)
    );

  constructor(
    protected routing: RoutingService,
    protected returnRequests: OrderReturnRequestFacade
  ) {}
}
