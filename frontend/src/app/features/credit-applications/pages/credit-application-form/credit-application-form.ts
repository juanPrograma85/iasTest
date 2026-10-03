import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Icon } from '../../../../core/components/icon/icon';
import { ApplicationDetail } from '../../components/application-detail/application-detail';
import {
  APPLICATION_REFERENCE_PATTERN,
  CUSTOMER_ID_PATTERN,
  CustomerSummary,
  MAX_TERM_MONTHS,
  MIN_TERM_MONTHS,
} from '../../models/credit-application.model';
import { CustomersService } from '../../services/customers.service';
import { CreditApplicationsStore } from '../../state/credit-applications.store';
import { customerExists, integerNumber, positiveNumber } from '../../validators/credit-application.validators';

@Component({
  selector: 'app-credit-application-form',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    Icon,
    ApplicationDetail,
  ],
  templateUrl: './credit-application-form.html',
  styleUrl: './credit-application-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreditApplicationForm {
  protected readonly store = inject(CreditApplicationsStore);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly minTerm = MIN_TERM_MONTHS;
  protected readonly maxTerm = MAX_TERM_MONTHS;
  protected readonly knownCustomers = ['CLI-1001', 'CLI-1002', 'CLI-2001'];
  protected readonly customer = signal<CustomerSummary | null>(null);
  protected readonly customerBlocked = computed(() => this.customer()?.status === 'BLOCKED');

  // La respuesta del backend se muestra sin esperar a que el usuario salga del campo.
  protected readonly customerErrorMatcher: ErrorStateMatcher = {
    isErrorState: (control) =>
      !!control && control.invalid && (control.touched || control.hasError('customerNotFound')),
  };

  protected readonly form = this.fb.group({
    applicationReference: ['', [Validators.required, Validators.pattern(APPLICATION_REFERENCE_PATTERN)]],
    customerId: [
      '',
      {
        validators: [Validators.required, Validators.pattern(CUSTOMER_ID_PATTERN)],
        asyncValidators: [customerExists(inject(CustomersService), (c) => this.customer.set(c))],
      },
    ],
    amount: this.fb.control<number | null>(null, [Validators.required, positiveNumber]),
    termMonths: this.fb.control<number | null>(null, [
      Validators.required,
      integerNumber,
      Validators.min(MIN_TERM_MONTHS),
      Validators.max(MAX_TERM_MONTHS),
    ]),
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  private readonly formStatus = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  protected readonly pending = computed(() => this.formStatus() === 'PENDING');

  protected readonly summary = computed(() => {
    const value = this.formValue();
    return {
      reference: value.applicationReference?.trim() ?? '',
      customer: value.customerId?.trim() ?? '',
      amount: value.amount ?? null,
      term: value.termMonths ?? null,
    };
  });

  // Reutiliza los validadores del formulario; formStatus fuerza el recálculo en cada cambio.
  protected readonly checks = computed(() => {
    this.formStatus();
    const c = this.form.controls;
    return [
      { label: 'Referencia válida', ok: c.applicationReference.valid },
      { label: this.customerCheckLabel(), ok: c.customerId.valid },
      { label: 'Monto mayor a cero', ok: c.amount.valid },
      { label: `Plazo entre ${this.minTerm} y ${this.maxTerm} meses`, ok: c.termMonths.valid },
    ];
  });

  constructor() {
    this.store.clearSubmission();
  }

  protected submit(): void {
    if (this.form.invalid || this.form.pending) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.store.submit({
      applicationReference: value.applicationReference.trim(),
      customerId: value.customerId.trim(),
      amount: Number(value.amount),
      termMonths: Number(value.termMonths),
    });
  }

  protected showError(controlName: keyof typeof this.form.controls, error: string): boolean {
    const control = this.form.controls[controlName];
    return control.touched && control.hasError(error);
  }

  private customerCheckLabel(): string {
    const control = this.form.controls.customerId;
    if (control.pending) return 'Verificando cliente…';
    if (control.hasError('customerNotFound')) return 'El cliente no existe';
    return 'Cliente registrado';
  }
}
