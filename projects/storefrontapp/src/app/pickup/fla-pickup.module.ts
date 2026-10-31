import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CmsConfig, I18nModule, provideConfig } from '@spartacus/core';
import { StoreModule as PickupStoreModule } from '@spartacus/pickup-in-store/components';
import { CardModule, IconModule } from '@spartacus/storefront';
import { FlaMyPreferredStoreComponent } from './fla-my-preferred-store.component';

/**
 * Retirada na loja (protótipo Flamengo): "Minha loja" com os textos do
 * cartão traduzidos. A configuração do app tem prioridade sobre a do módulo
 * de retirada na loja (carregado sob demanda).
 */
@NgModule({
  imports: [
    CommonModule,
    I18nModule,
    CardModule,
    IconModule,
    PickupStoreModule,
  ],
  declarations: [FlaMyPreferredStoreComponent],
  providers: [
    provideConfig(<CmsConfig>{
      cmsComponents: {
        MyPreferredStoreComponent: { component: FlaMyPreferredStoreComponent },
      },
    }),
  ],
})
export class FlaPickupModule {}
