import {Routes} from '@angular/router';
import {entryRedirect, roleChildren, roleMatch} from './core/auth/guards/auth-guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [entryRedirect],
    loadComponent: () => import('./features/welcome/welcome').then((m) => m.Welcome),
  },
  {
    path: '',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'access-denied',
    loadComponent: () =>
      import('./features/auth/access-denied/access-denied').then((m) => m.AccessDenied),
  },
  {
    path: 'session-unavailable',
    loadComponent: () =>
      import('./features/auth/session-unavailable/session-unavailable').then(
        (m) => m.SessionUnavailable,
      ),
  },
  {
    path: 'customer',
    canMatch: [roleMatch('CUSTOMER')],
    canActivateChild: [roleChildren('CUSTOMER')],
    loadComponent: () =>
      import('./layouts/dashboard-layout/dashboard-layout').then((m) => m.DashboardLayout),
    loadChildren: () =>
      import('./features/customer/customer.routes').then((m) => m.CUSTOMER_ROUTES),
  },
  {
    path: 'agent',
    canMatch: [roleMatch('AGENT')],
    canActivateChild: [roleChildren('AGENT')],
    loadComponent: () =>
      import('./layouts/dashboard-layout/dashboard-layout').then((m) => m.DashboardLayout),
    loadChildren: () => import('./features/agent/agent.routes').then((m) => m.AGENT_ROUTES),
  },
  {
    path: '**',
    canActivate: [entryRedirect],
    loadComponent: () => import('./features/welcome/welcome').then((m) => m.Welcome),
  },
];
