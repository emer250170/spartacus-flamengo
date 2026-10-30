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
import { SocioPlan, WAVE_LABELS } from './socio.model';
import { SocioService } from './socio.service';

/** Benefícios que valem para os três planos */
const COMMON_BENEFITS = [
  { title: 'Frete grátis', text: 'Nas compras da loja oficial.' },
  { title: 'Cashback', text: 'Parte do valor das compras volta para você.' },
  {
    title: 'Personalização',
    text: 'Nome e número no manto sem custo extra.',
  },
  { title: 'Prêmios e experiências', text: 'Sorteios e ações exclusivas.' },
  { title: 'Rede de parceiros', text: 'Descontos em marcas parceiras.' },
  {
    title: 'Pacote Maracanã',
    text: 'Direito à compra do pacote anual de jogos.',
  },
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
  readonly waves = Object.entries(WAVE_LABELS).map(([k, label]) => ({
    wave: Number(k),
    label,
  }));

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
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.socio.getPlans().subscribe((plans) => {
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
    if (plan.soldOut) {
      return 'Esgotado';
    }
    if (plan.level === this.member) {
      return 'Seu plano';
    }
    if (plan.level === this.inCart) {
      return 'No carrinho';
    }
    if (this.level) {
      return 'Trocar para este plano';
    }
    return this.loggedIn ? 'Quero ser sócio' : 'Entrar e assinar';
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
              `${plan.name} no carrinho: a prioridade de compra e o desconto de ${plan.discount}% já valem para os seus ingressos.`,
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
              'Não foi possível incluir o plano agora. Tente de novo.',
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
    return value.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    });
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
