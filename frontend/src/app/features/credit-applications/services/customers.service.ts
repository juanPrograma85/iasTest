import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../../core/services/api-client.service';
import { CustomerSummary } from '../models/credit-application.model';

@Injectable({ providedIn: 'root' })
export class CustomersService {
  private readonly api = inject(ApiClient);

  getCustomer(customerId: string): Observable<CustomerSummary> {
    return this.api.get<CustomerSummary>(`/customers/${encodeURIComponent(customerId)}`);
  }
}
