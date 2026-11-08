import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ActiveCartFacade } from '@spartacus/cart/base/root';
import { AuthRedirectService, AuthService, RoutingService } from '@spartacus/core';
import { FocusConfig, LAUNCH_CALLER, LaunchDialogService } from '@spartacus/storefront';
import { BehaviorSubject, Observable, Subscription, combineLatest } from 'rxjs';
import { map, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  EstudioService,
  ME_COR_HEX,
  ME_CORES,
  ME_MODELOS,
  MeCamiseta,
  MeCatalogo,
  MeCor,
  MeEstampa,
  MeSituacao,
  meTrack,
} from './estudio.service';

type Modelo = 'MAS' | 'FEM' | 'INF';
type Tecido = 'ALG' | 'POL' | 'OVS';

/** Escolhas do torcedor no configurador */
interface Escolha {
  modelo: Modelo;
  tecido: Tecido;
  cor: MeCor;
  tamanho: string;
  qtd: number;
}

interface Card {
  e: MeEstampa;
  sit: MeSituacao;
  prazo: string;
  urgente: boolean;
}

interface Vm {
  c: MeCatalogo;
  e?: MeEstampa;
  sit?: MeSituacao;
  encerrada: boolean;
  /** "À venda até 01/12, 23h59 (Brasília)" */
  ate: string;
  /** Contador quando faltam até 7 dias (senão vazio) */
  contador: string;
  saiuEm: string;
  dropNome: string;
  linhaNome: string;
  titulo: string;
  subtitulo: string;
  modelos: { code: Modelo; ok: boolean }[];
  tecidos: { code: Tecido; precoTxt: string }[];
  cam?: MeCamiseta;
  precoTxt: string;
  parcelas: string;
  cores: { code: MeCor; hex: string; motivo?: string }[];
  tamanhos: string[];
  medidas: MeCamiseta['medidas'];
  totalTxt: string;
  totalParcelas: string;
  resumo: string;
  max: number;
  maisLinha: Card[];
  deOutras: Card[];
  outras: Card[];
  filtros: { code: string; nome: string }[];
}

/**
 * Configurador do Mengo Estúdio (08/11, PR 3; mockup E7–E11, E13): /mengo-estudio/estampa/:code (com a estampa) e
 * /mengo-estudio/camiseta (sem estampa: "Escolher a estampa"). Modelo, tecido e corte, cor (a arte pode bloquear
 * cores, com o motivo), tamanho por modelo e tecido (trocar limpa o que deixou de existir e avisa), quantidade de 1
 * a 10, resumo e "Adicionar ao carrinho" (POST /users/current/carts/{code}/mengo-estudio, o carrinho é criado se
 * não existir). Estampa encerrada: compra bloqueada e "Avise-me se voltar" no acervo. Painéis "Outras estampas" e
 * "Tabela de medidas" fecham no Esc e no fundo. ?editar=N (link "Editar" do carrinho) troca a linha N.
 */
