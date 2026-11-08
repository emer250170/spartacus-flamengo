import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { OrderHistoryComponent } from '@spartacus/order/components';
import { Order } from '@spartacus/order/root';
import { BehaviorSubject, Observable, combineLatest, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';
import {
  EstudioService,
  ME_SITUACAO_TOM,
  MePedidoResumo,
} from '../estudio/estudio.service';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { statusTone } from './conta-pages';

/** Filtro por origem em Meus pedidos (PR 4 do Mengo Estúdio) */
export type FlaOrigem = 'todos' | 'loja' | 'estudio';

/** Origem e situação do Estúdio de cada pedido, por código */
interface MeIndice {
  /** O torcedor tem pedido do Mengo Estúdio: mostra os selos e o filtro */
  ativo: boolean;
  porCodigo: Map<string, MePedidoResumo>;
  lista: MePedidoResumo[];
}

/** Linha de personalização (vai junto da camisa, não aparece no resumo dos itens) */
const ADDON = /^FLA-PERS(-|ONALIZACAO$)/;

/** Até quantos itens aparecem no resumo da linha ("+2" no resto) */
const MAX_ITEMS = 3;

interface OrderEntries {
  entries?: { quantity?: number; product?: { code?: string; name?: string } }[];
}

/**
 * Meus pedidos no visual do mockup (07/11): número, data, os itens do pedido em uma linha,
 * situação em selo colorido, total e "Ver pedido". É o OrderHistoryComponent do Spartacus com
 * outro template: ordenação, paginação, abas (contagem no título) e navegação continuam as dele.
 * Os itens vêm do detalhe de cada pedido da página (5 por página), um pedido por chamada.
 */
@Component({
  selector: 'fla-order-history',
  templateUrl: './fla-order-history.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaOrderHistoryComponent extends OrderHistoryComponent {
  protected readonly itemsCache = new Map<string, Observable<string>>();

  protected readonly http = inject(HttpClient);
  protected readonly occ = inject(OccEndpointsService);
  protected readonly i18n = inject(FlaI18nService);
  protected readonly estudio = inject(EstudioService);

  /** Origem (loja, Mengo Estúdio) e situação do Estúdio dos pedidos do torcedor */
  readonly me$: Observable<MeIndice> = this.estudio.pedidos().pipe(
    map((lista) => ({
      ativo: lista.some((p) => p.origem.includes('estudio')),
      porCodigo: new Map(lista.map((p) => [p.code, p])),
      lista,
    })),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  readonly origem$ = new BehaviorSubject<FlaOrigem>('todos');
  readonly origens: FlaOrigem[] = ['todos', 'loja', 'estudio'];

  /** Pedidos de uma origem (todos de uma vez, mais novos primeiro), no formato da lista do Spartacus */
  readonly filtrados$: Observable<Order[]> = combineLatest([
    this.me$,
    this.origem$,
  ]).pipe(
    map(([me, o]) =>
      o === 'todos'
        ? []
        : me.lista
            .filter((p) => p.origem.includes(o))
            .map(
              (p) =>
                ({
                  code: p.code,
                  placed: p.data ? new Date(p.data) : undefined,
                  status: p.status,
                  statusDisplay: p.statusDisplay,
                  total: { formattedValue: this.i18n.money(p.total ?? 0) },
                }) as Order
            )
    )
  );

  escolherOrigem(o: FlaOrigem): void {
    this.origem$.next(o);
  }

  /** Selos de origem do pedido ("Loja", "Mengo Estúdio") */
  origemDe(order: Order, me: MeIndice): string[] {
    return me.porCodigo.get(order.code ?? '')?.origem ?? ['loja'];
  }

  /** Situação do Estúdio para o selo (pedido só do Estúdio troca a do Commerce; misto mostra as duas) */
  meSituacao(
    order: Order,
    me: MeIndice
  ): { txt: string; tom: string; so: boolean } | null {
    const p = me.porCodigo.get(order.code ?? '');
    if (!p?.situacao) {
      return null;
    }
    return {
      txt: this.i18n.t('me.sit.' + p.situacao),
      tom: ME_SITUACAO_TOM[p.situacao] ?? 'grey',
      so: !p.origem.includes('loja'),
    };
  }

  /** "Ingresso Bahia · Camisa Jogo 2 · Manto" (cache por pedido) */
  items(order: Order): Observable<string> {
    const code = order?.code ?? '';
    if (!code) {
      return of('');
    }
    let cached = this.itemsCache.get(code);
    if (!cached) {
      cached = this.http
        .get<OrderEntries>(
          `${this.occ.getBaseUrl()}/users/current/orders/${encodeURIComponent(code)}`,
          {
            params: {
              fields: 'entries(quantity,product(code,name))',
              lang: this.i18n.lang,
              curr: 'BRL',
            },
          }
        )
        .pipe(
          map((o) => summary(o)),
          catchError(() => of('')),
          shareReplay({ bufferSize: 1, refCount: false })
        );
      this.itemsCache.set(code, cached);
    }
    return cached;
  }

  /** Cor do selo da situação */
  tone(order: Order): string {
    return statusTone(order?.status ?? order?.statusDisplay ?? '');
  }
}

/** Nomes dos itens (sem a linha de personalização), "2× Nome" quando há mais de um */
function summary(o: OrderEntries): string {
  const names = (o?.entries ?? [])
    .filter((e) => !ADDON.test(e.product?.code ?? ''))
    .map((e) =>
      (e.quantity ?? 1) > 1
        ? `${e.quantity}× ${e.product?.name ?? ''}`
        : (e.product?.name ?? '')
    )
    .filter((n) => !!n.trim());
  const shown = names.slice(0, MAX_ITEMS).join(' · ');
  return names.length > MAX_ITEMS
    ? `${shown} · +${names.length - MAX_ITEMS}`
    : shown;
}
