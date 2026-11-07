import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { AuthService } from '@spartacus/core';
import { UserAccountFacade } from '@spartacus/user/account/root';
import { combineLatest, Observable, of } from 'rxjs';
import { catchError, map, switchMap, take } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { EstudioService, MeEstampa } from './estudio.service';

/**
 * Acervo (/mengo-estudio/acervo; mockup E5–E6): estampas que saíram de linha, com a data, e o "Avise-me se
 * voltar" (e-mail validado, consentimento específico, confirmação; gravado no Commerce e visto no Backoffice).
 */
@Component({
  selector: 'fla-estudio-acervo',
  templateUrl: './estudio-acervo.component.html',
  styleUrls: ['./estudio.scss', './estudio-acervo.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioAcervoComponent {
  @ViewChild('dlg') dlg?: ElementRef<HTMLElement>;
  alvo: MeEstampa | null = null;
  email = '';
  consent = false;
  tentou = false;
  enviando = false;
  feito = false;
  erro = '';
  readonly inscritos = new Set<string>();
  protected abridor?: HTMLElement | null;

  readonly vm$: Observable<any> = combineLatest([this.estudio.catalogo$, this.i18n.lang$]).pipe(
    map(([c]) => {
      const now = this.estudio.now(c);
      return {
        ativo: c.ativo,
        c,
        lista: c.estampas
          .filter((e) => this.estudio.situacao(e, c, now) === 'ENCERRADA')
          .sort((a, b) => (b.encerramento ?? 0) - (a.encerramento ?? 0))
          .map((e) => ({ e, saiu: this.estudio.dia(e.encerramento) })),
      };
    })
  );

  constructor(
    protected estudio: EstudioService,
    protected i18n: FlaI18nService,
    protected auth: AuthService,
    protected userAccount: UserAccountFacade,
    protected cd: ChangeDetectorRef
  ) {}

  get emailOk(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(this.email.trim());
  }

  abrir(e: MeEstampa, ev: Event): void {
    this.abridor = ev.currentTarget as HTMLElement;
    this.alvo = e;
    this.tentou = false;
    this.feito = false;
    this.erro = '';
    this.consent = false;
    // logado: o e-mail da conta já vem preenchido
    this.auth
      .isUserLoggedIn()
      .pipe(
        take(1),
        switchMap((logged) => (logged ? this.userAccount.get().pipe(take(1)) : of(undefined))),
        catchError(() => of(undefined))
      )
      .subscribe((u: any) => {
        if (u?.uid && !this.email) {
          this.email = u.uid;
        }
        this.cd.markForCheck();
        setTimeout(() => this.dlg?.nativeElement.querySelector<HTMLElement>('input')?.focus());
      });
  }

  fechar(): void {
    this.alvo = null;
    this.cd.markForCheck();
    this.abridor?.focus();
  }

  enviar(ev: Event): void {
    ev.preventDefault();
    this.tentou = true;
    this.erro = '';
    if (!this.alvo || !this.emailOk || !this.consent) {
      return;
    }
    this.enviando = true;
    this.estudio.avisar(this.alvo.code, this.email.trim(), true).subscribe({
      next: () => {
        this.enviando = false;
        this.feito = true;
        this.inscritos.add(this.alvo?.code ?? '');
        this.cd.markForCheck();
      },
      error: (e: Error) => {
        this.enviando = false;
        this.erro = e.message || this.i18n.t('me.aviso.erro');
        this.cd.markForCheck();
      },
    });
  }

  @HostListener('document:keydown.escape')
  esc(): void {
    if (this.alvo) {
      this.fechar();
    }
  }
}
