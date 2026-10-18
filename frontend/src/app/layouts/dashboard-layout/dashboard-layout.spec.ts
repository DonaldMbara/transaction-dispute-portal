import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {provideRouter, Router, RouterOutlet} from '@angular/router';
import {DashboardLayout} from './dashboard-layout';
import {AuthService} from '../../core/auth/services/auth-service';
import {SessionState} from '../../core/auth/session-state';
import {AuthFailure} from '../../core/auth/models/auth-models';

@Component({standalone: true, template: ''})
class StubCustomerPage {
}

describe('DashboardLayout', () => {
  let state: SessionState, logout: jasmine.Spy;
  beforeEach(() => {
    logout = jasmine.createSpy('logout');
    TestBed.configureTestingModule({
      imports: [DashboardLayout],
      providers: [
        provideRouter([
          {
            path: 'customer',
            children: [
              {path: 'transactions', component: StubCustomerPage},
              {path: 'transactions/:id', component: StubCustomerPage},
              {path: 'disputes', component: StubCustomerPage},
              {path: 'disputes/:id', component: StubCustomerPage},
            ],
          },
          {path: 'agent', children: []},
        ]),
        {
          provide: AuthService,
          useFactory: () => ({state: TestBed.inject(SessionState), logout}),
        },
      ],
    });
    state = TestBed.inject(SessionState);
    state.authenticated({id: 'fixture', email: 'fixture@example.test', role: 'CUSTOMER'});
  });
  it('hosts its child outlet and renders the customer navigation item beside the brand', async () => {
    const fixture = TestBed.createComponent(DashboardLayout);
    fixture.detectChanges();
    expect(
      fixture.debugElement.query(By.css('main')).query(By.directive(RouterOutlet)),
    ).not.toBeNull();
    const headerStart = fixture.nativeElement.querySelector('.header-start') as HTMLElement;
    expect(headerStart.children[0].classList.contains('brand')).toBeTrue();
    expect(headerStart.children[1].classList.contains('portal-navigation')).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Accounts');
    expect(fixture.nativeElement.textContent).not.toContain('Customer area');
    expect(fixture.nativeElement.textContent).not.toContain('Agent area');
    await TestBed.inject(Router).navigateByUrl('/customer');
    fixture.detectChanges();
    const accountsLink = fixture.nativeElement.querySelector(
      '.portal-navigation a[routerLink="/customer"]',
    ) as HTMLAnchorElement;
    expect(accountsLink.classList.contains('is-active')).toBeTrue();
    expect(accountsLink.getAttribute('aria-current')).toBe('page');
    await TestBed.inject(Router).navigateByUrl('/customer/transactions');
    fixture.detectChanges();
    const transactionsLink = fixture.nativeElement.querySelector(
      '.portal-navigation a[routerLink="/customer/transactions"]',
    ) as HTMLAnchorElement;
    expect(transactionsLink.classList.contains('is-active')).toBeTrue();
    expect(transactionsLink.getAttribute('aria-current')).toBe('page');
    expect(accountsLink.classList.contains('is-active')).toBeFalse();
    const disputesLink = fixture.nativeElement.querySelector(
      '.portal-navigation a[routerLink="/customer/disputes"]',
    ) as HTMLAnchorElement;
    expect(disputesLink).toBeTruthy();
    await TestBed.inject(Router).navigateByUrl(
      '/customer/disputes/10000000-0000-0000-0000-000000000001',
    );
    fixture.detectChanges();
    expect(disputesLink.classList.contains('is-active')).toBeTrue();
    expect(disputesLink.getAttribute('aria-current')).toBe('page');
    await TestBed.inject(Router).navigateByUrl(
      '/customer/transactions/10000000-0000-0000-0000-000000000001',
    );
    fixture.detectChanges();
    expect(transactionsLink.classList.contains('is-active')).toBeTrue();
    expect(transactionsLink.getAttribute('aria-current')).toBe('page');
    expect(getComputedStyle(fixture.nativeElement.querySelector('.brand')).color).toBe(
      'rgb(58, 58, 58)',
    );
    state.authenticated({id: 'fixture', email: 'fixture@example.test', role: 'AGENT'});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Agent workspace');
    expect(fixture.nativeElement.textContent).not.toContain('Accounts');
    expect(fixture.nativeElement.textContent).not.toContain('Transactions');
    expect(fixture.nativeElement.textContent).not.toContain('Disputes');
    const customerSignOut = fixture.nativeElement.querySelector('.sign-out') as HTMLButtonElement;
    const customerSignOutStyle = getComputedStyle(customerSignOut);
    const customerStyle = {
      backgroundColor: customerSignOutStyle.backgroundColor,
      color: customerSignOutStyle.color,
      borderTopWidth: customerSignOutStyle.borderTopWidth,
      height: customerSignOutStyle.height,
    };
    await TestBed.inject(Router).navigateByUrl('/agent');
    fixture.detectChanges();
    const agentSignOut = fixture.nativeElement.querySelector('.sign-out') as HTMLButtonElement;
    const agentSignOutStyle = getComputedStyle(agentSignOut);
    expect(agentSignOutStyle.backgroundColor).toBe(customerStyle.backgroundColor);
    expect(agentSignOutStyle.color).toBe(customerStyle.color);
    expect(agentSignOutStyle.borderTopWidth).toBe(customerStyle.borderTopWidth);
    expect(agentSignOutStyle.height).toBe(customerStyle.height);
  });
  it('locks duplicate sign-out and navigates only after confirmed success', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(DashboardLayout);
    let finish!: (value: unknown) => void;
    logout.and.returnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const pending = fixture.componentInstance.signOut();
    await fixture.componentInstance.signOut();
    expect(logout.calls.count()).toBe(1);
    expect(navigate).not.toHaveBeenCalled();
    state.anonymous();
    finish({confirmed: true, csrfReady: false});
    await pending;
    expect(navigate).toHaveBeenCalledWith('/login');
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(RouterOutlet))).toBeNull();
  });
  it('hides sensitive content on unconfirmed logout and offers deliberate retry', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(DashboardLayout);
    logout.and.callFake(async () => {
      state.logoutUnconfirmed();
      throw new AuthFailure(503);
    });
    await fixture.componentInstance.signOut();
    fixture.detectChanges();
    expect(state.identity()).not.toBeNull();
    expect(fixture.debugElement.query(By.directive(RouterOutlet))).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sign-out was not confirmed');
    expect(fixture.nativeElement.textContent).toContain('Try again');
    expect(navigate).not.toHaveBeenCalled();
    logout.and.callFake(async () => {
      state.anonymous();
      return {confirmed: true, csrfReady: true};
    });
    await fixture.componentInstance.signOut(true);
    expect(logout.calls.mostRecent().args).toEqual([true]);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
