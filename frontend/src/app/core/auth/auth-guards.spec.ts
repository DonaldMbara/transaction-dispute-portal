import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from './auth-service';
import { SessionState } from './session-state';
import { landing, safeReturnUrl } from './auth-guards';
import { routes } from '../../app.routes';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
describe('Session route boundaries', () => {
  let state: SessionState;
  let initialize: jasmine.Spy;
  beforeEach(() => {
    initialize = jasmine.createSpy('initialize').and.resolveTo();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useFactory: () => ({ state: TestBed.inject(SessionState), initialize }),
        },
      ],
    });
    state = TestBed.inject(SessionState);
  });
  it('blocks anonymous protected routes and role content', async () => {
    state.anonymous();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/customer');
    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fcustomer');
    expect(harness.routeNativeElement?.querySelector('app-customer')).toBeNull();
  });
  it('denies the wrong role before its component loads', async () => {
    state.authenticated({ id: 'fixture', email: 'fixture@example.test', role: 'CUSTOMER' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/agent');
    expect(TestBed.inject(Router).url).toBe('/access-denied');
    expect(harness.routeNativeElement?.textContent).toContain('Access denied');
    expect(harness.routeNativeElement?.querySelector('app-agent') ?? null).toBeNull();
  });
  it('waits for verified identity and restores the correct landing', async () => {
    let resolve!: () => void;
    initialize.and.returnValue(
      new Promise<void>((done) => {
        resolve = done;
      }),
    );
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/agent');
    await Promise.resolve();
    expect(harness.routeNativeElement?.querySelector('app-agent') ?? null).toBeNull();
    state.authenticated({ id: 'fixture', email: 'fixture@example.test', role: 'AGENT' });
    resolve();
    await navigation;
    expect(TestBed.inject(Router).url).toBe('/agent/disputes');
    expect(harness.routeNativeElement?.textContent).toContain('Case queue');
  });
  it('renders retryable initialization failure without protected content', async () => {
    state.unavailable();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/customer');
    expect(TestBed.inject(Router).url).toBe('/session-unavailable');
    expect(harness.routeNativeElement?.textContent).toContain('Try again');
  });
  it('allowlists only exact local role landing return URLs', () => {
    for (const role of ['CUSTOMER', 'AGENT'] as const) {
      for (const candidate of [
        'https://evil.example',
        '//evil.example',
        '/customer/../agent',
        '/customer?role=AGENT',
        '/agent',
        '/customer',
        '/%2F/evil',
        null,
      ])
        expect(safeReturnUrl(candidate, role)).toBe(landing(role));
    }
  });
});
