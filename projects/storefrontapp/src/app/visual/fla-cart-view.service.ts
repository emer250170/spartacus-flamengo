import { Injectable } from '@angular/core';
import {
  ActiveCartFacade,
  Cart,
  CartVoucherFacade,
  OrderEntry,
} from '@spartacus/cart/base/root';
import { Observable, combineLatest, interval, of } from 'rxjs';
import {
  distinctUntilChanged,
  map,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs/operators';
import {
  EstudioService,
  MeCarrinho,
  MeCatalogo,
  MeCor,
  MeEstampa,
} from '../estudio/estudio.service';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from '../matchday/matchday-data.service';
import { Membership } from '../socio/socio.model';
import { SocioService } from '../socio/socio.service';
import {
  FlaPersonalizationService,
  PERSONALIZATION_ADDON,
  PersonalizationInfo,
  personalizationSummary,
} from './fla-personalization.service';

/** Item do carrinho como o box lateral e a página do carrinho mostram */
export interface DrawerEntry {
  entry: OrderEntry;
  name: string;
  /** "Tamanho: M" (variantes) */
  details: string;
  /** Preço cheio (antes do desconto), só quando há desconto */
  fullPrice?: string;
  price: string;
  /** Preço de uma unidade (página do carrinho) */
  unitPrice: string;
  /** Poltrona de jogo: sem quantidade e sem remover aqui (a jornada cuida) */
  seat: boolean;
  /** "Nome EMERSON · 10 · Kit de patrocínio" (manto personalizado) */
  personalization?: string;
  /** Linha de preço da personalização: a quantidade segue a do manto */
  addOn?: boolean;
  /** Camiseta do Mengo Estúdio (estampa + configuração) */
  estudio?: DrawerEstudio;
}

/** Linha do Mengo Estúdio no carrinho (08/11, PR 3) */
export interface DrawerEstudio {
  estampa: MeEstampa;
  cor: MeCor;
  /** "À venda até 01/12, 23h59 · produção sob demanda" ou o contador */
  prazo: string;
  urgente: boolean;
  /** A estampa saiu de linha: o item trava o fechamento do pedido */
  encerrada: boolean;
  /** "Editar": volta para a página da estampa com a configuração */
  link: string[];
  query: Record<string, string | number>;
}

/** Camisetas do Mengo Estúdio (produto por modelo e tecido) */
export const ESTUDIO_CAMISETA = /^ME-CAM-/;

export interface DrawerView {
  cartCode?: string;
  entries: DrawerEntry[];
  count: number;
  subtotal: string;
  discount?: string;
  discountLabel: string;
  /** Nome do plano quando o desconto é de sócio ("Nação Maraca 1") */
  memberPlan?: string;
  /** Frete já escolhido (checkout) */
  delivery?: string;
  total: string;
  /** "13:42" enquanto há poltrona reservada no carrinho */
  holdLeft?: string;
  /** Cupons aplicados */
  vouchers: { code: string; name: string }[];
  /** Há estampa do Mengo Estúdio que saiu de linha: "Finalizar compra" fica travado */
  bloqueio?: boolean;
  /** Há camiseta do Mengo Estúdio no carrinho */
  temEstudio?: boolean;
}

/**
 * Carrinho como o visual da loja oficial mostra (box lateral e página do carrinho): itens com
 * variante, personalização e preço cheio riscado, subtotal, desconto (de sócio quando há plano),
 * total, tempo da poltrona reservada; e as ações (quantidade, remover, cupom).
 */
@Injectable({ providedIn: 'root' })
export class FlaCartViewService {
  readonly view$: Observable<DrawerView | null>;
  readonly loading$: Observable<boolean>;

  constructor(
    protected activeCart: ActiveCartFacade,
    protected vouchers: CartVoucherFacade,
    protected i18n: FlaI18nService,
    protected matchday: MatchdayDataService,
    protected socio: SocioService,
    protected personalization: FlaPersonalizationService,
    protected estudio: EstudioService
  ) {
    this.loading$ = this.activeCart.isStable().pipe(map((stable) => !stable));
    const member$ = this.socio.getMembership().pipe(
      map((m: Membership) => (m?.member ?? m?.inCart)?.name ?? undefined),
      startWith(undefined as string | undefined)
    );
    const tick$ = interval(1000).pipe(startWith(0));
    // personalização dos mantos: só busca quando há linha de preço (FLA-PERS-*) no carrinho
    const pers$ = this.activeCart.getActive().pipe(
      map((cart) =>
        cart?.code &&
        (cart.entries ?? []).some((e) =>
          PERSONALIZATION_ADDON.test(e.product?.code ?? '')
        )
          ? `${cart.code}|${(cart.entries ?? [])
              .map((e) => `${e.entryNumber}:${e.product?.code}:${e.quantity}`)
              .join(',')}`
          : ''
      ),
      distinctUntilChanged(),
      switchMap((key) =>
        key
          ? this.personalization.forCart('current', key.split('|')[0])
          : of([] as PersonalizationInfo[])
      ),
      startWith([] as PersonalizationInfo[])
    );
    // Mengo Estúdio: só busca quando há camiseta do Estúdio (ME-CAM-*) no carrinho
    const est$ = this.activeCart.getActive().pipe(
      map((cart) =>
        cart?.code &&
        cart.user?.uid !== 'anonymous' &&
        (cart.entries ?? []).some((e) =>
          ESTUDIO_CAMISETA.test(e.product?.code ?? '')
        )
          ? `${cart.code}|${(cart.entries ?? [])
              .map((e) => `${e.entryNumber}:${e.product?.code}:${e.quantity}`)
              .join(',')}`
          : ''
      ),
      distinctUntilChanged(),
      switchMap((key) =>
        key
          ? combineLatest([
              this.estudio.carrinho(key.split('|')[0]),
              this.estudio.catalogo$,
            ])
          : of([undefined, undefined] as [MeCarrinho | undefined, MeCatalogo | undefined])
      ),
      startWith([undefined, undefined] as [MeCarrinho | undefined, MeCatalogo | undefined])
    );
    this.view$ = combineLatest([
      this.activeCart.getActive(),
      member$,
      tick$,
      this.i18n.lang$,
      pers$,
      est$,
    ]).pipe(
      map(([cart, plan, , , pers, est]) => this.toView(cart, plan, pers, est)),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }

  increase(e: DrawerEntry): void {
    this.activeCart.updateEntry(
      e.entry.entryNumber as number,
      (e.entry.quantity ?? 0) + 1
    );
  }

  decrease(e: DrawerEntry): void {
    const qty = (e.entry.quantity ?? 0) - 1;
    if (qty <= 0) {
      this.remove(e);
    } else {
      this.activeCart.updateEntry(e.entry.entryNumber as number, qty);
    }
  }

  remove(e: DrawerEntry): void {
    this.activeCart.removeEntry(e.entry);
  }

  applyCoupon(input: HTMLInputElement): void {
    const code = input.value.trim();
    if (code) {
      this.vouchers.addVoucher(code);
      input.value = '';
    }
  }

  removeCoupon(code: string): void {
    this.vouchers.removeVoucher(code);
  }

  protected toView(
    cart: Cart,
    plan: string | undefined,
    pers: PersonalizationInfo[] = [],
    est: [MeCarrinho | undefined, MeCatalogo | undefined] = [undefined, undefined]
  ): DrawerView | null {
    const entries = cart?.entries ?? [];
    if (!entries.length) {
      return null;
    }
    const money = (v: number) => this.i18n.money(v);
    const items: DrawerEntry[] = entries.map((entry) => {
      const qty = entry.quantity ?? 1;
      const total = entry.totalPrice?.value ?? 0;
      const full = (entry.basePrice?.value ?? 0) * qty;
      const qualifiers =
        entry.product?.baseOptions?.[0]?.selected?.variantOptionQualifiers ??
        [];
      return {
        entry,
        name: entry.product?.name ?? entry.product?.code ?? '',
        details: qualifiers
          .filter((q) => q.name && q.value)
          .map((q) => `${q.name}: ${q.value}`)
          .join(' · '),
        fullPrice: full > total + 0.004 ? money(full) : undefined,
        price: entry.totalPrice?.formattedValue ?? money(total),
        unitPrice: money(qty > 0 ? total / qty : total),
        seat: !!this.matchday.matchCodeOfSeat(entry.product?.code ?? ''),
        personalization: this.personalizationOf(entry, pers),
        addOn: PERSONALIZATION_ADDON.test(entry.product?.code ?? ''),
      };
    }).map((d) => this.withEstudio(d, est));
    // subTotal = itens já com o desconto de cada item; orderDiscounts = desconto no pedido
    const orderDiscount = cart.orderDiscounts?.value ?? 0;
    const total = (cart.subTotal?.value ?? 0) - orderDiscount;
    const discount = cart.totalDiscounts?.value ?? 0;
    const delivery = cart.deliveryCost?.value;
    return {
      cartCode: cart.code,
      entries: items,
      count: cart.totalUnitCount ?? items.length,
      subtotal: money(total + discount),
      discount: discount > 0.004 ? money(discount) : undefined,
      discountLabel: this.i18n.t(plan ? 'cd.memberDiscount' : 'cd.discount'),
      memberPlan: discount > 0.004 ? plan : undefined,
      delivery: delivery !== undefined && delivery !== null ? money(delivery) : undefined,
      total: money(delivery ? total + delivery : total),
      holdLeft: this.holdLeft(entries),
      vouchers: (cart.appliedVouchers ?? []).map((v) => ({
        code: v.code ?? v.voucherCode ?? '',
        name: v.name ?? v.voucherCode ?? v.code ?? '',
      })),
      bloqueio: items.some((i) => i.estudio?.encerrada),
      temEstudio: items.some((i) => !!i.estudio),
    };
  }

  /** Camiseta do Mengo Estúdio: "Estampa Elenco", configuração, prazo da estampa e o link para editar */
  protected withEstudio(
    d: DrawerEntry,
    [carrinho, c]: [MeCarrinho | undefined, MeCatalogo | undefined]
  ): DrawerEntry {
    const i = carrinho?.itens.find((x) => x.entryNumber === d.entry.entryNumber);
    if (!i || !c) {
      return d;
    }
    const now = this.estudio.now(c);
    const sit = this.estudio.situacao(i.estampa, c, now);
    const encerrada = i.encerrada || sit === 'ENCERRADA';
    return {
      ...d,
      name: this.i18n.t('me.c.nome', { n: i.estampa.nome }),
      details: this.estudio.configuracao(i),
      estudio: {
        estampa: i.estampa,
        cor: i.cor,
        prazo: `${this.estudio.prazo(i.estampa, c, now)} · ${this.i18n.t('me.c.sobDemanda')}`,
        urgente: sit === 'ULTIMAS_48H',
        encerrada,
        link: ['/mengo-estudio/estampa', i.estampa.code],
        query: {
          modelo: i.modelo,
          tecido: i.tecido,
          cor: i.cor,
          tamanho: i.tamanho,
          qtd: i.quantidade,
          editar: i.entryNumber,
        },
      },
    };
  }

  protected personalizationOf(
    entry: OrderEntry,
    pers: PersonalizationInfo[]
  ): string | undefined {
    const p = pers.find((x) => x.entryNumber === entry.entryNumber);
    return p
      ? personalizationSummary(p, this.i18n.t('pz.sponsorShort'))
      : undefined;
  }

  /** Tempo que falta da reserva da poltrona que está no carrinho */
  protected holdLeft(entries: OrderEntry[]): string | undefined {
    const hold = this.matchday.readHold();
    if (
      !hold?.expiresAt ||
      !entries.some((e) => e.product?.code === hold.code)
    ) {
      return undefined;
    }
    const ms = hold.expiresAt - Date.now();
    if (ms <= 0) {
      return undefined;
    }
    const s = Math.floor(ms / 1000);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }
}
