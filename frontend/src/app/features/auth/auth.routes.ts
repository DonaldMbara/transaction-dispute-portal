import {Routes} from '@angular/router';

export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    pathMatch: 'full',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
];
