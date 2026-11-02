import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { WindowRef } from '@spartacus/core';
import { Subscription, combineLatest } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioService } from './socio.service';
import { SocioTerm, SocioTermService } from './socio-term.service';

/**
 * Revisão do checkout com plano de sócio no carrinho: "Li e aceito o Termo do
 * Nação v…" (item 4 do plano de evolução). Entra antes do "Finalizar pedido"
 * pelo outlet do componente CMS CheckoutPlaceOrder. Marcar grava o aceite no
 * servidor (com o carrinho); sem ele o clique em Finalizar pedido é segurado
 * aqui, e o servidor também recusa o pedido. Sem plano no carrinho, sem versão
 * publicada ou com o Termo desligado, não aparece nada.
 */
@Component({
  selector: 'fla-socio-term-checkout',
  templateUrl: './socio-term-checkout.component.html',
  styleUrls: ['./socio-term.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioTermCheckoutComponent implements OnInit, OnDestroy {
  term?: SocioTerm;
  planName?: string;
  cartId?: string;
  checked = false;
  saving = false;
  error = '';

  private subscription = new Subscription();
  private readonly guard = (event: Event) => this.holdPlaceOrder(event);

  constructor(
    protected terms: SocioTermService,
    protected socio: SocioService,
    protected activeCart: ActiveCartFacade,
    protected i18n: FlaI18nService,
    protected winRef: WindowRef,
    protected host: ElementRef<HTMLElement>,
    protected cd: ChangeDetectorRef
  ) {}

  /** A caixa aparece (e é obrigatória) */
  get required(): boolean {
    return !!this.term?.enabled && !!this.term?.version && !!this.planName;
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([
        this.terms.term$,
        this.socio.getMembership().pipe(map((m) => m.inCart?.name)),
        this.activeCart.getActiveCartId().pipe(distinctUntilChanged()),
      ]).subscribe(([term, planName, cartId]) => {
        if (cartId !== this.cartId || term.version !== this.term?.version) {
          this.checked = false;
        }
        this.term = term;
        this.planName = planName;
        this.cartId = cartId;
        this.cd.markForCheck();
      })
    );
    // captura: roda antes do clique do botão do Spartacus
    this.winRef.document?.addEventListener('click', this.guard, true);
  }

  ngOnDestroy(): void {
    this.winRef.document?.removeEventListener('click', this.guard, true);
    this.subscription.unsubscribe();
  }

  toggle(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.error = '';
    if (!input.checked) {
      this.checked = false;
      return;
    }
    this.saving = true;
    this.terms.accept(this.cartId).subscribe({
      next: () => {
        this.checked = true;
        this.saving = false;
        this.cd.markForCheck();
      },
      error: (e: Error) => {
        input.checked = false;
        this.checked = false;
        this.saving = false;
        this.error = e.message || this.i18n.t('st.error');
        this.cd.markForCheck();
      },
    });
  }

  /** Clique em "Finalizar pedido" sem o aceite: segura e mostra o aviso */
  protected holdPlaceOrder(event: Event): void {
    if (!this.required || this.checked) {
      return;
    }
    const target = event.target as Element | null;
    if (!target?.closest?.('cx-place-order button')) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    this.error = this.i18n.t('st.checkout.required', {
      version: this.term?.version ?? '',
    });
    this.cd.markForCheck();
    this.host.nativeElement.scrollIntoView({ block: 'center' });
  }
}
