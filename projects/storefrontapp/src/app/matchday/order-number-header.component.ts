import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RoutingService } from '@spartacus/core';
import { Observable } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';

/**
 * Detalhe do pedido (Minha conta › Pedidos › pedido): "Número: 00025001" logo abaixo do título
 * "Detalhes do pedido", no mesmo cabeçalho cinza, como na confirmação do pedido (pedido do
 * Emerson, 04/11). Entra pelo outlet do componente de breadcrumb e só aparece na rota
 * orderDetails.
 */
@Component({
  selector: 'fla-order-number-header',
  template: `
    <div class="fla-order-number" *ngIf="code$ | async as code">
      {{ 'od.number' | flaT: { code: code } }}
    </div>
  `,
  styles: [
    `
      /* sem caixa própria: vazio (fora do detalhe do pedido) não ocupa espaço na barra do
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
  readonly code$: Observable<string | undefined> = this.routing
    .getRouterState()
    .pipe(
      map((router) =>
        router?.state?.semanticRoute === 'orderDetails'
          ? (router.state.params?.['orderCode'] as string | undefined)
          : undefined
      ),
      distinctUntilChanged()
    );

  constructor(protected routing: RoutingService) {}
}
