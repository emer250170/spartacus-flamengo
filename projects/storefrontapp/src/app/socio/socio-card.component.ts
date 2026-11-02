import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { User, WindowRef } from '@spartacus/core';
import { UserAccountFacade } from '@spartacus/user/account/root';
import { Subscription, combineLatest, of } from 'rxjs';
import { catchError, startWith } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioContract, SocioContractService } from './socio-contract.service';
import { Membership } from './socio.model';
import { SocioService } from './socio.service';
import { qrPattern } from './socio-qr';

/** O QR da carteirinha muda a cada 30 segundos (não adianta tirar print) */
const QR_SECONDS = 30;

/**
 * Carteirinha digital do sócio (item 8 do plano de evolução): nome, plano,
 * número de sócio, validade e QR Code fictício que muda a cada 30 segundos.
 * Em Meu sócio-torcedor (compacta, com o botão para a tela cheia) e na página
 * /socio-torcedor/carteirinha (full = true, para mostrar no celular).
 * Sócio suspenso: cinza, com "Benefícios suspensos". Sem plano comprado (ou com
 * Pix/boleto aguardando pagamento): não aparece.
 * O número é derivado do e-mail (demonstração); o de verdade viria do cadastro
 * do sócio.
 */
@Component({
  selector: 'fla-socio-card',
  templateUrl: './socio-card.component.html',
  styleUrls: ['./socio-account.component.scss', './socio-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioCardComponent implements OnInit, OnDestroy {
  /** Página da carteirinha (maior, sem o texto ao lado) */
  @Input() full = false;

  loading = true;
  membership?: Membership;
  contract?: SocioContract;
  user?: User;
  qr: boolean[][] = [];
  /** Segundos até o QR mudar */
  secondsLeft = QR_SECONDS;

  protected slot = -1;
  protected timer?: ReturnType<typeof setInterval>;
  protected subscription = new Subscription();

  constructor(
    protected socio: SocioService,
    protected contractService: SocioContractService,
    protected userAccount: UserAccountFacade,
    protected i18n: FlaI18nService,
    protected winRef: WindowRef,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([
        this.socio.getMembership(),
        this.contractService.load(),
        this.userAccount.get().pipe(
          startWith(undefined),
          catchError(() => of(undefined))
        ),
      ]).subscribe(([membership, contract, user]) => {
        this.membership = membership;
        this.contract = contract;
        this.user = user ?? this.user;
        this.loading = false;
        this.refreshQr(true);
        this.cd.markForCheck();
      })
    );
    if (this.winRef.isBrowser()) {
      this.timer = setInterval(() => {
        this.refreshQr(false);
        this.cd.markForCheck();
      }, 1000);
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  get visible(): boolean {
    return !!this.membership?.member;
  }

  get suspended(): boolean {
    return !!this.membership?.suspended;
  }

  get name(): string {
    const u = this.user;
    return (
      u?.name ||
      [u?.firstName, u?.lastName].filter(Boolean).join(' ') ||
      u?.uid ||
      ''
    );
  }

  /** Número de sócio de demonstração: 1895 (fundação do clube) + 8 dígitos do e-mail */
  get number(): string {
    const digits = String(hash(this.user?.uid ?? this.name) % 100000000).padStart(8, '0');
    return `1895 ${digits.slice(0, 4)} ${digits.slice(4)}`;
  }

  /** Fim da vigência (contrato do item 6); sem contrato: undefined */
  get validUntil(): Date | undefined {
    return this.contract?.enabled && this.contract.member ? this.contract.end : undefined;
  }

  month(date: Date): string {
    return date.toLocaleDateString(this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US', {
      month: '2-digit',
      year: 'numeric',
      timeZone: 'America/Sao_Paulo',
    });
  }

  /** Recalcula o QR quando muda a janela de 30 s (ou à força, com dados novos) */
  protected refreshQr(force: boolean): void {
    const now = Date.now();
    const slot = Math.floor(now / (QR_SECONDS * 1000));
    this.secondsLeft = QR_SECONDS - Math.floor((now / 1000) % QR_SECONDS);
    if (!force && slot === this.slot) {
      return;
    }
    this.slot = slot;
    const plan = this.membership?.member?.code ?? '';
    this.qr = qrPattern(`${this.user?.uid ?? ''}|${plan}|${slot}`);
  }
}

function hash(text: string): number {
  let h = 7;
  for (const ch of text.toLowerCase()) {
    h = (h * 131 + ch.charCodeAt(0)) % 2147483647;
  }
  return h;
}
