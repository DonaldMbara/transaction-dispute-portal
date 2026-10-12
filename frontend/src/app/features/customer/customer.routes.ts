import { Routes } from '@angular/router';

export const CUSTOMER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () =>
    import('./accounts/account-dashboard').then(m => m.AccountDashboard) },
  { path: 'transactions', pathMatch: 'full', loadComponent: () =>
    import('./transactions/transactions').then(m => m.Transactions) },
  { path: 'transactions/:id', loadComponent: () =>
    import('./transaction-detail/transaction-detail').then(m => m.TransactionDetail) },
  { path: 'disputes/new', loadComponent: () =>
    import('./disputes/dispute-submission').then(m => m.DisputeSubmission) },
  { path: 'disputes', pathMatch: 'full', loadComponent: () =>
    import('./disputes/disputes').then(m => m.Disputes) },
  { path: 'disputes/:id', loadComponent: () =>
    import('./disputes/dispute-detail/dispute-detail').then(m => m.DisputeDetail) }
];
