import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { LanguageService } from '@spartacus/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

const BRL = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

/**
 * Preços em reais no formato brasileiro (R$ 229,99) com o site em português.
 *
 * MEDIDO (30/10): a API devolve formattedValue "R$229.99" para lang=pt — o
 * idioma "pt" do SAP Commerce não tem país, e o formatador cai no padrão
 * inglês. Aqui o formattedValue de todo preço em BRL das respostas da API é
 * refeito a partir do valor numérico, só quando a chamada é com lang=pt.
 */
@Injectable()
export class BrlPriceInterceptor implements HttpInterceptor {
  /** Idioma ativo do site (não depende da ordem dos interceptors) */
  protected lang?: string;

  constructor(languageService: LanguageService) {
    languageService.getActive().subscribe((lang) => (this.lang = lang));
  }

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    if (
      this.lang !== 'pt' &&
      request.params.get('lang') !== 'pt' &&
      !/[?&]lang=pt(&|$)/.test(request.urlWithParams)
    ) {
      return next.handle(request);
    }
    return next
      .handle(request)
      .pipe(
        map((event) =>
          event instanceof HttpResponse &&
          event.body &&
          typeof event.body === 'object'
            ? event.clone({ body: this.reformat(event.body) })
            : event
        )
      );
  }

  protected reformat(node: unknown): unknown {
    if (Array.isArray(node)) {
      node.forEach((item) => this.reformat(item));
    } else if (node && typeof node === 'object') {
      const obj = node as Record<string, unknown>;
      if (
        typeof obj['value'] === 'number' &&
        typeof obj['formattedValue'] === 'string' &&
        (obj['currencyIso'] === 'BRL' ||
          (obj['formattedValue'] as string).startsWith('R$'))
      ) {
        obj['formattedValue'] = BRL.format(obj['value'] as number);
      }
      Object.values(obj).forEach((child) => this.reformat(child));
    }
    return node;
  }
}
