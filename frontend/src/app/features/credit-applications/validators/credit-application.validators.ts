import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, AsyncValidatorFn, ValidationErrors } from '@angular/forms';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { CustomerSummary } from '../models/credit-application.model';
import { CustomersService } from '../services/customers.service';

export const CUSTOMER_LOOKUP_DEBOUNCE_MS = 400;

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

export function positiveNumber(control: AbstractControl): ValidationErrors | null {
  return isEmpty(control.value) || Number(control.value) > 0 ? null : { positive: true };
}

export function integerNumber(control: AbstractControl): ValidationErrors | null {
  return isEmpty(control.value) || Number.isInteger(Number(control.value)) ? null : { integer: true };
}

/**
 * Verifica en el backend que el cliente exista. Solo 404 invalida el campo; otros fallos no bloquean
 * porque el backend vuelve a validar al procesar la solicitud.
 */
export function customerExists(
  customers: CustomersService,
  onResolved: (customer: CustomerSummary | null) => void,
): AsyncValidatorFn {
  return (control) => {
    onResolved(null);
    const customerId = String(control.value ?? '').trim();
    if (!customerId) {
      return of(null);
    }
    return timer(CUSTOMER_LOOKUP_DEBOUNCE_MS).pipe(
      switchMap(() => customers.getCustomer(customerId)),
      map((customer) => {
        onResolved(customer);
        return null;
      }),
      catchError((error: unknown) =>
        of(error instanceof HttpErrorResponse && error.status === 404 ? { customerNotFound: true } : null),
      ),
    );
  };
}
