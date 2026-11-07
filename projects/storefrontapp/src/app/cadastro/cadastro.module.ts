import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule, UntypedFormBuilder } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import {
  CmsConfig,
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
  NgSelectA11yModule,
  PasswordVisibilityToggleModule,
  SpinnerModule,
} from '@spartacus/storefront';
import { RegisterComponentService } from '@spartacus/user/profile/components';
import { UserRegisterFacade } from '@spartacus/user/profile/root';
import { FlaI18nModule } from '../i18n/fla-i18n.service';
import { FlaRegisterComponent } from './fla-register.component';
import { FlaSignupCpfInterceptor } from './fla-signup-cpf.interceptor';

/**
 * Criar conta com CPF e sem Tratamento (07/11): troca o RegisterCustomerComponent do Spartacus
 * pelo FlaRegisterComponent (mesmo formulário, mesmas regras) e manda o CPF no cadastro.
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
    NgSelectA11yModule,
    NgSelectModule,
    PasswordVisibilityToggleModule,
    FeaturesConfigModule,
    FlaI18nModule,
  ],
  declarations: [FlaRegisterComponent],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: FlaSignupCpfInterceptor,
      multi: true,
    },
    provideConfig(<CmsConfig>{
      cmsComponents: {
        RegisterCustomerComponent: {
          component: FlaRegisterComponent,
          guards: [NotAuthGuard],
          providers: [
            {
              provide: RegisterComponentService,
              useClass: RegisterComponentService,
              deps: [
                UserRegisterFacade,
                GlobalMessageService,
                UntypedFormBuilder,
              ],
            },
          ],
        },
      },
    }),
  ],
})
export class CadastroModule {}
