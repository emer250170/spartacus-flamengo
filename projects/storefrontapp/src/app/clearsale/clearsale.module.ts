import { CommonModule } from '@angular/common';
import { APP_INITIALIZER, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { OutletPosition, provideOutlet } from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaClearSaleService } from './clearsale.service';
import { FlaCheckoutDadosComponent } from './fla-checkout-dados.component';
import { FlaOrderRefusedComponent } from './fla-order-refused.component';

export function startClearSale(service: FlaClearSaleService): () => void {
  return () => service.start();
}

/**
 * ClearSale na loja (07/11): script de identificação do aparelho no checkout (FlaClearSaleService), CPF e
 * telefone das contas antigas antes do "Finalizar" (FlaCheckoutDadosComponent, dentro do bloco dos aceites)
 * e o aviso de pedido não aprovado na confirmação (FlaOrderRefusedComponent).
 */
@NgModule({
  imports: [CommonModule, RouterModule, FlaI18nModule],
  declarations: [FlaCheckoutDadosComponent, FlaOrderRefusedComponent],
  exports: [FlaCheckoutDadosComponent],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: startClearSale,
      deps: [FlaClearSaleService],
      multi: true,
    },
    // confirmação: aviso no topo, antes do bloco do Pix/boleto
    provideOutlet({
      id: 'BodyContent',
      position: OutletPosition.BEFORE,
      component: FlaOrderRefusedComponent,
    }),
  ],
})
export class FlaClearSaleModule {}
