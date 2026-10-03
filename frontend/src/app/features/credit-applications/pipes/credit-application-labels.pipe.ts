import { Pipe, PipeTransform } from '@angular/core';
import { CreditApplicationStatus, DecisionReason } from '../models/credit-application.model';

const REASON_LABELS: Record<DecisionReason, string> = {
  CREDIT_LIMIT_AVAILABLE: 'Cupo disponible',
  CUSTOMER_NOT_ELIGIBLE: 'Cliente no habilitado',
  CREDIT_LIMIT_EXCEEDED: 'Cupo insuficiente',
};

const STATUS_LABELS: Record<CreditApplicationStatus, string> = {
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
};

export function decisionReasonLabel(reason: DecisionReason): string {
  return REASON_LABELS[reason] ?? reason;
}

export function statusLabel(status: CreditApplicationStatus): string {
  return STATUS_LABELS[status] ?? status;
}

@Pipe({ name: 'decisionReasonLabel' })
export class DecisionReasonLabelPipe implements PipeTransform {
  transform(reason: DecisionReason): string {
    return decisionReasonLabel(reason);
  }
}

@Pipe({ name: 'statusLabel' })
export class StatusLabelPipe implements PipeTransform {
  transform(status: CreditApplicationStatus): string {
    return statusLabel(status);
  }
}
