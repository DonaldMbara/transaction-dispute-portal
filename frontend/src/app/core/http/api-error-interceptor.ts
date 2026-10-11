import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthFailure } from '../auth/auth-contracts';
import { isOwnApi } from '../auth/auth-interceptors';
import { NotificationService } from '../../shared/components/notification/notification';

function errorDetails(failure: unknown): { status: number; code: number } {
  if (failure instanceof AuthFailure) return { status: failure.status, code: failure.code };
  if (!(failure instanceof HttpErrorResponse)) return { status: 0, code: 0 };
  const body: unknown = failure.error;
  const code = typeof body === 'object' && body !== null && 'error_code' in body && typeof body.error_code === 'number'
    ? body.error_code : 0;
  return { status: failure.status, code };
}

function userMessage(status: number, code: number): string | null {
  if (code === 1010) return null;
  if (status === 403) return 'You do not have permission to perform this operation.';
  if (status === 0 || status >= 500) return 'The service is temporarily unavailable. Please try again.';
  return null;
}

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isOwnApi(request.url)) return next(request);
  const notifications = inject(NotificationService);
  const path = new URL(request.url, window.location.origin).pathname;
  const isAuthenticationRequest = path.startsWith('/api/v1/auth/');
  return next(request).pipe(catchError((failure: unknown) => {
    if (!isAuthenticationRequest) {
      const { status, code } = errorDetails(failure);
      const message = userMessage(status, code);
      if (message) notifications.show(message, 'error');
    }
    return throwError(() => failure);
  }));
};
