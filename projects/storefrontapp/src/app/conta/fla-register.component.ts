import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import {
  AbstractControl,
  UntypedFormControl,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RegisterComponent } from '@spartacus/user/profile/components';
import { UserSignUp } from '@spartacus/user/profile/root';
import { isValidCpf, maskCpf } from '../socio/socio-guests.service';

/** Dados do cadastro da loja: os do cadastro padrão, sem o "Tratamento", mais o CPF */
export interface FlaUserSignUp extends UserSignUp {
  cpf: string;
}

/** CPF com os dígitos verificadores certos (vazio fica para o "obrigatório") */
export function cpfValidator(control: AbstractControl): ValidationErrors | null {
  const value: string = control.value ?? '';
  return !value || isValidCpf(value) ? null : { cpfInvalido: true };
}

/**
 * Criar conta (07/11): o formulário padrão do Spartacus sem o campo "Tratamento" e com o CPF,
 * que fica no cadastro do cliente (Customer.cpf). A conta é criada pelo
 * FlaRegisterComponentService (POST /flamengo/cadastro), que faz o mesmo que o cadastro padrão.
 */
@Component({
  selector: 'fla-register',
  templateUrl: './fla-register.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaRegisterComponent extends RegisterComponent implements OnInit {
  override ngOnInit(): void {
    super.ngOnInit();
    this.registerForm.addControl(
      'cpf',
      new UntypedFormControl('', [Validators.required, cpfValidator])
    );
  }

  /** 12345678909 -> 123.456.789-09 enquanto digita */
  formatarCpf(): void {
    const control = this.registerForm.get('cpf');
    const formatado = maskCpf(control?.value ?? '');
    if (control && control.value !== formatado) {
      control.setValue(formatado, { emitEvent: false });
    }
  }

  override collectDataFromRegisterForm(formData: any): FlaUserSignUp {
    const { firstName, lastName, email, password, cpf } = formData;
    return {
      firstName,
      lastName,
      uid: email.toLowerCase(),
      password,
      cpf: (cpf ?? '').replace(/\D/g, ''),
    };
  }
}
