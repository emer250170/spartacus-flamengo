import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { LanguageService, WindowRef } from '@spartacus/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MATCHDAY_STORAGE_KEY, MatchdaySelection } from './matchday.model';

/** Modo de entrega do dia de jogo (04-entrega-brasil.impex) */
const MATCHDAY_MODE = 'matchday-digital';

type Part =
  | 'ticket'
  | 'parking'
  | 'food'
  | 'shirt'
  | 'plan'
  | 'experience'
  | 'store';
const ORDER: Part[] = [
  'ticket',
  'parking',
  'food',
  'shirt',
  'plan',
  'experience',
  'store',
];

/** Itens que vão ao estádio: com eles o nome do modo continua "Ingresso digital e retirada no estádio" */
const STADIUM: Part[] = ['ticket', 'parking', 'food', 'shirt', 'store'];

/** Nome do modo quando o carrinho só tem plano e/ou experiência com voucher (nada no estádio) */
const DIGITAL_NAME: Record<'pt' | 'en', string> = {
  pt: 'Entrega digital',
  en: 'Digital delivery',
};

const TEXTS: Record<
  'pt' | 'en',
  Record<Exclude<Part, 'food'> | 'foodSeat' | 'foodKiosk' | 'and', string>
> = {
  pt: {
    ticket: 'ingresso no app',
    parking: 'vaga do estacionamento liberada pela placa',
    foodSeat: 'comida entregue na poltrona',
    foodKiosk: 'comida retirada no quiosque do setor',
    shirt: 'camisa retirada no estádio',
    plan: 'plano de sócio ativado na hora',
    experience: 'voucher da experiência em Meus resgates',
    store: 'produtos da loja retirados no estádio',
    and: ' e ',
  },
  en: {
    ticket: 'ticket in the app',
    parking: 'parking spot released by your plate',
    foodSeat: 'food delivered to your seat',
    foodKiosk: 'food picked up at the sector kiosk',
    shirt: 'jersey picked up at the stadium',
    plan: 'membership plan active right away',
    experience: 'experience voucher in My redemptions',
    store: 'store products picked up at the stadium',
    and: ' and ',
  },
};

/**
 * Descrição do modo "Ingresso digital e retirada no estádio" conforme o que
 * está no carrinho (ou no pedido): só ingresso → "Ingresso no app"; ingresso e
 * camisa → "Ingresso no app e camisa retirada no estádio"; só experiência
 * com voucher → "Voucher da experiência em Meus resgates"; e assim por diante.
 *
 * O cadastro do modo tem um texto fixo, que citava comida e camisa mesmo sem
 * elas. Aqui a resposta da API é ajustada: na lista de modos de entrega, pelos
 * itens do carrinho ativo; no carrinho e no pedido (revisão, confirmação,
 * detalhe), pelos itens da própria resposta.
 */
@Injectable()
export class MatchdayDeliveryTextInterceptor implements HttpInterceptor {
  protected lang?: string;
  /** Códigos dos produtos do carrinho ativo (para a lista de modos) */
  protected cartCodes: string[] = [];
  protected watchingCart = false;

  constructor(
    protected injector: Injector,
    protected winRef: WindowRef,
    languageService: LanguageService
  ) {
    languageService.getActive().subscribe((lang) => (this.lang = lang));
  }

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    if (!/\/(carts|orders)\b/.test(request.url)) {
      return next.handle(request);
    }
    this.watchCart();
    const lang = this.langOf(request);
    return next
      .handle(request)
      .pipe(
        map((event) =>
          event instanceof HttpResponse &&
          event.body &&
          typeof event.body === 'object'
            ? event.clone({ body: this.rewrite(event.body as any, lang) })
            : event
        )
      );
  }

  /** Acompanha o carrinho ativo (pego só no primeiro uso: evita dependência circular com o HttpClient) */
  protected watchCart(): void {
    if (this.watchingCart || !this.winRef.isBrowser()) {
      return;
    }
    this.watchingCart = true;
    setTimeout(() =>
      this.injector
        .get(ActiveCartFacade)
        .getEntries()
        .subscribe(
          (entries) =>
            (this.cartCodes = entries.map((e) => e.product?.code ?? ''))
        )
    );
  }

  protected langOf(request: HttpRequest<unknown>): 'pt' | 'en' {
    const param =
      request.params.get('lang') ??
      /[?&]lang=([a-z]+)/.exec(request.urlWithParams)?.[1] ??
      this.lang;
    return param && !param.startsWith('pt') ? 'en' : 'pt';
  }

  protected rewrite(body: any, lang: 'pt' | 'en'): any {
    // lista de modos de entrega do carrinho
    if (Array.isArray(body.deliveryModes) && this.cartCodes.length) {
      body.deliveryModes
        .filter((m: any) => m?.code === MATCHDAY_MODE)
        .forEach((m: any) => this.relabel(m, this.cartCodes, lang));
    }
    // carrinho ou pedido com o modo já escolhido
    if (
      body.deliveryMode?.code === MATCHDAY_MODE &&
      Array.isArray(body.entries)
    ) {
      const codes = body.entries.map((e: any) => e?.product?.code ?? '');
      if (codes.length) {
        this.relabel(body.deliveryMode, codes, lang);
      }
    }
    return body;
  }

  /**
   * Descrição pelo carrinho e, sem nada que vá ao estádio (só plano e/ou experiência com voucher,
   * 08/11), o nome "Entrega digital" no lugar de "Ingresso digital e retirada no estádio".
   */
  protected relabel(mode: any, codes: string[], lang: 'pt' | 'en'): void {
    mode.description = this.describe(codes, lang);
    const kinds = codes.filter((c) => !!c).map((c) => this.kindOf(c));
    if (kinds.length && !kinds.some((k) => STADIUM.includes(k))) {
      mode.name = DIGITAL_NAME[lang];
    }
  }

  protected describe(codes: string[], lang: 'pt' | 'en'): string {
    const t = TEXTS[lang];
    const kinds = new Set(codes.filter((c) => !!c).map((c) => this.kindOf(c)));
    const parts = ORDER.filter((k) => kinds.has(k)).map((k) =>
      k === 'food' ? (this.foodAtKiosk() ? t.foodKiosk : t.foodSeat) : t[k]
    );
    if (!parts.length) {
      return '';
    }
    const text =
      parts.length === 1
        ? parts[0]
        : parts.slice(0, -1).join(', ') + t.and + parts[parts.length - 1];
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  protected kindOf(code: string): Part {
    if (/^JOGO-/.test(code)) {
      return 'ticket';
    }
    if (/^FLA-EST-/.test(code)) {
      return 'parking';
    }
    if (/^FLA-FOOD-/.test(code)) {
      return 'food';
    }
    if (/^FLA-(CAMISA-|PERSONALIZACAO)/.test(code)) {
      return 'shirt';
    }
    if (/^NACAO-/.test(code)) {
      return 'plan';
    }
    // experiência da loja (item 9): voucher NACAO-... em Meus sócio-torcedor
    if (/^EXP-/.test(code)) {
      return 'experience';
    }
    return 'store';
  }

  /** Escolha da jornada: comida retirada no quiosque (padrão: na poltrona) */
  protected foodAtKiosk(): boolean {
    try {
      const raw = this.winRef.localStorage?.getItem(MATCHDAY_STORAGE_KEY);
      const selection = raw
        ? (JSON.parse(raw) as MatchdaySelection)
        : undefined;
      return selection?.foodDelivery === 'pickup';
    } catch {
      return false;
    }
  }
}
