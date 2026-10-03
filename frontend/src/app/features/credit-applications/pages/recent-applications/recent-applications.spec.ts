import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeEsCo from '@angular/common/locales/es-CO';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../../core/services/notification.service';
import { CreditApplication } from '../../models/credit-application.model';
import { RecentApplications } from './recent-applications';

const RECENT_URL = '/api/v1/credit-applications/recent';

const ITEMS: CreditApplication[] = [
  {
    applicationReference: 'REF-002',
    customerId: 'CLI-1002',
    amount: 1000000,
    termMonths: 12,
    status: 'REJECTED',
    processedAt: '2026-10-03T15:25:00Z',
    decisionReason: 'CUSTOMER_NOT_ELIGIBLE',
  },
  {
    applicationReference: 'REF-001',
    customerId: 'CLI-1001',
    amount: 6000000,
    termMonths: 24,
    status: 'APPROVED',
    processedAt: '2026-10-03T15:20:00Z',
    decisionReason: 'CREDIT_LIMIT_AVAILABLE',
  },
];

describe('RecentApplications', () => {
  let fixture: ComponentFixture<RecentApplications>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeAll(() => registerLocaleData(localeEsCo));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecentApplications],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'es-CO' }],
    }).compileComponents();

    fixture = TestBed.createComponent(RecentApplications);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function flushRecent(items: CreditApplication[]) {
    http.expectOne((r) => r.url === RECENT_URL).flush({ items });
    fixture.detectChanges();
  }

  it('loads and renders recent applications in the order returned by the API', () => {
    flushRecent(ITEMS);

    const rows = el.querySelectorAll('[data-testid="recent-row"]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('REF-002');
    expect(rows[0].textContent).toContain('Cliente no habilitado');
    expect(rows[0].querySelector('[data-status]')?.getAttribute('data-status')).toBe('REJECTED');
    expect(rows[1].querySelector('[data-status]')?.getAttribute('data-status')).toBe('APPROVED');
  });

  it('shows an empty state', () => {
    flushRecent([]);
    expect(el.querySelector('[data-testid="empty"]')).not.toBeNull();
  });

  it('computes KPIs from the loaded applications', () => {
    flushRecent(ITEMS);

    const values = Array.from(el.querySelectorAll('[data-testid="kpi-value"]')).map((v) => v.textContent?.trim());
    expect(values.slice(0, 3)).toEqual(['2', '1', '1']);
    expect(values[3]).toContain('6.000.000');
  });

  it('filters loaded applications by reference, customer and status without calling the API', () => {
    flushRecent(ITEMS);
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const rows = () => el.querySelectorAll('[data-testid="recent-row"]');

    type('filter-reference', 'ref-001');
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('REF-001');

    type('filter-reference', '');
    type('filter-customer', '1002');
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('CLI-1002');

    type('filter-customer', '');
    const approvedToggle = Array.from(el.querySelectorAll<HTMLButtonElement>('mat-button-toggle button')).find((b) =>
      b.textContent?.includes('Aprobadas'),
    )!;
    approvedToggle.click();
    fixture.detectChanges();
    expect(rows().length).toBe(1);
    expect(rows()[0].querySelector('[data-status]')?.getAttribute('data-status')).toBe('APPROVED');

    type('filter-customer', 'CLI-9');
    expect(el.querySelector('[data-testid="no-results"]')).not.toBeNull();
    http.expectNone(() => true);
  });

  it('notifies backend errors when loading fails', () => {
    http.expectOne((r) => r.url === RECENT_URL).flush(
      { code: 'INTERNAL_ERROR', message: 'Ocurrió un error inesperado. Intente nuevamente.' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="recent-error"]')).not.toBeNull();
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(TestBed.inject(NotificationService).items()).toEqual([
      jasmine.objectContaining({ type: 'error', httpStatus: '500 Internal Server Error' }),
    ]);
  });

  it('looks up an application by reference and notifies 404', () => {
    flushRecent(ITEMS);
    const input = el.querySelector<HTMLInputElement>('#lookup')!;
    input.value = 'REF-404';
    input.dispatchEvent(new Event('input'));
    el.querySelector<HTMLFormElement>('form.lookup')!.dispatchEvent(new Event('submit'));

    http.expectOne('/api/v1/credit-applications/REF-404').flush(
      { code: 'CREDIT_APPLICATION_NOT_FOUND', message: 'No existe una solicitud con la referencia indicada.' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(el.querySelector('.lookup-result app-application-detail')).toBeNull();
    expect(TestBed.inject(NotificationService).items()).toEqual([
      jasmine.objectContaining({
        type: 'error',
        title: 'No encontrada',
        message: 'No existe una solicitud con la referencia indicada.',
      }),
    ]);
  });
});
