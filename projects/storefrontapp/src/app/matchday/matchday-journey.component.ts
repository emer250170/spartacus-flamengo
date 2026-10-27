import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActiveCartFacade, OrderEntry } from '@spartacus/cart/base/root';
import {
  GlobalMessageService,
  GlobalMessageType,
  RoutingService,
  WindowRef,
} from '@spartacus/core';
import { Subscription, raceWith, timer } from 'rxjs';
import { filter, switchMap, take } from 'rxjs/operators';
import { MatchdayDataService } from './matchday-data.service';
import {
  MatchdayCatalog,
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

  private subscription = new Subscription();

  constructor(
    protected data: MatchdayDataService,
    protected activeCart: ActiveCartFacade,
    protected routing: RoutingService,
    protected globalMessages: GlobalMessageService,
    protected cd: ChangeDetectorRef,
    protected winRef: WindowRef
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
      return !this.match;
    }
    if (this.step === 1) {
      return !(this.sector && this.seat);
    }
    if (this.step === 2) {
      return this.parking !== NO_PARKING && !this.plateValid;
    }
    return false;
  }

  get nextLabel(): string {
    return this.step === STEPS.length - 2 ? 'Revisar pedido' : 'Continuar';
  }

  // ---------- etapa 1: jogo ----------

  selectMatch(match: MatchdayMatch): void {
    if (this.match?.code !== match.code) {
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
      this.sector = sector;
      this.seat = undefined;
      this.ticketCode = undefined;
      this.seatMap = this.buildSeatMap(sector);
    }
  }

  selectSeat(cell: SeatCell): void {
    if (!cell.occupied) {
      this.seat = cell.id;
      this.ticketCode = cell.code ?? this.sector?.code;
    }
  }

  protected buildSeatMap(
    sector: MatchdaySector
  ): { row: string; seats: SeatCell[] }[] {
    if (sector.seats.length) {
      // poltronas reais do catálogo: livre = tem estoque
      const rows = new Map<string, SeatCell[]>();
      for (const s of sector.seats) {
        const cells = rows.get(s.row) ?? [];
        cells.push({
          id: s.label,
          number: s.number,
          occupied: !s.available,
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
        detail: `${this.match.title} · ${this.match.dateLabel}${this.seat ? ' · Poltrona ' + this.seat : ''}`,
        price: this.sector?.price ?? 0,
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
    const entries: OrderEntry[] = [
      { product: { code: this.ticketCode }, quantity: 1 },
    ];
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
    this.activeCart.addEntries(entries);

    // espera o carrinho processar as inclusões antes de seguir para o checkout
    const stable$ = this.activeCart.isStable().pipe(
      filter((stable) => !stable),
      take(1),
      switchMap(() => this.activeCart.isStable().pipe(filter(Boolean), take(1)))
    );
    this.subscription.add(
      stable$.pipe(raceWith(timer(15000)), take(1)).subscribe({
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
