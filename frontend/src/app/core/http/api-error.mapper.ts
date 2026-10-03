import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/api-error.model';

const UNEXPECTED_MESSAGE = 'Ocurrió un error inesperado. Intente nuevamente.';

export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, code: 'UNEXPECTED_ERROR', message: UNEXPECTED_MESSAGE };
  }
  if (error.status === 0) {
    return { status: 0, code: 'NETWORK_ERROR', message: 'No fue posible conectar con el servidor.' };
  }
  const body = error.error;
  if (body && typeof body.code === 'string' && typeof body.message === 'string') {
    return { status: error.status, code: body.code, message: body.message, timestamp: body.timestamp };
  }
  return { status: error.status, code: 'UNEXPECTED_ERROR', message: UNEXPECTED_MESSAGE };
}
