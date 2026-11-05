import { ChangeDetectionStrategy, Component } from '@angular/core';
import { OrderHistoryFacade } from '@spartacus/order/root';
import { Observable, of } from 'rxjs';
import { distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { SocioPayment, SocioPaymentService } from './socio-payment.service';

/**
 * Minha conta › Pedidos › detalhe (Adyen, 05/11): o Pix ou boleto do pedido
 * continua disponível até o pagamento (QR Code, copia e cola, linha digitável,
 * PDF do boleto) e, depois, "Pagamento confirmado". Mostra o mesmo quadro da
 * confirmação do pedido; pedido com cartão não mostra nada.
 */
@Component({
  selector: 'fla-socio-order-payment',
  template: `
    <ng-container *ngIf="payment$ | async as p">
      <fla-socio-payment-status
        *ngIf="p.orderCode && p.method"
        [payment]="p"
      ></fla-socio-payment-status>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioOrderPaymentComponent {
  payment$: Observable<SocioPayment | undefined> = this.orders
    .getOrderDetails()
    .pipe(
      map((order) => order?.code),
      distinctUntilChanged(),
      switchMap((code) =>
        code ? this.socioPayment.orderStatus(code) : of(undefined)
      )
    );

  constructor(
    protected orders: OrderHistoryFacade,
    protected socioPayment: SocioPaymentService
  ) {}
}
