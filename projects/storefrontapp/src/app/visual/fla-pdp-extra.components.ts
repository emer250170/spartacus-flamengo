import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  HostListener,
  inject,
} from '@angular/core';
import { ImageGroup, Product, ProductScope } from '@spartacus/core';
import {
  CurrentProductService,
  TAB_MODE,
  TabConfig,
  TabParagraphContainerComponent,
} from '@spartacus/storefront';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { filter, map, startWith } from 'rxjs/operators';

interface Gallery {
  name: string;
  /** Fotos na página: a grande e um número par embaixo (as duas colunas sem buraco) */
  images: ImageGroup[];
  /** Todas as fotos, para o ampliar (anterior/próxima) */
  all: ImageGroup[];
}

/** Na página, até 5 fotos: a grande e mais 4 em duas colunas (mockup tela 4) */
const MAX_PHOTOS = 5;

/** Aba de especificações (uid do componente no CMS) */
const SPECS_TAB = 'ProductSpecsTabComponent';

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
          (click)="open(i, g.all.length)"
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
    if (!list.length) {
      return null;
    }
    // a grande + um número par de fotos menores (4 fotos: a grande e 2; 5 ou mais: a grande e 4)
    const small = Math.min(list.length - 1, MAX_PHOTOS - 1);
    const shown = 1 + small - (small % 2);
    return {
      name: product.name ?? '',
      images: list.slice(0, shown),
      all: list,
    };
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
export class FlaPdpTabsComponent
  extends TabParagraphContainerComponent
  implements AfterViewInit
{
  override tabConfig$: BehaviorSubject<TabConfig> = new AccordionConfig$({
    openTabs: [0],
    mode: TAB_MODE.ACCORDIAN,
  });

  protected currentProduct = inject(CurrentProductService);

  /** Produto sem especificações (classificações): a sanfona "Especificações" não aparece */
  protected readonly hasSpecs$: Observable<boolean> = this.currentProduct
    .getProduct(ProductScope.ATTRIBUTES)
    .pipe(
      map((p) =>
        (p?.classifications ?? []).some((c) => (c.features ?? []).length > 0)
      ),
      startWith(true)
    );

  override ngAfterViewInit(): void {
    super.ngAfterViewInit();
    const tabs$ = this.tabs$;
    this.tabs$ = combineLatest([tabs$, this.hasSpecs$]).pipe(
      map(([tabs, specs]) =>
        specs
          ? tabs
          : tabs.filter((t) => !String(t.headerKey ?? '').includes(SPECS_TAB))
      )
    );
  }
}
