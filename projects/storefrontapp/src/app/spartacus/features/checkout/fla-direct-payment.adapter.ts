import { Injectable } from '@angular/core';
import { PAYMENT_DETAILS_SERIALIZER } from '@spartacus/checkout/base/core';
import { OccCheckoutPaymentAdapter } from '@spartacus/checkout/base/occ';
import {
  Occ,
  PAYMENT_DETAILS_NORMALIZER,
  PaymentDetails,
  backOff,
  isJaloError,
  normalizeHttpError,
} from '@spartacus/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Cadastro do cartão direto na API (POST .../carts/{cartId}/paymentdetails).
 *
 * O padrão do Spartacus (SOP) envia o formulário do cartão para o endereço do
 * provedor de pagamento que o servidor devolve; no protótipo esse endereço é o
 * do serviço de teste ("Mockup"), montado a partir de website.electronics-spa
 * (localhost:4200), e não é acessível pelo navegador — a compra parava na etapa
 * de pagamento. Com o provedor de teste, a própria API cria a assinatura do
 * cartão, então o resultado é o mesmo, sem depender desse endereço.
 *
 * O cartão fica salvo na conta (reaproveitado nos próximos pedidos).
 */
@Injectable()
export class FlaDirectPaymentAdapter extends OccCheckoutPaymentAdapter {
  public override createPaymentDetails(
    userId: string,
    cartId: string,
    paymentDetails: PaymentDetails
  ): Observable<PaymentDetails> {
    const body = this.converter.convert(
      { ...paymentDetails, saved: true },
      PAYMENT_DETAILS_SERIALIZER
    );
    const url = this.occEndpoints.buildUrl('setCartPaymentDetails', {
      urlParams: { userId, cartId },
    });
    return this.http.post<Occ.PaymentDetails>(url, body).pipe(
      catchError((error) =>
        throwError(() => normalizeHttpError(error, this.logger))
      ),
      backOff({ shouldRetry: isJaloError }),
      this.converter.pipeable(PAYMENT_DETAILS_NORMALIZER)
    );
  }
}
