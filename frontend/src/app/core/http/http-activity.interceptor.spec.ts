import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { httpActivityInterceptor } from './http-activity.interceptor';
import { HttpActivityStore } from './http-activity.store';

describe('httpActivityInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let activity: HttpActivityStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([httpActivityInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    activity = TestBed.inject(HttpActivityStore);
  });

  afterEach(() => backend.verify());

  it('records successful API responses with a business summary', () => {
    http.post('/api/v1/credit-applications', {}).subscribe();
    backend.expectOne('/api/v1/credit-applications').flush(
      { applicationReference: 'REF-1', status: 'APPROVED', decisionReason: 'CREDIT_LIMIT_AVAILABLE', idempotentReplay: false },
      { status: 201, statusText: 'Created' },
    );

    const [entry] = activity.entries();
    expect(entry.method).toBe('POST');
    expect(entry.status).toBe(201);
    expect(entry.ok).toBeTrue();
    expect(entry.message).toBe('REF-1: APPROVED - CREDIT_LIMIT_AVAILABLE.');
  });

  it('records API errors with the business code and the backend message', () => {
    http.get('/api/v1/credit-applications/REF-X').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/credit-applications/REF-X').flush(
      { code: 'CREDIT_APPLICATION_NOT_FOUND', message: 'No existe una solicitud con la referencia indicada.' },
      { status: 404, statusText: 'Not Found' },
    );

    const [entry] = activity.entries();
    expect(entry.ok).toBeFalse();
    expect(entry.status).toBe(404);
    expect(entry.code).toBe('CREDIT_APPLICATION_NOT_FOUND');
    expect(entry.message).toBe('No existe una solicitud con la referencia indicada.');
  });

  it('ignores requests outside the API', () => {
    http.get('/assets/config.json').subscribe();
    backend.expectOne('/assets/config.json').flush({});

    expect(activity.entries()).toEqual([]);
  });
});
