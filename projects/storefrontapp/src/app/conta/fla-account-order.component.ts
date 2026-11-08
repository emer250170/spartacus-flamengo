import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostListener,
  inject,
} from '@angular/core';
import { OrderEntry } from '@spartacus/cart/base/root';
import { OrderDetailsService } from '@spartacus/order/components';
import { Order } from '@spartacus/order/root';
import { BehaviorSubject, Observable, combineLatest, of } from 'rxjs';
import {
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import {
  BodyView,
  DIGITAL,
  FlaOcBodyComponent,
  OcItem,
} from '../visual/fla-order-confirmation.component';
import {
  ME_SITUACAO_TOM,
  MeCancelamento,
  MePedido,
  MeSituacaoPedido,
} from '../estudio/estudio.service';
import { statusTone } from './conta-pages';

/** Caminho normal da parte Mengo Estúdio do pedido */
const ME_FLUXO: MeSituacaoPedido[] = [
  'RECEBIDO',
  'EM_PRODUCAO',
  'ENVIADO',
  'ENTREGUE',
];

/** Um passo do "Andamento" (linha do tempo do Mengo Estúdio) */
interface MePasso {
  nome: string;
  quando?: string;
  nota?: string;
  estado: 'feito' | 'pendente' | 'cancelado';
}

/** Parte Mengo Estúdio do detalhe do pedido (PR 4) */
interface MeConta {
  pedido: MePedido;
  situacao: string;
  tom: string;
  passos: MePasso[];
  series: { nome: string; det: string; serie: string }[];
  /** Por que não dá mais para cancelar (quando não dá) */
  semCancelar?: string;
}

interface OrderLine {
  entry: OrderEntry;
  /** Camiseta do Mengo Estúdio: miniatura com a estampa */
  me?: OcItem['me'];
  name: string;
  code: string;
  details: string;
  qty: number;
  price: string;
  /** Preço sem desconto, riscado */
  was?: string;
  /** "Comprar de novo" (produto da loja, não ingresso, plano ou serviço) */
  again: boolean;
}

interface OrderGroup {
  key: 'ship' | 'pickup';
  sub: string;
  lines: OrderLine[];
}

interface AccountOrderView {
  order: Order;
  tone: string;
  groups: OrderGroup[];
  body: BodyView;
  saved?: string;
  /** Pedido com camiseta do Mengo Estúdio */
  me?: MeConta;
}

/**
 * Detalhe do pedido em Minha conta no visual do mockup (07/11): número, data e situação em linha;
 * itens a entregar e itens para retirar (miniatura, código, detalhes, quantidade, preço com o
 * valor cheio riscado, "Comprar de novo"); "Entrega e pagamento" ao lado do Resumo. Os valores,
 * a entrega, o pagamento e os detalhes dos itens (personalização, titular do ingresso) vêm da
 * mesma lógica da confirmação do pedido (FlaOcBodyComponent), com o pedido da página. Ficam os
 * componentes do Spartacus para o Pix/boleto, exportar CSV e Voltar/Cancelar/Devolver; os outros
 * somem no tema. Entra pelo outlet do slot BodyContent (antes) e só aparece no detalhe do pedido.
 */
@Component({
  selector: 'fla-account-order',
  templateUrl: './fla-account-order.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaAccountOrderComponent extends FlaOcBodyComponent {
  protected readonly cd = inject(ChangeDetectorRef);
  protected readonly host = inject(ElementRef);
  /** Pedido do Estúdio devolvido pelo cancelamento (vale enquanto for o mesmo pedido) */
  protected readonly meNovo$ = new BehaviorSubject<MePedido | null>(null);

  readonly account$: Observable<AccountOrderView | null> = combineLatest([
    this.order$,
    this.view$,
    combineLatest([this.me$, this.meNovo$]).pipe(
      map(([me, novo]) => (novo && novo.code === me?.code ? novo : me))
    ),
  ]).pipe(
    map(([order, body, me]) =>
      order && body ? this.toAccountView(order, body, me) : null
    ),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  /** Diálogo "Cancelar pedido?": pergunta, enviando, falha */
  modal: 'pergunta' | 'enviando' | 'falha' | null = null;
  /** Resultado do cancelamento, no cartão Ações */
  resultado: { texto: string; tom: 'ok' | 'aviso' } | null = null;
  protected abridor?: HTMLElement;

  /** Pedido da página /my-account/order/{code} (no lugar do pedido da confirmação) */
  protected orderSource(): Observable<Order | undefined> {
    const details = inject(OrderDetailsService);
    // só no detalhe do pedido (o componente entra em toda página pelo outlet do BodyContent)
    return this.routing.getRouterState().pipe(
      map((s) => s?.state?.semanticRoute === 'orderDetails'),
      distinctUntilChanged(),
      switchMap((on) => (on ? details.getOrderDetails() : of(undefined))),
      map((o) => (o?.code ? o : undefined)),
      distinctUntilChanged(),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }

  /** Abre o "Cancelar pedido?" */
  abrirCancelar(ev: Event): void {
    this.abridor = ev.target as HTMLElement;
    this.resultado = null;
    this.modal = 'pergunta';
    this.cd.markForCheck();
  }

  fecharCancelar(): void {
    if (this.modal === 'enviando') {
      return;
    }
    this.modal = null;
    this.cd.markForCheck();
    setTimeout(() => this.abridor?.focus());
  }

  @HostListener('document:keydown.escape')
  esc(): void {
    if (this.modal) {
      this.fecharCancelar();
    }
  }

  /** Pede o cancelamento à estamparia: aceito, recusado (entrou em produção) ou falha (tenta de novo) */
  confirmarCancelar(code: string): void {
    if (this.modal === 'enviando') {
      return;
    }
    this.modal = 'enviando';
    this.cd.markForCheck();
    this.estudio.cancelar(code).subscribe({
      next: (r: MeCancelamento) => {
        if (r.resultado === 'FALHA') {
          this.modal = 'falha';
          this.cd.markForCheck();
          return;
        }
        if (r.pedido) {
          this.meNovo$.next(r.pedido);
        }
        this.modal = null;
        this.resultado = {
          texto: r.mensagem,
          tom: r.resultado === 'ACEITO' ? 'ok' : 'aviso',
        };
        this.cd.markForCheck();
        setTimeout(() =>
          this.host.nativeElement
            .querySelector('.fao-me-msg')
            ?.focus?.({ preventScroll: false })
        );
      },
      error: (e: Error) => {
        this.modal = null;
        this.resultado = {
          texto: e.message || this.i18n.t('me.o.erro'),
          tom: 'aviso',
        };
        this.cd.markForCheck();
      },
    });
  }

  protected toAccountView(
    order: Order,
    body: BodyView,
    me?: MePedido | null
  ): AccountOrderView {
    const money = (v: number) => this.i18n.money(v);
    const lines = body.items.map((item) => this.toLine(order, item, money));
    const ship = lines.filter((l) => !l.entry.deliveryPointOfService);
    const pickup = lines.filter((l) => !!l.entry.deliveryPointOfService);
    const groups: OrderGroup[] = [];
    if (ship.length) {
      groups.push({
        key: 'ship',
        sub: [order.deliveryMode?.name, order.deliveryMode?.description]
          .filter((x) => !!x)
          .join(' · '),
        lines: ship,
      });
    }
    if (pickup.length) {
      const pos = pickup[0].entry.deliveryPointOfService;
      const a = pos?.address;
      groups.push({
        key: 'pickup',
        sub: [
          pos?.displayName ?? pos?.name,
          [a?.line1, a?.line2].filter((x) => !!x).join(', '),
          a?.town,
        ]
          .filter((x) => !!x)
          .join(' · '),
        lines: pickup,
      });
    }
    const saved = order.totalDiscounts?.value ?? 0;
    return {
      order,
      tone: statusTone(order.status ?? order.statusDisplay ?? ''),
      groups,
      body,
      saved: saved > 0.004 ? money(saved) : undefined,
      me: me?.temEstudio ? this.toMe(me) : undefined,
    };
  }

  /** Andamento, série, rastreio e cancelamento da parte Mengo Estúdio */
  protected toMe(p: MePedido): MeConta {
    const sit: MeSituacaoPedido = p.situacao ?? 'RECEBIDO';
    const ultimo = (s: MeSituacaoPedido) =>
      [...p.eventos].reverse().find((e) => e.situacao === s);
    let passos: MePasso[];
    const idx = ME_FLUXO.indexOf(sit);
    if (idx >= 0) {
      passos = ME_FLUXO.map((s, i) => {
        const e = ultimo(s);
        const feito = i <= idx;
        return {
          nome: this.i18n.t('me.sit.' + s),
          quando: feito && e?.data ? this.quando(e.data) : undefined,
          nota: feito ? e?.nota : this.i18n.t('me.o.pend.' + s),
          estado: feito ? 'feito' : 'pendente',
        };
      });
    } else {
      // cancelado ou devolução: o que aconteceu, na ordem
      passos = p.eventos.map((e) => ({
        nome: this.i18n.t('me.sit.' + e.situacao),
        quando: e.data ? this.quando(e.data) : undefined,
        nota: e.nota,
        estado: e.situacao === 'CANCELADO' ? 'cancelado' : 'feito',
      }));
    }
    return {
      pedido: p,
      situacao: this.i18n.t('me.sit.' + sit),
      tom: ME_SITUACAO_TOM[sit] ?? 'grey',
      passos,
      series: p.itens
        .filter((i) => !!i.numeroSerie)
        .map((i) => ({
          nome: this.i18n.t('me.c.nome', { n: i.estampa.nome }),
          det: this.estudio.configuracao(i),
          serie: i.numeroSerie ?? '',
        })),
      semCancelar: p.podeCancelar
        ? undefined
        : this.i18n.t(
            sit === 'CANCELADO'
              ? 'me.o.jaCancelado'
              : p.devolucao
                ? 'me.o.comDevolucao'
                : sit === 'ENTREGUE'
                  ? 'me.o.entregue'
                  : 'me.o.naoCancela'
          ),
    };
  }

  /** "07/11, 10h12" (horário de Brasília) */
  protected quando(ms: number): string {
    const pt = this.i18n.lang === 'pt';
    const o: Record<string, string> = {};
    new Intl.DateTimeFormat(pt ? 'pt-BR' : 'en-US', {
      timeZone: 'America/Sao_Paulo',
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(ms))
      .forEach((x) => (o[x.type] = x.value));
    return pt
      ? `${o['day']}/${o['month']}, ${o['hour']}h${o['minute']}`
      : `${o['month']}/${o['day']}, ${o['hour']}:${o['minute']}`;
  }

  protected toLine(
    order: Order,
    item: OcItem,
    money: (v: number) => string
  ): OrderLine {
    const entry = item.entry;
    const code = entry.product?.code ?? '';
    const qty = entry.quantity ?? 1;
    const full = (entry.basePrice?.value ?? 0) * qty;
    const paid = entry.totalPrice?.value ?? full;
    // promoção do item (ex.: "Sócio Nação Maraca 3: 60% de desconto no ingresso")
    const promo = (order.appliedProductPromotions ?? [])
      .filter((p) =>
        (p.consumedEntries ?? []).some(
          (c) => c.orderEntryNumber === entry.entryNumber
        )
      )
      .map((p) => p.description ?? '')
      .filter((d) => !!d);
    // o último detalhe da confirmação é "N unidade(s)": aqui a quantidade tem coluna própria
    const details = [...item.details.slice(0, -1), ...promo].join(' · ');
    return {
      entry,
      me: item.me,
      name: item.name,
      code,
      details,
      qty,
      price: item.price,
      was: full - paid > 0.004 ? money(full) : undefined,
      again:
        !item.me &&
        !!code &&
        !DIGITAL.test(code) &&
        entry.product?.purchasable !== false,
    };
  }
}
