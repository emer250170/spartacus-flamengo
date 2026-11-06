import { Injectable, OnDestroy, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActiveCartFacade, OrderEntry } from '@spartacus/cart/base/root';
import {
  AuthService,
  OccEndpointsService,
  PageMetaService,
  ProductScope,
  ProductService,
  RouterState,
  RoutingService,
  WindowRef,
} from '@spartacus/core';
import { Order, OrderFacade } from '@spartacus/order/root';
import { UserAccountFacade } from '@spartacus/user/account/root';
import {
  Observable,
  Subscription,
  firstValueFrom,
  of,
  race,
  timer,
} from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  shareReplay,
  skip,
  switchMap,
  take,
} from 'rxjs/operators';

/** Linha de carrinho/pedido no formato do Emarsys (preço = total da linha) */
export interface EmarsysItem {
  item: string;
  price: number;
  quantity: number;
}

type ScarabCommand = unknown[];

/** Espera máxima por um dado (produto, carrinho, usuário) antes de mandar sem ele */
const ESPERA_MS = 5000;
/** Profundidade máxima de variantes (jogo › setor › poltrona) */
const MAX_NIVEIS = 4;
/** Pedidos já enviados nesta aba (o F5 na confirmação não manda a compra de novo) */
const CHAVE_COMPRAS = 'fla-emarsys-compras';
/** localStorage['fla-emarsys-debug'] = '1' mostra no console cada comando enviado */
const CHAVE_DEBUG = 'fla-emarsys-debug';

/**
 * Rastreamento do Emarsys (Web Extend / Scarab) na loja.
 *
 * A tag `scarab-v2.js` fica no index.html. A loja é uma página única: o Emarsys
 * não vê troca de página sozinho, então a cada navegação este serviço manda os
 * comandos da página e fecha com `go`:
 *
 * - `setEmail`: torcedor logado (o uid é o e-mail);
 * - `cart`: carrinho atual (em toda página, vazio se não houver nada);
 * - `view`: página de produto ou jornada de matchday com `?jogo=`;
 * - `category`: página de categoria, com o caminho completo do catálogo exportado
 *   (`GET /emarsys/categorias`; sem ele, o nome da categoria como na tela);
 * - `searchTerm`: página de busca;
 * - `purchase`: confirmação do pedido (uma vez por pedido).
 *
 * Mudança no carrinho sem trocar de página (gaveta do carrinho) manda
 * `cart` + `go` de novo.
 *
 * Os códigos enviados são sempre do produto **base** (poltrona → jogo,
 * tamanho → camisa), os mesmos do catálogo de produtos exportado para o
 * Emarsys. Sem a tag no index.html, nada é enviado.
 */
@Injectable({ providedIn: 'root' })
export class EmarsysTrackingService implements OnDestroy {
  protected routing = inject(RoutingService);
  protected cart = inject(ActiveCartFacade);
  protected auth = inject(AuthService);
  protected userAccount = inject(UserAccountFacade);
  protected products = inject(ProductService);
  protected orders = inject(OrderFacade);
  protected pageMeta = inject(PageMetaService);
  protected http = inject(HttpClient);
  protected occEndpoints = inject(OccEndpointsService);
  protected winRef = inject(WindowRef);
  protected platformId = inject(PLATFORM_ID);

  protected subscription = new Subscription();
  protected baseCache = new Map<string, Observable<string>>();
  /** Caminho de cada categoria, igual ao campo category do catálogo exportado */
  protected caminhos$?: Observable<Record<string, string>>;
  protected iniciado = false;
  /** Assinatura do último carrinho enviado (evita reenviar o mesmo) */
  protected ultimoCarrinho?: string;

