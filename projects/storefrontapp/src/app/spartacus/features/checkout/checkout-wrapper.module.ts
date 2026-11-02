/*
 * SPDX-FileCopyrightText: 2024 SAP Spartacus team <spartacus-team@sap.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { NgModule, Type } from '@angular/core';
import { CheckoutB2BModule } from '@spartacus/checkout/b2b';
import { CheckoutModule } from '@spartacus/checkout/base';
import { CheckoutStepsSetGuard } from '@spartacus/checkout/base/components';
import { CheckoutPaymentAdapter } from '@spartacus/checkout/base/core';
import { CheckoutScheduledReplenishmentModule } from '@spartacus/checkout/scheduled-replenishment';
import { DigitalPaymentsModule } from '@spartacus/digital-payments';

import { S4ServiceCheckoutModule } from '@spartacus/s4-service/checkout';
import { environment } from '../../../../environments/environment';
import { FlaCheckoutStepsSetGuard } from './fla-checkout-steps-set.guard';
import { FlaDirectPaymentAdapter } from './fla-direct-payment.adapter';

const extensions: Type<any>[] = [];

if (environment.b2b) {
  extensions.push(CheckoutB2BModule, CheckoutScheduledReplenishmentModule);
}

if (environment.digitalPayments) {
  extensions.push(DigitalPaymentsModule);
}

if (environment.s4Service) {
  extensions.push(S4ServiceCheckoutModule);
}

@NgModule({
  imports: [CheckoutModule, ...extensions],
  providers: [
    // protótipo Flamengo: cartão cadastrado direto na API (ver FlaDirectPaymentAdapter)
    { provide: CheckoutPaymentAdapter, useClass: FlaDirectPaymentAdapter },
    // plano em Pix ou boleto: o pagamento conta como feito sem cartão (item 7)
    { provide: CheckoutStepsSetGuard, useExisting: FlaCheckoutStepsSetGuard },
  ],
})
export class CheckoutWrapperModule {}
