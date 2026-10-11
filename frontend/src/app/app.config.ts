import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection, provideAppInitializer, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth-service';
import { authResponseInterceptor, csrfInterceptor } from './core/auth/auth-interceptors';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withNoXsrfProtection(), withInterceptors([authResponseInterceptor, csrfInterceptor])),
    provideAppInitializer(() => inject(AuthService).initialize())
  ]
};
