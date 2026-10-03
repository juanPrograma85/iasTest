export type CreditApplicationStatus = 'APPROVED' | 'REJECTED';

export type DecisionReason =
  | 'CREDIT_LIMIT_AVAILABLE'
  | 'CUSTOMER_NOT_ELIGIBLE'
  | 'CREDIT_LIMIT_EXCEEDED';

export interface CreateCreditApplicationRequest {
  applicationReference: string;
  customerId: string;
  amount: number;
  termMonths: number;
}

export interface CreditApplication {
  applicationReference: string;
  customerId: string;
  amount: number;
  termMonths: number;
  status: CreditApplicationStatus;
  processedAt: string;
  decisionReason: DecisionReason;
}

export interface ProcessCreditApplicationResponse extends CreditApplication {
  idempotentReplay: boolean;
}

export interface RecentCreditApplicationsResponse {
  items: CreditApplication[];
}

export type CustomerStatus = 'ELIGIBLE' | 'BLOCKED';

export interface CustomerSummary {
  customerId: string;
  status: CustomerStatus;
}

export const MIN_TERM_MONTHS = 6;
export const MAX_TERM_MONTHS = 60;
export const APPLICATION_REFERENCE_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
export const CUSTOMER_ID_PATTERN = /^[A-Za-z0-9-]{1,20}$/;
