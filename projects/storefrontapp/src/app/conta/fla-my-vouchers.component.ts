import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { map, shareReplay, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  SocioPointsService,
  SocioRedemption,
} from '../socio/socio-points.service';
import {
  VOUCHER_TONE,
  VoucherStatus,
  isPurchase,
  voucherStatus,
} from '../socio/fla-voucher';

type Filtro = 'all' | VoucherStatus;

interface VoucherView {
  r: SocioRedemption;
  status: VoucherStatus;
  tone: string;
  image?: string;
  purchase: boolean;
}

interface VouchersView {
  all: VoucherView[];
  shown: VoucherView[];
  filters: { key: Filtro; n: number }[];
  filter: Filtro;
}

/**
 * Meus vouchers (Minha conta, 08/11): os vouchers de experiência do torcedor, sócio ou não —
 * comprados na loja (com o pedido) ou resgatados com pontos — com o código, a situação (Válido,
 * Utilizado, Cancelado) e os detalhes. Filtro por situação. Dados de GET /users/current/sociopoints
 * (redemptions; com os pontos desligados no servidor a lista vem vazia). CMS: página
 * /my-account/vouchers com o CMSFlexComponent FlaMyVouchersComponent
 * (docs/flamengo-matchday/scripts/meus-vouchers-cms.groovy).
 */
@Component({
  selector: 'fla-my-vouchers',
  templateUrl: './fla-my-vouchers.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMyVouchersComponent {
  protected readonly filter$ = new BehaviorSubject<Filtro>('all');
  copied?: string;

  readonly view$: Observable<VouchersView>;

  constructor(
    protected points: SocioPointsService,
    protected i18n: FlaI18nService
  ) {
    // nomes das experiências na língua da tela: recarrega na troca de idioma
    const list$ = this.i18n.lang$.pipe(
      switchMap(() => this.points.load()),
      map((p) => {
        const images = new Map(
          p.experiences.map((e) => [e.code, e.imageUrl] as const)
        );
        return p.redemptions.map((r) => {
          const status = voucherStatus(r);
          return {
            r,
            status,
            tone: VOUCHER_TONE[status],
            image: r.experienceCode ? images.get(r.experienceCode) : undefined,
            purchase: isPurchase(r),
          };
        });
      }),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    this.view$ = combineLatest([list$, this.filter$]).pipe(
      map(([all, filter]) => {
        const count = (k: VoucherStatus) =>
          all.filter((v) => v.status === k).length;
        const filters: { key: Filtro; n: number }[] = [
          { key: 'all', n: all.length },
          { key: 'valid', n: count('valid') },
          { key: 'used', n: count('used') },
        ];
        if (count('cancelled')) {
          filters.push({ key: 'cancelled', n: count('cancelled') });
        }
        return {
          all,
          filter,
          filters,
          shown:
            filter === 'all' ? all : all.filter((v) => v.status === filter),
        };
      })
    );
  }

  choose(f: Filtro): void {
    this.filter$.next(f);
  }

  copy(code: string): void {
    try {
      navigator.clipboard?.writeText(code);
      this.copied = code;
    } catch {
      this.copied = undefined;
    }
  }

  day(date?: Date): string {
    return date
      ? date.toLocaleDateString(this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          timeZone: 'America/Sao_Paulo',
        })
      : '';
  }

  brl(value?: number): string {
    return this.i18n.money(value ?? 0);
  }

  num(value: number): string {
    return value.toLocaleString(this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US');
  }

  track(_i: number, v: VoucherView): string {
    return v.r.voucher;
  }
}
