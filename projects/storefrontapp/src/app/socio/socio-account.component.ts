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
  GlobalMessageService,
  GlobalMessageType,
  WindowRef,
} from '@spartacus/core';
import {
  Observable,
  Subscription,
  combineLatest,
  forkJoin,
  of,
  raceWith,
  timer,
} from 'rxjs';
import { filter, map, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  NewCard,
  SocioAccountService,
  SocioCancellation,
  SocioCard,
  SocioPlanOrder,
} from './socio-account.service';
import { SocioExit, SocioExitInfo, SocioExitService } from './socio-exit.service';
import { Membership, SocioPlan } from './socio.model';
import { SocioService } from './socio.service';

/** Assinatura anual: 12 mensalidades */
const TERM = 12;

export interface SocioCharge {
  date: Date;
  label: string;
  amount: number;
  card?: SocioCard;
  order?: string;
}

export interface SocioRating {
  score: number;
  level: 'bronze' | 'silver' | 'gold' | 'diamond';
  stars: number;
  months: number;
  /** % para as barras */
  tenure: number;
  punctuality: number;
}

/**
 * Minha conta > Meu sócio-torcedor (/my-account/socio-torcedor).
 *
 * Dados gerais (plano e situação), pagamentos (próxima cobrança, histórico,
 * cartão das mensalidades, upgrade e cancelamento), benefícios e rating.
 * Plano, onda e descontos vêm do SocioService; pagamentos dos pedidos do plano
 * (SocioAccountService). O rating é ilustrativo (demonstração): cresce com o
 * tempo de plano e com as mensalidades em dia.
 * Mapeado no CMS como CMSFlexComponent (flexType SocioAccountComponent).
 */
