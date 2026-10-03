import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { API_BASE_URL } from './api-base-url.token';
import { toApiError } from './api-error.mapper';
import { HttpActivityStore } from './http-activity.store';
import { httpStatusTitle } from './http-status';

export const httpActivityInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(inject(API_BASE_URL))) {
    return next(req);
  }
  const activity = inject(HttpActivityStore);
  const startedAt = Date.now();
  const base = { method: req.method, url: req.urlWithParams };

  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          activity.record({
            ...base,
            status: event.status,
            ok: true,
            message: describeSuccess(event),
            durationMs: Date.now() - startedAt,
          });
        }
      },
      error: (error: unknown) => {
        const apiError = toApiError(error);
        activity.record({
          ...base,
          status: apiError.status,
          ok: false,
          code: apiError.code,
          message: apiError.message,
          durationMs: Date.now() - startedAt,
        });
      },
    }),
  );
};

function describeSuccess(response: HttpResponse<unknown>): string {
  const body = response.body as Record<string, unknown> | null;
  if (body && Array.isArray(body['items'])) {
    return `${body['items'].length} solicitud(es) recientes.`;
  }
  if (body && typeof body['applicationReference'] === 'string' && typeof body['status'] === 'string') {
    const replay = body['idempotentReplay'] === true ? ' (repetición idempotente)' : '';
    return `${body['applicationReference']}: ${body['status']} - ${body['decisionReason']}${replay}.`;
  }
  return httpStatusTitle(response.status);
}
