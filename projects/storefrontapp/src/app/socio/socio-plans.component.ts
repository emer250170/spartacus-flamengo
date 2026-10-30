import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { ActiveCartFacade, OrderEntry } from '@spartacus/cart/base/root';
import {
  AuthRedirectService,
  AuthService,
  GlobalMessageService,
  GlobalMessageType,
  RoutingService,
} from '@spartacus/core';
import { Observable, Subscription, raceWith, timer } from 'rxjs';
import { filter, map, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { GENERAL_SALE_WAVE, SocioPlan } from './socio.model';
import { SocioService } from './socio.service';

/** Benefícios que valem para os três planos (textos em fla-texts: st.b.*) */
const COMMON_BENEFITS = [
  'shipping',
  'cashback',
  'custom',
  'prizes',
  'partners',
  'package',
];

/**
 * Página /socio-torcedor: os três planos Nação Maraca com preço, benefícios
 * e convidados. Escolher o plano o põe no carrinho do SAP Commerce e volta
 * para a escolha de jogos: enquanto o plano estiver no carrinho, o torcedor já
 * compra na onda do plano e com o desconto dele (tirou o plano, perde os dois).
 * Quando o pedido fecha, ele vira sócio de vez.
 * Mapeado no CMS como CMSFlexComponent (flexType SocioPlansComponent).
 */
@Component({
  selector: 'fla-socio-plans',
  templateUrl: './socio-plans.component.html',
  styleUrls: ['./socio-plans.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioPlansComponent implements OnInit, OnDestroy {
  readonly commonBenefits = COMMON_BENEFITS;
  /** Ondas 1 a 3 e venda geral */
  readonly waves = Array.from({ length: GENERAL_SALE_WAVE }, (_, i) => i + 1);

  plans: SocioPlan[] = [];
  loading = true;
  loggedIn = false;
  /** Plano que vale para ingressos (comprado ou no carrinho) */
  level: number | null = null;
  /** Plano comprado */
  member: number | null = null;
  /** Plano no carrinho */
  inCart: number | null = null;
  /** Jogo de onde o torcedor veio (volta para ele depois de escolher o plano) */
  fromMatch?: string;
  guests: Record<string, number> = {};
  submitting?: string;

  private subscription = new Subscription();

  constructor(
    protected socio: SocioService,
    protected activeCart: ActiveCartFacade,
    protected auth: AuthService,
    protected authRedirect: AuthRedirectService,
    protected routing: RoutingService,
    protected router: Router,
    protected globalMessages: GlobalMessageService,
    protected cd: ChangeDetectorRef,
    protected i18n: FlaI18nService
  ) {}

  ngOnInit(): void {
    // planos no idioma do site (nome e benefícios vêm da API)
    this.subscription.add(
      this.i18n.lang$
        .pipe(switchMap(() => this.socio.getPlans()))
        .subscribe((plans) => {
          this.plans = plans;
          this.loading = false;
          this.cd.markForCheck();
        })
    );
    this.subscription.add(
      this.auth.isUserLoggedIn().subscribe((loggedIn) => {
        this.loggedIn = loggedIn;
        this.cd.markForCheck();
      })
    );
    this.fromMatch =
      this.router.parseUrl(this.router.url).queryParams['jogo'] || undefined;
    this.socio.refreshMembership();
    this.subscription.add(
      this.socio.getMembership().subscribe((m) => {
        this.level = m.level;
        this.member = m.member;
        this.inCart = m.inCart;
        this.cd.markForCheck();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  guestCount(plan: SocioPlan): number {
    return this.guests[plan.code] ?? 0;
  }

  changeGuests(plan: SocioPlan, delta: number): void {
    const value = Math.max(
      0,
      Math.min(plan.maxGuests, this.guestCount(plan) + delta)
    );
    this.guests = { ...this.guests, [plan.code]: value };
  }

  monthly(plan: SocioPlan): number {
    return plan.price + this.guestCount(plan) * (plan.guest?.price ?? 0);
  }

  /** Plano já comprado ou já no carrinho */
  isCurrent(plan: SocioPlan): boolean {
    return plan.level === this.member || plan.level === this.inCart;
  }

  buttonLabel(plan: SocioPlan): string {
    const t = (key: string) => this.i18n.t('st.btn.' + key);
    if (plan.soldOut) {
      return t('soldOut');
    }
    if (plan.level === this.member) {
      return t('mine');
    }
    if (plan.level === this.inCart) {
      return t('inCart');
    }
    if (this.level) {
      return t('switch');
    }
    return this.loggedIn ? t('join') : t('login');
  }

  /**
   * Põe o plano (e os convidados) no carrinho e volta para a escolha de jogos:
   * enquanto o plano estiver no carrinho, a prioridade de compra e o desconto
   * do plano já valem para os ingressos. O pagamento é um só, no fim.
   */
  subscribe(plan: SocioPlan): void {
    if (plan.soldOut || this.isCurrent(plan) || this.submitting) {
      return;
    }
    if (!this.loggedIn) {
      this.authRedirect.setRedirectUrl(this.router.url);
      this.routing.go({ cxRoute: 'login' });
      return;
    }
    this.submitting = plan.code;
    this.cd.markForCheck();

    const entries: OrderEntry[] = [
      { product: { code: plan.code }, quantity: 1 },
    ];
    const guests = this.guestCount(plan);
    if (guests > 0 && plan.guest) {
      entries.push({ product: { code: plan.guest.code }, quantity: guests });
    }

    this.subscription.add(
      this.activeCart
        .getEntries()
        .pipe(
          take(1),
          switchMap((current) => {
            // um plano por pedido: tira outro plano ou convidado que já esteja no carrinho
            current
              .filter((e) => e.product?.code?.startsWith('NACAO-'))
              .forEach((e) => this.activeCart.removeEntry(e));
            this.activeCart.addEntries(entries);
            return this.cartSettled();
          })
        )
        .subscribe({
          next: () => {
            this.globalMessages.add(
              this.i18n.t('st.added', { plan: plan.name, d: plan.discount }),
              GlobalMessageType.MSG_TYPE_CONFIRMATION
            );
            this.router.navigateByUrl(
              this.fromMatch
                ? `/matchday?jogo=${encodeURIComponent(this.fromMatch)}`
                : '/matchday'
            );
          },
          error: () => {
            this.submitting = undefined;
            this.globalMessages.add(
              this.i18n.t('st.addError'),
              GlobalMessageType.MSG_TYPE_ERROR
            );
            this.cd.markForCheck();
          },
        })
    );
  }

  /** Onda do plano do torcedor */
  get myWave(): number {
    return this.socio.memberWave(this.level);
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  trackByCode(_: number, item: { code: string }): string {
    return item.code;
  }

  /** Espera o carrinho processar as alterações (com limite de 15 s) */
  protected cartSettled(): Observable<unknown> {
    return this.activeCart.isStable().pipe(
      filter((stable) => !stable),
      take(1),
      switchMap(() =>
        this.activeCart.isStable().pipe(filter(Boolean), take(1))
      ),
      raceWith(timer(15000)),
      take(1),
      map(() => undefined)
    );
  }
}
