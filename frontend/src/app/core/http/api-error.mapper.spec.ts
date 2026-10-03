import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './api-error.mapper';

describe('toApiError', () => {
  it('keeps the backend error contract', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { code: 'APPLICATION_REFERENCE_CONFLICT', message: 'Conflict', timestamp: '2026-10-03T15:30:00Z' },
    });

    expect(toApiError(error)).toEqual({
      status: 409,
      code: 'APPLICATION_REFERENCE_CONFLICT',
      message: 'Conflict',
      timestamp: '2026-10-03T15:30:00Z',
    });
  });

  it('maps connection failures', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK_ERROR');
  });

  it('hides unexpected payloads behind a generic message', () => {
    const result = toApiError(new HttpErrorResponse({ status: 502, error: '<html>Bad gateway</html>' }));

    expect(result.code).toBe('UNEXPECTED_ERROR');
    expect(result.message).not.toContain('html');
  });
});
