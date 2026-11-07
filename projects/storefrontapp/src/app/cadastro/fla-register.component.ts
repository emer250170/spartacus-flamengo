import { Component, OnInit } from '@angular/core';
import {
  AbstractControl,
  UntypedFormControl,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  AnonymousConsentsConfig,
  AnonymousConsentsService,
  AuthConfigService,
  GlobalMessageService,
  RoutingService,
} from '@spartacus/core';
import {
  RegisterComponent,
  RegisterComponentService,
} from '@spartacus/user/profile/components';
import { UntypedFormBuilder } from '@angular/forms';
import { isValidCpf, maskCpf } from '../socio/socio-guests.service';
import { FlaSignupCpfService } from './fla-signup-cpf.service';

/** CPF com 11 números e dígitos verificadores certos */
export function cpfValidator(
  control: AbstractControl
): ValidationErrors | null {
  const value = String(control.value ?? '');
  return !value || isValidCpf(value) ? null : { cpfInvalid: true };
}

/**
 * Criar conta da loja Flamengo (07/11, pedido do Emerson): o formulário do Spartacus sem o
 * Tratamento e com o CPF (obrigatório, com máscara e conferência dos dígitos). O CPF vai no
 * cadastro pelo FlaSignupCpfInterceptor; o servidor confere de novo e não aceita CPF de outra conta.
 * Mesmo seletor (cx-register), para o tema e os outlets continuarem valendo.
 */
@Component({
  selector: 'cx-register',
  templateUrl: './fla-register.component.html',
})
export class FlaRegisterComponent extends RegisterComponent implements OnInit {
  constructor(
    globalMessageService: GlobalMessageService,
    fb: UntypedFormBuilder,
    router: RoutingService,
    anonymousConsentsService: AnonymousConsentsService,
    anonymousConsentsConfig: AnonymousConsentsConfig,
    authConfigService: AuthConfigService,
    registerComponentService: RegisterComponentService,
    protected signupCpf: FlaSignupCpfService
  ) {
    super(
      globalMessageService,
      fb,
      router,
      anonymousConsentsService,
      anonymousConsentsConfig,
      authConfigService,
      registerComponentService
    );
  }

  override ngOnInit(): void {
    this.registerForm.addControl(
      'cpf',
      new UntypedFormControl('', [Validators.required, cpfValidator])
    );
    super.ngOnInit();
  }

  /** Máscara 000.000.000-00 enquanto digita */
  onCpf(value: string): void {
    const masked = maskCpf(value);
    const control = this.registerForm.get('cpf');
    if (control && control.value !== masked) {
      control.setValue(masked, { emitEvent: false });
    }
  }

  /** Chave do texto do erro do CPF (fla-texts), depois de tocar no campo ou tentar enviar */
  get cpfError(): string | null {
    const control = this.registerForm.get('cpf');
    if (!control || !control.invalid || !(control.touched || control.dirty)) {
      return null;
    }
    return control.errors?.['required'] ? 'rg.cpfRequired' : 'rg.cpfInvalid';
  }

  override registerUser(): void {
    this.signupCpf.set(String(this.registerForm.get('cpf')?.value ?? ''));
    super.registerUser();
  }
}
