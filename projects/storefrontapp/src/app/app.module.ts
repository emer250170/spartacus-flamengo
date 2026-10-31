/*
 * SPDX-FileCopyrightText: 2024 SAP Spartacus team <spartacus-team@sap.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { registerLocaleData } from '@angular/common';
import {
  provideHttpClient,
  withFetch,
  withInterceptorsFromDi,
} from '@angular/common/http';
import localeDe from '@angular/common/locales/de';
import localeJa from '@angular/common/locales/ja';
import localePt from '@angular/common/locales/pt';
import localeZh from '@angular/common/locales/zh';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { EffectsModule } from '@ngrx/effects';
import { StoreModule } from '@ngrx/store';
import { StoreDevtoolsModule } from '@ngrx/store-devtools';
import { translationChunksConfig, translations } from '@spartacus/assets';
import {
  I18nConfig,
  OccConfig,
  RoutingConfig,
  TestConfigModule,
  provideConfig,
} from '@spartacus/core';
import { StoreFinderConfig } from '@spartacus/storefinder/core';
import { GOOGLE_MAPS_DEVELOPMENT_KEY_CONFIG } from '@spartacus/storefinder/root';
import { AppRoutingModule, StorefrontComponent } from '@spartacus/storefront';
import { environment } from '../environments/environment';
import { TestOutletModule } from '../test-outlets/test-outlet.module';
import { MatchdayModule } from './matchday/matchday.module';
import { SocioModule } from './socio/socio.module';
import { BrlPriceInterceptor } from './i18n/brl-price.interceptor';
import { ptCarrinhoCheckout } from './i18n/pt-carrinho-checkout';
import { ptCadastroSenha } from './i18n/pt-cadastro-senha';
import { ptLogin } from './i18n/pt-login';
import { ptMinhaConta } from './i18n/pt-minha-conta';
import { ptProduto } from './i18n/pt-produto';
import { SpartacusModule } from './spartacus/spartacus.module';

registerLocaleData(localeDe);
registerLocaleData(localeJa);
// datas em português ("30 de out. de 2026"); sem isso o site em pt usava o formato inglês
registerLocaleData(localePt);
registerLocaleData(localeZh);

const devImports = [];
if (!environment.production) {
  devImports.push(StoreDevtoolsModule.instrument());
}

@NgModule({
  imports: [
    BrowserModule,
    AppRoutingModule,
    StoreModule.forRoot({}),
    EffectsModule.forRoot([]),
    SpartacusModule,
    MatchdayModule,
    SocioModule,
    TestOutletModule, // custom usages of cxOutletRef only for e2e testing
    TestConfigModule.forRoot({ cookie: 'cxConfigE2E' }), // Injects config dynamically from e2e tests. Should be imported after other config modules.

    ...devImports,
  ],
  providers: [
    provideHttpClient(withFetch(), withInterceptorsFromDi()),
    provideConfig(<OccConfig>{
      backend: {
        occ: {
          baseUrl: environment.occBaseUrl,
          prefix: environment.occApiPrefix,
        },
      },
    }),
    provideConfig(<RoutingConfig>{
      // custom routing configuration for e2e testing
      routing: {
        routes: {
          product: {
            paths: ['product/:productCode/:name', 'product/:productCode'],
            paramsMapping: { name: 'slug' },
          },
        },
      },
    }),
    provideConfig(<I18nConfig>{
      // we bring in static translations to be up and running soon right away
      i18n: {
        resources: translations,
        chunks: translationChunksConfig,
        fallbackLang: 'en',
      },
    }),
    // português da página de produto (o Spartacus só traz inglês)
    provideConfig(<I18nConfig>{ i18n: { resources: ptProduto } }),
    provideConfig(<I18nConfig>{ i18n: { resources: ptCarrinhoCheckout } }),
    provideConfig(<I18nConfig>{ i18n: { resources: ptLogin } }),
    provideConfig(<I18nConfig>{ i18n: { resources: ptCadastroSenha } }),
    provideConfig(<I18nConfig>{ i18n: { resources: ptMinhaConta } }),
    // preços em R$ 229,99 com o site em português (a API manda R$229.99)
    { provide: HTTP_INTERCEPTORS, useClass: BrlPriceInterceptor, multi: true },
    provideConfig({ features: { level: '*' } }), // For the development environment and CI, feature level is always the highest.
    provideConfig(<StoreFinderConfig>{
      // For security compliance, by default, google maps does not display.
      // Using special key value 'cx-development' allows google maps to display
      // without a key, for development or demo purposes.
      googleMaps: { apiKey: GOOGLE_MAPS_DEVELOPMENT_KEY_CONFIG },
    }),
  ],
  bootstrap: [StorefrontComponent],
})
export class AppModule {}
