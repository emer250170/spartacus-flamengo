import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
} from '@angular/core';
import { AuthService } from '@spartacus/core';
import { Observable } from 'rxjs';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { SocioTerm, SocioTermService } from './socio-term.service';

/**
 * Página do Termo do Nação (/socio-torcedor/termo): título, versão, data de
 * publicação, o que mudou e o texto da versão atual, com o aceite do torcedor.
 * Rota própria (socio.module) com o cabeçalho e o rodapé da página
 * /socio-torcedor do CMS. O texto vem da API (só logado).
 */
@Component({
  selector: 'fla-socio-term-page',
  templateUrl: './socio-term-page.component.html',
  styleUrls: ['./socio-term.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioTermPageComponent {
  readonly term$: Observable<SocioTerm> = this.terms.term$;
  readonly loggedIn$: Observable<boolean> = this.auth.isUserLoggedIn();
  saving = false;
  error = '';

  constructor(
    protected terms: SocioTermService,
    protected auth: AuthService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  paragraphs(term: SocioTerm): string[] {
    return this.terms.paragraphs(term);
  }

  /**
   * Parágrafos em seções (mockup): "Mensalidade: o plano é..." vira o título "Mensalidade" e o
   * texto; parágrafo sem rótulo curto antes dos dois-pontos fica como texto corrido.
   */
  sections(term: SocioTerm): { title?: string; text: string }[] {
    return this.paragraphs(term).map((p) => {
      const m = /^([^:.]{2,40}):\s+(.+)$/s.exec(p.trim());
      return m
        ? {
            title: m[1].trim(),
            text: m[2].charAt(0).toUpperCase() + m[2].slice(1),
          }
        : { text: p };
    });
  }

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
