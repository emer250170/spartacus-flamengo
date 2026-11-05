import { Injectable, inject } from '@angular/core';
import { UrlTree } from '@angular/router';
import { CheckoutStepsSetGuard } from '@spartacus/checkout/base/components';
import { CheckoutStep } from '@spartacus/checkout/base/root';
import { Observable, of } from 'rxjs';
import { filter, switchMap, take } from 'rxjs/operators';
import { SocioPaymentService } from '../../../socio/socio-payment.service';

/**
 * Plano em Pix ou boleto (item 7) e cartão pela Adyen (05/11, o cartão é
 * informado na revisão): o passo de pagamento conta como feito sem cartão salvo,
 * e a revisão abre. Nos demais casos, a regra do Spartacus.
 */
@Injectable({ providedIn: 'root' })
export class FlaCheckoutStepsSetGuard extends CheckoutStepsSetGuard {
  protected socioPayment = inject(SocioPaymentService);

  protected override isPaymentDetailsSet(
    step: CheckoutStep
  ): Observable<boolean | UrlTree> {
    return this.activeCartFacade.getActiveCartId().pipe(
      filter((id): id is string => !!id),
      take(1),
      switchMap((id) => this.socioPayment.options(id)),
      switchMap((p) =>
        (p.planCode && this.socioPayment.isUpfront(p.method)) ||
        this.socioPayment.isAdyenCard(p)
          ? of(true)
          : super.isPaymentDetailsSet(step)
      )
    );
  }
}
