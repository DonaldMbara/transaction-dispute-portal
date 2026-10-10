import { Routes } from '@angular/router';

export const CUSTOMER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () =>
    import('./customer').then(m => m.Customer) }
];
