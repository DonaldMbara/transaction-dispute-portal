import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () =>
    import('./features/welcome/welcome').then(m => m.Welcome) },
  { path: '', loadChildren: () =>
    import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES) },
  { path: 'customer', loadComponent: () =>
    import('./layouts/dashboard-layout/dashboard-layout').then(m => m.DashboardLayout),
    loadChildren: () => import('./features/customer/customer.routes').then(m => m.CUSTOMER_ROUTES) },
  { path: 'agent', loadComponent: () =>
    import('./layouts/dashboard-layout/dashboard-layout').then(m => m.DashboardLayout),
    loadChildren: () => import('./features/agent/agent.routes').then(m => m.AGENT_ROUTES) },
  { path: '**', redirectTo: '' }
];
