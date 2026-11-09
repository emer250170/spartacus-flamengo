import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { map, shareReplay, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { FLA_DEFAULT_MATCH_IMAGE } from '../vitrine/fla-media-url.service';
import { qrMatrix, qrSvgPath } from './fla-qr';
import {
  FlaTicketsService,
  MyTicket,
  TicketGame,
  TicketStatus,
} from './fla-tickets.service';

type Filtro = 'all' | TicketStatus;

/** Cor do selo (fla-badge): verde válido, âmbar aguardando, azul utilizado, cinza cancelado */
const TONE: Record<TicketStatus, string> = {
  VALIDO: 'green',
  AGUARDANDO_PAGAMENTO: 'amber',
  UTILIZADO: 'blue',
  CANCELADO: 'grey',
};

interface GameGroup {
  key: string;
  game?: TicketGame;
  image: string;
  upcoming: boolean;
  tickets: MyTicket[];
  /** Ingressos do jogo em todas as situações (o filtro não muda o resumo) */
  total: number;
}

interface TicketsView {
  enabled: boolean;
  count: number;
  filter: Filtro;
  filters: { key: Filtro; n: number }[];
  upcoming: GameGroup[];
  past: GameGroup[];
}

/**
 * Meus ingressos (Minha conta, 09/11): os ingressos que o torcedor comprou, agrupados por jogo
 * (próximos primeiro, depois os que já aconteceram), com setor e poltrona, quem vai usar (titular,
 * convidado do sócio ou ingresso adicional, CPF mascarado), situação, valor, pedido, histórico e um
 * QR code ilustrativo com o código do ingresso. Só consulta. Dados de GET
 * /users/current/matchdaytickets. CMS: página /my-account/ingressos com o CMSFlexComponent
 * FlaMyTicketsComponent (docs/flamengo-matchday/scripts/meus-ingressos-cms.groovy).
 */
@Component({
  selector: 'fla-my-tickets',
  templateUrl: './fla-my-tickets.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaMyTicketsComponent {
  protected readonly filter$ = new BehaviorSubject<Filtro>('all');
  /** Ingresso com o QR aberto */
  qrOpen?: string;
  protected readonly qrCache = new Map<
    string,
    { d: string; size: number } | null
  >();

  readonly view$: Observable<TicketsView>;

  constructor(
    protected tickets: FlaTicketsService,
    protected i18n: FlaI18nService
  ) {
    const data$ = this.i18n.lang$.pipe(
      switchMap(() => this.tickets.load()),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    this.view$ = combineLatest([data$, this.filter$]).pipe(
      map(([d, filter]) => {
        const all = d.tickets;
        const count = (k: TicketStatus) =>
          all.filter((t) => t.status === k).length;
        const filters: { key: Filtro; n: number }[] = [
          { key: 'all', n: all.length },
          { key: 'VALIDO', n: count('VALIDO') },
        ];
        (['AGUARDANDO_PAGAMENTO', 'UTILIZADO', 'CANCELADO'] as const).forEach(
          (k) => {
            if (count(k)) {
              filters.push({ key: k, n: count(k) });
            }
          }
        );
        const groups = new Map<string, GameGroup>();
        all.forEach((t) => {
          const key = t.game?.code ?? '-';
          let g = groups.get(key);
          if (!g) {
            g = {
              key,
              game: t.game,
              image: t.game?.imageUrl ?? FLA_DEFAULT_MATCH_IMAGE,
              // jogo em andamento (até 3 h depois do início) ainda conta como próximo
              upcoming:
                !t.game?.kickoff ||
                t.game.kickoff.getTime() + 3 * 3600_000 > d.now.getTime(),
              tickets: [],
              total: 0,
            };
            groups.set(key, g);
          }
          g.total++;
          if (filter === 'all' || t.status === filter) {
            g.tickets.push(t);
          }
        });
        const list = [...groups.values()].filter((g) => g.tickets.length);
        const time = (g: GameGroup) => g.game?.kickoff?.getTime() ?? 0;
        return {
          enabled: d.enabled,
          count: all.length,
          filter,
          filters,
          upcoming: list
            .filter((g) => g.upcoming)
            .sort((a, b) => time(a) - time(b)),
          past: list
            .filter((g) => !g.upcoming)
            .sort((a, b) => time(b) - time(a)),
        };
      })
    );
  }

  choose(f: Filtro): void {
    this.filter$.next(f);
  }

  toggleQr(t: MyTicket): void {
    this.qrOpen = this.qrOpen === t.code ? undefined : t.code;
  }

  /** Texto do QR: só o código do ingresso, o jogo e a poltrona (sem dado pessoal) */
  qr(t: MyTicket): { d: string; size: number } | null {
    if (!this.qrCache.has(t.code)) {
      const text = ['FLA-INGRESSO', t.code, t.game?.code, t.seatLabel]
        .filter(Boolean)
        .join(' | ');
      const m = qrMatrix(text);
      this.qrCache.set(t.code, m ? qrSvgPath(m) : null);
    }
    return this.qrCache.get(t.code) ?? null;
  }

  tone(t: MyTicket): string {
    return TONE[t.status] ?? 'blue';
  }

  /** "Flamengo x Bahia" */
  title(g?: TicketGame): string {
    return g?.opponent ? `Flamengo x ${g.opponent}` : (g?.code ?? '');
  }

  /** "dom., 15/11/2026 · 16:00" */
  kickoff(date?: Date): string {
    if (!date) {
      return '';
    }
    const locale = this.locale();
    const day = date.toLocaleDateString(locale, {
      weekday: 'short',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'America/Sao_Paulo',
    });
    const hour = date.toLocaleTimeString(locale, {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'America/Sao_Paulo',
    });
    return `${day} · ${hour}`;
  }

  day(date?: Date): string {
    return date
      ? date.toLocaleDateString(this.locale(), {
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

  /** Setor com a primeira letra maiúscula ("Norte") */
  sector(t: MyTicket): string {
    const s = (t.sector ?? '').toLowerCase();
    return s ? s[0].toUpperCase() + s.slice(1) : '';
  }

  trackGroup(_i: number, g: GameGroup): string {
    return g.key;
  }

  track(_i: number, t: MyTicket): string {
    return t.code;
  }

  protected locale(): string {
    return this.i18n.lang === 'pt' ? 'pt-BR' : 'en-US';
  }
}
