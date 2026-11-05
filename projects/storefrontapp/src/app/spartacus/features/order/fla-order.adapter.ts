import { Injectable, inject } from '@angular/core';
import {
  GlobalMessageService,
  GlobalMessageType,
  OCC_USER_ID_ANONYMOUS,
  Occ,
} from '@spartacus/core';
import { OccOrderAdapter } from '@spartacus/order/occ';
import { ORDER_NORMALIZER, Order } from '@spartacus/order/root';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { SocioPaymentService } from '../../../socio/socio-payment.service';

/**
 * Finalizar pedido com o plano em Pix ou boleto (item 7 do plano de evolução):
 * o pedido sai pelo endpoint sociopayment/order (sem cartão) e volta como o
 * pedido completo da API, para a confirmação do Spartacus seguir igual. Item 14:
 * vale também para o carrinho sem plano (o servidor só devolve Pix/boleto como
 * forma escolhida com a operação do pedido da loja ligada). Com cartão, o caminho
 * padrão.
 */
@Injectable()
export class FlaOrderAdapter extends OccOrderAdapter {
  protected socioPayment = inject(SocioPaymentService);
  protected globalMessages = inject(GlobalMessageService);

  public override placeOrder(
    userId: string,
    cartId: string,
    termsChecked: boolean
  ): Observable<Order> {
    if (userId === OCC_USER_ID_ANONYMOUS) {
      return super.placeOrder(userId, cartId, termsChecked);
    }
    return this.socioPayment.options(cartId).pipe(
      switchMap((p) =>
        p.enabled && this.socioPayment.isUpfront(p.method)
          ? this.socioPayment.placeOrder(cartId).pipe(
              // recusa do servidor (ex.: Termo não aceito): mostra o motivo. A recusa 400 o
              // tratamento de erros do Spartacus já mostra (05/11: aparecia duas vezes)
              catchError((e: Error & { status?: number }) => {
                if (e.status !== 400) {
                  this.globalMessages.add(
                    e.message,
                    GlobalMessageType.MSG_TYPE_ERROR
                  );
                }
                return throwError(() => e);
              }),
              switchMap((placed) =>
                this.http
                  .get<Occ.Order>(
                    this.occEndpoints.buildUrl('orderDetail', {
                      urlParams: { userId, orderId: placed.orderCode ?? '' },
                    })
                  )
                  .pipe(this.converter.pipeable(ORDER_NORMALIZER))
              )
            )
          : super.placeOrder(userId, cartId, termsChecked)
      )
    );
  }
}
