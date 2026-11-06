import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Inject,
  NgZone,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { FlaI18nService } from '../i18n/fla-i18n.service';

/**
 * Logo sem tooltip (06/11): o banner do logo vem do CMS com o título "SAP Commerce", que o
 * Spartacus põe como `title` no link e na imagem — passando o mouse aparecia "SAP Commerce".
 * Este componente (sem nada na tela) fica no slot SiteLogo, tira os `title` do logo e dá ao
 * link e à imagem o nome "Loja Oficial do Flamengo" para leitores de tela. Refaz quando o
 * banner é redesenhado (troca de idioma, navegação).
 */
@Component({
  selector: 'fla-logo-label',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaLogoLabelComponent implements OnInit, OnDestroy {
  protected observer?: MutationObserver;
  protected sub?: Subscription;

  constructor(
    protected host: ElementRef<HTMLElement>,
    protected i18n: FlaI18nService,
    protected zone: NgZone,
    @Inject(PLATFORM_ID) protected platformId: object
  ) {}

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const slot = this.host.nativeElement.closest('cx-page-slot');
    if (!slot) {
      return;
    }
    this.zone.runOutsideAngular(() => {
      this.observer = new MutationObserver(() => this.fix(slot));
      this.observer.observe(slot, {
        subtree: true,
        childList: true,
        attributes: true,
        attributeFilter: ['title', 'alt', 'aria-label'],
      });
    });
    this.sub = this.i18n.lang$.subscribe(() => this.fix(slot));
  }

  protected fix(slot: Element): void {
    const label = this.i18n.t('hd.logo');
    slot.querySelectorAll('cx-banner [title]').forEach((el) => {
      el.removeAttribute('title');
    });
    slot.querySelectorAll('cx-banner a').forEach((a) => {
      if (a.getAttribute('aria-label') !== label) {
        a.setAttribute('aria-label', label);
      }
    });
    slot.querySelectorAll('cx-banner img').forEach((img) => {
      if (img.getAttribute('alt') !== label) {
        img.setAttribute('alt', label);
      }
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    this.sub?.unsubscribe();
  }
}
