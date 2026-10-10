import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, map, throwError } from 'rxjs';
import { AuthFailure } from './auth-contracts';
import { CsrfState, SessionState } from './session-state';
export function isOwnApi(url: string): boolean {
  try {
    const target = new URL(url, window.location.origin);
    return target.origin === window.location.origin && (target.pathname === '/api/v1' || target.pathname.startsWith('/api/v1/'));
  } catch { return false; }
}
export const csrfInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isOwnApi(request.url) || ['GET', 'HEAD', 'OPTIONS'].includes(request.method)) return next(request);
  const token = inject(CsrfState).token();
  if (!token) return throwError(() => new AuthFailure(403, 1010));
  return next(request.clone({ setHeaders: { 'X-CSRF-TOKEN': token } }));
};
export const authResponseInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isOwnApi(request.url)) return next(request);
  const state = inject(SessionState), csrf = inject(CsrfState), generation = state.generation;
  return next(request).pipe(
    map(event => {
      if (generation !== state.generation) throw new AuthFailure(409);
      return event;
    }),
    catchError((failure: unknown) => {
      if (generation !== state.generation) return throwError(() => new AuthFailure(409));
      const status = failure instanceof HttpErrorResponse ? failure.status : failure instanceof AuthFailure ? failure.status : 0;
      const body: unknown = failure instanceof HttpErrorResponse ? failure.error : null;
      const code = typeof body === 'object' && body !== null && 'error_code' in body && typeof body.error_code === 'number' ? body.error_code : failure instanceof AuthFailure ? failure.code : 0;
      if (status === 401) { state.anonymous(); csrf.clear(); }
      if (status === 403 && code === 1010) csrf.clear();
      return throwError(() => new AuthFailure(status, code));
    })
  );
};
