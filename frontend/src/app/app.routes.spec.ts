import {TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {routes} from './app.routes';
import {AuthService} from './core/auth/services/auth-service';
import {SessionState} from './core/auth/session-state';
import {AccountApi} from './features/customer/accounts/serviecs/account-api';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {of} from 'rxjs';

describe('Foundation routing', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useFactory: () => ({
            state: TestBed.inject(SessionState),
            initialize: () => Promise.resolve(),
          }),
        },
        {
          provide: AccountApi,
          useValue: {list: () => of({items: [], page: 0, size: 0, total: 0})},
        },
      ],
    }),
  );

  for (const [url, heading, inDashboard] of [
    ['/login', 'Welcome back', false],
    ['/customer', 'Accounts', true],
    ['/agent', 'Case queue', true],
  ] as const) {
    it(`renders ${url} in the correct layout`, async () => {
      TestBed.inject(SessionState).authenticated({
        id: 'fixture',
        email: 'fixture@example.test',
        role: url === '/agent' ? 'AGENT' : 'CUSTOMER',
      });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      const element = harness.routeNativeElement;
      expect(element?.querySelector('h1')?.textContent).toBe(heading);
      expect(element?.tagName.toLowerCase() === 'app-dashboard-layout').toBe(inDashboard);
      expect(element?.querySelectorAll('main').length).toBe(1);
      expect(TestBed.inject(Router).url).toBe(url === '/agent' ? '/agent/disputes' : url);
    });
  }

  it('resolves root and unknown routes to the verified role landing', async () => {
    TestBed.inject(SessionState).authenticated({
      id: 'fixture',
      email: 'fixture@example.test',
      role: 'AGENT',
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/agent/disputes');
    await harness.navigateByUrl('/unknown/path');
    expect(TestBed.inject(Router).url).toBe('/agent/disputes');
  });

  it('resolves anonymous root to login without rendering welcome content', async () => {
    TestBed.inject(SessionState).anonymous();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Welcome back');
  });

  it('routes an unavailable initial session check to its retry page', async () => {
    TestBed.inject(SessionState).unavailable();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/session-unavailable');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe(
      'Service unavailable',
    );
  });

  it('navigates between sibling features without the auth route consuming them', async () => {
    const harness = await RouterTestingHarness.create('/login');
    for (const [url, heading] of [
      ['/customer', 'Accounts'],
      ['/agent', 'Agent'],
      ['/login', 'Welcome back'],
    ]) {
      TestBed.inject(SessionState).authenticated({
        id: 'fixture',
        email: 'fixture@example.test',
        role: url === '/agent' ? 'AGENT' : 'CUSTOMER',
      });
      await harness.navigateByUrl(url);
      expect(TestBed.inject(Router).url).toBe(url === '/agent' ? '/agent/disputes' : url);
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe(
        url === '/agent' ? 'Case queue' : heading,
      );
    }
  });
});
