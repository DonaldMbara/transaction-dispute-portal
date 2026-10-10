import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Foundation routing', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter(routes)] }));

  for (const [url, heading, inDashboard] of [
    ['/', 'Transaction Dispute Portal', false],
    ['/login', 'Sign in', false],
    ['/customer', 'Customer', true],
    ['/agent', 'Agent', true]
  ] as const) {
    it(`renders ${url} in the correct layout`, async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      const element = harness.routeNativeElement;
      expect(element?.querySelector('h1')?.textContent).toBe(heading);
      expect(element?.tagName.toLowerCase() === 'app-dashboard-layout').toBe(inDashboard);
      expect(element?.querySelectorAll('main').length).toBe(1);
      expect(TestBed.inject(Router).url).toBe(url);
    });
  }

  it('redirects unknown routes to welcome', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/unknown/path');
    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Transaction Dispute Portal');
  });

  it('navigates between sibling features without the auth route consuming them', async () => {
    const harness = await RouterTestingHarness.create('/login');
    for (const [url, heading] of [['/customer', 'Customer'], ['/agent', 'Agent'], ['/login', 'Sign in']]) {
      await harness.navigateByUrl(url);
      expect(TestBed.inject(Router).url).toBe(url);
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe(heading);
    }
  });
});
