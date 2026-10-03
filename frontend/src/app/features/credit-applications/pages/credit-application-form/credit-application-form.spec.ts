import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeEsCo from '@angular/common/locales/es-CO';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationService } from '../../../../core/services/notification.service';
import { CustomerStatus, ProcessCreditApplicationResponse } from '../../models/credit-application.model';
import { CUSTOMER_LOOKUP_DEBOUNCE_MS } from '../../validators/credit-application.validators';
import { CreditApplicationForm } from './credit-application-form';

const URL = '/api/v1/credit-applications';
const CUSTOMERS_URL = '/api/v1/customers';

describe('CreditApplicationForm', () => {
  let fixture: ComponentFixture<CreditApplicationForm>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeAll(() => registerLocaleData(localeEsCo));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreditApplicationForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'es-CO' }],
    }).compileComponents();

    fixture = TestBed.createComponent(CreditApplicationForm);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function fill(values: Partial<Record<'applicationReference' | 'customerId' | 'amount' | 'termMonths', string>>) {
    for (const [id, value] of Object.entries(values)) {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
    }
    fixture.detectChanges();
  }

  function submit() {
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  /** Debe ejecutarse dentro de fakeAsync. */
  function resolveCustomer(customerId: string, status: CustomerStatus | 404 = 'ELIGIBLE') {
    tick(CUSTOMER_LOOKUP_DEBOUNCE_MS);
    const req = http.expectOne(`${CUSTOMERS_URL}/${customerId}`);
    if (status === 404) {
      req.flush(
        { code: 'CUSTOMER_NOT_FOUND', message: 'El cliente no existe.' },
        { status: 404, statusText: 'Not Found' },
      );
    } else {
      req.flush({ customerId, status });
    }
    fixture.detectChanges();
  }

  function fillValid(ref: string, customerId: string, amount: string, term: string, status: CustomerStatus = 'ELIGIBLE') {
    fill({ applicationReference: ref, customerId, amount, termMonths: term });
    resolveCustomer(customerId, status);
  }

  function respond(body: Partial<ProcessCreditApplicationResponse>) {
    http.expectOne(URL).flush({
      applicationReference: 'REF-001',
      customerId: 'CLI-1001',
      amount: 6000000,
      termMonths: 24,
      processedAt: '2026-10-03T15:20:00Z',
      idempotentReplay: false,
      ...body,
    });
    fixture.detectChanges();
  }

  it('does not call the API and shows required errors when the form is empty', () => {
    submit();

    http.expectNone(URL);
    expect(el.querySelectorAll('.field-error').length).toBe(4);
  });

  it('rejects non-positive amounts', () => {
    fill({ amount: '0' });
    expect(el.textContent).toContain('El monto debe ser mayor a cero.');
  });

  for (const term of ['5', '61']) {
    it(`rejects term ${term}`, () => {
      fill({ termMonths: term });
      expect(el.textContent).toContain('El plazo debe estar entre 6 y 60 meses.');
    });
  }

  it('rejects a customer that does not exist in the backend', fakeAsync(() => {
    fill({ applicationReference: 'REF-5844', customerId: 'ZZZZZ', amount: '6000000', termMonths: '24' });
    resolveCustomer('ZZZZZ', 404);

    expect(el.textContent).toContain('El cliente no existe.');
    submit();
    http.expectNone(URL);
  }));

  it('does not submit while the customer is being verified', fakeAsync(() => {
    fill({ applicationReference: 'REF-1', customerId: 'CLI-1001', amount: '1000', termMonths: '24' });

    expect(el.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBeTrue();
    submit();
    http.expectNone(URL);
    resolveCustomer('CLI-1001');
  }));

  it('warns about blocked customers but still allows the submission', fakeAsync(() => {
    fillValid('REF-B', 'CLI-1002', '1000000', '12', 'BLOCKED');

    expect(el.querySelector('[data-testid="customer-blocked"]')).not.toBeNull();
    submit();
    http.expectOne(URL).flush({});
  }));

  it('builds the request with trimmed text and numeric values', fakeAsync(() => {
    fillValid('REF-001', 'CLI-1001', '6000000', '24');
    submit();

    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({
      applicationReference: 'REF-001',
      customerId: 'CLI-1001',
      amount: 6000000,
      termMonths: 24,
    });
    req.flush({});
  }));

  it('shows an APPROVED result', fakeAsync(() => {
    fillValid('REF-001', 'CLI-1001', '6000000', '24');
    submit();
    respond({ status: 'APPROVED', decisionReason: 'CREDIT_LIMIT_AVAILABLE' });

    expect(el.querySelector('[data-status]')?.getAttribute('data-status')).toBe('APPROVED');
    expect(el.querySelector('.detail--approved')).not.toBeNull();
    expect(el.querySelector('[data-testid="reason"]')?.textContent).toContain('Cupo disponible');
    expect(TestBed.inject(NotificationService).items()).toEqual([
      jasmine.objectContaining({ type: 'success', title: 'Solicitud aprobada', httpStatus: '201 Created' }),
    ]);
    tick(10000);
  }));

  it('shows a REJECTED result even though the HTTP response was successful', fakeAsync(() => {
    fillValid('REF-002', 'CLI-1002', '1000000', '12', 'BLOCKED');
    submit();
    respond({ status: 'REJECTED', decisionReason: 'CUSTOMER_NOT_ELIGIBLE' });

    expect(el.querySelector('[data-status]')?.getAttribute('data-status')).toBe('REJECTED');
    expect(el.querySelector('.detail--rejected')).not.toBeNull();
    expect(el.querySelector('[data-testid="reason"]')?.textContent).toContain('Cliente no habilitado');
    expect(TestBed.inject(NotificationService).items()).toEqual([
      jasmine.objectContaining({ type: 'warning', title: 'Solicitud rechazada' }),
    ]);
    tick(10000);
  }));

  it('informs when the result is an idempotent replay', fakeAsync(() => {
    fillValid('REF-001', 'CLI-1001', '6000000', '24');
    submit();
    respond({ status: 'APPROVED', decisionReason: 'CREDIT_LIMIT_AVAILABLE', idempotentReplay: true });

    expect(el.querySelector('[data-testid="replay-notice"]')).not.toBeNull();
    tick(10000);
  }));

  it('shows backend errors only as a notification', fakeAsync(() => {
    fillValid('REF-001', 'CLI-1001', '7000000', '24');
    submit();
    http.expectOne(URL).flush(
      {
        code: 'APPLICATION_REFERENCE_CONFLICT',
        message: 'La referencia ya fue procesada con datos diferentes y no puede reutilizarse.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('app-application-detail')).toBeNull();
    expect(TestBed.inject(NotificationService).items()).toEqual([
      jasmine.objectContaining({
        type: 'error',
        title: 'Referencia ya utilizada con otros datos',
        message: 'La referencia ya fue procesada con datos diferentes y no puede reutilizarse.',
        httpStatus: '409 Conflict',
      }),
    ]);
    tick(10000);
  }));
});
