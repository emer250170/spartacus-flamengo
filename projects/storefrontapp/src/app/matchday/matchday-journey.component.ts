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
  interval,
  raceWith,
  timer,
} from 'rxjs';
import { filter, map, switchMap, take } from 'rxjs/operators';
import { WAVE_LABELS, WaveStatus } from '../socio/socio.model';
import { SocioService } from '../socio/socio.service';
import { MatchdayDataService } from './matchday-data.service';
import {
  MATCHDAY_HOLD_MINUTES,
  MatchdayCatalog,
  MatchdayHold,
  MatchdayMatch,
  MatchdayProduct,
  MatchdaySector,
} from './matchday.model';

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

const STEPS = [
  'Jogo',
  'Setor e poltrona',
  'Estacionamento',
  'Comidas e bebidas',
  'Camisa',
  'Revisão',
];
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

  // sócio-torcedor: plano do torcedor e ondas de venda
  /** Plano Nação Maraca do torcedor (1 a 3) ou null */
  memberLevel: number | null = null;
  /** Onda simulada na demonstração (null = pela data do jogo) */
  simulatedWave: number | null = null;
  readonly waveOptions = Object.entries(WAVE_LABELS).map(([k, label]) => ({
    wave: Number(k),
    label,
  }));

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
    protected socio: SocioService
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      // recarrega a cada visita: a ocupação das poltronas muda a cada venda
      this.data.getCatalog(true).subscribe({
        next: (catalog) => {
          this.catalog = catalog;
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
    this.subscription.add(
      this.auth.isUserLoggedIn().subscribe((loggedIn) => {
        this.loggedIn = loggedIn;
        if (loggedIn) {
          this.needLogin = false;
        }
        this.cd.markForCheck();
      })
    );
    // volta para a jornada com a poltrona que já está no carrinho
    this.subscription.add(
      this.activeCart
        .getEntries()
        .subscribe((entries) => this.restoreHold(entries))
    );
    this.subscription.add(interval(1000).subscribe(() => this.tick()));
    // plano do sócio (lido de novo a cada visita: pode ter acabado de assinar)
    this.socio.refreshMembership();
    this.subscription.add(
      combineLatest([
        this.socio.getMemberLevel(),
        this.socio.getSimulation(),
      ]).subscribe(([level, simulated]) => {
        this.memberLevel = level;
        this.simulatedWave = simulated;
        this.cd.markForCheck();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  // ---------- navegação entre etapas ----------

  canOpen(index: number): boolean {
    if (index === 0) {
      return true;
    }
    if (!this.match) {
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
    return this.step === STEPS.length - 2 ? 'Revisar pedido' : 'Continuar';
  }

  // ---------- sócio-torcedor: ondas e desconto ----------

  /** Situação de venda do jogo para este torcedor */
  waveFor(match: MatchdayMatch): WaveStatus {
    return this.socio.waveStatus(
      match.code,
      match.timeLabel,
      this.memberLevel,
      this.simulatedWave
    );
  }

  waveLabel(wave: number): string {
    return wave <= 0 ? 'Vendas ainda não abertas' : (WAVE_LABELS[wave] ?? '');
  }

  /** "sex., 23/10 às 10h00" */
  dateTimeLabel(date?: Date): string {
    if (!date) {
      return '';
    }
    return `${date.toLocaleDateString('pt-BR', {
      weekday: 'short',
      day: '2-digit',
      month: '2-digit',
    })} às ${date.toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    })}`;
  }

  /** Onda do torcedor (7 = venda geral) */
  get myWave(): number {
    return this.socio.memberWave(this.memberLevel);
  }

  get memberDiscount(): number {
    return this.socio.discount(this.memberLevel);
  }

  /** Preço do ingresso com o desconto do plano (o carrinho aplica pela promoção) */
  ticketPrice(price: number): number {
    return Math.round(price * (100 - this.memberDiscount)) / 100;
  }

  onSimulationChange(value: string): void {
    this.socio.setSimulation(value === '' ? null : Number(value));
  }

  // ---------- etapa 1: jogo ----------

  selectMatch(match: MatchdayMatch): void {
    if (this.match?.code !== match.code) {
      this.releaseHeld();
      this.match = match;
      this.sector = undefined;
      this.seat = undefined;
      this.ticketCode = undefined;
      this.seatMap = [];
    }
  }

  // ---------- etapa 2: setor e poltrona ----------

  selectSector(sector: MatchdaySector): void {
    if (this.sector?.code !== sector.code) {
      this.releaseHeld();
      this.sector = sector;
      this.seat = undefined;
      this.ticketCode = undefined;
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
      this.needLogin = true;
      return;
    }
    this.holdSeat(cell);
  }

  /** Entrar e voltar para esta mesma página */
  login(): void {
    this.authRedirect.setRedirectUrl(this.router.url);
    this.routing.go({ cxRoute: 'login' });
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
    this.cd.markForCheck();
    const previousCode = this.held?.code;
    this.subscription.add(
      this.activeCart
        .getEntries()
        .pipe(
          take(1),
          switchMap((entries) => {
            entries
              .filter(
                (e) =>
                  e.product?.code === code ||
                  (previousCode && e.product?.code === previousCode)
              )
              .forEach((e) => this.activeCart.removeEntry(e));
            this.activeCart.addEntry(code, 1);
            return this.cartSettled();
          }),
          switchMap(() => this.activeCart.getEntries().pipe(take(1)))
        )
        .subscribe((entries) => {
          this.holding = false;
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
            this.holdMessage =
              'Essa poltrona acabou de ser reservada por outra pessoa. Escolha outra.';
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
    const matchCode = this.match?.code;
    const sectorCode = this.sector?.code;
    this.subscription.add(
      this.data.getCatalog(true).subscribe((catalog) => {
        this.catalog = catalog;
        this.match = catalog.matches.find((m) => m.code === matchCode);
        this.sector = this.match?.sectors.find((s) => s.code === sectorCode);
        this.seatMap = this.sector ? this.buildSeatMap(this.sector) : [];
        this.cd.markForCheck();
      })
    );
  }

  /** Se o carrinho já tem uma poltrona de jogo, retoma a jornada com ela */
  protected restoreHold(entries: OrderEntry[]): void {
    if (!this.catalog || this.held || this.holding || this.match) {
      return;
    }
    for (const match of this.catalog.matches) {
      for (const sector of match.sectors) {
        const seat = sector.seats.find((s) =>
          entries.some((e) => e.product?.code === s.code)
        );
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
          this.tick();
          this.cd.markForCheck();
          return;
        }
      }
    }
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
      lines.push({
        label: `Ingresso · ${this.sector ? this.sector.name : 'setor a escolher'}`,
        detail: `${this.match.title} · ${this.match.dateLabel}${this.seat ? ' · Poltrona ' + this.seat : ''}${this.memberDiscount ? ` · Sócio ${this.memberDiscount}% off` : ''}`,
        price: this.ticketPrice(this.sector?.price ?? 0),
      });
    }
    const parking = this.parkingProduct;
    if (parking) {
      lines.push({
        label: parking.name,
        detail: this.plate ? `Placa ${this.plate}` : 'Vaga garantida',
        price: parking.price,
      });
    }
    for (const item of this.catalog?.food ?? []) {
      const q = this.qty(item.code);
      if (q > 0) {
        lines.push({
          label: `${q}x ${item.name}`,
          detail:
            this.foodDelivery === 'seat'
              ? 'Entrega na poltrona'
              : 'Retirada no quiosque',
          price: q * item.price,
        });
      }
    }
    const shirt = this.catalog?.shirt;
    if (this.addShirt && shirt) {
      lines.push({
        label: shirt.name,
        detail: `Tamanho ${this.shirtSize}`,
        price: shirt.price,
      });
      if (this.hasPersonalization && shirt.personalization) {
        lines.push({
          label: 'Personalização',
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
    count += this.addShirt ? 1 : 0;
    return count;
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
    // a poltrona já está no carrinho desde que foi escolhida
    const entries: OrderEntry[] =
      this.held?.code === this.ticketCode
        ? []
        : [{ product: { code: this.ticketCode }, quantity: 1 }];
    const parking = this.parkingProduct;
    if (parking) {
      entries.push({ product: { code: parking.code }, quantity: 1 });
    }
    const foodLines = (this.catalog?.food ?? []).filter(
      (f) => this.qty(f.code) > 0
    );
    foodLines.forEach((f) =>
      entries.push({ product: { code: f.code }, quantity: this.qty(f.code) })
    );
    const shirtCode = this.addShirt ? this.shirtVariantCode : undefined;
    if (shirtCode) {
      entries.push({ product: { code: shirtCode }, quantity: 1 });
      const pers = this.catalog?.shirt?.personalization;
      if (this.hasPersonalization && pers) {
        entries.push({ product: { code: pers.code }, quantity: 1 });
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
    if (!entries.length) {
      this.routing.go({ cxRoute: 'checkout' });
      return;
    }
    this.activeCart.addEntries(entries);

    // espera o carrinho processar as inclusões antes de seguir para o checkout
    this.subscription.add(
      this.cartSettled().subscribe({
        next: () => this.routing.go({ cxRoute: 'checkout' }),
        error: () => {
          this.submitting = false;
          this.globalMessages.add(
            'Não foi possível montar o pedido agora. Tente de novo.',
            GlobalMessageType.MSG_TYPE_ERROR
          );
          this.cd.markForCheck();
        },
      })
    );
  }

  protected scrollTop(): void {
    this.winRef.nativeWindow?.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
