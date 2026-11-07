import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  HostListener,
} from '@angular/core';
import { ImageGroup, Product, ProductScope } from '@spartacus/core';
import {
  CurrentProductService,
  TAB_MODE,
  TabConfig,
  TabParagraphContainerComponent,
} from '@spartacus/storefront';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';

interface Gallery {
  name: string;
  images: ImageGroup[];
}

/** Até 5 fotos: a grande e mais 4 em duas colunas (mockup tela 4) */
const MAX_PHOTOS = 5;

/**
 * Fotos do produto no visual do mockup (07/11, tela 4): a primeira grande e as outras em grade
 * de duas colunas embaixo, sem carrossel de miniaturas. Clicar numa foto abre ela ampliada
 * (com anterior/próxima, Esc fecha). No lugar do ProductImagesComponent do Spartacus.
 */
@Component({
  selector: 'fla-pdp-gallery',
  template: `
    <ng-container *ngIf="gallery$ | async as g">
      <div class="fla-gal">
        <button
          type="button"
          *ngFor="let img of g.images; let i = index"
          class="fla-gal-item"
          [class.is-main]="i === 0"
          [attr.aria-label]="'pdp.zoomPhoto' | flaT: { n: i + 1 }"
          (click)="open(i, g.images.length)"
        >
          <cx-media [container]="img" format="zoom" [alt]="g.name"></cx-media>
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
          *ngIf="g.images.length > 1"
          type="button"
          class="fla-gal-nav is-prev"
          [attr.aria-label]="'pdp.zoomPrev' | flaT"
          (click)="step(-1, $event)"
        >
          ‹
        </button>
        <div class="fla-gal-big" (click)="$event.stopPropagation()">
          <cx-media
            [container]="g.images[zoom]"
            format="zoom"
            [alt]="g.name"
          ></cx-media>
        </div>
        <button
          *ngIf="g.images.length > 1"
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
  /** Foto ampliada (índice) ou null */
  zoom: number | null = null;
  protected count = 0;

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
    return list.length
      ? { name: product.name ?? '', images: list.slice(0, MAX_PHOTOS) }
      : null;
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

/** Configuração das abas que sempre vira sanfona (a primeira aberta) */
class AccordionConfig$ extends BehaviorSubject<TabConfig> {
  override next(value: TabConfig): void {
    super.next({ ...value, mode: TAB_MODE.ACCORDIAN, openTabs: [0] });
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
export class FlaPdpTabsComponent extends TabParagraphContainerComponent {
  override tabConfig$: BehaviorSubject<TabConfig> = new AccordionConfig$({
    openTabs: [0],
    mode: TAB_MODE.ACCORDIAN,
  });
}
