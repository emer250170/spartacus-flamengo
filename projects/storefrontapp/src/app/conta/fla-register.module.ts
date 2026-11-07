import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule, UntypedFormBuilder } from '@angular/forms';
import { RouterModule } from '@angular/router';
import {
  CmsConfig,
  I18nConfig,
  FeaturesConfigModule,
  GlobalMessageService,
  I18nModule,
  NotAuthGuard,
  UrlModule,
  provideConfig,
} from '@spartacus/core';
import {
  CaptchaModule,
  FormErrorsModule,
  PasswordVisibilityToggleModule,
  SpinnerModule,
} from '@spartacus/storefront';
import { RegisterComponentService } from '@spartacus/user/profile/components';
import { UserRegisterFacade } from '@spartacus/user/profile/root';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaRegisterComponentService } from './fla-register-component.service';
import { FlaRegisterComponent } from './fla-register.component';

/**
 * Criar conta com CPF e sem "Tratamento" (07/11): troca o RegisterCustomerComponent do CMS pelo
 * FlaRegisterComponent, que cria a conta pelo POST /flamengo/cadastro (CPF no cliente).
 */
@NgModule({
  imports: [
    CaptchaModule,
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    UrlModule,
    I18nModule,
    SpinnerModule,
    FormErrorsModule,
    PasswordVisibilityToggleModule,
    FeaturesConfigModule,
    FlaI18nModule,
  ],
  declarations: [FlaRegisterComponent],
  providers: [
    // mensagem do validador de CPF no componente de erros do Spartacus (com e sem o rótulo)
    provideConfig(<I18nConfig>{
      i18n: {
        resources: {
          pt: {
            common: {
              formErrors: {
                cpfInvalido: 'CPF inválido: confira os 11 números.',
                labeled: { cpfInvalido: 'CPF inválido: confira os 11 números.' },
              },
            },
          },
          en: {
            common: {
              formErrors: {
                cpfInvalido: 'Invalid CPF: check the 11 digits.',
                labeled: { cpfInvalido: 'Invalid CPF: check the 11 digits.' },
              },
            },
          },
        },
      },
    }),
    provideConfig(<CmsConfig>{
      cmsComponents: {
        RegisterCustomerComponent: {
          component: FlaRegisterComponent,
          guards: [NotAuthGuard],
          providers: [
            {
              provide: RegisterComponentService,
              useClass: FlaRegisterComponentService,
              deps: [UserRegisterFacade, GlobalMessageService, UntypedFormBuilder],
            },
          ],
        },
      },
    }),
  ],
})
export class FlaRegisterModule {}
