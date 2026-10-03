import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, LOCALE_ID, OnInit, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Icon } from '../../../../core/components/icon/icon';
import { ApplicationDetail } from '../../components/application-detail/application-detail';
import { EmptyState } from '../../components/empty-state/empty-state';
import { KpiCard } from '../../components/kpi-card/kpi-card';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { APPLICATION_REFERENCE_PATTERN, CreditApplicationStatus } from '../../models/credit-application.model';
import { DecisionReasonLabelPipe } from '../../pipes/credit-application-labels.pipe';
import { CreditApplicationsStore } from '../../state/credit-applications.store';

type StatusFilter = 'ALL' | CreditApplicationStatus;

@Component({
  selector: 'app-recent-applications',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    Icon,
    ApplicationDetail,
    EmptyState,
    KpiCard,
    StatusBadge,
    DecisionReasonLabelPipe,
  ],
  templateUrl: './recent-applications.html',
  styleUrl: './recent-applications.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecentApplications implements OnInit {
  protected readonly store = inject(CreditApplicationsStore);
  private readonly currency = new CurrencyPipe(inject(LOCALE_ID));

  protected readonly lookupForm = inject(NonNullableFormBuilder).group({
    reference: ['', [Validators.required, Validators.pattern(APPLICATION_REFERENCE_PATTERN)]],
  });

  protected readonly referenceFilter = signal('');
  protected readonly customerFilter = signal('');
  protected readonly statusFilter = signal<StatusFilter>('ALL');

  protected readonly kpis = computed(() => {
    const items = this.store.recent();
    const approved = items.filter((a) => a.status === 'APPROVED');
    const approvedAmount = approved.reduce((sum, a) => sum + Number(a.amount), 0);
    return {
      total: items.length,
      approved: approved.length,
      rejected: items.length - approved.length,
      approvedAmount: this.currency.transform(approvedAmount, 'COP', 'symbol-narrow', '1.0-0') ?? '',
    };
  });

  protected readonly filtered = computed(() => {
    const reference = this.referenceFilter().trim().toLowerCase();
    const customer = this.customerFilter().trim().toLowerCase();
    const status = this.statusFilter();
    return this.store.recent().filter(
      (a) =>
        (!reference || a.applicationReference.toLowerCase().includes(reference)) &&
        (!customer || a.customerId.toLowerCase().includes(customer)) &&
        (status === 'ALL' || a.status === status),
    );
  });

  protected readonly hasFilters = computed(
    () => !!this.referenceFilter() || !!this.customerFilter() || this.statusFilter() !== 'ALL',
  );

  ngOnInit(): void {
    this.store.loadRecent();
  }

  protected refresh(): void {
    this.store.loadRecent();
  }

  protected clearFilters(): void {
    this.referenceFilter.set('');
    this.customerFilter.set('');
    this.statusFilter.set('ALL');
  }

  protected inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected search(): void {
    if (this.lookupForm.invalid) {
      this.lookupForm.markAllAsTouched();
      return;
    }
    this.store.findByReference(this.lookupForm.getRawValue().reference.trim());
  }
}
