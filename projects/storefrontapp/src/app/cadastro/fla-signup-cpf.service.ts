import { Injectable } from '@angular/core';

/**
 * CPF do cadastro que está sendo enviado (07/11): o FlaRegisterComponent guarda aqui antes de
 * chamar o cadastro do Spartacus e o FlaSignupCpfInterceptor põe no POST /users (?cpf=...).
 */
@Injectable({ providedIn: 'root' })
export class FlaSignupCpfService {
  protected pending?: string;

  set(cpf: string): void {
    this.pending = (cpf ?? '').replace(/\D/g, '') || undefined;
  }

  /** Lê e esquece (vale para um único envio) */
  take(): string | undefined {
    const cpf = this.pending;
    this.pending = undefined;
    return cpf;
  }
}
