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
  WindowRef,
} from '@spartacus/core';
import {
  Observable,
  Subscription,
  combineLatest,
  concat,
  defer,
  interval,
  of,
  raceWith,
  timer,
} from 'rxjs';
import {
  debounceTime,
  filter,
  last,
  map,
  switchMap,
  take,
} from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioPlan, WaveStatus } from '../socio/socio.model';
import { SocioService } from '../socio/socio.service';
import { MatchdayDataService } from './matchday-data.service';
import { MatchdayHoldWatcherService } from './matchday-hold-watcher.service';
import {
  MATCHDAY_HOLD_MINUTES,
  MatchdayCatalog,
  MatchdayHold,
  MatchdayLoginIntent,
  MatchdayMatch,
  MatchdayProduct,
  MatchdaySector,
} from './matchday.model';
import { StadiumSectorView } from './matchday-stadium-map.component';

interface SummaryLine {
  label: string;
  detail: string;
  price: number;
}

interface SeatCell {
  id: string;
  number: number;
  occupied: boolean;
  /** Código da poltrona no catálogo (modelo poltrona como produto) */
  code?: string;
}

/** Etapas (textos em fla-texts: md.step.N) */
const STEPS = [0, 1, 2, 3, 4, 5];
const SEAT_ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
const SEATS_PER_ROW = 12;
const NO_PARKING = 'none';

/**
 * "Monte o seu dia de jogo": ingresso com setor e poltrona, estacionamento,
 * comidas e bebidas e a camisa nova num só carrinho do SAP Commerce Cloud.
 * Mapeado no CMS como CMSFlexComponent (flexType MatchdayJourneyComponent)
 * na página de conteúdo /matchday.
 */
