import { NgModule } from '@angular/core';
import { EmarsysTrackingService } from './emarsys-tracking.service';

/**
 * Integração da loja com o Emarsys (Web Extend). A tag do Emarsys fica no
 * index.html; este módulo só liga o serviço que manda os comandos a cada
 * página. Para desligar: tirar a tag do index.html (o serviço não manda
 * nada sem ela) ou tirar o módulo do AppModule.
 */
@NgModule({})
export class EmarsysModule {
  constructor(tracking: EmarsysTrackingService) {
    tracking.iniciar();
  }
}
