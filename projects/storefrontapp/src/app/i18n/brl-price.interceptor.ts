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
      const formatted = obj['formattedValue'];
      if (typeof formatted === 'string') {
        // o carrinho às vezes pede só formattedValue (ex.: frete), sem value
        const value =
          typeof obj['value'] === 'number'
            ? (obj['value'] as number)
            : this.parseBrl(formatted);
        if (
          value !== undefined &&
          (obj['currencyIso'] === 'BRL' || formatted.startsWith('R$'))
        ) {
          obj['formattedValue'] = BRL.format(value);
        }
      }
      Object.values(obj).forEach((child) => this.reformat(child));
    }
    return node;
  }

  /** "R$1,234.56" (formato que a API manda) -> 1234.56 */
  protected parseBrl(text: string): number | undefined {
    // só o formato inglês da API (ponto decimal, 2 casas); "R$ 229,99" fica como está
    const m = /^R\$(-?\d{1,3}(?:,\d{3})*\.\d{2})$/.exec(text.trim());
    return m ? Number(m[1].replace(/,/g, '')) : undefined;
  }
}
