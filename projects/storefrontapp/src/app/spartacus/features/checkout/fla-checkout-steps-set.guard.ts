import { Injectable, inject } from '@angular/core';
import { UrlTree } from '@angular/router';
import { CheckoutStepsSetGuard } from '@spartacus/checkout/base/components';
import { CheckoutStep } from '@spartacus/checkout/base/root';
import { Observable, of } from 'rxjs';
import { filter, switchMap, take } from 'rxjs/operators';
import { SocioPaymentService } from '../../../socio/socio-payment.service';

/**
 * Plano em Pix ou boleto (item 7): o passo de pagamento conta como feito sem
 * cartão, e a revisão abre. Nos demais casos, a regra do Spartacus.
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
        p.planCode && this.socioPayment.isUpfront(p.method)
          ? of(true)
          : super.isPaymentDetailsSet(step)
      )
    );
  }
}
