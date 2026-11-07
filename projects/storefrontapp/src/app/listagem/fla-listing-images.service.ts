import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, OnDestroy, PLATFORM_ID } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { FlaMediaUrlService } from '../vitrine/fla-media-url.service';

interface OccImage {
  format?: string;
  imageType?: string;
  url?: string;
}

/** Cards da listagem e da busca (grade e lista) */
const ITEMS = 'cx-product-grid-item, cx-product-list-item';
const LANDSCAPE = 'fla-landscape';

/**
 * Fotos da listagem com definição (07/11). A busca do Commerce só traz as fotos de 96 e 300 px
 * ("thumbnail" e "product"), e as fotos deitadas (experiências) vêm com 300×168: no card em pé
 * (3:4, foto cobrindo a área) elas eram ampliadas mais de duas vezes e ficavam borradas.
 * Aqui cada card troca a foto pela "zoom" do produto (515 px, lida uma vez por produto na API
 * de produto) e o card de foto deitada ganha a classe fla-landscape (área 4:3 no tema).
 * Só no navegador; olha as mudanças da página e só mexe quando precisa.
 */
@Injectable({ providedIn: 'root' })
export class FlaListingImagesService implements OnDestroy {
  protected observer?: MutationObserver;
  protected scheduled = false;
  /** Foto zoom por produto ('' = sem foto zoom; ausente = ainda não pedida) */
  protected zoom = new Map<string, string>();
  protected pending = new Set<string>();

  constructor(
    protected http: HttpClient,
    protected occ: OccEndpointsService,
    protected media: FlaMediaUrlService,
    @Inject(PLATFORM_ID) protected platformId: object
  ) {}

  start(doc: Document): void {
    if (this.observer || !isPlatformBrowser(this.platformId) || !doc?.body) {
      return;
    }
    this.observer = new MutationObserver(() => this.schedule(doc));
    this.observer.observe(doc.body, { childList: true, subtree: true });
    this.schedule(doc);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  protected schedule(doc: Document): void {
    if (this.scheduled) {
      return;
    }
    this.scheduled = true;
    const run = () => {
      this.scheduled = false;
      this.apply(doc);
    };
    const win = doc.defaultView;
    if (win?.requestAnimationFrame) {
      win.requestAnimationFrame(run);
    } else {
      setTimeout(run, 50);
    }
  }

  protected apply(doc: Document): void {
    doc.querySelectorAll(ITEMS).forEach((item) => {
      const img = item.querySelector<HTMLImageElement>(
        '.cx-product-image-container img'
      );
      const code = productCode(item);
      if (!img || !code) {
        return;
      }
      this.watchShape(img);
      const url = this.zoom.get(code);
      if (url === undefined) {
        this.load(code, doc);
      } else if (url && img.getAttribute('src') !== url) {
        img.removeAttribute('srcset');
        img.setAttribute('src', url);
      }
    });
  }

  /** Card de foto deitada: classe fla-landscape (o tema troca a área para 4:3) */
  protected watchShape(img: HTMLImageElement): void {
    const mark = () => {
      const box = img.closest('.cx-product-image-container');
      if (box && img.naturalWidth && img.naturalHeight) {
        box.classList.toggle(
          LANDSCAPE,
          img.naturalWidth > img.naturalHeight * 1.15
        );
      }
    };
    if (!img.dataset['flaShape']) {
      img.dataset['flaShape'] = '1';
      img.addEventListener('load', mark);
    }
    if (img.complete) {
      mark();
    }
  }

  protected load(code: string, doc: Document): void {
    if (this.pending.has(code)) {
      return;
    }
    this.pending.add(code);
    this.http
      .get<{
        images?: OccImage[];
      }>(`${this.occ.getBaseUrl()}/products/${encodeURIComponent(code)}`, { params: { fields: 'images(FULL)' } })
      .subscribe({
        next: (p) => {
          const zoom = (p?.images ?? []).find(
            (i) => i.imageType === 'PRIMARY' && i.format === 'zoom'
          );
          this.zoom.set(code, this.media.url(zoom?.url) ?? '');
        },
        error: () => this.zoom.set(code, ''),
        complete: () => {
          this.pending.delete(code);
          this.schedule(doc);
        },
      });
  }
}

/** Código do produto pelo link do card (/product/{código}/...) */
function productCode(item: Element): string | undefined {
  const href = item.querySelector('a[href*="/product/"]')?.getAttribute('href');
  const code = href?.split('/product/')[1]?.split(/[/?#]/)[0];
  return code ? decodeURIComponent(code) : undefined;
}
