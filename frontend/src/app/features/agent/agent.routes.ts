import { Routes } from '@angular/router';

export const AGENT_ROUTES: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () =>
    import('./agent').then(m => m.Agent) }
];
