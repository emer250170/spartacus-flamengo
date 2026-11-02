import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioTerm, SocioTermService } from './socio-term.service';

/**
 * Meu sócio-torcedor: aviso de versão nova do Termo do Nação com "Li e aceito"
 * (o aceite não bloqueia nada, fica registrado para o clube) e, depois de
 * aceito, a linha "Termo v… aceito em …". Some com o Termo desligado, sem versão
 * publicada ou para quem não é sócio.
 */
@Component({
  selector: 'fla-socio-term-notice',
  templateUrl: './socio-term-notice.component.html',
  styleUrls: ['./socio-term.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioTermNoticeComponent {
  readonly term$: Observable<SocioTerm> = this.terms.term$;
  saving = false;
  error = '';

  constructor(
    protected terms: SocioTermService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  day(iso?: string): string {
    return SocioTermService.day(iso);
  }

  accept(): void {
    this.saving = true;
    this.error = '';
    this.terms.accept().subscribe({
      next: () => {
        this.saving = false;
        this.cd.markForCheck();
      },
      error: (e: Error) => {
        this.saving = false;
        this.error = e.message || this.i18n.t('st.error');
        this.cd.markForCheck();
      },
    });
  }
}
