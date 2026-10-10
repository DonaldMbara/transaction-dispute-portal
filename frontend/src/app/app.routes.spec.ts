import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth-service';
import { SessionState } from './core/auth/session-state';

describe('Foundation routing', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter(routes), { provide: AuthService, useFactory: () => ({ state: TestBed.inject(SessionState), initialize: () => Promise.resolve() }) }] }));

  for (const [url, heading, inDashboard] of [
    ['/login', 'Sign in', false],
    ['/customer', 'Customer', true],
    ['/agent', 'Agent', true]
  ] as const) {
    it(`renders ${url} in the correct layout`, async () => {
      TestBed.inject(SessionState).authenticated({ id: 'fixture', email: 'fixture@example.test', role: url === '/agent' ? 'AGENT' : 'CUSTOMER' });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      const element = harness.routeNativeElement;
      expect(element?.querySelector('h1')?.textContent).toBe(heading);
      expect(element?.tagName.toLowerCase() === 'app-dashboard-layout').toBe(inDashboard);
      expect(element?.querySelectorAll('main').length).toBe(1);
      expect(TestBed.inject(Router).url).toBe(url);
    });
  }

  it('resolves root and unknown routes to the verified role landing', async () => {
    TestBed.inject(SessionState).authenticated({ id: 'fixture', email: 'fixture@example.test', role: 'AGENT' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/agent');
    await harness.navigateByUrl('/unknown/path');
    expect(TestBed.inject(Router).url).toBe('/agent');
  });

  it('resolves anonymous root to login without rendering welcome content', async () => {
    TestBed.inject(SessionState).anonymous();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Sign in');
  });

  it('routes an unavailable initial session check to its retry page', async () => {
    TestBed.inject(SessionState).unavailable();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/session-unavailable');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Service unavailable');
  });

  it('navigates between sibling features without the auth route consuming them', async () => {
    const harness = await RouterTestingHarness.create('/login');
    for (const [url, heading] of [['/customer', 'Customer'], ['/agent', 'Agent'], ['/login', 'Sign in']]) {
      TestBed.inject(SessionState).authenticated({ id: 'fixture', email: 'fixture@example.test', role: url === '/agent' ? 'AGENT' : 'CUSTOMER' });
      await harness.navigateByUrl(url);
      expect(TestBed.inject(Router).url).toBe(url);
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe(heading);
    }
  });
});
