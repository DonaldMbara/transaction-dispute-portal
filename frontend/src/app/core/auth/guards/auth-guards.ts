import {inject} from '@angular/core';
import {CanActivateChildFn, CanActivateFn, CanMatchFn, Router} from '@angular/router';
import {AuthService} from '../services/auth-service';
import {Role} from '../models/auth-models';

export function landing(role: Role): string {
  return role === 'CUSTOMER' ? '/customer' : '/agent';
}

export function safeReturnUrl(candidate: string | null, role: Role): string {
  return candidate === landing(role) ? candidate : landing(role);
}

export const entryRedirect: CanActivateFn = async () => {
  const auth = inject(AuthService),
    router = inject(Router);
  await auth.initialize();
  if (auth.state.phase() === 'unavailable') return router.createUrlTree(['/session-unavailable']);
  const identity = auth.state.identity();
  return router.createUrlTree([identity ? landing(identity.role) : '/login']);
};

async function authorize(auth: AuthService, router: Router, role: Role, returnUrl: string) {
  await auth.initialize();
  if (auth.state.phase() === 'unavailable') return router.createUrlTree(['/session-unavailable']);
  const identity = auth.state.identity();
  if (!identity || auth.state.phase() !== 'authenticated')
    return router.createUrlTree(['/login'], {
      queryParams: {returnUrl: safeReturnUrl(returnUrl, role)},
    });
  return identity.role === role ? true : router.createUrlTree(['/access-denied']);
}

export function roleMatch(role: Role): CanMatchFn {
  return (_route, segments) =>
    authorize(
      inject(AuthService),
      inject(Router),
      role,
      '/' + segments.map((segment) => segment.path).join('/'),
    );
}

export function roleChildren(role: Role): CanActivateChildFn {
  return (_route, state) => authorize(inject(AuthService), inject(Router), role, state.url);
}
