import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService, WindowRef } from '@spartacus/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';
import {
  MATCHDAY_HOLD_KEY,
  MATCHDAY_STORAGE_KEY,
  MatchdayCatalog,
  MatchdayHold,
  MatchdayMatch,
  MatchdayProduct,
  MatchdaySeat,
  MatchdaySector,
  MatchdaySelection,
  MatchdayShirt,
} from './matchday.model';

/** Categorias e códigos criados no flamengoProductCatalog para o protótipo */
const MATCHES_CATEGORY = 'flamengo_jogos_casa';
const SERVICES_CATEGORY = 'flamengo_matchday';
const SHIRT_CODE = 'FLA-CAMISA-J2-2026';
const PERSONALIZATION_CODE = 'FLA-PERSONALIZACAO';
const RECOMMENDED_FOOD = 'FLA-FOOD-COMBO';

const WEEKDAYS = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB'];

/**
 * Campos pedidos à API: só o que a jornada usa. MEDIDO (30/10, jogo com 384
 * poltronas, cache frio): fields=FULL 647 KB / 3,1 s; estes campos 170 KB / 1,4 s.
 */
const SEAT_FIELDS =
  'code,name,summary,description,price(value),' +
  'variantMatrix(variantValueCategory(name,sequence),' +
  'variantOption(code,priceData(value),stock(stockLevel,stockLevelStatus)),' +
  'elements(variantValueCategory(name,sequence),' +
  'variantOption(code,priceData(value),stock(stockLevel,stockLevelStatus))))';
const SHIRT_FIELDS =
  'code,name,summary,price(value),' +
  'variantMatrix(variantValueCategory(name,sequence),variantOption(code))';

/** Descrição de cada setor do Maracanã (texto de apoio do protótipo) */
const SECTOR_DESCRIPTIONS: Record<string, string> = {
  NORTE: 'Arquibancada atrás do gol',
  SUL: 'Arquibancada atrás do gol',
  LESTE: 'Lateral · visão central',
  OESTE: 'Lateral coberta · cadeira numerada',
};

interface OccPrice {
  value?: number;
}
interface OccStock {
  stockLevel?: number;
  stockLevelStatus?: string;
}
interface OccProduct {
  code: string;
  name?: string;
  summary?: string;
  description?: string;
  price?: OccPrice;
  stock?: OccStock;
  variantMatrix?: OccVariantMatrixElement[];
}
interface OccVariantMatrixElement {
  variantValueCategory?: { name?: string; sequence?: number };
  variantOption?: {
    code: string;
    priceData?: OccPrice;
    stock?: OccStock;
  };
  elements?: OccVariantMatrixElement[];
}
interface OccSearchResult {
  products?: OccProduct[];
}

