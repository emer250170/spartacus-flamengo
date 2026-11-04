/*
 * SPDX-FileCopyrightText: 2024 SAP Spartacus team <spartacus-team@sap.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { NgModule, Type } from '@angular/core';
import { OmfOrderModule } from '@spartacus/omf/order';
import { OpfOrderModule } from '@spartacus/opf/order';
import { OrderModule } from '@spartacus/order';
import { MyAccountV2OrderConsignmentsService } from '@spartacus/order/components';
import { OrderAdapter } from '@spartacus/order/core';
import { S4ServiceOrderModule } from '@spartacus/s4-service/order';
import { environment } from '../../../../environments/environment';
import { FlaOrderAdapter } from './fla-order.adapter';
import { FlaOrderConsignmentsService } from './fla-order-consignments.service';

const extensions: Type<any>[] = [];
if (environment.s4Service) {
  extensions.push(S4ServiceOrderModule);
}
if (environment.omf) {
  extensions.push(OmfOrderModule);
}
if (environment.opf) {
  extensions.push(OpfOrderModule);
}
@NgModule({
  imports: [OrderModule, ...extensions],
  providers: [
    // protótipo Flamengo: plano em Pix ou boleto fecha o pedido sem cartão (item 7)
    { provide: OrderAdapter, useClass: FlaOrderAdapter },
    // item 14: remessa cancelada mostra só o que saiu do pedido (cancelamento de uma linha)
    {
      provide: MyAccountV2OrderConsignmentsService,
      useClass: FlaOrderConsignmentsService,
    },
  ],
})
export class OrderWrapperModule {}
