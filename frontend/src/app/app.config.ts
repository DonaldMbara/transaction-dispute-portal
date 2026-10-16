import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
  provideAppInitializer,
  inject,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth-service';
import { authResponseInterceptor, csrfInterceptor } from './core/auth/auth-interceptors';
import { httpProgressInterceptor } from './core/http/http-progress';
import { apiErrorInterceptor } from './core/http/api-error-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
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
