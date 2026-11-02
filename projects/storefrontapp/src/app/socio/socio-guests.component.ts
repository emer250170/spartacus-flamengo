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
  NewSocioGuest,
  SocioGuest,
  SocioGuestList,
  SocioGuestsService,
  isValidCpf,
  maskCpf,
  maskPhone,
} from './socio-guests.service';

/**
 * Minha conta > Meu sócio-torcedor > Meus convidados (item 3 do plano de evolução).
 *
 * O convidado é contratado e cobrado na compra do plano; aqui o titular dá nome
 * às vagas: lista os convidados, inclui (nome, CPF, nascimento, e-mail e
 * celular opcional) e tira. As regras ficam no servidor (vagas contratadas, CPF
 * válido e sem duplicidade, suspenso não inclui); a tela repete as validações
 * simples para avisar antes de enviar e mostra a recusa do servidor quando houver.
 * Some quando o cadastro está desligado (flamengo.socio.convidados.ativo=false)
 * ou quando o torcedor não é sócio.
 */
@Component({
  selector: 'fla-socio-guests',
  templateUrl: './socio-guests.component.html',
  styleUrls: [
    './socio-account.component.scss',
    './socio-guests.component.scss',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioGuestsComponent implements OnInit, OnDestroy {
  loading = true;
  data?: SocioGuestList;
  /** Falha ao carregar (servidor fora, por exemplo) */
  loadError = false;

  formOpen = false;
  form: NewSocioGuest = this.emptyForm();
  busy = false;
  error = '';
  /** Mensagem de sucesso depois de incluir ou tirar */
  done = '';
  /** Convidado com a confirmação de "tirar" aberta */
  confirming?: string;
  showEnded = false;

  readonly today = new Date().toISOString().slice(0, 10);

  protected subscription = new Subscription();

  constructor(
    protected guestsService: SocioGuestsService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /** Mostra o cartão: cadastro ligado e torcedor sócio */
  get visible(): boolean {
    return !!this.data?.enabled && !!this.data.planCode;
  }

  /** Uma bolinha por vaga contratada */
  get slots(): number[] {
    return Array.from({ length: this.data?.contracted ?? 0 }, (_, i) => i);
  }

  get free(): number {
    return this.data ? Math.max(0, this.data.contracted - this.data.used) : 0;
  }

  /** Por que não pode incluir, no idioma da loja */
  get blockedText(): string {
    const d = this.data;
    if (!d || d.canAdd) {
      return '';
    }
    if (d.suspended) {
      return this.t('sg.block.suspended');
    }
    if (d.contracted <= 0) {
      return d.planMaxGuests > 0
        ? this.t('sg.block.noSlots', { max: d.planMaxGuests })
        : this.t('sg.block.planNoGuests', { plan: d.planName ?? '' });
    }
    if (d.used >= d.contracted) {
      return this.t('sg.block.full', { n: d.contracted });
    }
    return d.reason ?? '';
  }

  load(): void {
    this.subscription.add(
      this.guestsService.list().subscribe({
        next: (data) => this.show(data),
        error: () => {
          this.loading = false;
          this.loadError = true;
          this.cd.markForCheck();
        },
      })
    );
  }

  openForm(): void {
    this.formOpen = true;
    this.error = '';
    this.done = '';
    this.form = this.emptyForm();
  }

  cancelForm(): void {
    this.formOpen = false;
    this.error = '';
  }

  onCpf(value: string): void {
    this.form.cpf = maskCpf(value);
  }

  onPhone(value: string): void {
    this.form.phone = maskPhone(value);
  }

  submit(): void {
    if (this.busy) {
      return;
    }
    this.error = this.validate();
    if (this.error) {
      this.cd.markForCheck();
      return;
    }
    this.busy = true;
    const name = this.form.name.trim().replace(/\s+/g, ' ');
    this.subscription.add(
      this.guestsService
        .add({
          name,
          cpf: this.form.cpf,
          email: this.form.email.trim(),
          phone: this.form.phone?.trim() || undefined,
          birthDate: this.form.birthDate,
        })
        .subscribe({
          next: (data) => {
            this.formOpen = false;
            this.done = this.t('sg.added', { name });
            this.show(data);
          },
          error: (e: Error) => this.fail(e),
        })
    );
  }

  askRemove(guest: SocioGuest): void {
    this.confirming = guest.id;
    this.error = '';
    this.done = '';
  }

  remove(guest: SocioGuest): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.subscription.add(
      this.guestsService.remove(guest.id).subscribe({
        next: (data) => {
          this.confirming = undefined;
          this.done = this.t('sg.removed', { name: guest.name });
          this.show(data);
        },
        error: (e: Error) => {
          this.confirming = undefined;
          this.fail(e);
        },
      })
    );
  }

  // ---------- apoio ----------

  t(key: string, params?: Record<string, unknown>): string {
    return this.i18n.t(key, params);
  }

  brl(value: number): string {
    return this.i18n.money(value);
  }

  /** aaaa-mm-dd ou ISO -> 10/05/1990 (pt) ou May 10, 1990 (en) */
  date(value?: string): string {
    if (!value) {
      return '-';
    }
    const [y, m, d] = value.slice(0, 10).split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString(
      this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US',
      this.i18n.lang === 'pt'
        ? { day: '2-digit', month: '2-digit', year: 'numeric' }
        : { day: 'numeric', month: 'short', year: 'numeric' }
    );
  }

  /** Motivo do encerramento (o servidor grava em português) */
  reason(guest: SocioGuest): string {
    switch (guest.endReason) {
      case 'Retirado pelo titular':
        return this.t('sg.reason.owner');
      case 'Cancelamento do plano':
        return this.t('sg.reason.cancel');
      default:
        return guest.endReason ?? '';
    }
  }

  phone(value?: string): string {
    return value ? maskPhone(value) : '-';
  }

  /** Ingressos do convidado para jogos futuros ("Ingresso: Flamengo x Bahia · 15/11 · Sul · H11") */
  ticketsOf(guest: SocioGuest): string[] {
    return (this.data?.tickets ?? [])
      .filter((t) => t.guestId === guest.id)
      .map((t) =>
        this.t(t.orderCode ? 'sg.ticket' : 'sg.ticketCart', {
          game: t.gameName ?? t.gameCode,
          date: this.date(t.kickoff),
          seat: (t.seatName ?? t.seatCode).replace(`${t.gameName ?? ''} · `, ''),
          order: t.orderCode ?? '',
        })
      );
  }

  trackById(_: number, guest: SocioGuest): string {
    return guest.id;
  }

  protected validate(): string {
    const f = this.form;
    const name = f.name.trim().replace(/\s+/g, ' ');
    if (name.length < 3 || !name.includes(' ')) {
      return this.t('sg.err.name');
    }
    if (!isValidCpf(f.cpf)) {
      return this.t('sg.err.cpf');
    }
    if (!f.birthDate || f.birthDate > this.today) {
      return this.t('sg.err.birth');
    }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim())) {
      return this.t('sg.err.email');
    }
    const phone = (f.phone ?? '').replace(/\D/g, '');
    if (phone && (phone.length < 10 || phone.length > 11)) {
      return this.t('sg.err.phone');
    }
    return '';
  }

  protected show(data: SocioGuestList): void {
    this.data = data;
    this.loading = false;
    this.loadError = false;
    this.busy = false;
    this.cd.markForCheck();
  }

  protected fail(e: Error): void {
    this.busy = false;
    this.error = e?.message || this.t('sg.err.generic');
    this.cd.markForCheck();
  }

  protected emptyForm(): NewSocioGuest {
    return { name: '', cpf: '', email: '', phone: '', birthDate: '' };
  }
}
