import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { SocioPaymentService } from './socio-payment.service';
import { SocioService } from './socio.service';

/** O que a página mostra: a carteirinha, o aviso de pagamento pendente ou o convite */
type CardPageState = 'card' | 'awaiting' | 'none';

/**
 * Página da carteirinha digital (/socio-torcedor/carteirinha, item 8): a
 * carteirinha grande, para mostrar no celular na entrada do estádio e nos
 * parceiros. Rota própria (socio.module, só logado) com o cabeçalho e o rodapé
 * da página /socio-torcedor do CMS. Quem não é sócio vê o convite para os planos;
 * Pix/boleto aguardando, o aviso de que a carteirinha sai com a confirmação.
 */
@Component({
  selector: 'fla-socio-card-page',
  templateUrl: './socio-card-page.component.html',
  styleUrls: ['./socio-account.component.scss', './socio-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocioCardPageComponent {
  readonly state$: Observable<CardPageState> = combineLatest([
    this.socio.getMembership(),
    this.payment.pending(),
  ]).pipe(
    map(([m, p]): CardPageState =>
      m.member ? 'card' : p?.awaiting ? 'awaiting' : 'none'
    )
  );

  constructor(
    protected socio: SocioService,
    protected payment: SocioPaymentService
  ) {}
}
