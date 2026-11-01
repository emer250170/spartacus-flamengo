import { Injectable, OnDestroy } from '@angular/core';
import { ActiveCartFacade, Cart } from '@spartacus/cart/base/root';
import {
  AuthService,
  GlobalMessageService,
  GlobalMessageType,
  WindowRef,
} from '@spartacus/core';
import { Subject, Subscription, combineLatest, interval } from 'rxjs';
import { filter, take, withLatestFrom } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { MatchdayDataService } from './matchday-data.service';

/** De quanto em quanto tempo o carrinho é relido enquanto há poltrona reservada */
const POLL_MS = 3_000;

/**
 * Vigia a poltrona reservada em qualquer página da loja.
 *
 * Enquanto há reserva (guardada pela jornada), relê o carrinho a cada 3 s.
 * Se a poltrona some do carrinho sem a jornada ter pedido (o administrador
 * apagou a reserva no Backoffice, ou ela saiu junto com o plano de sócio),
 * avisa o torcedor para reservar de novo: dentro da jornada pelo `lost$`, que
 * ela mostra na etapa Setor e poltrona; fora dela, por mensagem no topo.
 */
@Injectable({ providedIn: 'root' })
export class MatchdayHoldWatcherService implements OnDestroy {
  /** Código da poltrona que saiu do carrinho por fora */
  readonly lost$ = new Subject<string>();
  /** Jornadas abertas: com uma aberta, o aviso aparece nela, não no topo */
  journeyOpen = 0;
  /** A jornada está mexendo no carrinho (reserva ou fechamento): não reler */
  paused = false;

  private started = false;
  private lastCart?: { code: string; seats: string[] };
  private subscription = new Subscription();

  constructor(
    protected activeCart: ActiveCartFacade,
    protected auth: AuthService,
    protected data: MatchdayDataService,
    protected globalMessages: GlobalMessageService,
    protected i18n: FlaI18nService,
    protected winRef: WindowRef
  ) {}

  start(): void {
    if (this.started || !this.winRef.isBrowser()) {
      return;
    }
    this.started = true;
    this.subscription.add(
      interval(POLL_MS)
        .pipe(withLatestFrom(this.auth.isUserLoggedIn()))
        .subscribe(([, loggedIn]) => this.poll(loggedIn))
    );
    this.subscription.add(
      combineLatest([this.activeCart.getActive(), this.activeCart.isStable()])
        .pipe(filter(([cart, stable]) => stable && !!cart?.code))
        .subscribe(([cart]) => this.check(cart))
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /** "Norte A08" a partir de JOGO-20261101-FLU-NORTE-A08 */
  seatLabel(code: string): string {
    const parts = code.split('-');
    const seat = parts.pop() ?? code;
    const sector = parts.length > 3 ? (parts.pop() ?? '') : '';
    const name = sector
      ? sector.charAt(0) + sector.slice(1).toLowerCase() + ' '
      : '';
    return name + seat;
  }

  protected poll(loggedIn: boolean): void {
    const hold = this.data.readHold();
    if (
      !loggedIn ||
      !hold ||
      this.paused ||
      this.winRef.document?.hidden ||
      (hold.expiresAt !== undefined && hold.expiresAt < Date.now())
    ) {
      return;
    }
    // só relê com o carrinho parado (nada sendo incluído ou tirado)
    this.activeCart
      .isStable()
      .pipe(take(1))
      .subscribe((stable) => {
        if (stable && !this.paused) {
          this.activeCart.reloadActiveCart();
        }
      });
  }

  protected check(cart: Cart): void {
    const seats = (cart.entries ?? [])
      .map((e) => e.product?.code ?? '')
      .filter((code) => !!this.data.matchCodeOfSeat(code));
    const previous = this.lastCart;
    this.lastCart = { code: cart.code as string, seats };
    const hold = this.data.readHold();
    if (!previous || !hold || this.paused) {
      return;
    }
    if (previous.code !== cart.code) {
      // outro carrinho (o pedido foi fechado): a reserva antiga não vale mais
      if (!seats.includes(hold.code)) {
        this.data.clearHold();
      }
      return;
    }
    if (previous.seats.includes(hold.code) && !seats.includes(hold.code)) {
      this.data.clearHold();
      this.lost$.next(hold.code);
      if (!this.journeyOpen) {
        this.globalMessages.add(
          this.i18n.t('md.holdLostGlobal', { seat: this.seatLabel(hold.code) }),
          GlobalMessageType.MSG_TYPE_ERROR
        );
      }
    }
  }
}
