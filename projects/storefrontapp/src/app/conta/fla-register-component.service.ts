import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  GlobalMessageType,
  InterceptorUtil,
  OccEndpointsService,
  User,
  USE_CLIENT_TOKEN,
} from '@spartacus/core';
import { RegisterComponentService } from '@spartacus/user/profile/components';
import { UserSignUp } from '@spartacus/user/profile/root';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Cria a conta pelo endpoint da demo (POST /{site}/flamengo/cadastro), que grava o CPF no cliente;
 * o cadastro padrão (POST /users) não aceita campos novos. Usa o token do cliente da loja, como o
 * padrão, e mostra a recusa do servidor (em português) no aviso da página. Sem o endpoint no servidor
 * (404), volta para o cadastro padrão, sem o CPF.
 */
@Injectable()
export class FlaRegisterComponentService extends RegisterComponentService {
  protected http = inject(HttpClient);
  protected occEndpoints = inject(OccEndpointsService);

  override register(user: UserSignUp): Observable<User> {
    const headers = InterceptorUtil.createHeader(
      USE_CLIENT_TOKEN,
      true,
      new HttpHeaders({ 'Content-Type': 'application/json' })
    );
    return this.http
      .post<User>(`${this.occEndpoints.getBaseUrl()}/flamengo/cadastro`, user, {
        headers,
      })
      .pipe(
        catchError((e: HttpErrorResponse) => {
          // servidor ainda sem o endpoint (loja publicada antes do ant all): cadastro padrão, sem CPF
          if (e?.status === 404) {
            const { cpf, ...padrao } = user as UserSignUp & { cpf?: string };
            return super.register(padrao);
          }
          const raw: string =
            e?.error?.errors?.[0]?.message ||
            'Não foi possível criar a conta agora. Tente de novo em instantes.';
          this.globalMessageService.add({ raw }, GlobalMessageType.MSG_TYPE_ERROR);
          return throwError(() => e);
        })
      );
  }
}
