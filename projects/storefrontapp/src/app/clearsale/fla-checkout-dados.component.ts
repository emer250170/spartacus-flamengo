import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { isValidTelefone, maskTelefone } from '../cadastro/telefone';
import { isValidCpf, maskCpf } from '../socio/socio-guests.service';
import { FlaCustomerData, FlaCustomerDataService } from './clearsale.service';

/**
 * CPF e telefone das contas antigas (07/11), no bloco dos aceites antes do "Finalizar": aparece só quando o
 * cadastro não tem um dos dois; gravado, some e o "Finalizar" libera.
 */
@Component({
  selector: 'fla-checkout-dados',
  template: `
    <fieldset
      class="fla-dados"
      *ngIf="state.loaded && state.faltando.length"
      [class.is-error]="!!error"
    >
      <legend>{{ 'cs.dadosTitle' | flaT }}</legend>
      <p>{{ 'cs.dadosLead' | flaT }}</p>
      <label *ngIf="falta('cpf')">
        <span>{{ 'rg.cpf' | flaT }}</span>
        <input
          class="form-control"
          type="text"
          inputmode="numeric"
          maxlength="14"
          autocomplete="off"
          [placeholder]="'rg.cpfPlaceholder' | flaT"
          [value]="cpf"
          (input)="onCpf($any($event.target).value)"
        />
      </label>
      <label *ngIf="falta('telefone')">
        <span>{{ 'rg.phone' | flaT }}</span>
        <input
          class="form-control"
          type="tel"
          inputmode="tel"
          maxlength="15"
          autocomplete="tel-national"
          [placeholder]="'rg.phonePlaceholder' | flaT"
          [value]="telefone"
          (input)="onTelefone($any($event.target).value)"
        />
      </label>
      <p class="fla-dados-error" role="alert" *ngIf="error">
        {{ error.startsWith('rg.') ? (error | flaT) : error }}
      </p>
      <button
        type="button"
        class="btn btn-secondary"
        [disabled]="saving"
        (click)="save()"
      >
        {{ 'cs.dadosSave' | flaT }}
      </button>
    </fieldset>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlaCheckoutDadosComponent implements OnInit, OnDestroy {
  state: FlaCustomerData = { loaded: false, faltando: [] };
  cpf = '';
  telefone = '';
  error = '';
  saving = false;
  protected subscription = new Subscription();

  constructor(
    protected data: FlaCustomerDataService,
    protected cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.data.state$.subscribe((s) => {
        this.state = s;
        this.cd.markForCheck();
      })
    );
  }

  falta(campo: string): boolean {
    return this.state.faltando.includes(campo);
  }

  onCpf(value: string): void {
    this.cpf = maskCpf(value);
  }

  onTelefone(value: string): void {
    this.telefone = maskTelefone(value);
  }

  save(): void {
    if (this.falta('cpf') && !isValidCpf(this.cpf)) {
      this.error = 'rg.cpfInvalid';
      return;
    }
    if (this.falta('telefone') && !isValidTelefone(this.telefone)) {
      this.error = 'rg.phoneInvalid';
      return;
    }
    this.error = '';
    this.saving = true;
    this.data.save(this.cpf, this.telefone).subscribe({
      next: () => {
        this.saving = false;
        this.cd.markForCheck();
      },
      error: (message: string) => {
        this.saving = false;
        this.error = message;
        this.cd.markForCheck();
      },
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