@Component({
  selector: 'fla-socio-account',
  templateUrl: './socio-account.component.html',
  styleUrls: ['./socio-account.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioAccountComponent implements OnInit, OnDestroy {
  loading = true;
  membership?: Membership;
  plan: SocioPlan | null = null;
  plans: SocioPlan[] = [];

  orders: SocioPlanOrder[] = [];
  currentOrder?: SocioPlanOrder;
  cards: SocioCard[] = [];
  /** Cancelamento solicitado e ainda não atendido (sócio ativo) */
  cancellation: SocioCancellation | null = null;
  /** Ex-sócio: o atendimento confirmou o cancelamento e o plano foi encerrado */
  ended: SocioCancellation | null = null;
  /** Regras de saída: simulação (sócio) e valor do cancelamento confirmado (ex-sócio) */
  exit: SocioExitInfo = { enabled: false, quote: null, settlement: null };

  start?: Date;
  endDate?: Date;
  guests = 0;
  monthly = 0;
  paidCount = 0;
  nextCharge?: SocioCharge;
  history: SocioCharge[] = [];
  upgrades: SocioPlan[] = [];
  rating?: SocioRating;

  // cartão
  cardPanel = false;
  newCardForm = false;
  selectedCard?: string;
  newCard: NewCard = this.emptyCard();
  cardBusy = false;
  cardError = '';

  // cancelamento
  cancelPanel = false;
  cancelReason = '';
  cancelNote = '';
  cancelBusy = false;
  readonly cancelReasons = ['price', 'games', 'moving', 'service', 'other'];

  // upgrade
  upgrading?: string;

  readonly months = Array.from({ length: 12 }, (_, i) =>
    String(i + 1).padStart(2, '0')
  );
  readonly years = Array.from({ length: 11 }, (_, i) =>
    String(new Date().getFullYear() + i)
  );

  protected subscription = new Subscription();

  constructor(
    protected socio: SocioService,
    protected account: SocioAccountService,
    protected exitService: SocioExitService,
    protected activeCart: ActiveCartFacade,
    protected globalMessages: GlobalMessageService,
    protected router: Router,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef,
    protected winRef: WindowRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.socio.getMembership(), this.socio.getPlans()])
        .pipe(
          switchMap(([membership, plans]) => {
            this.membership = membership;
            this.plan = membership.member;
            this.plans = plans;
            // plano no carrinho: a página só convida a finalizar a compra
            if (!membership.member && membership.inCart) {
              return of(null);
            }
            // não é sócio: pode ser ex-sócio (cancelamento confirmado)
            return this.loadAccount(plans);
          })
        )
        .subscribe((data) => {
          this.ended = null;
          this.cancellation = null;
          if (data) {
            this.orders = data.orders;
            this.cards = data.cards;
            this.exit = data.exit;
            const c = data.cancellation;
            if (this.plan) {
              // sócio: só um pedido ainda aberto conta (um antigo fechado é de um plano anterior)
              this.cancellation = c && !c.closed ? c : null;
            } else if (c?.closed && this.orders.length) {
              // ex-sócio: mostra o plano que foi cancelado, inativo
              this.ended = c;
              this.plan =
                this.plans.find((p) => p.code === this.orders[0].planCode) ??
                null;
            }
          }
          this.compute();
          this.loading = false;
          this.cd.markForCheck();
        })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  // ---------- textos ----------

  t(key: string, params?: Record<string, unknown>): string {
    return this.i18n.t(key, params);
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  date(value?: Date, long = false): string {
    if (!value) {
      return '—';
    }
    return value.toLocaleDateString(
      this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US',
      {
        day: '2-digit',
        month: long ? 'long' : '2-digit',
        year: 'numeric',
        timeZone: 'America/Sao_Paulo',
      }
    );
  }

  monthYear(value?: Date): string {
    if (!value) {
      return '—';
    }
    return value.toLocaleDateString(
      this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US',
      {
        month: 'long',
        year: 'numeric',
        timeZone: 'America/Sao_Paulo',
      }
    );
  }

  waveLabel(plan: SocioPlan): string {
    return plan.wave > 0
      ? this.t('wave.n', { n: plan.wave })
      : this.t('wave.general');
  }

  cardLabel(card?: SocioCard): string {
    if (!card) {
      return '—';
    }
    const last = card.number.replace(/\D/g, '').slice(-4);
    return `${card.typeName || card.type} •••• ${last}`;
  }

  /** Sócio com os benefícios suspensos por mensalidade em atraso */
  get suspended(): boolean {
    return (
      !this.ended &&
      !!this.membership?.suspended &&
      !!this.membership?.member
    );
  }

  /** Situação do plano: ativo, suspenso, cancelamento solicitado ou inativo (encerrado) */
  get statusKey(): string {
    return this.ended
      ? 'sa.status.inactive'
      : this.cancellation
        ? 'sa.status.cancelling'
        : this.suspended
          ? 'sa.status.suspended'
          : 'sa.status.active';
  }

  get statusTone(): 'ok' | 'warn' | 'off' {
    return this.ended
      ? 'off'
      : this.cancellation || this.suspended
        ? 'warn'
        : 'ok';
  }

  /**
   * Regularizar: abre o cadastro de cartão novo; ao salvar, o SAP Commerce cobra
   * a mensalidade em atraso nele (simulado) e devolve os benefícios.
   */
  regularize(): void {
    this.cardPanel = true;
    this.newCardForm = true;
    this.cardError = '';
    this.cd.markForCheck();
    setTimeout(() =>
      this.winRef.document
        ?.getElementById('sa-card-panel')
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    );
  }

  get defaultCard(): SocioCard | undefined {
    return (
      this.cards.find((c) => c.isDefault) ??
      this.cards[0] ??
      this.currentOrder?.card
    );
  }

  get starList(): ('full' | 'half' | 'empty')[] {
    const stars = this.rating?.stars ?? 0;
    return [1, 2, 3, 4, 5].map((n) =>
      stars >= n ? 'full' : stars >= n - 0.5 ? 'half' : 'empty'
    );
  }

  /** Arco do medidor do rating (0 a 1000) */
  get gaugeDash(): string {
    const length = 251.3; // meia-volta de raio 80
    const value = Math.max(0, Math.min(1, (this.rating?.score ?? 0) / 1000));
    return `${(length * value).toFixed(1)} ${length}`;
  }

  // ---------- cartão das mensalidades ----------

  toggleCardPanel(): void {
    this.cardPanel = !this.cardPanel;
    this.newCardForm = false;
    this.cardError = '';
    this.selectedCard = this.defaultCard?.id;
  }

  useCard(): void {
    if (!this.selectedCard || this.cardBusy) {
      return;
    }
    if (this.selectedCard === this.defaultCard?.id) {
      this.cardPanel = false;
      return;
    }
    this.cardBusy = true;
    this.cardError = '';
    this.subscription.add(
      this.account
        .setDefaultCard(this.selectedCard)
        .pipe(switchMap(() => this.account.cards()))
        .subscribe({
          next: (cards) => this.cardSaved(cards),
          error: () => this.cardFailed(),
        })
    );
  }

  saveNewCard(): void {
    if (this.cardBusy) {
      return;
    }
    const digits = this.newCard.number.replace(/\D/g, '');
    if (
      !this.newCard.holder.trim() ||
      digits.length < 13 ||
      !this.newCard.month ||
      !this.newCard.year ||
      this.newCard.cvn.replace(/\D/g, '').length < 3
    ) {
      this.cardError = this.t('sa.card.invalid');
      return;
    }
    this.cardBusy = true;
    this.cardError = '';
    this.subscription.add(
      this.account
        .addCard({ ...this.newCard, type: this.cardType(digits) })
        .pipe(switchMap(() => this.account.cards()))
        .subscribe({
          next: (cards) => this.cardSaved(cards, true),
          error: (e) =>
            this.cardFailed(
              e?.message === 'no-address' ? 'sa.card.noAddress' : undefined
            ),
        })
    );
  }

  /** Bandeira pelo número (cartões de teste: Visa 4111..., Master 5555...) */
  cardType(digits: string): string {
    if (/^3[47]/.test(digits)) {
      return 'amex';
    }
    if (/^(5[1-5]|2[2-7])/.test(digits)) {
      return 'master';
    }
    return 'visa';
  }

  protected cardSaved(cards: SocioCard[], added = false): void {
    const regularized = added && this.suspended;
    this.cards = cards;
    this.cardBusy = false;
    this.cardPanel = false;
    this.newCardForm = false;
    this.newCard = this.emptyCard();
    this.globalMessages.add(
      regularized
        ? this.t('sa.regularized', { card: this.cardLabel(this.defaultCard) })
        : this.t('sa.card.saved', { card: this.cardLabel(this.defaultCard) }),
      GlobalMessageType.MSG_TYPE_CONFIRMATION
    );
    if (regularized) {
      // o SAP Commerce já tirou o torcedor dos suspensos: selo, ondas e descontos voltam
      this.socio.refreshMembership();
      this.activeCart.reloadActiveCart();
    }
    this.cd.markForCheck();
  }

  protected cardFailed(key = 'sa.card.error'): void {
    this.cardBusy = false;
    this.cardError = this.t(key);
    this.cd.markForCheck();
  }

  protected emptyCard(): NewCard {
    return {
      holder: '',
      number: '',
      type: 'visa',
      month: '',
      year: '',
      cvn: '',
    };
  }

  // ---------- upgrade ----------

  upgrade(target: SocioPlan): void {
    if (this.upgrading) {
      return;
    }
    this.upgrading = target.code;
    const entries: OrderEntry[] = [
      { product: { code: target.code }, quantity: 1 },
    ];
    const guests = target.guest ? Math.min(this.guests, target.maxGuests) : 0;
    if (guests > 0 && target.guest) {
      entries.push({ product: { code: target.guest.code }, quantity: guests });
    }
    const socioCodes = this.plans.flatMap((p) =>
      p.guest ? [p.code, p.guest.code] : [p.code]
    );
    this.subscription.add(
      this.activeCart
        .getEntries()
        .pipe(
          take(1),
          switchMap((current) => {
            current
              .filter((e) => socioCodes.includes(e.product?.code ?? ''))
              .forEach((e) => this.activeCart.removeEntry(e));
            this.activeCart.addEntries(entries);
            return this.cartSettled();
          })
        )
        .subscribe({
          next: () => {
            this.globalMessages.add(
              this.t('sa.up.added', { plan: target.name }),
              GlobalMessageType.MSG_TYPE_CONFIRMATION
            );
            this.router.navigateByUrl('/cart');
          },
          error: () => {
            this.upgrading = undefined;
            this.globalMessages.add(
              this.t('st.addError'),
              GlobalMessageType.MSG_TYPE_ERROR
            );
            this.cd.markForCheck();
          },
        })
    );
  }

  /** Diferença na mensalidade em relação ao plano atual */
  priceDiff(target: SocioPlan): number {
    return target.price - (this.plan?.price ?? 0);
  }

  // ---------- cancelamento ----------

  confirmCancel(): void {
    if (!this.plan || !this.cancelReason || this.cancelBusy) {
      return;
    }
    this.cancelBusy = true;
    const plan = this.plan;
    const quote = this.exit.quote;
    const message = [
      `${this.t('sa.cancel.reason')}: ${this.t('sa.reason.' + this.cancelReason)}`,
      this.cancelNote.trim(),
      // regras de saída: o atendimento vê o valor que o sócio viu (o relatório não conta como comentário)
      quote ? `${this.t('sa.exit.msg')}: ${this.exitText(quote)}` : '',
      `${plan.name} (${plan.code}) · ${this.t('sa.since')} ${this.date(this.start)}`,
    ]
      .filter(Boolean)
      .join('\n');
    this.subscription.add(
      this.account.requestCancellation(plan.name, message).subscribe({
        next: (cancellation) => {
          this.cancellation = cancellation;
          this.cancelBusy = false;
          this.cancelPanel = false;
          // a simulação passa a valer na data do pedido
          this.subscription.add(
            this.exitService.load().subscribe((exit) => {
              this.exit = exit;
              this.cd.markForCheck();
            })
          );
          this.globalMessages.add(
            this.t('sa.cancel.done'),
            GlobalMessageType.MSG_TYPE_CONFIRMATION
          );
          this.cd.markForCheck();
        },
        error: () => {
          this.cancelBusy = false;
          this.globalMessages.add(
            this.t('sa.cancel.error'),
            GlobalMessageType.MSG_TYPE_ERROR
          );
          this.cd.markForCheck();
        },
      })
    );
  }

  // ---------- regras de saída ----------

  /** "Reembolso de R$ 67,10 (arrependimento até 09/11/2026)" / "Multa de R$ 60,39 (…)" */
  exitText(e: SocioExit): string {
    if (e.withinWithdrawal) {
      return e.confirmed
        ? this.t('sa.exit.refundDone', { amount: this.brl(e.refund) })
        : this.t('sa.exit.refund', {
            amount: this.brl(e.refund),
            date: this.date(e.withdrawalUntil),
          });
    }
    if (e.fine <= 0) {
      return this.t('sa.exit.noFine');
    }
    return e.confirmed
      ? this.t('sa.exit.fineDone', { amount: this.brl(e.fine) })
      : this.t('sa.exit.fine', {
          amount: this.brl(e.fine),
          pct: e.finePercent,
          n: e.remainingInstallments,
          monthly: this.brl(e.monthly),
        });
  }

  /** A regra que levou ao valor */
  exitRule(e: SocioExit): string {
    return e.withinWithdrawal
      ? this.t('sa.exit.ruleIn', { days: e.withdrawalDays })
      : this.t('sa.exit.ruleOut', {
          days: e.withdrawalDays,
          date: this.date(e.withdrawalUntil),
          pct: e.finePercent,
        });
  }

  // ---------- cálculo ----------

  protected loadAccount(plans: SocioPlan[]): Observable<{
    orders: SocioPlanOrder[];
    cards: SocioCard[];
    cancellation: SocioCancellation | null;
    exit: SocioExitInfo;
  }> {
    const planCodes = plans.map((p) => p.code);
    const guestCodes = plans
      .map((p) => p.guest?.code)
      .filter((c): c is string => !!c);
    return forkJoin({
      orders: this.account.planOrders(planCodes, guestCodes),
      cards: this.account.cards(),
      cancellation: this.account.cancellation(),
      exit: this.exitService.load(),
    });
  }

  protected compute(): void {
    const plan = this.plan;
    if (!plan) {
      return;
    }
    // inativo: as contas param na data em que o plano foi encerrado
    const now = this.ended?.closedAt ?? new Date();
    this.currentOrder =
      this.orders.find((o) => o.planCode === plan.code) ?? this.orders[0];
    this.start = this.currentOrder?.created;
    this.endDate = this.start ? addMonths(this.start, TERM) : undefined;
    this.guests = this.currentOrder?.guests ?? 0;
    this.monthly = plan.price + this.guests * (plan.guest?.price ?? 0);

    // mensalidades: a 1ª na compra, as outras no mesmo dia dos meses seguintes
    const history: SocioCharge[] = [];
    this.paidCount = 0;
    this.nextCharge = undefined;
    if (this.start && this.currentOrder) {
      const elapsed = monthsBetween(this.start, now);
      this.paidCount = Math.min(TERM, elapsed + 1);
      for (let i = 0; i < this.paidCount; i++) {
        history.push(
          i === 0
            ? {
                date: this.start,
                label: this.t('sa.pay.first', { plan: plan.name }),
                amount:
                  this.currentOrder.planAmount + this.currentOrder.guestAmount,
                card: this.currentOrder.card,
                order: this.currentOrder.code,
              }
            : {
                date: addMonths(this.start, i),
                label: this.t('sa.pay.month', { n: i + 1, total: TERM }),
                amount: this.monthly,
                card: this.defaultCard,
              }
        );
      }
      this.nextCharge = this.ended
        ? undefined
        : this.paidCount < TERM
          ? {
              date: addMonths(this.start, this.paidCount),
              label: this.t('sa.pay.month', {
                n: this.paidCount + 1,
                total: TERM,
              }),
              amount: this.monthly,
            }
          : {
              date: addMonths(this.start, TERM),
              label: this.t('sa.pay.renewal'),
              amount: this.monthly,
            };
    }
    // planos anteriores (antes de um upgrade)
    this.orders
      .filter((o) => o !== this.currentOrder)
      .forEach((o) =>
        history.push({
          date: o.created,
          label: this.t('sa.pay.first', {
            plan:
              this.plans.find((p) => p.code === o.planCode)?.name ?? o.planCode,
          }),
          amount: o.planAmount + o.guestAmount,
          card: o.card,
          order: o.code,
        })
      );
    this.history = history.sort((a, b) => b.date.getTime() - a.date.getTime());

    // upgrade: planos com onda melhor, ou a mesma onda com mais benefícios
    const rank = (p: SocioPlan) => (p.wave >= 1 ? p.wave : 99);
    this.upgrades = this.ended
      ? []
      : this.plans.filter(
          (p) =>
            p.code !== plan.code &&
            !p.soldOut &&
            (rank(p) < rank(plan) ||
              (rank(p) === rank(plan) && p.price > plan.price))
        );

    this.rating = this.ended ? undefined : this.mockRating(plan, now);
  }

  /** Rating ilustrativo: tempo de plano + mensalidades em dia (sem regra de negócio) */
  protected mockRating(plan: SocioPlan, now: Date): SocioRating {
    const months = this.start ? monthsBetween(this.start, now) : 0;
    const seed = [...(plan.code + (this.start?.toISOString() ?? ''))].reduce(
      (h, c) => (h * 31 + c.charCodeAt(0)) % 997,
      7
    );
    const waveBonus = plan.wave === 1 ? 60 : plan.wave === 2 ? 35 : 15;
    const score = Math.min(
      1000,
      Math.max(300, 690 + months * 12 + waveBonus + (seed % 90))
    );
    const level =
      score >= 900
        ? 'diamond'
        : score >= 780
          ? 'gold'
          : score >= 650
            ? 'silver'
            : 'bronze';
    return {
      score,
      level,
      stars: Math.round((score / 200) * 2) / 2,
      months,
      tenure: Math.min(100, 35 + months * 5),
      punctuality: 100,
    };
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

function addMonths(date: Date, months: number): Date {
  const d = new Date(date.getTime());
  const day = d.getDate();
  d.setMonth(d.getMonth() + months);
  // 31/01 + 1 mês = último dia de fevereiro, não 03/03
  if (d.getDate() !== day) {
    d.setDate(0);
  }
  return d;
}

/** Meses completos entre duas datas */
function monthsBetween(from: Date, to: Date): number {
  let months =
    (to.getFullYear() - from.getFullYear()) * 12 +
    (to.getMonth() - from.getMonth());
  if (to.getDate() < from.getDate()) {
    months--;
  }
  return Math.max(0, months);
}
