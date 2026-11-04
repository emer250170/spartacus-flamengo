import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CmsConfig, I18nModule, provideConfig } from '@spartacus/core';
import { StoreModule as PickupStoreModule } from '@spartacus/pickup-in-store/components';
import { OrderOutlets } from '@spartacus/order/root';
import {
  CardModule,
  IconModule,
  OutletPosition,
  provideOutlet,
} from '@spartacus/storefront';
import { FlaPickupConsignmentMarkComponent } from './fla-pickup-consignment-mark.component';
import { FlaMyPreferredStoreComponent } from './fla-my-preferred-store.component';

/**
 * Retirada na loja (protótipo Flamengo): "Minha loja" com os textos do
 * cartão traduzidos. A configuração do app tem prioridade sobre a do módulo
 * de retirada na loja (carregado sob demanda). Nos detalhes do pedido, marca as
 * consignações de retirada (concluída ou a retirar) para o tema não repetir
 * "Itens para retirar" e "Retirada concluída".
 */
@NgModule({
  imports: [
    CommonModule,
    I18nModule,
    CardModule,
    IconModule,
    PickupStoreModule,
  ],
  declarations: [
    FlaMyPreferredStoreComponent,
    FlaPickupConsignmentMarkComponent,
  ],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MyPreferredStoreComponent: { component: FlaMyPreferredStoreComponent },
      },
    }),
    provideOutlet({
      id: OrderOutlets.ORDER_CONSIGNMENT,
      position: OutletPosition.BEFORE,
      component: FlaPickupConsignmentMarkComponent,
    }),
  ],
})
export class FlaPickupModule {}
