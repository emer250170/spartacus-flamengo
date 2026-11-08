import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  HostListener,
  OnInit,
} from '@angular/core';
import { ImageGroup, Product, ProductScope } from '@spartacus/core';
import {
  BREAKPOINT,
  CurrentProductService,
  TAB_MODE,
  TabConfig,
  TabParagraphContainerComponent,
} from '@spartacus/storefront';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, map, take } from 'rxjs/operators';

interface Gallery {
  name: string;
  /** Todas as fotos: a grande mostra a escolhida; o carrossel embaixo, todas */
  all: ImageGroup[];
}

/** Única aba que fica (uid do componente no CMS): a descrição, "Sobre o produto" (07/11) */
const DETAILS_TAB = 'ProductDetailsTabComponent';

/**
 * Fotos do produto (07/11, mockup tela 4 e pedido do Emerson): a foto grande e, embaixo, as
 * fotos de detalhe num carrossel (rola de lado, setas quando não cabem). Clicar numa foto do
 * carrossel troca a grande; clicar na grande abre ela ampliada (anterior/próxima, Esc fecha).
 * No lugar do ProductImagesComponent do Spartacus.
 */
@Component({
  selector: 'fla-pdp-gallery',
  template: `
    <ng-container *ngIf="gallery$ | async as g">
      <button
        #mainBtn
        type="button"
        class="fla-gal-main"
        [class.is-landscape]="landscape"
        [attr.aria-label]="'pdp.zoomPhoto' | flaT: { n: current + 1 }"
        (click)="open(current, g.all.length)"
      >
        <cx-media
          [container]="g.all[current]"
          format="zoom"
          [alt]="g.name"
          (loaded)="shape(mainBtn)"
        ></cx-media>
      </button>

      <div class="fla-gal-strip" *ngIf="g.all.length > 1">
        <button
          *ngIf="g.all.length > visibleThumbs"
          type="button"
          class="fla-gal-arrow is-prev"
          [attr.aria-label]="'pdp.zoomPrev' | flaT"
          (click)="scrollThumbs(track, -1)"
        >
          ‹
        </button>
        <div class="fla-gal-track" #track>
          <button
            type="button"
            *ngFor="let img of g.all; let i = index"
            class="fla-gal-thumb"
            [class.is-active]="i === current"
            [attr.aria-pressed]="i === current"
            [attr.aria-label]="'pdp.showPhoto' | flaT: { n: i + 1 }"
            (click)="current = i"
          >
            <cx-media [container]="img" format="product" [alt]="''"></cx-media>
          </button>
        </div>
        <button
          *ngIf="g.all.length > visibleThumbs"
          type="button"
          class="fla-gal-arrow is-next"
          [attr.aria-label]="'pdp.zoomNext' | flaT"
          (click)="scrollThumbs(track, 1)"
        >
          ›
        </button>
      </div>

      <div
        *ngIf="zoom !== null"
        class="fla-gal-zoom"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="g.name"
        (click)="close()"
      >
        <button
          type="button"
          class="fla-gal-close"
          [attr.aria-label]="'pdp.zoomClose' | flaT"
          (click)="close()"
        >
          ×
        </button>
        <button
          *ngIf="g.all.length > 1"
          type="button"
          class="fla-gal-nav is-prev"
          [attr.aria-label]="'pdp.zoomPrev' | flaT"
          (click)="step(-1, $event)"
        >
          ‹
        </button>
        <div class="fla-gal-big" (click)="$event.stopPropagation()">
          <cx-media
            [container]="g.all[zoom]"
            format="zoom"
            [alt]="g.name"
          ></cx-media>
        </div>
        <button
          *ngIf="g.all.length > 1"
          type="button"
          class="fla-gal-nav is-next"
          [attr.aria-label]="'pdp.zoomNext' | flaT"
          (click)="step(1, $event)"
        >
          ›
        </button>
      </div>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPdpGalleryComponent {
  /** Foto mostrada grande */
  current = 0;
  /** Foto grande deitada (experiências): área 16:9 em vez de em pé, sem ampliar demais */
  landscape = false;
  /** Fotos que cabem no carrossel sem rolar (setas a partir daí) */
  readonly visibleThumbs = 4;
  /** Foto ampliada (índice) ou null */
  zoom: number | null = null;
  protected count = 0;
  protected lastCode?: string;

  readonly gallery$: Observable<Gallery | null> = this.currentProduct
    .getProduct(ProductScope.DETAILS)
    .pipe(
      filter((p): p is Product => !!p),
      map((p) => this.toGallery(p))
    );

  constructor(
    protected currentProduct: CurrentProductService,
    protected cd: ChangeDetectorRef
  ) {}

  protected toGallery(product: Product): Gallery | null {
    const images = product.images ?? {};
    const gallery = images['GALLERY'];
    const primary = images['PRIMARY'];
    const list: ImageGroup[] = Array.isArray(gallery)
      ? gallery
      : gallery
        ? [gallery]
        : [];
    if (!list.length && primary && !Array.isArray(primary)) {
      list.push(primary);
    }
    if (product.code !== this.lastCode) {
      this.lastCode = product.code;
      this.current = 0;
    }
    return list.length ? { name: product.name ?? '', all: list } : null;
  }

  /** Formato da foto grande, quando ela carrega */
  shape(box: HTMLElement): void {
    const img = box.querySelector('img');
    const landscape =
      !!img?.naturalWidth && img.naturalWidth > img.naturalHeight * 1.15;
    if (landscape !== this.landscape) {
      this.landscape = landscape;
      this.cd.markForCheck();
    }
  }

  /** Setas do carrossel: rola uma "página" de fotos */
  scrollThumbs(track: HTMLElement, direction: number): void {
    track.scrollBy({ left: direction * track.clientWidth, behavior: 'smooth' });
  }

  open(index: number, count: number): void {
    this.zoom = index;
    this.count = count;
  }

  close(): void {
    this.zoom = null;
    this.cd.markForCheck();
  }

  step(delta: number, event?: Event): void {
    event?.stopPropagation();
    if (this.zoom === null || !this.count) {
      return;
    }
    this.zoom = (this.zoom + delta + this.count) % this.count;
    this.cd.markForCheck();
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (this.zoom === null) {
      return;
    }
    if (event.key === 'Escape') {
      this.close();
    } else if (event.key === 'ArrowLeft') {
      this.step(-1);
    } else if (event.key === 'ArrowRight') {
      this.step(1);
    }
  }
}

/**
 * Configuração das abas: na página do produto vira sanfona (a primeira aberta); nos outros contêineres de
 * abas (ex.: Meus pedidos: Todos os pedidos / Devoluções) fica a do Spartacus (08/11: antes valia para todos e
 * as abas de Meus pedidos sumiam).
 */
class AccordionConfig$ extends BehaviorSubject<TabConfig> {
  sanfona = true;

  override next(value: TabConfig): void {
    super.next(
      this.sanfona
        ? { ...value, mode: TAB_MODE.ACCORDIAN, openTabs: [0] }
        : value
    );
  }
}

/**
 * Abas da página do produto como sanfonas (07/11, decisão do Emerson): "Sobre o produto"
 * aberta e Especificações, Avaliações e Entrega fechadas, na coluna da direita (o tema põe o
 * slot Tabs embaixo do resumo). Mesmo componente do Spartacus, só o modo muda.
 */
@Component({
  selector: 'cx-tab-paragraph-container',
  template: `
    <ng-container *ngIf="components$ | async as components">
      <cx-tab [tabs]="tabs$ | async" [config]="tabConfig$ | async">
        <ng-template
          *ngFor="let component of components; let i = index"
          [cxOutlet]="component.flexType"
          #tabRef
        >
          <ng-container
            [cxComponentWrapper]="component"
            (cxComponentRef)="tabCompLoaded($event, component.uid)"
          ></ng-container>
        </ng-template>
      </cx-tab>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaPdpTabsComponent
  extends TabParagraphContainerComponent
  implements AfterViewInit, OnInit
{
  override tabConfig$: AccordionConfig$ = new AccordionConfig$({
    openTabs: [0],
    mode: TAB_MODE.ACCORDIAN,
  });

  override ngOnInit(): void {
    super.ngOnInit();
    // só o contêiner da página do produto (o que tem a descrição) vira "Sobre o produto"
    this.componentData.data$.pipe(take(1)).subscribe((d) => {
      const pdp = String(d?.components ?? '').includes(DETAILS_TAB);
      if (!pdp) {
        this.tabConfig$.sanfona = false;
        this.tabConfig$.next({ openTabs: [0], breakpoint: BREAKPOINT.md });
      }
    });
  }

  /** Sem sanfona (Emerson, 07/11): só a descrição, como seção "Sobre o produto" (o tema tira o +/–) */
  override ngAfterViewInit(): void {
    super.ngAfterViewInit();
    this.tabs$ = this.tabs$.pipe(
      map((tabs) =>
        tabs.some((t) => String(t.headerKey ?? '').includes(DETAILS_TAB))
          ? tabs.filter((t) => String(t.headerKey ?? '').includes(DETAILS_TAB))
          : tabs
      )
    );
  }
}
