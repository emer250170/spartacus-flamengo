import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, UrlTree } from '@angular/router';
import { CheckoutStepsSetGuard } from '@spartacus/checkout/base/components';
import { CheckoutStep, CheckoutStepType } from '@spartacus/checkout/base/root';
import { Observable, of } from 'rxjs';
import { filter, switchMap, take } from 'rxjs/operators';
import { SocioPaymentService } from '../../../socio/socio-payment.service';

/**
 * Plano em Pix ou boleto (item 7) e cartão pela Adyen (05/11, o cartão é
 * informado na revisão): o passo de pagamento conta como feito sem cartão salvo,
 * e a revisão abre. Nos demais casos, a regra do Spartacus.
 *
 * Retirada na loja (06/11): com o carrinho todo para retirada o Spartacus desliga o
 * passo de endereço e manda /checkout/delivery-address para o pagamento; a tela de
 * Entrega (FlaCheckoutDeliveryComponent) fica nessa rota, então ela continua
 * acessível para o torcedor trocar a loja ou voltar para "Receber em casa".
 */
@Injectable({ providedIn: 'root' })
export class FlaCheckoutStepsSetGuard extends CheckoutStepsSetGuard {
  protected socioPayment = inject(SocioPaymentService);

  override canActivate(route: ActivatedRouteSnapshot): Observable<boolean | UrlTree> {
    const path = this.routingConfigService.getRouteConfig('checkoutDeliveryAddress')?.paths?.[0];
    const step = this.checkoutStepService.getCheckoutStep(CheckoutStepType.DELIVERY_ADDRESS);
    if (path && '/' + route.url.join('/') === '/' + path && step?.disabled) {
      return of(true);
    }
    return super.canActivate(route);
  }

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