@Injectable({ providedIn: 'root' })
export class MatchdayDataService {
  private catalog$?: Observable<MatchdayCatalog>;
  private matches$?: Observable<MatchdayMatch[]>;
  private seats = new Map<string, Observable<MatchdayMatch | undefined>>();

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected winRef: WindowRef
  ) {}

  /**
   * Lista de jogos (sem poltronas), estacionamento, comidas e camisa.
   * Setores e poltronas de cada jogo vêm de getSeats(), só quando o torcedor
   * escolhe o jogo. MEDIDO (30/10): com o cache da API frio, o detalhe de um
   * jogo leva 3,4–6 s; pedir os 4 jogos juntos levava 20–35 s (disputam o
   * servidor). Com cache, ~40 ms.
   * `refresh` busca de novo.
   */
  getCatalog(refresh = false): Observable<MatchdayCatalog> {
    if (!this.catalog$ || refresh) {
      this.catalog$ = forkJoin({
        matches: this.loadMatchList(),
        services: this.search(SERVICES_CATEGORY),
        shirt: this.loadProduct(SHIRT_CODE, SHIRT_FIELDS),
      }).pipe(
        map(({ matches, services, shirt }) =>
          this.buildCatalog(matches, services, shirt)
        ),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.catalog$;
  }

  /**
   * Setores e poltronas de um jogo (com a ocupação). Guardado por jogo;
   * `refresh` busca de novo (a ocupação muda a cada venda).
   */
  getSeats(
    matchCode: string,
    refresh = false
  ): Observable<MatchdayMatch | undefined> {
    let seats$ = this.seats.get(matchCode);
    if (!seats$ || refresh) {
      seats$ = this.loadProduct(matchCode, SEAT_FIELDS).pipe(
        map((detail) =>
          detail ? { ...this.toMatch(detail), seatsLoaded: true } : undefined
        ),
        shareReplay({ bufferSize: 1, refCount: false })
      );
      this.seats.set(matchCode, seats$);
    }
    return seats$;
  }

  /** JOGO-20261115-BAH-SUL-A09 -> JOGO-20261115-BAH */
  matchCodeOfSeat(seatCode: string): string | undefined {
    const m = /^([A-Z]+-\d{8}-[A-Z0-9]+)-/.exec(seatCode);
    return m?.[1];
  }

  /** Só a lista de jogos, sem poltronas (banner da home) */
  getMatches(): Observable<MatchdayMatch[]> {
    if (!this.matches$) {
      this.matches$ = this.loadMatchList().pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.matches$;
  }

  saveSelection(selection: MatchdaySelection): void {
    try {
      this.winRef.localStorage?.setItem(
        MATCHDAY_STORAGE_KEY,
        JSON.stringify(selection)
      );
    } catch {
      // armazenamento indisponível (modo privado, SSR): a jornada segue sem os detalhes
    }
  }

  readSelection(): MatchdaySelection | undefined {
    try {
      const raw = this.winRef.localStorage?.getItem(MATCHDAY_STORAGE_KEY);
      return raw ? (JSON.parse(raw) as MatchdaySelection) : undefined;
    } catch {
      return undefined;
    }
  }

  saveHold(hold: MatchdayHold): void {
    try {
      this.winRef.localStorage?.setItem(
        MATCHDAY_HOLD_KEY,
        JSON.stringify(hold)
      );
    } catch {
      // sem armazenamento o contador só não sobrevive a um recarregamento
    }
  }

  readHold(): MatchdayHold | undefined {
    try {
      const raw = this.winRef.localStorage?.getItem(MATCHDAY_HOLD_KEY);
      return raw ? (JSON.parse(raw) as MatchdayHold) : undefined;
    } catch {
      return undefined;
    }
  }

  clearHold(): void {
    try {
      this.winRef.localStorage?.removeItem(MATCHDAY_HOLD_KEY);
    } catch {
      // nada a limpar
    }
  }

  /**
   * Lista de jogos só pela busca: nome, preço "a partir de" e descrição (com as
   * datas das ondas). MEDIDO (30/10): o detalhe do produto, mesmo pedindo só
   * code,description, levava ~20 s com o cache frio — a API monta a matriz de
   * 384 poltronas de qualquer jeito. A busca responde em ~0,3 s.
   */
  protected loadMatchList(): Observable<MatchdayMatch[]> {
    return this.search(MATCHES_CATEGORY, true).pipe(
      map((products) =>
        products
          .map((p) => this.toMatch(p))
          .sort((a, b) =>
            this.dateKey(a.code).localeCompare(this.dateKey(b.code))
          )
      )
    );
  }

  protected search(
    category: string,
    withDescription = false
  ): Observable<OccProduct[]> {
    const url = `${this.occEndpoints.getBaseUrl()}/products/search`;
    return this.http
      .get<OccSearchResult>(url, {
        params: {
          query: `:relevance:allCategories:${category}`,
          pageSize: '50',
          fields: `products(code,name,summary,${withDescription ? 'description,' : ''}price(value,formattedValue),stock(stockLevel,stockLevelStatus))`,
        },
      })
      .pipe(
        map((res) => res.products ?? []),
        catchError(() => of([] as OccProduct[]))
      );
  }

  protected loadProduct(
    code: string,
    fields = 'FULL'
  ): Observable<OccProduct | undefined> {
    const url = `${this.occEndpoints.getBaseUrl()}/products/${encodeURIComponent(code)}`;
    return this.http
      .get<OccProduct>(url, { params: { fields } })
      .pipe(catchError(() => of(undefined)));
  }

  protected toMatch(p: OccProduct): MatchdayMatch {
    const name = this.clean(p.name);
    const [title = name, dateLabel = '', timeLabel = ''] = name.split(' · ');
    const opponent = title.split(/\s+x\s+/i)[1] ?? title;
    const summaryParts = this.clean(p.summary).split(' · ');
    const venue = summaryParts.length > 1 ? (summaryParts.pop() ?? '') : '';
    const competition = summaryParts.join(' · ');

    const bySequence = (
      a: OccVariantMatrixElement,
      b: OccVariantMatrixElement
    ) =>
      (a.variantValueCategory?.sequence ?? 0) -
      (b.variantValueCategory?.sequence ?? 0);

    const sectors: MatchdaySector[] = (p.variantMatrix ?? [])
      .filter((el) => !!el.variantOption?.code)
      .sort(bySequence)
      .map((el) => {
        const children = (el.elements ?? []).filter(
          (c) => !!c.variantOption?.code
        );
        if (children.length) {
          // modelo "poltrona como produto": Setor > Poltrona
          const seats: MatchdaySeat[] = children.sort(bySequence).map((c) => {
            const label = c.variantValueCategory?.name ?? '';
            const m = /^([A-Z]+)(\d+)$/i.exec(label);
            return {
              code: c.variantOption?.code ?? '',
              label,
              row: m ? m[1].toUpperCase() : label,
              number: m ? Number(m[2]) : 0,
              available: this.inStock(c.variantOption?.stock),
            };
          });
          // ...-LESTE-D04 -> LESTE
          const parts = seats[0].code.split('-');
          const key = parts[parts.length - 2] ?? '';
          return {
            code: key,
            name: el.variantValueCategory?.name ?? key,
            description: SECTOR_DESCRIPTIONS[key] ?? '',
            price:
              children[0].variantOption?.priceData?.value ??
              p.price?.value ??
              0,
            stock: seats.filter((st) => st.available).length,
            seats,
          };
        }
        // modelo antigo: o setor é o item vendido
        const code = el.variantOption?.code ?? '';
        const key = code.split('-').pop() ?? '';
        return {
          code,
          name: el.variantValueCategory?.name ?? key,
          description: SECTOR_DESCRIPTIONS[key] ?? '',
          price: el.variantOption?.priceData?.value ?? p.price?.value ?? 0,
          stock: el.variantOption?.stock?.stockLevel,
          seats: [],
        };
      });

    const minPrice = sectors.length
      ? Math.min(...sectors.map((s) => s.price))
      : (p.price?.value ?? 0);
    const lowStock = sectors.some((s) => s.stock !== undefined && s.stock < 20);

    return {
      code: p.code,
      title,
      opponent,
      weekday: this.weekdayFromCode(p.code),
      dateLabel,
      timeLabel,
      competition,
      venue,
      minPrice,
      tag: lowStock
        ? 'Últimos ingressos'
        : /pré-venda/i.test(
              [p.name, p.summary, p.description]
                .map((t) => this.clean(t))
                .join(' ')
            )
          ? 'Pré-venda sócio'
          : 'Vendas abertas',
      sectors,
      waveStarts: this.waveStarts(p.description),
    };
  }

  /**
   * Datas das ondas na descrição do jogo (escritas pelo SAP Commerce ao abrir
   * o jogo): "Onda 1 (...): 20/10/2026 10h00; ...; Venda geral: 23/10/2026 10h00".
   * Horário de Brasília.
   */
  protected waveStarts(description?: string): string[] | undefined {
    const text = this.clean(description);
    const at = (label: RegExp): string | undefined => {
      const m = new RegExp(
        label.source +
          String.raw`[^:]*:\s*(\d{2})\/(\d{2})\/(\d{4}) (\d{2})h(\d{2})`,
        'i'
      ).exec(text);
      return m ? `${m[3]}-${m[2]}-${m[1]}T${m[4]}:${m[5]}:00-03:00` : undefined;
    };
    const dates = [/Onda 1/, /Onda 2/, /Onda 3/, /Venda geral/].map(at);
    return dates.every(Boolean) ? (dates as string[]) : undefined;
  }

  protected buildCatalog(
    matches: MatchdayMatch[],
    services: OccProduct[],
    shirt: OccProduct | undefined
  ): MatchdayCatalog {
    const toProduct = (p: OccProduct): MatchdayProduct => ({
      code: p.code,
      name: this.clean(p.name),
      summary: this.clean(p.summary),
      price: p.price?.value ?? 0,
      recommended: p.code === RECOMMENDED_FOOD,
    });
    const byCode = (a: OccProduct, b: OccProduct) =>
      (a.price?.value ?? 0) === (b.price?.value ?? 0)
        ? a.code.localeCompare(b.code)
        : (b.price?.value ?? 0) - (a.price?.value ?? 0);

    const parking = services
      .filter((p) => p.code.startsWith('FLA-EST-'))
      .sort(byCode)
      .map(toProduct);
    const food = services
      .filter((p) => p.code.startsWith('FLA-FOOD-'))
      .sort(byCode)
      .map(toProduct);
    const personalization = services.find(
      (p) => p.code === PERSONALIZATION_CODE
    );

    let shirtModel: MatchdayShirt | undefined;
    if (shirt) {
      const sizes = (shirt.variantMatrix ?? [])
        .filter((el) => !!el.variantOption?.code)
        .sort(
          (a, b) =>
            (a.variantValueCategory?.sequence ?? 0) -
            (b.variantValueCategory?.sequence ?? 0)
        )
        .map((el) => ({
          label: el.variantValueCategory?.name ?? '',
          code: el.variantOption?.code ?? '',
        }));
      shirtModel = {
        code: shirt.code,
        name: this.clean(shirt.name),
        summary: this.clean(shirt.summary),
        price: shirt.price?.value ?? 0,
        sizes,
        personalization: personalization
          ? toProduct(personalization)
          : undefined,
      };
    }

    return { matches, parking, food, shirt: shirtModel };
  }

  /** FLA-PAL-20261019 -> "20261019" (ordena os jogos pela data) */
  protected dateKey(code: string): string {
    return /(\d{8})/.exec(code)?.[1] ?? code;
  }

  /** FLA-PAL-20261019 -> "SEG" */
  protected weekdayFromCode(code: string): string {
    const m = /(\d{4})(\d{2})(\d{2})/.exec(code);
    if (!m) {
      return '';
    }
    const date = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    return WEEKDAYS[date.getDay()] ?? '';
  }

  protected inStock(stock?: OccStock): boolean {
    return (
      stock?.stockLevelStatus !== 'outOfStock' && (stock?.stockLevel ?? 1) > 0
    );
  }

  /** Remove o destaque de busca (<em>) que o Solr pode devolver */
  protected clean(text?: string): string {
    return (text ?? '').replace(/<[^>]+>/g, '').trim();
  }
}
