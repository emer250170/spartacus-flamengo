import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import {
  SocioExperience,
  SocioPoints,
  SocioPointsService,
} from './socio-points.service';

/**
 * Minha conta > Meu sócio-torcedor > Pontos e experiências (item 8 do plano de
 * evolução). Saldo (ingressos, compras e bônus de adesão desde a adesão), a
 * vitrine de experiências cadastrada no Backoffice e os vouchers resgatados. O
 * resgate pede confirmação na própria linha; as regras (sócio em dia, plano
 * mínimo, vagas e saldo) ficam no SAP Commerce, que devolve o motivo da recusa.
 * Some com a chave desligada (flamengo.socio.pontos.ativo=false) ou para quem
 * nunca aderiu.
 */
@Component({
  selector: 'fla-socio-points',
  templateUrl: './socio-points.component.html',
  styleUrls: [
    './socio-account.component.scss',
    './socio-points.component.scss',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioPointsComponent implements OnInit, OnDestroy {
  loading = true;
  data?: SocioPoints;
  /** Experiência com a confirmação aberta */
  confirming?: string;
  busy = false;
  error = '';
  /** Voucher do resgate que acabou de ser feito */
  done?: { name: string; voucher: string };
  showEarnings = false;

  protected subscription = new Subscription();

  constructor(
    protected points: SocioPointsService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.points.load().subscribe((data) => {
        this.data = data;
        this.loading = false;
        this.cd.markForCheck();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  get visible(): boolean {
    return !!this.data?.enabled && !!this.data.member;
  }

  /** Vouchers válidos primeiro (a lista já vem do mais novo para o mais antigo) */
  get redemptions() {
    const list = this.data?.redemptions ?? [];
    return [...list.filter((r) => !r.cancelledAt), ...list.filter((r) => r.cancelledAt)];
  }

  /** Quanto falta para a experiência (0 se já dá) */
  missing(x: SocioExperience): number {
    return Math.max(0, x.points - (this.data?.balance ?? 0));
  }

  /** Barra de progresso até a experiência (0 a 100) */
  progress(x: SocioExperience): number {
    if (!x.points) {
      return 100;
    }
    return Math.min(100, Math.round(((this.data?.balance ?? 0) / x.points) * 100));
  }

  ask(x: SocioExperience): void {
    this.confirming = x.code;
    this.error = '';
    this.done = undefined;
  }

  cancel(): void {
    this.confirming = undefined;
    this.error = '';
  }

  redeem(x: SocioExperience): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.subscription.add(
      this.points.redeem(x.code).subscribe({
        next: (r) => {
          this.busy = false;
          this.confirming = undefined;
          this.done = { name: x.name, voucher: r.voucher };
          if (r.status.enabled) {
            this.data = r.status;
          }
          this.cd.markForCheck();
        },
        error: (e: Error) => {
          this.busy = false;
          this.error = e.message || this.i18n.t('sp.err.generic');
          this.cd.markForCheck();
        },
      })
    );
  }

  num(value: number): string {
    return value.toLocaleString(this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US');
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  day(date?: Date): string {
    if (!date) {
      return '';
    }
    return date.toLocaleDateString(this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'America/Sao_Paulo',
    });
  }
}
