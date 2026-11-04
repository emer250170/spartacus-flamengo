import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  FLA_DEFAULT_MATCH_IMAGE,
  FlaMediaUrlService,
  bestImageUrl,
} from './fla-media-url.service';

/** Categoria das experiências à venda (SocioExperienceService.CATEGORY no backend) */
const EXPERIENCES_CATEGORY = 'experiencias';

export interface ShowcaseExperience {
  code: string;
  name: string;
  /** "Experiência Nação · sócio também resgata com 500 pontos" */
  summary: string;
  price?: string;
  imageUrl?: string;
  soldOut: boolean;
}

interface OccExperienceProduct {
  code: string;
  name?: string;
  summary?: string;
  price?: { formattedValue?: string; value?: number };
  stock?: { stockLevelStatus?: string };
  images?: { format?: string; url?: string }[];
}

/**
 * Vitrine "Experiências Nação" (04/11): só os produtos de experiência à venda (categoria
 * experiencias, produtos EXP-), com a foto cadastrada no Backoffice, preço e a linha de
 * pontos do sócio. Usada na home e na página Sócio-Torcedor.
 */
@Component({
  selector: 'fla-experience-showcase',
  templateUrl: './experience-showcase.component.html',
  styleUrls: ['./vitrine.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperienceShowcaseComponent {
  /** Chaves de texto do título e da linha de apoio (fla-texts) */
  @Input() titleKey = 'vit.exp.title';
  @Input() subKey = 'vit.exp.sub';
  /** Cards escuros (home e página Sócio-Torcedor, que é escura) */
  @Input() dark = false;
  /** Sem fundo próprio: a página em volta já é escura (Sócio-Torcedor) */
  @Input() flat = false;

  readonly defaultImage = FLA_DEFAULT_MATCH_IMAGE;

  experiences$: Observable<ShowcaseExperience[]> = this.i18n.lang$.pipe(
    switchMap(() => this.load()),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected media: FlaMediaUrlService,
    protected i18n: FlaI18nService
  ) {}

  protected load(): Observable<ShowcaseExperience[]> {
    return this.http
      .get<{ products?: OccExperienceProduct[] }>(
        `${this.occEndpoints.getBaseUrl()}/products/search`,
        {
          params: {
            query: `:relevance:allCategories:${EXPERIENCES_CATEGORY}`,
            pageSize: '20',
            fields:
              'products(code,name,summary,price(formattedValue,value),stock(stockLevelStatus),images(DEFAULT))',
          },
        }
      )
      .pipe(
        map((res) =>
          (res.products ?? [])
            .filter((p) => p.code.startsWith('EXP-'))
            .map((p) => ({
              code: p.code,
              name: this.clean(p.name) || p.code,
              summary: this.clean(p.summary),
              price: p.price?.value
                ? this.i18n.money(p.price.value)
                : undefined,
              imageUrl: this.media.url(bestImageUrl(p.images)),
              soldOut: p.stock?.stockLevelStatus === 'outOfStock',
            }))
        ),
        catchError(() => of([] as ShowcaseExperience[]))
      );
  }

  fallback(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.src.endsWith(this.defaultImage)) {
      img.src = this.defaultImage;
    }
  }

  /** Nomes da busca vêm com <em> de destaque */
  protected clean(text?: string): string {
    return (text ?? '').replace(/<[^>]+>/g, '').trim();
  }
}
