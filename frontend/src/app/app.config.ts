import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideHttpClient, withInterceptors, withNoXsrfProtection} from '@angular/common/http';

import {routes} from './app.routes';
import {AuthService} from './core/auth/services/auth-service';
import {authResponseInterceptor, csrfInterceptor} from './core/auth/interceptors/auth-interceptors';
import {httpProgressInterceptor} from './core/http/http-progress';
import {apiErrorInterceptor} from './core/http/interceptors/api-error-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({eventCoalescing: true}),
    provideRouter(routes),
    provideHttpClient(
      withNoXsrfProtection(),
      withInterceptors([
        httpProgressInterceptor,
        authResponseInterceptor,
        apiErrorInterceptor,
        csrfInterceptor,
      ]),
    ),
    provideAppInitializer(() => inject(AuthService).initialize()),
  ],
};
