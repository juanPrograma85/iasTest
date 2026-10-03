import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { toApiError } from '../../../core/http/api-error.mapper';
import { httpStatusLabel, httpStatusTitle } from '../../../core/http/http-status';
import { ApiError } from '../../../core/models/api-error.model';
import { NotificationService } from '../../../core/services/notification.service';
import {
  CreateCreditApplicationRequest,
  CreditApplication,
  ProcessCreditApplicationResponse,
} from '../models/credit-application.model';
import { decisionReasonLabel } from '../pipes/credit-application-labels.pipe';
import { CreditApplicationsService } from '../services/credit-applications.service';

@Injectable({ providedIn: 'root' })
export class CreditApplicationsStore {
  private readonly service = inject(CreditApplicationsService);
  private readonly notifications = inject(NotificationService);

  private readonly _submitting = signal(false);
  private readonly _lastResult = signal<ProcessCreditApplicationResponse | null>(null);
  private readonly _submitError = signal<ApiError | null>(null);

  private readonly _recentLoading = signal(false);
  private readonly _recent = signal<CreditApplication[]>([]);
  private readonly _recentError = signal<ApiError | null>(null);

  private readonly _lookupLoading = signal(false);
  private readonly _lookupResult = signal<CreditApplication | null>(null);
  private readonly _lookupError = signal<ApiError | null>(null);

  readonly submitting = this._submitting.asReadonly();
  readonly lastResult = this._lastResult.asReadonly();
  readonly submitError = this._submitError.asReadonly();
  readonly recentLoading = this._recentLoading.asReadonly();
  readonly recent = this._recent.asReadonly();
  readonly recentError = this._recentError.asReadonly();
  readonly lookupLoading = this._lookupLoading.asReadonly();
  readonly lookupResult = this._lookupResult.asReadonly();
  readonly lookupError = this._lookupError.asReadonly();

  submit(request: CreateCreditApplicationRequest): void {
    this._submitting.set(true);
    this._lastResult.set(null);
    this._submitError.set(null);
    this.service
      .process(request)
      .pipe(finalize(() => this._submitting.set(false)))
      .subscribe({
        next: (result) => {
          this._lastResult.set(result);
          this.notifyProcessed(result);
        },
        error: (error) => this._submitError.set(this.notifyError(error)),
      });
  }

  clearSubmission(): void {
    this._lastResult.set(null);
    this._submitError.set(null);
  }

  loadRecent(limit = 20): void {
    this._recentLoading.set(true);
    this._recentError.set(null);
    this.service
      .getRecent(limit)
      .pipe(finalize(() => this._recentLoading.set(false)))
      .subscribe({
        next: (items) => this._recent.set(items),
        error: (error) => this._recentError.set(this.notifyError(error)),
      });
  }

  findByReference(applicationReference: string): void {
    this._lookupLoading.set(true);
    this._lookupResult.set(null);
    this._lookupError.set(null);
    this.service
      .getByReference(applicationReference)
      .pipe(finalize(() => this._lookupLoading.set(false)))
      .subscribe({
        next: (application) => this._lookupResult.set(application),
        error: (error) => this._lookupError.set(this.notifyError(error)),
      });
  }

  private notifyProcessed(result: ProcessCreditApplicationResponse): void {
    const reason = decisionReasonLabel(result.decisionReason);
    if (result.idempotentReplay) {
      this.notifications.info({
        title: 'Solicitud ya procesada',
        message: `${result.applicationReference}: se devolvió el resultado original (${result.status}). No se consumió cupo adicional.`,
        httpStatus: httpStatusLabel(200),
      });
    } else if (result.status === 'APPROVED') {
      this.notifications.success({
        title: 'Solicitud aprobada',
        message: `${result.applicationReference}: ${reason}.`,
        httpStatus: httpStatusLabel(201),
      });
    } else {
      this.notifications.warning({
        title: 'Solicitud rechazada',
        message: `${result.applicationReference}: ${reason}.`,
        httpStatus: httpStatusLabel(201),
      });
    }
  }

  private notifyError(error: unknown): ApiError {
    const apiError = toApiError(error);
    this.notifications.error({
      title: httpStatusTitle(apiError.status),
      message: apiError.message,
      httpStatus: httpStatusLabel(apiError.status),
    });
    return apiError;
  }
}
