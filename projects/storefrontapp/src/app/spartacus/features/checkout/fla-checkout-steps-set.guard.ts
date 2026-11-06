import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, UrlTree } from '@angular/router';
import { CheckoutStepsSetGuard } from '@spartacus/checkout/base/components';
import { CheckoutStep, CheckoutStepType } from '@spartacus/checkout/base/root';
import { Observable, combineLatest, of } from 'rxjs';
import { filter, map, switchMap, take } from 'rxjs/operators';
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
 *
 * Forma de entrega que deixou de valer (06/11): a forma gravada no carrinho só conta como feita
 * se ainda estiver entre as permitidas (ex.: "retirada no estádio" de antes, num carrinho que
 * agora só tem o manto para receber em casa); senão o torcedor volta para escolher o frete. Vale
 * também para a revisão aberta direto pelo endereço.
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

  protected override isDeliveryModeSet(step: CheckoutStep): Observable<boolean | UrlTree> {
    return combineLatest([
      this.checkoutDeliveryModesFacade.getSelectedDeliveryModeState().pipe(filter((st) => !st.loading)),
      this.checkoutDeliveryModesFacade.getSupportedDeliveryModesState().pipe(filter((st) => !st.loading)),
    ]).pipe(
      take(1),
      map(([selected, supported]) => {
        const code = selected.data?.code;
        const modes = supported.data ?? [];
        if (!code) {
          return this.getUrl(step.routeName);
        }
        // retirada na loja não vem na lista; sem lista (erro), a regra do Spartacus
        return code === 'pickup' || !modes.length || modes.some((m) => m.code === code)
          ? true
          : this.getUrl(step.routeName);
      })
    );
  }

  /**
   * Revisão (passo depois do pagamento): o Spartacus só confere o passo anterior; aqui a forma de
   * entrega também é conferida antes (06/11), para quem digita /checkout/review-order direto com uma
   * forma que deixou de valer voltar para escolher o frete.
   */
  protected override isPaymentDetailsSet(
    step: CheckoutStep
  ): Observable<boolean | UrlTree> {
    const modeStep = this.checkoutStepService.getCheckoutStep(CheckoutStepType.DELIVERY_MODE);
    if (!modeStep || modeStep.disabled) {
      return this.paymentDetailsSet(step);
    }
    return this.isDeliveryModeSet(modeStep).pipe(
      take(1),
      switchMap((ok) => (ok === true ? this.paymentDetailsSet(step) : of(ok)))
    );
  }

  protected paymentDetailsSet(
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
