import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiClient } from '../../../core/services/api-client.service';
import {
  CreateCreditApplicationRequest,
  CreditApplication,
  ProcessCreditApplicationResponse,
  RecentCreditApplicationsResponse,
} from '../models/credit-application.model';

const RESOURCE = '/credit-applications';

@Injectable({ providedIn: 'root' })
export class CreditApplicationsService {
  private readonly api = inject(ApiClient);

  process(request: CreateCreditApplicationRequest): Observable<ProcessCreditApplicationResponse> {
    return this.api.post<ProcessCreditApplicationResponse>(RESOURCE, request);
  }

  getByReference(applicationReference: string): Observable<CreditApplication> {
    return this.api.get<CreditApplication>(`${RESOURCE}/${encodeURIComponent(applicationReference)}`);
  }

  getRecent(limit = 20): Observable<CreditApplication[]> {
    return this.api
      .get<RecentCreditApplicationsResponse>(`${RESOURCE}/recent`, { limit })
      .pipe(map((response) => response.items));
  }
}