@Component({
  selector: 'fla-estudio-produto',
  templateUrl: './estudio-produto.component.html',
  styleUrls: ['./estudio.scss', './estudio-produto.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioProdutoComponent implements OnInit, OnDestroy {
  @ViewChild('addBtn', { read: ElementRef }) addBtn?: ElementRef<HTMLElement>;
  @ViewChild('tamanhoRef') tamanhoRef?: ElementRef<HTMLElement>;

  readonly tb = meTrack;
  readonly hex = ME_COR_HEX;
  readonly focusConfig: FocusConfig = { trap: true, block: true, autofocus: true, focusOnEscape: true };

  escolha: Escolha = { modelo: 'MAS', tecido: 'ALG', cor: 'PRT', tamanho: '', qtd: 1 };
  /** Linha do carrinho em edição (?editar=N) */
  editar?: number;
  avisoTamanho = '';
  avisoTecido = '';
  avisoCor = '';
  erroTamanho = '';
  erro = '';
  salvando = false;
  painel: '' | 'outras' | 'medidas' = '';
  filtroOutras = '';

  protected readonly escolha$ = new BehaviorSubject<Escolha>(this.escolha);
  protected readonly code$: Observable<string | undefined> = this.route.paramMap.pipe(
    map((p) => p.get('code') ?? undefined)
  );
  readonly vm$: Observable<Vm>;
  protected subscription = new Subscription();
  protected lastEstampa?: string;

  constructor(
    protected estudio: EstudioService,
    protected i18n: FlaI18nService,
    protected route: ActivatedRoute,
    protected router: Router,
    protected routing: RoutingService,
    protected activeCart: ActiveCartFacade,
    protected auth: AuthService,
    protected authRedirect: AuthRedirectService,
    protected launchDialog: LaunchDialogService,
    protected cd: ChangeDetectorRef
  ) {
    this.vm$ = combineLatest([
      this.estudio.catalogo$,
      this.estudio.tick$,
      this.code$,
      this.escolha$,
      this.i18n.lang$,
    ]).pipe(map(([c, , code]) => this.toVm(c, code)));
  }

  ngOnInit(): void {
    // configuração pela URL (link "Editar" do carrinho e das versões da vitrine)
    this.subscription.add(
      combineLatest([this.route.queryParamMap, this.estudio.catalogo$, this.code$])
        .pipe(take(1))
        .subscribe(([q, c, code]) => {
          const modelo = (q.get('modelo') as Modelo) || 'MAS';
          const tecido = (q.get('tecido') as Tecido) || 'ALG';
          const qtd = Math.min(c.regras.quantidadeMax, Math.max(1, Number(q.get('qtd')) || 1));
          this.escolha = {
            modelo: ME_MODELOS.includes(modelo) ? modelo : 'MAS',
            tecido,
            cor: (ME_CORES.includes(q.get('cor') as MeCor) ? q.get('cor') : 'PRT') as MeCor,
            tamanho: q.get('tamanho') ?? '',
            qtd,
          };
          const ed = q.get('editar');
          this.editar = ed !== null && ed !== '' && !isNaN(Number(ed)) ? Number(ed) : undefined;
          this.ajustar(c, code);
        })
    );
    // trocar de estampa (painel "Outras estampas"): mantém a configuração e confere a cor
    this.subscription.add(
      combineLatest([this.estudio.catalogo$, this.code$]).subscribe(([c, code]) => {
        if (code !== this.lastEstampa) {
          this.lastEstampa = code;
          this.ajustar(c, code);
        }
      })
    );
  }

  /** Cor bloqueada pela arte, tecido que o modelo não tem e tamanho que deixou de existir */
  protected ajustar(c: MeCatalogo, code?: string): void {
    if (!c.ativo) {
      return;
    }
    const e = code ? c.estampas.find((x) => x.code === code) : undefined;
    const tecidos = this.estudio.tecidos(c, this.escolha.modelo);
    if (tecidos.length && !tecidos.some((t) => t.tecido === this.escolha.tecido)) {
      const novo = tecidos[0].tecido;
      this.avisoTecido = this.i18n.t('me.p.avisoTecido', {
        m: this.i18n.t('me.modelo.' + this.escolha.modelo),
        t: this.i18n.t('me.tecido.' + this.escolha.tecido),
        n: this.i18n.t('me.tecido.' + novo),
      });
      this.escolha.tecido = novo;
    }
    if (e?.coresIndisponiveis[this.escolha.cor] !== undefined) {
      const antes = this.escolha.cor;
      this.escolha.cor = this.estudio.corVitrine(e);
      this.avisoCor = this.i18n.t('me.p.avisoCor', {
        c: this.i18n.t('me.cor.' + antes),
        n: this.i18n.t('me.cor.' + this.escolha.cor),
      });
    }
    const cam = this.estudio.camiseta(c, this.escolha.modelo, this.escolha.tecido);
    if (this.escolha.tamanho && cam && !cam.tamanhos.includes(this.escolha.tamanho)) {
      this.avisoTamanho = this.i18n.t('me.p.avisoTamanho', {
        t: this.escolha.tamanho,
        c: this.estudio.configuracao({ modelo: this.escolha.modelo, tecido: this.escolha.tecido }),
      });
      this.escolha.tamanho = '';
    }
    this.publicar();
  }

  protected publicar(): void {
    this.escolha = { ...this.escolha };
    this.escolha$.next(this.escolha);
    this.cd.markForCheck();
  }

  modelo(c: MeCatalogo, code: Modelo): void {
    this.limparAvisos();
    this.escolha.modelo = code;
    this.ajustar(c, this.lastEstampa);
  }

  tecido(c: MeCatalogo, code: Tecido): void {
    this.limparAvisos();
    this.escolha.tecido = code;
    this.ajustar(c, this.lastEstampa);
  }

  cor(code: MeCor, bloqueada?: string): void {
    if (bloqueada !== undefined) {
      return;
    }
    this.avisoCor = '';
    this.escolha.cor = code;
    this.publicar();
  }

  tamanho(t: string): void {
    this.avisoTamanho = '';
    this.erroTamanho = '';
    this.escolha.tamanho = t;
    this.publicar();
  }

  qtd(d: number, max: number): void {
    this.escolha.qtd = Math.min(max, Math.max(1, this.escolha.qtd + d));
    this.publicar();
  }

  protected limparAvisos(): void {
    this.avisoTamanho = '';
    this.avisoTecido = '';
    this.avisoCor = '';
    this.erro = '';
  }

  abrir(p: 'outras' | 'medidas'): void {
    this.painel = p;
  }

  fechar(): void {
    this.painel = '';
  }

  /** Painel "Outras estampas": troca a estampa e mantém a configuração */
  trocar(e: MeEstampa): void {
    this.painel = '';
    this.router.navigate(['/mengo-estudio/estampa', e.code], {
      queryParams: this.queryParams(),
    });
  }

  queryParams(): Record<string, string | number> {
    const q: Record<string, string | number> = {
      modelo: this.escolha.modelo,
      tecido: this.escolha.tecido,
      cor: this.escolha.cor,
    };
    if (this.escolha.tamanho) {
      q['tamanho'] = this.escolha.tamanho;
    }
    if (this.editar !== undefined) {
      q['editar'] = this.editar;
    }
    return q;
  }

  /** Barra fixa do celular: sem tamanho, leva até o campo */
  irTamanho(): void {
    this.erroTamanho = this.i18n.t('me.p.erroTamanho');
    this.tamanhoRef?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    (this.tamanhoRef?.nativeElement.querySelector('.opcoes.tam button') as HTMLElement | null)?.focus();
    this.cd.markForCheck();
  }

  adicionar(vm: Vm): void {
    if (!vm.e || vm.encerrada || !vm.cam || this.salvando) {
      return;
    }
    if (!this.escolha.tamanho) {
      this.irTamanho();
      return;
    }
    this.erro = '';
    this.auth
      .isUserLoggedIn()
      .pipe(take(1))
      .subscribe((logado) => {
        if (!logado) {
          this.authRedirect.setRedirectUrl(this.router.url);
          this.routing.go({ cxRoute: 'login' });
          return;
        }
        this.salvando = true;
        this.cd.markForCheck();
        const e = vm.e as MeEstampa;
        const cam = vm.cam as MeCamiseta;
        this.activeCart
          .requireLoadedCart()
          .pipe(
            take(1),
            switchMap((cart) =>
              this.estudio.adicionar(cart.code as string, {
                estampa: e.code,
                camiseta: cam.code,
                cor: this.escolha.cor,
                tamanho: this.escolha.tamanho,
                quantidade: this.escolha.qtd,
                substituir: this.editar,
              })
            )
          )
          .subscribe({
            next: () => {
              this.salvando = false;
              this.activeCart.reloadActiveCart();
              if (this.editar !== undefined) {
                this.editar = undefined;
                this.routing.go({ cxRoute: 'cart' });
              } else if (this.addBtn) {
                this.launchDialog
                  .openDialog(LAUNCH_CALLER.ADDED_TO_CART, this.addBtn, undefined, { estudio: true })
                  ?.pipe(take(1))
                  .subscribe();
              }
              this.cd.markForCheck();
            },
            error: (err: Error) => {
              this.salvando = false;
              this.erro = err?.message || this.i18n.t('me.p.erro');
              this.cd.markForCheck();
            },
          });
      });
  }

  protected toVm(c: MeCatalogo, code?: string): Vm {
    const now = this.estudio.now(c);
    const e = code ? c.estampas.find((x) => x.code === code) : undefined;
    const sit = e ? this.estudio.situacao(e, c, now) : undefined;
    const encerrada = sit === 'ENCERRADA';
    const resto = (e?.encerramento ?? Number.MAX_SAFE_INTEGER) - now;
    const linhaNome = c.linhas.find((l) => l.code === e?.linha)?.nome ?? '';
    const drop = c.drops.find((d) => d.code === e?.drop) ?? c.drops[0];
    const dropNome = drop
      ? `${this.i18n.t('me.drop.n', { n: String(drop.numero ?? 1).padStart(2, '0') })} · ${drop.nome ?? ''}`
      : '';
    const cam = this.estudio.camiseta(c, this.escolha.modelo, this.escolha.tecido);
    const preco = cam?.preco ?? 0;
    const total = preco * this.escolha.qtd;
    const config = this.estudio.configuracao({
      modelo: this.escolha.modelo,
      tecido: this.escolha.tecido,
      cor: this.escolha.cor,
    });
    const card = (x: MeEstampa): Card => {
      const s = this.estudio.situacao(x, c, now);
      return { e: x, sit: s, prazo: this.estudio.prazo(x, c, now), urgente: s === 'ULTIMAS_48H' };
    };
    const aVenda = c.estampas.filter(
      (x) => x.code !== e?.code && this.estudio.situacao(x, c, now) !== 'ENCERRADA'
    );
    const porFim = (a: MeEstampa, b: MeEstampa) =>
      (a.encerramento ?? Number.MAX_SAFE_INTEGER) - (b.encerramento ?? Number.MAX_SAFE_INTEGER);
    return {
      c,
      e,
      sit,
      encerrada,
      ate: e ? this.i18n.t('me.p.ate', { d: this.estudio.fim(e.encerramento) }) : '',
      contador: e && !encerrada && resto <= c.regras.ultimosDias * 86_400_000 ? this.estudio.contador(resto) : '',
      saiuEm: e && encerrada ? this.i18n.t('me.p.saiuEm', { d: this.estudio.dia(e.encerramento) }) : '',
      dropNome,
      linhaNome,
      titulo: e ? this.i18n.t('me.c.nome', { n: e.nome }) : this.i18n.t('me.p.semEstampaT'),
      subtitulo: e
        ? `${dropNome} · ${this.i18n.t('me.linhaDe', { l: linhaNome })} — ${this.estudio.configuracao({
            modelo: this.escolha.modelo,
            tecido: this.escolha.tecido,
            cor: this.escolha.cor,
          })}`
        : config,
      modelos: ME_MODELOS.map((m) => ({ code: m, ok: this.estudio.tecidos(c, m).length > 0 })),
      tecidos: this.estudio
        .tecidos(c, this.escolha.modelo)
        .map((t) => ({ code: t.tecido, precoTxt: this.i18n.money(t.preco ?? 0) })),
      cam,
      precoTxt: this.i18n.money(preco),
      parcelas: preco ? this.estudio.parcelas(preco, c) : '',
      cores: ME_CORES.map((x) => ({ code: x, hex: ME_COR_HEX[x], motivo: e?.coresIndisponiveis[x] })),
      tamanhos: cam?.tamanhos ?? [],
      medidas: cam?.medidas ?? [],
      totalTxt: this.i18n.money(total),
      totalParcelas: total ? this.estudio.parcelas(total, c) : '',
      resumo: e ? `${config} · ${this.i18n.t('me.c.nome', { n: e.nome })}` : config,
      max: c.regras.quantidadeMax,
      maisLinha: aVenda.filter((x) => x.linha === e?.linha).sort(porFim).slice(0, 4).map(card),
      deOutras: aVenda.filter((x) => x.linha !== e?.linha).sort(porFim).slice(0, 4).map(card),
      outras: aVenda
        .filter((x) => !this.filtroOutras || x.linha === this.filtroOutras)
        .sort(porFim)
        .map(card),
      filtros: [{ code: '', nome: this.i18n.t('me.todas') }, ...c.linhas.map((l) => ({ code: l.code, nome: l.nome ?? l.code }))],
    };
  }

  filtrarOutras(code: string): void {
    this.filtroOutras = code;
    this.publicar();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
