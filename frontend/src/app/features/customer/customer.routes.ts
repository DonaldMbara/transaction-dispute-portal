import { Routes } from '@angular/router';

export const CUSTOMER_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./accounts/account-dashboard').then((m) => m.AccountDashboard),
  },
  {
    path: 'transactions',
    pathMatch: 'full',
    loadComponent: () => import('./transactions/list/transactions').then((m) => m.Transactions),
  },
  {
    path: 'transactions/:id',
    loadComponent: () =>
      import('./transactions/detail/transaction-detail').then((m) => m.TransactionDetail),
  },
  {
    path: 'disputes/new',
    loadComponent: () => import('./disputes/submit/dispute-submission').then((m) => m.DisputeSubmission),
  },
  {
    path: 'disputes',
    pathMatch: 'full',
    loadComponent: () => import('./disputes/list/disputes').then((m) => m.Disputes),
  },
  {
    path: 'disputes/:id',
    loadComponent: () =>
      import('./disputes/detail/dispute-detail').then((m) => m.DisputeDetail),
  },
];
