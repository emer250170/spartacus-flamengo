import { Injectable } from '@angular/core';
import {
  BasePageMetaResolver,
  BreadcrumbMeta,
  ContentPageMetaResolver,
  RoutingService,
} from '@spartacus/core';
import { combineLatest, Observable } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  TICKET_MODE_PARAM,
  TICKET_MODE_VALUE,
} from './matchday-journey.component';

/**
 * Cabeçalho padrão da loja (breadcrumb e título no fundo cinza) na página /matchday
 * (matchdayPage, 04/11). No modo só ingresso (?modo=ingresso) mostra "Início / Ingressos"
 * e "Compre seu ingresso"; no dia de jogo completo, o título do CMS ("Monte o seu dia de
 * jogo"), como antes.
 */
@Injectable({ providedIn: 'root' })
export class MatchdayPageMetaResolver extends ContentPageMetaResolver {
  constructor(
    basePageMetaResolver: BasePageMetaResolver,
    protected routing: RoutingService,
    protected i18n: FlaI18nService
  ) {
    super(basePageMetaResolver);
    this.pageUid = 'matchdayPage';
  }

  protected ticketMode$(): Observable<boolean> {
    return this.routing.getRouterState().pipe(
      map(
        (s) => s?.state?.queryParams?.[TICKET_MODE_PARAM] === TICKET_MODE_VALUE
      ),
      distinctUntilChanged()
    );
  }

  resolveTitle(): Observable<string | undefined> {
    return combineLatest([
      super.resolveTitle(),
      this.ticketMode$(),
      this.i18n.lang$,
    ]).pipe(
      map(([title, ticket]) => (ticket ? this.i18n.t('md.ticketTitle') : title))
    );
  }

  resolveBreadcrumbs(): Observable<BreadcrumbMeta[] | undefined> {
    return combineLatest([
      super.resolveBreadcrumbs(),
      this.ticketMode$(),
      this.i18n.lang$,
    ]).pipe(
      map(([crumbs, ticket]) =>
        ticket
          ? [
              ...(crumbs ?? []),
              // sem link: é a página atual (o routerLink não aceita "?modo=" no texto)
              { label: this.i18n.t('md.crumbTicket') } as BreadcrumbMeta,
            ]
          : crumbs
      )
    );
  }
}
