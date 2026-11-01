import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { Membership } from './socio.model';
import { SocioService } from './socio.service';

/**
 * Selo de sócio-torcedor no header, à esquerda do "Olá, Fulano".
 *
 * Aparece só para quem já comprou um plano (plano só no carrinho não conta).
 * Mostra o nome do plano; ao passar o mouse, os benefícios (onda e descontos);
 * leva à página /socio-torcedor. Benefícios suspensos (inadimplência): selo em
 * âmbar com "Benefícios suspensos", levando à área do sócio para regularizar.
 * Entra no header pelo outlet do slot SiteLogin.
 */
@Component({
  selector: 'fla-socio-badge',
  templateUrl: './socio-badge.component.html',
  styleUrls: ['./socio-badge.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioBadgeComponent {
  readonly membership$: Observable<Membership | null> = this.socio
    .getMembership()
    .pipe(map((membership) => (membership.member ? membership : null)));

  constructor(
    protected socio: SocioService,
    protected i18n: FlaI18nService
  ) {}

  /** "Onda 1 · 75% no ingresso · 10% na loja" (suspenso: como regularizar) */
  details(m: Membership): string {
    const plan = m.member;
    if (!plan) {
      return '';
    }
    if (m.suspended) {
      return this.i18n.t('sb.suspendedHint');
    }
    const parts = [
      plan.wave > 0
        ? this.i18n.t('wave.n', { n: plan.wave })
        : this.i18n.t('wave.general'),
    ];
    if (plan.discount) {
      parts.push(this.i18n.t('sb.ticket', { d: plan.discount }));
    }
    if (plan.storeDiscount) {
      parts.push(this.i18n.t('sb.store', { d: plan.storeDiscount }));
    }
    return parts.join(' · ');
  }
}
