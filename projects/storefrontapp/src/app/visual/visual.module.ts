import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { OutletPosition, provideOutlet } from '@spartacus/storefront';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import {
  FlaFooterBottomComponent,
  FlaFooterInfoComponent,
  FlaPortalLinksComponent,
  FlaPortalNavComponent,
  FlaTicketsButtonComponent,
} from './fla-header.components';
import { FlaLogoLabelComponent } from './fla-logo-label.component';

/**
 * Visual da loja oficial (flamengo.com.br/loja, mockup aprovado em 05/11): header em três
 * faixas e rodapé escuro. Os componentes entram pelos outlets dos slots do header e do
 * rodapé; a disposição fica no tema (tema-flamengo.scss, seção "Visual da loja oficial").
 * Desligar: tirar VisualModule do AppModule e a seção do tema.
 */
@NgModule({
  imports: [CommonModule, RouterModule, FlaI18nModule],
  declarations: [
    FlaPortalLinksComponent,
    FlaPortalNavComponent,
    FlaTicketsButtonComponent,
    FlaFooterInfoComponent,
    FlaFooterBottomComponent,
    FlaLogoLabelComponent,
  ],
  providers: [
    // faixa de cima: links do portal (no lugar dos links do CMS, no desktop)
    provideOutlet({
      id: 'SiteLinks',
      position: OutletPosition.BEFORE,
      component: FlaPortalLinksComponent,
    }),
    // barra vermelha: menu do portal depois do logo
    provideOutlet({
      id: 'SiteLogo',
      position: OutletPosition.AFTER,
      component: FlaPortalNavComponent,
    }),
    // logo sem o tooltip "SAP Commerce" do CMS (tira os title, nome para leitor de tela)
    provideOutlet({
      id: 'SiteLogo',
      position: OutletPosition.AFTER,
      component: FlaLogoLabelComponent,
    }),
    // barra vermelha: "Ingressos" antes da busca
    provideOutlet({
      id: 'SearchBox',
      position: OutletPosition.BEFORE,
      component: FlaTicketsButtonComponent,
    }),
    // rodapé: endereços antes dos links do CMS e a linha final depois
    provideOutlet({
      id: 'Footer',
      position: OutletPosition.BEFORE,
      component: FlaFooterInfoComponent,
    }),
    provideOutlet({
      id: 'Footer',
      position: OutletPosition.AFTER,
      component: FlaFooterBottomComponent,
    }),
  ],
})
export class VisualModule {}
