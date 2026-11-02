import { Injectable, inject } from '@angular/core';
import { OCC_USER_ID_ANONYMOUS, Occ } from '@spartacus/core';
import { OccOrderAdapter } from '@spartacus/order/occ';
import { ORDER_NORMALIZER, Order } from '@spartacus/order/root';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { SocioPaymentService } from '../../../socio/socio-payment.service';

/**
 * Finalizar pedido com o plano em Pix ou boleto (item 7 do plano de evolução):
 * o pedido sai pelo endpoint sociopayment/order (sem cartão) e volta como o
 * pedido completo da API, para a confirmação do Spartacus seguir igual. Com
 * cartão (ou sem plano no carrinho), o caminho padrão.
 */
@Injectable()
export class FlaOrderAdapter extends OccOrderAdapter {
  protected socioPayment = inject(SocioPaymentService);

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
        p.planCode && this.socioPayment.isUpfront(p.method)
          ? this.socioPayment.placeOrder(cartId).pipe(
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