@Component({
  selector: 'fla-matchday-journey',
  templateUrl: './matchday-journey.component.html',
  styleUrls: ['./matchday-journey.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchdayJourneyComponent implements OnInit, OnDestroy {
  readonly steps = STEPS;
  readonly noParking = NO_PARKING;
  readonly seatRows = SEAT_ROWS;

  catalog?: MatchdayCatalog;
  loading = true;
  loadError = false;
  submitting = false;

  step = 0;
  match?: MatchdayMatch;
  sector?: MatchdaySector;
  seat?: string;
  /** Item de ingresso que vai para o carrinho: a poltrona (ou o setor, no modelo antigo) */
  ticketCode?: string;
  seatMap: { row: string; seats: SeatCell[] }[] = [];
  parking = NO_PARKING;
  plate = '';
  food: Record<string, number> = {};
  foodDelivery: 'seat' | 'pickup' = 'seat';
  addShirt = false;
  /** Quantidade da camisa (1 na jornada; mais se já havia mais no carrinho) */
  shirtQty = 1;
  shirtSize = 'M';
  shirtName = '';
  shirtNumber = '';

  // reserva da poltrona: ela entra no carrinho assim que é escolhida
  loggedIn = false;
  /** Poltrona segura no carrinho */
  held?: MatchdayHold;
  holding = false;
  holdExpired = false;
  /** Segundos que faltam para a reserva vencer */
  remaining = 0;
  needLogin = false;
  holdMessage?: string;
  /** Poltrona clicada sem login: fica marcada e é reservada na volta do login */
  pendingSeat?: string;
  /** Escolha guardada antes do login, retomada quando o jogo carregar */
  protected loginIntent?: MatchdayLoginIntent;

  // sócio-torcedor: plano do torcedor e ondas de venda
  /** Plano de sócio que vale para ingressos (comprado ou no carrinho) */
  memberPlan: SocioPlan | null = null;
  /** O plano que vale está só no carrinho (ainda não comprado) */
  planInCart = false;
  /** Planos de sócio (nomes das ondas e maior desconto) */
  socioPlans: SocioPlan[] = [];
  /** Jogo pedido na URL (?jogo=...), por exemplo na volta da página de planos */
  protected requestedMatch?: string;

  /** Itens atuais do carrinho (base para ajustar o carrinho no fechamento) */
  protected cartEntries: OrderEntry[] = [];
  /** Estacionamento, comidas e camisa já lidos do carrinho nesta visita */
  protected extrasRestored = false;

  private subscription = new Subscription();

  constructor(
    protected data: MatchdayDataService,
    protected activeCart: ActiveCartFacade,
    protected routing: RoutingService,
    protected globalMessages: GlobalMessageService,
    protected cd: ChangeDetectorRef,
    protected winRef: WindowRef,
    protected auth: AuthService,
    protected authRedirect: AuthRedirectService,
    protected router: Router,
    protected socio: SocioService,
    public i18n: FlaI18nService,
    protected holdWatcher: MatchdayHoldWatcherService
  ) {}

  ngOnInit(): void {
    // a reserva some do carrinho por fora: o aviso aparece aqui, não no topo
    this.holdWatcher.start();
    this.holdWatcher.journeyOpen++;
    this.requestedMatch =
      this.router.parseUrl(this.router.url).queryParams['jogo'] || undefined;
    // volta do login: reabre o jogo, o setor e a poltrona escolhidos antes
    this.loginIntent = this.data.readLoginIntent();
    if (this.loginIntent && !this.requestedMatch) {
      this.requestedMatch = this.loginIntent.matchCode;
    }
    // recarrega a cada visita (a ocupação das poltronas muda a cada venda) e
    // a cada troca de idioma (nomes e datas vêm da API no idioma do site)
    this.subscription.add(
      this.i18n.lang$.subscribe(() => {
        this.loadCatalog();
        this.cd.markForCheck();
      })
    );
    this.subscription.add(
      this.auth.isUserLoggedIn().subscribe((loggedIn) => {
        this.loggedIn = loggedIn;
        if (loggedIn) {
          this.needLogin = false;
          this.resumeLoginIntent();
        }
        this.cd.markForCheck();
      })
    );
    // volta para a jornada com a poltrona que já está no carrinho
    // só com o carrinho carregado e estável: na troca de idioma o Spartacus
    // limpa os dados do carrinho (ResetCartDetails, já "estável" e sem itens)
    // e depois o busca de novo
    this.subscription.add(
      this.loadedEntries().subscribe((entries) => {
        this.cartEntries = entries;
        this.dropRemovedHold(entries);
        this.restoreHold(entries);
      })
    );
    this.subscription.add(interval(1000).subscribe(() => this.tick()));
    // plano do sócio (lido de novo a cada visita: pode ter acabado de assinar)
    this.socio.refreshMembership();
    this.subscription.add(
      this.socio.getMembership().subscribe((m) => {
        this.memberPlan = m.plan;
        this.planInCart =
          !!m.plan &&
          m.plan.code === m.inCart?.code &&
          m.plan.code !== m.member?.code;
        this.cd.markForCheck();
      })
    );
    // planos no idioma do site (o nome entra no nome das ondas)
    this.subscription.add(
      this.i18n.lang$
        .pipe(switchMap(() => this.socio.getPlans()))
        .subscribe((plans) => {
          this.socioPlans = plans;
          this.cd.markForCheck();
        })
    );
    // a onda vira pelo relógio: confere a cada 30 s
    this.subscription.add(
      interval(30000).subscribe(() => this.cd.markForCheck())
    );
  }

  /** Lista de jogos, estacionamento, comidas e camisa (no idioma do site) */
  protected loadCatalog(): void {
    this.subscription.add(
      this.data.getCatalog(true).subscribe({
        next: (catalog) => {
          this.applyCatalog(catalog);
          this.loading = false;
          this.loadError = catalog.matches.length === 0;
          const sizes = catalog.shirt?.sizes ?? [];
          if (sizes.length && !sizes.some((s) => s.label === this.shirtSize)) {
            this.shirtSize = sizes[0].label;
          }
          this.cd.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.loadError = true;
          this.cd.markForCheck();
        },
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.holdWatcher.journeyOpen = Math.max(
      0,
      this.holdWatcher.journeyOpen - 1
    );
    this.holdWatcher.paused = false;
  }

  // ---------- navegação entre etapas ----------

  canOpen(index: number): boolean {
    if (index === 0) {
      return true;
    }
    if (!this.match || !this.match.seatsLoaded) {
      return false;
    }
    // a venda do jogo ainda não chegou na onda do torcedor
    if (!this.waveFor(this.match).open && !this.held) {
      return false;
    }
    return index <= 1 || !!(this.sector && this.seat);
  }

  goTo(index: number): void {
    if (this.canOpen(index)) {
      this.step = index;
      this.scrollTop();
    }
  }

  next(): void {
    if (this.step < STEPS.length - 1 && this.canOpen(this.step + 1)) {
      this.step++;
      this.scrollTop();
    }
  }

  back(): void {
    if (this.step > 0) {
      this.step--;
      this.scrollTop();
    }
  }

  get nextDisabled(): boolean {
    if (this.step === 0) {
      return !this.match || !this.canOpen(1);
    }
    if (this.step === 1) {
      return this.holding || !(this.sector && this.seat);
    }
    if (this.step === 2) {
      return this.parking !== NO_PARKING && !this.plateValid;
    }
    return false;
  }

  get nextLabel(): string {
    return this.i18n.t(
      this.step === STEPS.length - 2 ? 'md.review' : 'md.continue'
    );
  }

  // ---------- sócio-torcedor: ondas e desconto ----------

  /** Situação de venda do jogo para este torcedor */
  waveFor(match: MatchdayMatch): WaveStatus {
    return this.socio.waveStatus(match.waveStarts, this.memberPlan);
  }

  /** Calendário de vendas do jogo, para o aviso de onda fechada */
  waveSchedule(
    match: MatchdayMatch
  ): { wave: number; label: string; at: Date }[] {
    return (match.waveStarts ?? []).map((iso, i) => ({
      wave: i + 1,
      label: this.waveLabel(i + 1),
      at: new Date(iso),
    }));
  }

  /** "09/11 10h00" (pt) ou "Nov 9, 10:00 AM" (en) */
  shortDate(date?: Date): string {
    return this.i18n.shortDate(date);
  }

  /** "Onda 1 · Nação Maraca 1" com os planos de cada onda */
  waveLabel(wave: number): string {
    return this.socio.waveLabel(wave, this.socioPlans);
  }

  /** Selo de onda do card do jogo */
  waveTag(w: WaveStatus): string {
    if (w.open) {
      return w.current >= 4
        ? this.i18n.t('md.wave.generalOpen')
        : this.i18n.t('md.wave.open', { n: w.current });
    }
    return this.i18n.t(
      w.mine >= 4 ? 'md.wave.generalOpens' : 'md.wave.mineOpens',
      { date: this.shortDate(w.opensAt) }
    );
  }

  /** Aviso de onda fechada: quando a onda do torcedor abre */
  lockText(w: WaveStatus): string {
    const date = this.shortDate(w.opensAt);
    if (!this.memberPlan) {
      return this.i18n.t('md.lock.nonMember', { date });
    }
    return this.memberPlan.wave
      ? this.i18n.t('md.lock.member', { wave: this.waveLabel(w.mine), date })
      : this.i18n.t('md.lock.noPriority', { date });
  }

  /** Sugere subir de plano: não é sócio, ou o plano não compra na onda 1 */
  get canUpgrade(): boolean {
    return this.memberPlan?.wave !== 1;
  }

  /** Maior desconto entre os planos (convite para ser sócio) */
  get maxDiscount(): number {
    return Math.max(0, ...this.socioPlans.map((p) => p.discount));
  }

  /** "Restam 3 lugares" / "3 seats left" */
  stockText(s: MatchdaySector): string {
    const n = this.sectorStock(s) ?? 0;
    return n === 1
      ? this.i18n.t('md.left.one')
      : this.i18n.t('md.left.many', { n });
  }

  /** Poltrona do torcedor: reservada, ou clicada antes do login */
  isMine(c: SeatCell): boolean {
    return this.seat === c.id || (!!c.code && this.pendingSeat === c.code);
  }

  seatAria(row: string, c: SeatCell): string {
    const state = c.occupied
      ? 'md.seat.occupied'
      : this.isMine(c)
        ? 'md.seat.selected'
        : 'md.seat.free';
    return this.i18n.t('md.seatAria', {
      row,
      n: c.number,
      state: this.i18n.t(state),
    });
  }

  /** Onda do torcedor (4 = venda geral) */
  get myWave(): number {
    return this.socio.memberWave(this.memberPlan);
  }

  get memberDiscount(): number {
    return this.socio.discount(this.memberPlan);
  }

  /** Preço do ingresso com o desconto do plano (o carrinho aplica pela promoção) */
  ticketPrice(price: number): number {
    return Math.round(price * (100 - this.memberDiscount)) / 100;
  }

  // ---------- etapa 1: jogo ----------

  selectMatch(match: MatchdayMatch): void {
    if (this.match?.code !== match.code) {
      this.releaseHeld();
      this.match = match;
      this.sector = undefined;
      this.seat = undefined;
      this.ticketCode = undefined;
      this.pendingSeat = undefined;
      this.needLogin = false;
      this.seatMap = [];
    }
    if (!this.match.seatsLoaded) {
      this.loadSeats(match.code);
    }
  }

  /** Mouse ou foco no card: já começa a buscar as poltronas daquele jogo */
  prefetchSeats(match: MatchdayMatch): void {
    if (!match.seatsLoaded) {
      this.loadSeats(match.code);
    }
  }

  /**
   * Busca setores e poltronas de um jogo e põe no catálogo; se for o jogo
   * aberto, atualiza setor e mapa.
   */
  protected loadSeats(matchCode: string, refresh = false): void {
    this.subscription.add(
      this.data.getSeats(matchCode, refresh).subscribe((full) => {
        if (!full || !this.catalog) {
          return;
        }
        this.catalog = {
          ...this.catalog,
          matches: this.catalog.matches.map((m) =>
            m.code === matchCode
              ? { ...full, waveStarts: full.waveStarts ?? m.waveStarts }
              : m
          ),
        };
        if (this.match?.code === matchCode) {
          this.match =
            this.catalog.matches.find((m) => m.code === matchCode) ??
            this.match;
          const sector = this.match.sectors.find(
            (s) => s.code === this.sector?.code
          );
          this.sector = sector;
          this.seatMap = sector ? this.buildSeatMap(sector) : [];
        }
        this.loadedEntries()
          .pipe(take(1))
          .subscribe((entries) => this.restoreHold(entries));
        this.resumeLoginIntent();
        this.cd.markForCheck();
      })
    );
  }

  // ---------- etapa 2: setor e poltrona ----------

  selectSector(sector: MatchdaySector): void {
    if (this.sector?.code !== sector.code) {
      this.releaseHeld();
      this.sector = sector;
      this.seat = undefined;
      this.ticketCode = undefined;
      this.pendingSeat = undefined;
      this.needLogin = false;
      this.seatMap = this.buildSeatMap(sector);
    }
  }

  selectSeat(cell: SeatCell): void {
    if (cell.occupied || this.holding || !this.sector) {
      return;
    }
    this.holdMessage = undefined;
    if (!cell.code) {
      // modelo antigo (só setor): não há poltrona para reservar
      this.seat = cell.id;
      this.ticketCode = this.sector.code;
      return;
    }
    if (this.held?.code === cell.code && !this.holdExpired) {
      return;
    }
    if (!this.loggedIn) {
      // fica marcada; na volta do login ela é reservada
      this.pendingSeat = cell.code;
      this.needLogin = true;
      return;
    }
    this.holdSeat(cell);
  }

  /**
   * Entrar e voltar para a jornada no mesmo ponto: guarda jogo, setor e
   * poltrona e volta para /matchday?jogo=... (sem isso a volta abria a
   * lista de jogos).
   */
  login(): void {
    const url = this.router.parseUrl(this.router.url);
    if (this.match) {
      this.data.saveLoginIntent({
        matchCode: this.match.code,
        sectorCode: this.sector?.code,
        seatCode: this.pendingSeat,
        savedAt: Date.now(),
      });
      url.queryParams = { ...url.queryParams, jogo: this.match.code };
    }
    this.authRedirect.setRedirectUrl(this.router.serializeUrl(url));
    this.routing.go({ cxRoute: 'login' });
  }

  /**
   * Volta do login: com o torcedor logado e as poltronas do jogo carregadas,
   * abre a etapa Setor e poltrona no setor escolhido e reserva a poltrona.
   * Se outra pessoa a pegou nesse meio-tempo, avisa e deixa o setor aberto.
   */
  protected resumeLoginIntent(): void {
    const intent = this.loginIntent;
    const match = this.match;
    if (
      !intent ||
      !this.loggedIn ||
      !match?.seatsLoaded ||
      match.code !== intent.matchCode
    ) {
      return;
    }
    this.loginIntent = undefined;
    this.data.clearLoginIntent();
    const sector = match.sectors.find((s) => s.code === intent.sectorCode);
    if (!sector || (!this.waveFor(match).open && !this.held)) {
      // sem setor guardado, ou o jogo não está aberto para a onda deste
      // torcedor: fica no jogo, com o aviso da onda
      return;
    }
    this.step = 1;
    if (this.held?.code === intent.seatCode) {
      // a poltrona já estava reservada neste carrinho
      this.cd.markForCheck();
      return;
    }
    this.selectSector(sector);
    const cell = this.seatMap
      .flatMap((r) => r.seats)
      .find((c) => c.code === intent.seatCode);
    if (cell && !cell.occupied) {
      // espera o carrinho do torcedor logado assentar (junção com o carrinho
      // anônimo); torcedor sem carrinho também segue: o addEntry cria um
      this.subscription.add(
        this.activeCart
          .isStable()
          .pipe(debounceTime(300), filter(Boolean), take(1))
          .subscribe(() => this.holdSeat(cell))
      );
    } else if (cell) {
      this.holdMessage = this.i18n.t('md.holdTaken');
    }
    this.cd.markForCheck();
  }

  /** Reserva vencida: tenta segurar a mesma poltrona de novo */
  renewHold(): void {
    const cell = this.seatMap
      .flatMap((r) => r.seats)
      .find((c) => c.code === this.held?.code);
    if (cell) {
      this.holdSeat(cell);
    }
  }

  get remainingLabel(): string {
    const m = Math.floor(this.remaining / 60);
    const s = this.remaining % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  /** Setores no formato do mapa do Maracanã (mesmos dados dos cartões) */
  get stadiumSectors(): StadiumSectorView[] {
    const view = (this.match?.sectors ?? []).map((s) => {
      const stock = this.sectorStock(s);
      const state: StadiumSectorView['state'] =
        this.sector?.code === s.code
          ? 'selected'
          : stock === 0
            ? 'soldout'
            : stock !== undefined && stock < 20
              ? 'low'
              : 'free';
      const status =
        stock === 0
          ? this.i18n.t('md.soldOut')
          : stock !== undefined && stock < 20
            ? this.stockText(s)
            : stock !== undefined
              ? this.i18n.t('md.free', { n: stock })
              : '';
      return {
        code: s.code,
        // modelo atual: código = LESTE; modelo antigo: ...-LESTE
        key: s.code.split('-').pop()?.toUpperCase() ?? '',
        name: s.name,
        price: this.brl(this.ticketPrice(s.price)),
        status,
        state,
      };
    });
    // mesma referência enquanto nada muda (evita redesenhar o mapa a cada ciclo)
    const key = JSON.stringify(view);
    if (this.stadiumCache && this.stadiumCache.key === key) {
      return this.stadiumCache.view;
    }
    this.stadiumCache = { key, view };
    return view;
  }
  private stadiumCache?: { key: string; view: StadiumSectorView[] };

  /** Clique no mapa: escolhe o setor como o cartão */
  pickSector(code: string): void {
    const s = this.match?.sectors.find((x) => x.code === code);
    if (s && this.sectorStock(s) !== 0) {
      this.selectSector(s);
    }
  }

  /** Lugares livres no setor, contando a poltrona que já é sua */
  sectorStock(s: MatchdaySector): number | undefined {
    if (s.stock === undefined) {
      return undefined;
    }
    const mine = s.seats.some(
      (x) => x.code === this.held?.code && !x.available
    );
    return s.stock + (mine ? 1 : 0);
  }

  /**
   * Põe a poltrona no carrinho na hora: o servidor a reserva para este
   * carrinho e, para todo o resto, ela passa a aparecer ocupada.
   */
  protected holdSeat(cell: SeatCell): void {
    const code = cell.code;
    if (!code) {
      return;
    }
    this.holding = true;
    this.needLogin = false;
    this.pendingSeat = undefined;
    this.cd.markForCheck();
    const previousCode = this.held?.code;
    const matchCode = this.data.matchCodeOfSeat(code);
    // troca de poltrona: a anterior sai do carrinho a pedido da jornada
    this.holdWatcher.paused = true;
    this.data.clearHold();
    this.subscription.add(
      this.activeCart
        .getEntries()
        .pipe(
          take(1),
          switchMap((entries) => {
            // um ingresso por jornada: sai do carrinho qualquer outra poltrona
            // do mesmo jogo (a anterior, uma retomada do carrinho depois do
            // login ou sobra de outra visita), não só a que a jornada segurava
            entries
              .filter((e) => {
                const c = e.product?.code;
                return (
                  !!c &&
                  (c === code ||
                    c === previousCode ||
                    (!!matchCode && this.data.matchCodeOfSeat(c) === matchCode))
                );
              })
              // da última linha para a primeira: tirar uma renumera as seguintes
              .sort((a, b) => (b.entryNumber ?? 0) - (a.entryNumber ?? 0))
              .forEach((e) => this.activeCart.removeEntry(e));
            this.activeCart.addEntry(code, 1);
            return this.cartSettled();
          }),
          switchMap(() => this.activeCart.getEntries().pipe(take(1)))
        )
        .subscribe((entries) => {
          this.holding = false;
          this.holdWatcher.paused = false;
          if (entries.some((e) => e.product?.code === code)) {
            this.held = {
              code,
              expiresAt: Date.now() + MATCHDAY_HOLD_MINUTES * 60_000,
            };
            this.data.saveHold(this.held);
            this.seat = cell.id;
            this.ticketCode = code;
            this.holdExpired = false;
            this.tick();
          } else {
            this.clearHeld();
            this.holdMessage = this.i18n.t('md.holdTaken');
            this.refreshSeats();
          }
          this.cd.markForCheck();
        })
    );
  }

  /** Solta a poltrona segura (troca de jogo ou de setor) */
  protected releaseHeld(): void {
    const code = this.held?.code;
    if (!code) {
      return;
    }
    this.clearHeld();
    this.subscription.add(
      this.activeCart
        .getEntries()
        .pipe(take(1))
        .subscribe((entries) =>
          entries
            .filter((e) => e.product?.code === code)
            .forEach((e) => this.activeCart.removeEntry(e))
        )
    );
  }

  protected clearHeld(): void {
    this.held = undefined;
    this.holdExpired = false;
    this.remaining = 0;
    this.seat = undefined;
    this.ticketCode = undefined;
    this.data.clearHold();
    if (this.sector) {
      this.seatMap = this.buildSeatMap(this.sector);
    }
  }

  /** Recarrega a ocupação do setor aberto */
  protected refreshSeats(): void {
    if (this.match) {
      this.loadSeats(this.match.code, true);
    }
  }

  /**
   * Novo catálogo: troca o jogo e o setor escolhidos pelos objetos novos
   * (a 2a emissão traz as poltronas) e tenta retomar a poltrona do carrinho.
   */
  protected applyCatalog(catalog: MatchdayCatalog): void {
    this.catalog = catalog;
    // troca de idioma: o jogo aberto passa a ser o do catálogo novo
    if (this.match) {
      const code = this.match.code;
      const fresh = catalog.matches.find((m) => m.code === code);
      if (fresh) {
        this.match = fresh;
        this.loadSeats(code);
      }
    }
    const requested =
      !this.match && this.requestedMatch
        ? catalog.matches.find((m) => m.code === this.requestedMatch)
        : undefined;
    if (requested) {
      this.selectMatch(requested);
    }
    // poltrona de jogo já no carrinho: carrega aquele jogo para retomar a
    // jornada (espera o carrinho carregar; a última poltrona reservada vem antes)
    this.subscription.add(
      this.loadedEntries()
        .pipe(take(1))
        .subscribe((entries) => {
          const savedCode = this.data.readHold()?.code ?? '';
          const code = this.seatCodes(entries)
            .sort((a, b) => Number(b === savedCode) - Number(a === savedCode))
            .map((seat) => this.data.matchCodeOfSeat(seat))
            .find((c) => !!c && catalog.matches.some((m) => m.code === c));
          if (code) {
            // retoma a jornada quando as poltronas desse jogo chegarem
            this.loadSeats(code);
          } else {
            // sem poltrona no carrinho (por exemplo, a reserva venceu):
            // estacionamento, comidas e camisa que ficaram nele aparecem no
            // resumo. Navegando pela loja, o carrinho já está carregado e chega
            // antes da lista de jogos; só na carga da página (Ctrl+F5) ele
            // chegava depois e o resumo era montado.
            this.restoreHold(entries);
          }
        })
    );
  }

  /**
   * A poltrona segura saiu do carrinho por fora da jornada (por exemplo, o
   * plano saiu do carrinho e o jogo ainda não está liberado para a onda dele).
   */
  protected dropRemovedHold(entries: OrderEntry[]): void {
    const code = this.held?.code;
    if (
      !code ||
      this.holding ||
      this.submitting ||
      entries.some((e) => e.product?.code === code)
    ) {
      return;
    }
    const seat = this.seat ?? this.holdWatcher.seatLabel(code);
    const waveOpen = !this.match || this.waveFor(this.match).open;
    this.clearHeld();
    // a reserva caiu (por exemplo, apagada pelo administrador): reservar de
    // novo; se o plano de sócio saiu do carrinho, o jogo só abre na onda dele
    this.holdMessage = waveOpen
      ? this.i18n.t('md.holdLost', { seat })
      : this.i18n.t('md.holdRemoved');
    this.step = Math.min(this.step, 1);
    this.refreshSeats();
    this.cd.markForCheck();
  }

  /** Se o carrinho já tem uma poltrona de jogo, retoma a jornada com ela */
  protected restoreHold(entries: OrderEntry[]): void {
    if (!this.catalog || this.held || this.holding || this.match) {
      return;
    }
    const inCart = this.seatCodes(entries);
    const savedCode = this.data.readHold()?.code;
    const wanted =
      savedCode && inCart.includes(savedCode) ? [savedCode] : inCart;
    for (const match of this.catalog.matches) {
      for (const sector of match.sectors) {
        const seat = sector.seats.find((s) => wanted.includes(s.code));
        if (seat) {
          const saved = this.data.readHold();
          this.held = {
            code: seat.code,
            expiresAt: saved?.code === seat.code ? saved.expiresAt : undefined,
          };
          this.match = match;
          this.sector = sector;
          this.seat = seat.label;
          this.ticketCode = seat.code;
          this.seatMap = this.buildSeatMap(sector);
          this.restoreExtras(entries);
          this.tick();
          this.cd.markForCheck();
          return;
        }
      }
    }
    // sem poltrona no carrinho: os outros itens da jornada que ficaram nele
    // também aparecem (e não somem no fechamento)
    this.restoreExtras(entries);
    this.cd.markForCheck();
  }

  /**
   * Na volta para a jornada (por exemplo, depois de ir ao pagamento), o resumo
   * "Seu pedido" passa a refletir o carrinho: estacionamento, comidas e camisa
   * que já estão nele. Placa, entrega das comidas e nome/número da camisa vêm
   * da última escolha salva para o mesmo jogo.
   */
  protected restoreExtras(entries: OrderEntry[]): void {
    const catalog = this.catalog;
    if (!catalog || this.extrasRestored) {
      return;
    }
    this.extrasRestored = true;
    const inCart = this.quantitiesInCart(entries);
    const saved = this.data.readSelection();
    const sameMatch = !!saved && saved.matchCode === this.match?.code;

    const parking = catalog.parking.find((p) => inCart.has(p.code));
    if (parking) {
      this.parking = parking.code;
      if (sameMatch && saved?.parkingCode === parking.code && saved.plate) {
        this.plate = saved.plate;
      }
    }

    const food: Record<string, number> = {};
    for (const item of catalog.food) {
      const q = inCart.get(item.code) ?? 0;
      if (q > 0) {
        food[item.code] = q;
      }
    }
    if (Object.keys(food).length) {
      this.food = food;
      if (sameMatch && saved) {
        this.foodDelivery = saved.foodDelivery;
      }
    }

    const shirt = catalog.shirt;
    const size = shirt?.sizes.find((s) => inCart.has(s.code));
    if (shirt && size) {
      this.addShirt = true;
      this.shirtSize = size.label;
      this.shirtQty = inCart.get(size.code) ?? 1;
      const pers = shirt.personalization;
      if (pers && inCart.has(pers.code) && sameMatch && saved) {
        this.shirtName = saved.shirtName ?? '';
        this.shirtNumber = saved.shirtNumber ?? '';
      }
    }
  }

  /** Quantidade de cada produto no carrinho (soma das linhas do mesmo produto) */
  protected quantitiesInCart(entries: OrderEntry[]): Map<string, number> {
    const result = new Map<string, number>();
    for (const e of entries) {
      const code = e.product?.code;
      if (code) {
        result.set(code, (result.get(code) ?? 0) + (e.quantity ?? 0));
      }
    }
    return result;
  }

  /**
   * Itens do carrinho só com ele carregado e estável. Na troca de idioma o
   * Spartacus limpa os dados do carrinho (ResetCartDetails: "estável" e sem
   * itens) e depois o busca de novo; ao abrir a página ele ainda pode estar
   * carregando.
   */
  protected loadedEntries(): Observable<OrderEntry[]> {
    return combineLatest([
      this.activeCart.getActive(),
      this.activeCart.isStable(),
    ]).pipe(
      filter(([cart, stable]) => stable && !!cart?.code),
      map(([cart]) => cart.entries ?? [])
    );
  }

  /** Códigos das poltronas de jogo no carrinho */
  protected seatCodes(entries: OrderEntry[]): string[] {
    return entries
      .map((e) => e.product?.code ?? '')
      .filter((code) => !!this.data.matchCodeOfSeat(code));
  }

  protected tick(): void {
    const expiresAt = this.held?.expiresAt;
    if (!expiresAt) {
      return;
    }
    const remaining = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
    if (
      remaining !== this.remaining ||
      (remaining === 0 && !this.holdExpired)
    ) {
      this.remaining = remaining;
      this.holdExpired = remaining === 0;
      this.cd.markForCheck();
    }
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

  protected buildSeatMap(
    sector: MatchdaySector
  ): { row: string; seats: SeatCell[] }[] {
    if (sector.seats.length) {
      // poltronas reais do catálogo: livre = tem estoque (a sua conta como livre)
      const rows = new Map<string, SeatCell[]>();
      for (const s of sector.seats) {
        const cells = rows.get(s.row) ?? [];
        cells.push({
          id: s.label,
          number: s.number,
          occupied: !s.available && s.code !== this.held?.code,
          code: s.code,
        });
        rows.set(s.row, cells);
      }
      return Array.from(rows.entries()).map(([row, seats]) => ({
        row,
        seats: seats.sort((a, b) => a.number - b.number),
      }));
    }
    // modelo antigo (só setor): ocupação ilustrativa e estável
    const seed = Array.from(sector.code).reduce(
      (a, c) => a + c.charCodeAt(0),
      0
    );
    return SEAT_ROWS.map((row, r) => ({
      row,
      seats: Array.from({ length: SEATS_PER_ROW }, (_, i) => ({
        id: `${row}${i + 1}`,
        number: i + 1,
        occupied: (r * 7 + i * 3 + seed) % 5 === 0,
      })),
    }));
  }

  // ---------- etapa 3: estacionamento ----------

  get parkingProduct(): MatchdayProduct | undefined {
    return this.catalog?.parking.find((p) => p.code === this.parking);
  }

  get plateValid(): boolean {
    return /^[A-Z]{3}-?\d[A-Z0-9]\d{2}$/i.test(this.plate.trim());
  }

  normalizePlate(): void {
    this.plate = this.plate
      .toUpperCase()
      .replace(/[^A-Z0-9-]/g, '')
      .slice(0, 8);
  }

  // ---------- etapa 4: comidas e bebidas ----------

  qty(code: string): number {
    return this.food[code] ?? 0;
  }

  changeQty(code: string, delta: number): void {
    const value = Math.max(0, Math.min(10, this.qty(code) + delta));
    this.food = { ...this.food, [code]: value };
  }

  // ---------- etapa 5: camisa ----------

  get hasPersonalization(): boolean {
    return (
      this.addShirt && (!!this.shirtName.trim() || !!this.shirtNumber.trim())
    );
  }

  normalizeShirt(): void {
    this.shirtName = this.shirtName.toUpperCase().slice(0, 12);
    this.shirtNumber = this.shirtNumber.replace(/\D/g, '').slice(0, 2);
  }

  get shirtVariantCode(): string | undefined {
    return this.catalog?.shirt?.sizes.find((s) => s.label === this.shirtSize)
      ?.code;
  }

  // ---------- resumo ----------

  get matchLine(): string {
    if (!this.match) {
      return '';
    }
    const m = this.match;
    return [m.weekday && `${m.weekday} ${m.dateLabel}`, m.timeLabel, m.venue]
      .filter(Boolean)
      .join(' · ');
  }

  get lines(): SummaryLine[] {
    const lines: SummaryLine[] = [];
    if (this.match) {
      const t = (key: string, params?: Record<string, unknown>) =>
        this.i18n.t(key, params);
      lines.push({
        label: t('md.line.ticket', {
          sector: this.sector ? this.sector.name : t('md.line.sectorTbd'),
        }),
        detail: [
          this.match.title,
          this.match.dateLabel,
          this.seat ? t('md.line.seat', { seat: this.seat }) : '',
          this.memberDiscount
            ? t('md.memberOff', { d: this.memberDiscount })
            : '',
        ]
          .filter(Boolean)
          .join(' · '),
        price: this.ticketPrice(this.sector?.price ?? 0),
      });
    }
    const parking = this.parkingProduct;
    if (parking) {
      lines.push({
        label: parking.name,
        detail: this.plate
          ? this.i18n.t('md.line.plate', { plate: this.plate })
          : this.i18n.t('md.line.spot'),
        price: parking.price,
      });
    }
    for (const item of this.catalog?.food ?? []) {
      const q = this.qty(item.code);
      if (q > 0) {
        lines.push({
          label: `${q}x ${item.name}`,
          detail: this.i18n.t(
            this.foodDelivery === 'seat' ? 'md.line.atSeat' : 'md.line.atKiosk'
          ),
          price: q * item.price,
        });
      }
    }
    const shirt = this.catalog?.shirt;
    if (this.addShirt && shirt) {
      lines.push({
        label:
          this.shirtQty > 1 ? `${this.shirtQty}x ${shirt.name}` : shirt.name,
        detail: this.i18n.t('md.line.size', { size: this.shirtSize }),
        price: this.shirtQty * shirt.price,
      });
      if (this.hasPersonalization && shirt.personalization) {
        lines.push({
          label: this.i18n.t('md.line.personalization'),
          detail: [this.shirtName, this.shirtNumber].filter(Boolean).join(' '),
          price: shirt.personalization.price,
        });
      }
    }
    return lines;
  }

  get total(): number {
    return this.lines.reduce((sum, l) => sum + l.price, 0);
  }

  get itemCount(): number {
    let count = this.sector ? 1 : 0;
    count += this.parkingProduct ? 1 : 0;
    count += Object.values(this.food).reduce((a, b) => a + b, 0);
    count += this.addShirt ? this.shirtQty : 0;
    return count;
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  trackByCode(_: number, item: { code: string }): string {
    return item.code;
  }

  // ---------- fechamento: tudo num carrinho só ----------

  checkout(): void {
    if (
      !this.match ||
      !this.sector ||
      !this.seat ||
      !this.ticketCode ||
      this.submitting
    ) {
      return;
    }
    const parking = this.parkingProduct;
    const foodLines = (this.catalog?.food ?? []).filter(
      (f) => this.qty(f.code) > 0
    );
    const shirtCode = this.addShirt ? this.shirtVariantCode : undefined;
    const pers = this.catalog?.shirt?.personalization;

    // quantidade que cada produto da jornada deve ter no carrinho
    const desired = new Map<string, number>();
    if (parking) {
      desired.set(parking.code, 1);
    }
    foodLines.forEach((f) => desired.set(f.code, this.qty(f.code)));
    if (shirtCode) {
      desired.set(shirtCode, this.shirtQty);
      if (this.hasPersonalization && pers) {
        desired.set(pers.code, 1);
      }
    }

    this.data.saveSelection({
      matchCode: this.match.code,
      matchTitle: this.match.title,
      matchLine: this.matchLine,
      sectorCode: this.sector.code,
      sectorName: this.sector.name,
      seat: this.seat,
      ticketCode: this.ticketCode,
      parkingCode: parking?.code,
      parkingName: parking?.name,
      plate: parking ? this.plate : undefined,
      food: foodLines.map((f) => ({
        code: f.code,
        name: f.name,
        quantity: this.qty(f.code),
      })),
      foodDelivery: this.foodDelivery,
      shirtCode,
      shirtSize: shirtCode ? this.shirtSize : undefined,
      shirtName: shirtCode ? this.shirtName.trim() : undefined,
      shirtNumber: shirtCode ? this.shirtNumber.trim() : undefined,
      createdAt: new Date().toISOString(),
    });

    this.submitting = true;
    this.holdWatcher.paused = true;
    // espera o carrinho processar as alterações antes de seguir para o checkout
    this.subscription.add(
      this.syncCart(desired).subscribe({
        complete: () => this.routing.go({ cxRoute: 'checkout' }),
        error: () => {
          this.submitting = false;
          this.holdWatcher.paused = false;
          this.globalMessages.add(
            this.i18n.t('md.orderError'),
            GlobalMessageType.MSG_TYPE_ERROR
          );
          this.cd.markForCheck();
        },
      })
    );
  }

  /**
   * Deixa no carrinho exatamente o que está na jornada: inclui o que falta,
   * ajusta quantidades e tira o que foi desmarcado. Vale só para os produtos da
   * jornada (estacionamentos, comidas e bebidas, tamanhos da camisa e
   * personalização) e para o ingresso escolhido; o resto do carrinho não muda.
   * Antes, cada fechamento somava os itens de novo e as quantidades dobravam
   * na volta do pagamento.
   *
   * Em fases, esperando o carrinho a cada uma, porque o número das linhas muda
   * quando uma linha sai: 1) quantidades, 2) remoções de baixo para cima,
   * 3) inclusões.
   */
  protected syncCart(desired: Map<string, number>): Observable<unknown> {
    const catalog = this.catalog;
    const shirt = catalog?.shirt;
    const managed = new Set<string>([
      ...(catalog?.parking ?? []).map((p) => p.code),
      ...(catalog?.food ?? []).map((f) => f.code),
      ...(shirt?.sizes ?? []).map((size) => size.code),
      ...(shirt?.personalization ? [shirt.personalization.code] : []),
    ]);
    const updates: { entryNumber: number; quantity: number }[] = [];
    const removals: OrderEntry[] = [];
    const additions: OrderEntry[] = [];

    // ingresso: a poltrona normalmente já está no carrinho desde que foi escolhida
    if (
      this.ticketCode &&
      !this.cartEntries.some((e) => e.product?.code === this.ticketCode)
    ) {
      additions.push({ product: { code: this.ticketCode }, quantity: 1 });
    }
    for (const code of managed) {
      const want = desired.get(code) ?? 0;
      const lines = this.cartEntries.filter((e) => e.product?.code === code);
      const have = lines.reduce((sum, e) => sum + (e.quantity ?? 0), 0);
      if (want === have) {
        continue;
      }
      if (!lines.length) {
        additions.push({ product: { code }, quantity: want });
        continue;
      }
      // ajusta a primeira linha do produto pela diferença
      const first = lines[0];
      const quantity = (first.quantity ?? 0) + (want - have);
      if (quantity > 0) {
        updates.push({ entryNumber: first.entryNumber as number, quantity });
      } else {
        removals.push(first);
      }
    }
    removals.sort((a, b) => (b.entryNumber ?? 0) - (a.entryNumber ?? 0));

    const phase = (count: number, run: () => void): Observable<unknown> =>
      count ? defer(() => (run(), this.cartSettled())) : of(undefined);
    return concat(
      phase(updates.length, () =>
        updates.forEach((u) =>
          this.activeCart.updateEntry(u.entryNumber, u.quantity)
        )
      ),
      phase(removals.length, () =>
        removals.forEach((e) => this.activeCart.removeEntry(e))
      ),
      phase(additions.length, () => this.activeCart.addEntries(additions))
    ).pipe(last(undefined, undefined));
  }

  protected scrollTop(): void {
    this.winRef.nativeWindow?.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
