import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { CmsService, OccEndpointsService } from '@spartacus/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { installments } from '../listagem/fla-product-card-price.component';
import { FlaMediaUrlService, bestImageUrl } from './fla-media-url.service';

/** Produto como os blocos da home mostram */
export interface FlaHomeProduct {
  code: string;
  name: string;
  link: string;
  img?: string;
  price: string;
  /** "ou 10x de R$ 44,99" */
  installments?: string;
}

/** Banner do CMS (imagem, link e texto alternativo) */
export interface FlaHomeBanner {
  img?: string;
  link: string;
  alt: string;
}

/** Carrossel de produtos do CMS: título e códigos */
export interface FlaHomeCarousel {
  title: string;
  codes: string[];
}

const PRODUCT_FIELDS = 'code,name,purchasable,price(value,formattedValue),images(DEFAULT)';

/**
 * Dados da home no visual da loja oficial (mockup "Home", 06/11). Os blocos leem o que já está
 * no SAP Commerce: banners e carrosséis do CMS (editáveis no Backoffice/SmartEdit, Staged e
 * Online) e produtos das categorias do catálogo.
 */
@Injectable({ providedIn: 'root' })
export class FlaHomeService {
  protected cache = new Map<string, Observable<FlaHomeProduct[]>>();

  constructor(
    protected http: HttpClient,
    protected occ: OccEndpointsService,
    protected cms: CmsService,
    protected media: FlaMediaUrlService,
    protected i18n: FlaI18nService
  ) {}

  /** Banner do CMS pelo uid; sem o componente, null */
  banner(uid: string): Observable<FlaHomeBanner | null> {
    return this.cms.getComponentData<any>(uid).pipe(
      take(1),
      map((c) => {
        const m = c?.media;
        const url = m?.widescreen?.url ?? m?.desktop?.url ?? m?.tablet?.url ?? m?.mobile?.url ?? m?.url;
        return c
          ? {
              img: this.media.url(url),
              link: c.urlLink ?? '/',
              alt: m?.desktop?.altText ?? m?.altText ?? c.name ?? '',
            }
          : null;
      }),
      catchError(() => of(null))
    );
  }

  /** Carrossel de produtos do CMS pelo uid (título no idioma do site e os códigos) */
  carousel(uid: string): Observable<FlaHomeCarousel | null> {
    return this.cms.getComponentData<any>(uid).pipe(
      take(1),
      map((c) =>
        c
          ? {
              title: c.title ?? '',
              codes: String(c.productCodes ?? '')
                .split(/\s+/)
                .filter((x: string) => !!x),
            }
          : null
      ),
      catchError(() => of(null))
    );
  }

  /** Produtos pelos códigos, na ordem; os que não carregam ficam de fora */
  byCodes(codes: string[], n: number): Observable<FlaHomeProduct[]> {
    const list = codes.slice(0, n);
    if (!list.length) {
      return of([]);
    }
    return this.cached('codes:' + list.join(','), () =>
      forkJoin(
        list.map((code) =>
          this.http
            .get<any>(`${this.base}/products/${encodeURIComponent(code)}`, { params: this.params() })
            .pipe(catchError(() => of(null)))
        )
      ).pipe(map((ps) => ps.filter((p) => !!p).map((p) => this.toProduct(p))))
    );
  }

  /** Primeiros produtos de uma categoria (com as subcategorias) */
  byCategory(category: string, n: number): Observable<FlaHomeProduct[]> {
    return this.cached(`cat:${category}:${n}`, () =>
      this.http
        .get<any>(`${this.base}/products/search`, {
          params: {
            ...this.params(),
            query: `:relevance:allCategories:${category}`,
            pageSize: String(n),
            fields: `products(${PRODUCT_FIELDS})`,
          },
        })
        .pipe(
          map((r) => (r?.products ?? []).map((p: any) => this.toProduct(p))),
          catchError(() => of([]))
        )
    );
  }

  protected get base(): string {
    return this.occ.getBaseUrl();
  }

  protected params(): Record<string, string> {
    return { fields: PRODUCT_FIELDS };
  }

  /** Cache por idioma (nomes e preços formatados mudam com o idioma) */
  protected cached(key: string, load: () => Observable<FlaHomeProduct[]>): Observable<FlaHomeProduct[]> {
    return this.i18n.lang$.pipe(
      switchMap((lang) => {
        const k = `${lang}|${key}`;
        if (!this.cache.has(k)) {
          this.cache.set(k, load().pipe(shareReplay(1)));
        }
        return this.cache.get(k) as Observable<FlaHomeProduct[]>;
      })
    );
  }

  protected toProduct(p: any): FlaHomeProduct {
    const value: number = p.price?.value ?? 0;
    const n = value > 0 ? installments(value) : 1;
    return {
      code: p.code,
      name: p.name ?? p.code,
      link: '/product/' + encodeURIComponent(p.code),
      img: this.media.url(bestImageUrl(p.images)),
      price: p.price?.formattedValue ?? (value ? this.i18n.money(value) : ''),
      installments:
        n > 1
          ? this.i18n.t('list.installments', { n, v: this.i18n.money(Math.floor((value * 100) / n) / 100) })
          : undefined,
    };
  }
}
