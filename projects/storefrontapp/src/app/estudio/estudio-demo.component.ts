import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
} from '@angular/core';
import { BehaviorSubject, Observable, combineLatest, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { FlaI18nService } from '../i18n/fla-i18n.service';
import { EstudioService, MeDemo, meTrack } from './estudio.service';

interface DemoVm {
  /** null = carregando; false = sem permissão */
  estado: MeDemo | null | false;
  erro?: string;
}

/**
 * Painel da demonstração do Mengo Estúdio (08/11, PR 5; mockup E25): /mengo-estudio/demo, fora dos menus e só
 * para os usuários de teste (o servidor confere). Relógio, prazo de uma estampa, próximo pagamento, resposta da
 * estamparia ao cancelamento, pedidos de teste (avançar como a estamparia), avisos e "Restaurar".
 */
@Component({
  selector: 'fla-estudio-demo',
  templateUrl: './estudio-demo.component.html',
  styleUrls: ['./estudio.scss', './estudio-demo.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstudioDemoComponent {
  readonly tb = meTrack;
  estampa = '';
  mensagem = '';
  erro = '';
  ocupado = false;

  protected readonly recarregar$ = new BehaviorSubject<MeDemo | null>(null);

  readonly vm$: Observable<DemoVm> = combineLatest([
    this.recarregar$,
    this.i18n.lang$,
  ]).pipe(
    switchMap(([novo]) =>
      novo
        ? of({ estado: novo } as DemoVm)
        : this.estudio.demo().pipe(
            map((estado) => ({ estado }) as DemoVm),
            catchError((e: Error & { status?: number }) =>
              of({
                estado: false,
                erro:
                  e.status === 403 || e.status === 401
                    ? this.i18n.t('me.demo.proibido')
                    : e.message || this.i18n.t('me.demo.erro'),
              } as DemoVm)
            )
          )
    ),
    map((vm) => {
      if (vm.estado && !this.estampa) {
        this.estampa = vm.estado.estampas[0]?.code ?? '';
      }
      return vm;
    })
  );

  constructor(
    protected estudio: EstudioService,
    protected i18n: FlaI18nService,
    protected cd: ChangeDetectorRef
  ) {}

  acao(body: Record<string, unknown>): void {
    if (this.ocupado) {
      return;
    }
    this.ocupado = true;
    this.erro = '';
    this.mensagem = '';
    this.cd.markForCheck();
    this.estudio.demoAcao(body).subscribe({
      next: (r) => {
        this.ocupado = false;
        this.mensagem = r.mensagem;
        this.recarregar$.next(r.estado);
        this.cd.markForCheck();
      },
      error: (e: Error) => {
        this.ocupado = false;
        this.erro = e.message || this.i18n.t('me.demo.erro');
        this.cd.markForCheck();
      },
    });
  }

  /** "08/11, 10h52" (Brasília) */
  hora(ms: number): string {
    const o: Record<string, string> = {};
    new Intl.DateTimeFormat('pt-BR', {
      timeZone: 'America/Sao_Paulo',
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(ms))
      .forEach((x) => (o[x.type] = x.value));
    return `${o['day']}/${o['month']}, ${o['hour']}h${o['minute']}`;
  }

  /** Estampa escolhida no "Prazo de uma estampa" */
  escolhida(d: MeDemo): MeDemo['estampas'][number] | undefined {
    return d.estampas.find((e) => e.code === this.estampa);
  }

  horas(min: number): number {
    return Math.round(min / 60);
  }
}
