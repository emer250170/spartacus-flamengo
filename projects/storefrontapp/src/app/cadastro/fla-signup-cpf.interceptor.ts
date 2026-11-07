import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { FlaSignupCpfService } from './fla-signup-cpf.service';

/** Cadastro na API de comércio: POST .../{site}/users (sem /current, /anonymous...) */
const REGISTER_URL = /\/users\/?(?:\?|$)/;

/**
 * Criar conta com CPF (07/11): o cadastro do Spartacus manda só nome, e-mail e senha; aqui o CPF
 * vai junto no parâmetro cpf do POST /users. O servidor confere (dígitos e conta repetida) e
 * grava no torcedor (SignupCpfInterceptor e CustomerCpfPrepareInterceptor da extensão).
 */
@Injectable()
export class FlaSignupCpfInterceptor implements HttpInterceptor {
  constructor(protected cpf: FlaSignupCpfService) {}

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    if (request.method === 'POST' && REGISTER_URL.test(request.url)) {
      const cpf = this.cpf.take();
      const phone = this.cpf.takePhone();
      let params = request.params;
      if (cpf) {
        params = params.set('cpf', cpf);
      }
      // telefone (07/11): o servidor confere (DDD + número) e grava em Customer.phone
      if (phone) {
        params = params.set('telefone', phone);
      }
      if (cpf || phone) {
        return next.handle(request.clone({ params }));
      }
    }
    return next.handle(request);
  }
}
