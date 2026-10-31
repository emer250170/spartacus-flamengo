import { Injectable } from '@angular/core';
import {
  AuthService,
  CmsNavigationComponent,
  CmsService,
  SemanticPathService,
} from '@spartacus/core';
import { NavigationNode, NavigationService } from '@spartacus/storefront';
import { Observable, of } from 'rxjs';
import {
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import { SocioAccountService } from './socio-account.service';
import { SocioService } from './socio.service';

/** Link do menu Minha conta para a área do sócio (CMS: SocioTorcedorAccountLink) */
export const SOCIO_ACCOUNT_URL = '/my-account/socio-torcedor';

/**
 * Menu Minha conta: "Meu sócio-torcedor" só para quem é ou já foi sócio.
 * - sócio (grupo do plano) → mostra;
 * - ex-sócio (tem um pedido de cancelamento, aberto ou já atendido) → mostra,
 *   para ele ver o plano cancelado e a situação inativa;
 * - nunca foi sócio (inclusive com o plano só no carrinho) → esconde.
 * Os outros menus (categorias, rodapé) passam direto, sem consulta extra.
 */
@Injectable({ providedIn: 'root' })
export class SocioNavigationService extends NavigationService {
  protected showSocio$?: Observable<boolean>;

  constructor(
    cmsService: CmsService,
    semanticPathService: SemanticPathService,
    protected auth: AuthService,
    protected socio: SocioService,
    protected account: SocioAccountService
  ) {
    super(cmsService, semanticPathService);
  }

  getNavigationNode(
    data$: Observable<CmsNavigationComponent>
  ): Observable<NavigationNode> {
    return super
      .getNavigationNode(data$)
      .pipe(
        switchMap((node) =>
          hasSocioLink(node)
            ? this.showSocio().pipe(
                map((show) => (show ? node : withoutSocioLink(node)))
              )
            : of(node)
        )
      );
  }

  /** É ou já foi sócio? (recalcula quando o login ou o plano mudam) */
  protected showSocio(): Observable<boolean> {
    if (!this.showSocio$) {
      this.showSocio$ = this.auth.isUserLoggedIn().pipe(
        distinctUntilChanged(),
        switchMap((loggedIn) =>
          loggedIn
            ? this.socio.getMembership().pipe(
                map((m) => !!m.member),
                distinctUntilChanged(),
                switchMap((member) =>
                  member
                    ? of(true)
                    : this.account.cancellation().pipe(map((c) => !!c))
                )
              )
            : of(false)
        ),
        distinctUntilChanged(),
        shareReplay({ bufferSize: 1, refCount: true })
      );
    }
    return this.showSocio$;
  }
}

function isSocioLink(node: NavigationNode): boolean {
  const url = Array.isArray(node.url) ? node.url.join('/') : (node.url ?? '');
  return url.replace(/\/+$/, '').endsWith(SOCIO_ACCOUNT_URL);
}

function hasSocioLink(node: NavigationNode | undefined): boolean {
  return (
    !!node &&
    (isSocioLink(node) || (node.children ?? []).some((c) => hasSocioLink(c)))
  );
}

function withoutSocioLink(node: NavigationNode): NavigationNode {
  return {
    ...node,
    children: node.children
      ?.filter((c) => !isSocioLink(c))
      .map((c) => withoutSocioLink(c)),
  };
}
