import { Routes } from '@angular/router';

export const AGENT_ROUTES: Routes = [
  {
    path: 'disputes',
    pathMatch: 'full',
    loadComponent: () => import('./queue/case-queue').then((m) => m.CaseQueue),
  },
  { path: 'disputes/:id', loadComponent: () => import('./detail/case-detail').then((m) => m.CaseDetail) },
  { path: '', pathMatch: 'full', redirectTo: 'disputes' },
];
