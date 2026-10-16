import { TestBed } from '@angular/core/testing';
import {
  provideHttpClient,
  withInterceptors,
  withNoXsrfProtection,
  HttpClient,
} from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth-service';
import { authResponseInterceptor, csrfInterceptor } from './auth-interceptors';
import { CsrfState, SessionState } from './session-state';
import { AuthFailure } from './auth-models';
const identity = { id: 'fixture-id', email: 'fixture@example.test', role: 'CUSTOMER' as const };
const ok = <T>(data: T) => ({ status: true, data, error_code: 0 });
const tick = async () => {
  for (let i = 0; i < 8; i++) await Promise.resolve();
};
describe('Authentication service and HTTP boundary', () => {
  let service: AuthService,
    http: HttpClient,
    control: HttpTestingController,
    state: SessionState,
    csrf: CsrfState;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withNoXsrfProtection(),
          withInterceptors([authResponseInterceptor, csrfInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    control = TestBed.inject(HttpTestingController);
    state = TestBed.inject(SessionState);
    csrf = TestBed.inject(CsrfState);
  });
  afterEach(() => control.verify());
  it('bootstraps single-flight then restores identity without polling', async () => {
    const one = service.initialize(),
      two = service.initialize();
    expect(one).toBe(two);
    control
      .expectOne('/api/v1/auth/csrf')
      .flush(ok({ csrfToken: 'masked-fixture', headerName: 'X-CSRF-TOKEN' }));
    await tick();
    expect(state.phase()).toBe('unresolved');
    control.expectOne('/api/v1/auth/me').flush(ok(identity));
    await one;
    expect(state.identity()).toEqual(identity);
    await service.initialize();
    control.expectNone('/api/v1/auth/me');
  });
  it('blocks unsafe missing-token requests and sends only same-origin unsafe API headers', () => {
    http.post('/api/v1/write', {}).subscribe({ error: (error) => expect(error.code).toBe(1010) });
    control.expectNone('/api/v1/write');
    csrf.set('masked-fixture');
    for (const url of [
      '/api/v1/write',
      window.location.origin + '/api/v1/write',
      '/api/v10/write',
      '/other',
      'https://unrelated.example/api/v1/write',
    ]) {
      http.post(url, {}).subscribe();
      const request = control.expectOne(url);
      expect(request.request.headers.has('X-CSRF-TOKEN')).toBe(
        url.includes('/api/v1/write') && !url.startsWith('https://unrelated'),
      );
      expect(request.request.headers.has('Authorization')).toBeFalse();
      request.flush(ok(null));
    }
    for (const method of ['GET', 'HEAD', 'OPTIONS']) {
      http.request(method, '/api/v1/safe').subscribe();
      const req = control.expectOne('/api/v1/safe');
      expect(req.request.headers.has('X-CSRF-TOKEN')).toBeFalse();
      req.flush(ok(null));
    }
  });
  it('clears identity on 401, retains it on 403/503, and sanitizes proxy/network errors', () => {
    for (const status of [403, 503, 401, 502, 0]) {
      state.authenticated(identity);
      csrf.set('fixture');
      http.get('/api/v1/private').subscribe({
        error: (error: AuthFailure) => {
          expect(error.message).not.toContain('private diagnostics');
          expect(error.status).toBe(status);
        },
      });
      const req = control.expectOne('/api/v1/private');
      if (status === 0) req.error(new ProgressEvent('error'));
      else req.flush('private diagnostics', { status, statusText: 'Fixture error' });
      expect(state.identity()).toEqual(status === 401 ? null : identity);
    }
  });
  it('keeps confirmed login when transition bootstrap fails and never replays credentials', async () => {
    csrf.set('fixture');
    const result = service.login(' Mixed@Example.test ', ' exact password 123 ');
    const req = control.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({
      email: ' Mixed@Example.test ',
      password: ' exact password 123 ',
    });
    req.flush(ok(identity));
    await tick();
    control.expectOne('/api/v1/auth/csrf').flush(null, { status: 503, statusText: 'Unavailable' });
    expect(await result).toEqual({ confirmed: true, csrfReady: false });
    expect(state.identity()).toEqual(identity);
    expect(csrf.token()).toBeNull();
    control.expectNone('/api/v1/auth/login');
  });
  it('rejects stale in-flight responses after a new authentication generation', () => {
    http
      .get('/api/v1/private')
      .subscribe({
        next: () => fail('stale data delivered'),
        error: (error) => expect(error.status).toBe(409),
      });
    state.authenticated(identity);
    control.expectOne('/api/v1/private').flush(ok({ private: true }));
    expect(state.identity()).toEqual(identity);
  });
  it('retains unconfirmed logout and retries only deliberately after fresh bootstrap', async () => {
    state.authenticated(identity);
    csrf.set('fixture');
    const failed = service.logout().catch((error) => error);
    control.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    await failed;
    expect(state.phase()).toBe('logout-unconfirmed');
    expect(state.identity()).toEqual(identity);
    control.expectNone('/api/v1/auth/logout');
    const retry = service.logout(true);
    control
      .expectOne('/api/v1/auth/csrf')
      .flush(ok({ csrfToken: 'fresh', headerName: 'X-CSRF-TOKEN' }));
    await tick();
    control.expectOne('/api/v1/auth/logout').flush(ok(null));
    await tick();
    control.expectOne('/api/v1/auth/csrf').flush(null, { status: 503, statusText: 'Unavailable' });
    expect(await retry).toEqual({ confirmed: true, csrfReady: false });
    expect(state.identity()).toBeNull();
    expect(state.phase()).toBe('anonymous');
    expect(state.notice()).toContain('signed out');
  });
  it('rejects stale 401 without clearing a newer verified identity', () => {
    http.get('/api/v1/private').subscribe({ error: (error) => expect(error.status).toBe(409) });
    state.authenticated(identity);
    control.expectOne('/api/v1/private').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(state.identity()).toEqual(identity);
  });
  it('clears stale CSRF without clearing identity or automatically replaying', () => {
    state.authenticated(identity);
    csrf.set('stale');
    http.post('/api/v1/write', {}).subscribe({ error: (error) => expect(error.code).toBe(1010) });
    control
      .expectOne('/api/v1/write')
      .flush({ error_code: 1010 }, { status: 403, statusText: 'Forbidden' });
    expect(csrf.token()).toBeNull();
    expect(state.identity()).toEqual(identity);
    control.expectNone('/api/v1/write');
    control.expectNone('/api/v1/auth/csrf');
  });
  it('handles anonymous startup after exactly one bootstrap and identity request', async () => {
    const init = service.initialize();
    control
      .expectOne('/api/v1/auth/csrf')
      .flush(ok({ csrfToken: 'fresh', headerName: 'X-CSRF-TOKEN' }));
    await tick();
    control.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await init;
    expect(state.phase()).toBe('anonymous');
    expect(state.identity()).toBeNull();
    control.expectNone('/api/v1/auth/csrf');
  });
  it('finishes failed initialization as retryable, without protected identity', async () => {
    const result = service.initialize();
    control.expectOne('/api/v1/auth/csrf').error(new ProgressEvent('error'));
    await result;
    expect(state.phase()).toBe('unavailable');
    expect(state.identity()).toBeNull();
    control.expectNone('/api/v1/auth/me');
  });
});
