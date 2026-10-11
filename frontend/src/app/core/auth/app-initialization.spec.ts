import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { appConfig } from '../../app.config';
import { SessionState } from './session-state';
describe('Angular application initialization', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [...appConfig.providers, provideHttpClientTesting()] }));
  it('uses the supported initializer to await bootstrap and verified identity', async () => {
    const control = TestBed.inject(HttpTestingController), status = TestBed.inject(ApplicationInitStatus);
    expect(status.done).toBeFalse();
    control.expectOne('/api/v1/auth/csrf').flush({ status: true, error_code: 0, data: { csrfToken: 'fixture', headerName: 'X-CSRF-TOKEN' } });
    for (let i = 0; i < 8; i++) await Promise.resolve();
    expect(status.done).toBeFalse();control.expectOne('/api/v1/auth/me').flush({ status: true, error_code: 0, data: { id: 'fixture', email: 'fixture@example.test', role: 'AGENT' } });
    await status.donePromise;expect(TestBed.inject(SessionState).phase()).toBe('authenticated');control.verify();
  });
  it('completes unavailable startup so a retry screen can render', async () => {
    const control = TestBed.inject(HttpTestingController), status = TestBed.inject(ApplicationInitStatus);
    control.expectOne('/api/v1/auth/csrf').flush(null, { status: 503, statusText: 'Unavailable' });await status.donePromise;
    expect(status.done).toBeTrue();expect(TestBed.inject(SessionState).phase()).toBe('unavailable');control.expectNone('/api/v1/auth/me');control.verify();
  });
});