  iniciar(): void {
    if (this.iniciado || !isPlatformBrowser(this.platformId)) {
      return;
    }
    this.iniciado = true;

    // uma visualização por navegação concluída
    this.subscription.add(
      this.routing
        .getRouterState()
        .pipe(
          filter((s) => !!s?.state?.url && !s.nextState),
          distinctUntilChanged((a, b) => a.state.url === b.state.url),
          debounceTime(150),
          switchMap((s) => this.comandosDaPagina(s))
        )
        .subscribe((comandos) => this.enviar(comandos))
    );

    // carrinho mudou sem trocar de página
    this.subscription.add(
      this.cart
        .isStable()
        .pipe(
          filter(Boolean),
          switchMap(() => this.cart.getEntries().pipe(take(1))),
          map((entries) => this.assinatura(entries)),
          distinctUntilChanged(),
          skip(1),
          debounceTime(500),
          filter((assinatura) => assinatura !== this.ultimoCarrinho),
          switchMap(() => this.comandosDoCarrinho())
        )
        .subscribe((comandos) => this.enviar(comandos))
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  // ---------------------------------------------------------------------------
  // montagem dos comandos

  protected comandosDaPagina(s: RouterState): Promise<ScarabCommand[]> {
    return (async () => {
      const comandos: ScarabCommand[] = [];
      const route = s.state.semanticRoute ?? '';
      const params = s.state.params ?? {};
      const query = s.state.queryParams ?? {};

      const email = await this.emailDoTorcedor();
      if (email) {
        comandos.push(['setEmail', email]);
      }

      if (route === 'orderConfirmation') {
        const order = await this.pedidoConfirmado();
        if (order?.code && !this.compraJaEnviada(order.code)) {
          // pedido feito: o carrinho fica vazio e a compra vai uma vez só
          comandos.push(['cart', []]);
          comandos.push([
            'purchase',
            {
              orderId: order.code,
              items: await this.itens(order.entries ?? []),
            },
          ]);
          this.marcarCompra(order.code);
          this.ultimoCarrinho = this.assinatura([]);
          // sem o go a compra fica na fila e não chega ao Emarsys
          comandos.push(['go']);
          return comandos;
        }
      }

      comandos.push(...(await this.comandosDoCarrinho()).slice(0, -1));

      const jogo = query['jogo'];
      const produto =
        route === 'product'
          ? params['productCode']
          : jogo && jogo !== 'undefined'
            ? jogo
            : undefined;
      if (produto) {
        comandos.push(['view', await this.base(produto)]);
      } else if (route === 'category') {
        const categoria =
          (await this.caminhoDaCategoria(params['categoryCode'])) ??
          (await this.nomeDaCategoria());
        if (categoria) {
          comandos.push(['category', categoria]);
        }
      } else if (route === 'search' && params['query']) {
        comandos.push(['searchTerm', decodeURIComponent(params['query'])]);
      }

      comandos.push(['go']);
      return comandos;
    })().catch((e) => {
      this.debug('erro ao montar os comandos', e);
      return [['go']];
    });
  }

  /** `cart` + `go` com o carrinho atual */
  protected async comandosDoCarrinho(): Promise<ScarabCommand[]> {
    const entries = await this.comEspera(
      this.cart.isStable().pipe(
        filter(Boolean),
        switchMap(() => this.cart.getEntries())
      ),
      [] as OrderEntry[]
    );
    this.ultimoCarrinho = this.assinatura(entries);
    return [['cart', await this.itens(entries)], ['go']];
  }

  /** Linhas no formato do Emarsys, somadas por produto base */
  protected async itens(entries: OrderEntry[]): Promise<EmarsysItem[]> {
    const porBase = new Map<string, EmarsysItem>();
    for (const e of entries) {
      const code = e.product?.code;
      if (!code) {
        continue;
      }
      const item = await this.base(code);
      const quantity = e.quantity ?? 1;
      const price = e.totalPrice?.value ?? (e.basePrice?.value ?? 0) * quantity;
      const atual = porBase.get(item);
      if (atual) {
        atual.quantity += quantity;
        atual.price = this.arredondar(atual.price + price);
      } else {
        porBase.set(item, { item, price: this.arredondar(price), quantity });
      }
    }
    return [...porBase.values()];
  }

  // ---------------------------------------------------------------------------
  // dados

  /** Código do produto base (sobe pelas variantes); se não achar, o próprio código */
  protected base(code: string): Promise<string> {
    return firstValueFrom(this.base$(code, 0));
  }

  protected base$(code: string, nivel: number): Observable<string> {
    let cached = this.baseCache.get(code);
    if (!cached) {
      cached = race(
        this.products.get(code, ProductScope.LIST).pipe(
          filter((p) => !!p),
          take(1),
          switchMap((p) =>
            p?.baseProduct && p.baseProduct !== code && nivel < MAX_NIVEIS
              ? this.base$(p.baseProduct, nivel + 1)
              : of(code)
          )
        ),
        timer(ESPERA_MS).pipe(map(() => code))
      ).pipe(
        take(1),
        catchError(() => of(code)),
        shareReplay(1)
      );
      this.baseCache.set(code, cached);
    }
    return cached;
  }

  protected emailDoTorcedor(): Promise<string | undefined> {
    return this.comEspera(
      this.auth.isUserLoggedIn().pipe(
        take(1),
        switchMap((logado) =>
          logado
            ? this.userAccount.get().pipe(
                filter((u) => !!u?.uid),
                map((u) => u?.uid?.toLowerCase())
              )
            : of(undefined)
        )
      ),
      undefined
    );
  }

  protected pedidoConfirmado(): Promise<Order | undefined> {
    return this.comEspera(
      this.orders.getOrderDetails().pipe(filter((o) => !!o?.code)),
      undefined
    );
  }

  /**
   * Caminho completo da categoria ("Masculino > Roupas > Camisas Diversas"), o mesmo
   * do campo category do catálogo (GET /emarsys/categorias). Busca uma vez por sessão.
   */
  protected caminhoDaCategoria(code?: string): Promise<string | undefined> {
    if (!code) {
      return Promise.resolve(undefined);
    }
    if (!this.caminhos$) {
      this.caminhos$ = this.http
        .get<
          Record<string, string>
        >(`${this.occEndpoints.getBaseUrl()}/emarsys/categorias`)
        .pipe(
          catchError(() => of({} as Record<string, string>)),
          shareReplay(1)
        );
    }
    return this.comEspera(
      this.caminhos$.pipe(map((mapa) => mapa?.[code] || undefined)),
      undefined
    );
  }

  /** Nome da categoria como aparece na tela (último item do breadcrumb), se não houver o caminho */
  protected nomeDaCategoria(): Promise<string | undefined> {
    return this.comEspera(
      this.pageMeta.getMeta().pipe(
        map((meta) => {
          const trilha = meta?.breadcrumbs ?? [];
          return trilha.length > 1
            ? trilha[trilha.length - 1].label
            : undefined;
        }),
        filter((nome): nome is string => !!nome)
      ),
      undefined
    );
  }

  /** Primeiro valor da fonte; se demorar, o valor padrão */
  protected comEspera<T>(fonte: Observable<T>, padrao: T): Promise<T> {
    return firstValueFrom(
      race(fonte.pipe(take(1)), timer(ESPERA_MS).pipe(map(() => padrao))).pipe(
        catchError(() => of(padrao))
      )
    );
  }

  // ---------------------------------------------------------------------------
  // envio

  protected enviar(comandos: ScarabCommand[]): void {
    const w = this.winRef.nativeWindow as
      | (Window & { ScarabQueue?: ScarabCommand[] })
      | undefined;
    if (!w || !w.ScarabQueue) {
      // sem a tag do Emarsys no index.html, nada a fazer
      return;
    }
    for (const c of comandos) {
      w.ScarabQueue.push(c);
    }
    this.debug('comandos enviados', comandos);
  }

  protected compraJaEnviada(code: string): boolean {
    return this.lerCompras().includes(code);
  }

  protected marcarCompra(code: string): void {
    try {
      const compras = [...this.lerCompras(), code].slice(-20);
      this.winRef.sessionStorage?.setItem(
        CHAVE_COMPRAS,
        JSON.stringify(compras)
      );
    } catch {
      // armazenamento bloqueado: segue sem a proteção do F5
    }
  }

  protected lerCompras(): string[] {
    try {
      return JSON.parse(
        this.winRef.sessionStorage?.getItem(CHAVE_COMPRAS) ?? '[]'
      );
    } catch {
      return [];
    }
  }

  protected assinatura(entries: OrderEntry[]): string {
    return entries
      .map((e) => `${e.product?.code}:${e.quantity}:${e.totalPrice?.value}`)
      .sort()
      .join('|');
  }

  protected arredondar(valor: number): number {
    return Math.round(valor * 100) / 100;
  }

  protected debug(...args: unknown[]): void {
    try {
      if (this.winRef.localStorage?.getItem(CHAVE_DEBUG) === '1') {
        // eslint-disable-next-line no-console
        console.log('[Emarsys]', ...args);
      }
    } catch {
      // sem localStorage: sem log
    }
  }
}
