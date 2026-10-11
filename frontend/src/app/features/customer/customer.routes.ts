import { Routes } from '@angular/router';

export const CUSTOMER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () =>
    import('./accounts/account-dashboard').then(m => m.AccountDashboard) }
];
