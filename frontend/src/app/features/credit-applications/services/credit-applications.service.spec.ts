import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreditApplicationsService } from './credit-applications.service';

describe('CreditApplicationsService', () => {
  let service: CreditApplicationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CreditApplicationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the request to the process endpoint', () => {
    const request = { applicationReference: 'REF-001', customerId: 'CLI-1001', amount: 6000000, termMonths: 24 };

    service.process(request).subscribe();

    const req = http.expectOne('/api/v1/credit-applications');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('requests recent applications with a limit and unwraps items', () => {
    let count = -1;
    service.getRecent(5).subscribe((items) => (count = items.length));

    const req = http.expectOne((r) => r.url === '/api/v1/credit-applications/recent');
    expect(req.request.params.get('limit')).toBe('5');
    req.flush({ items: [{}, {}] });
    expect(count).toBe(2);
  });

  it('encodes the reference when querying by reference', () => {
    service.getByReference('REF/../x').subscribe();

    http.expectOne('/api/v1/credit-applications/REF%2F..%2Fx').flush({});
  });
});
