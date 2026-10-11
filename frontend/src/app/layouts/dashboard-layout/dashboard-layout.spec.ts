import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { DashboardLayout } from './dashboard-layout';
import { AuthService } from '../../core/auth/auth-service';
import { SessionState } from '../../core/auth/session-state';
import { AuthFailure } from '../../core/auth/auth-contracts';
describe('DashboardLayout', () => {
  let state: SessionState, logout: jasmine.Spy;
  beforeEach(() => {
    logout = jasmine.createSpy('logout');
    TestBed.configureTestingModule({ imports: [DashboardLayout], providers: [provideRouter([]), { provide: AuthService, useFactory: () => ({ state: TestBed.inject(SessionState), logout }) }] });
    state = TestBed.inject(SessionState);state.authenticated({ id: 'fixture', email: 'fixture@example.test', role: 'CUSTOMER' });
  });
  it('hosts its child outlet inside the main landmark with verified role navigation', () => {
    const fixture = TestBed.createComponent(DashboardLayout);fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('main')).query(By.directive(RouterOutlet))).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Customer area');expect(fixture.nativeElement.textContent).not.toContain('Agent area');
    state.authenticated({ id: 'fixture', email: 'fixture@example.test', role: 'AGENT' });fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Agent area');expect(fixture.nativeElement.textContent).not.toContain('Customer area');
  });
  it('locks duplicate sign-out and navigates only after confirmed success', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(DashboardLayout);let finish!: (value: unknown) => void;
    logout.and.returnValue(new Promise(resolve => { finish = resolve; }));
    const pending = fixture.componentInstance.signOut();await fixture.componentInstance.signOut();expect(logout.calls.count()).toBe(1);expect(navigate).not.toHaveBeenCalled();
    state.anonymous();finish({ confirmed: true, csrfReady: false });await pending;expect(navigate).toHaveBeenCalledWith('/login');fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(RouterOutlet))).toBeNull();
  });
  it('hides sensitive content on unconfirmed logout and offers deliberate retry', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);const fixture = TestBed.createComponent(DashboardLayout);
    logout.and.callFake(async () => { state.logoutUnconfirmed();throw new AuthFailure(503); });await fixture.componentInstance.signOut();fixture.detectChanges();
    expect(state.identity()).not.toBeNull();expect(fixture.debugElement.query(By.directive(RouterOutlet))).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sign-out was not confirmed');expect(fixture.nativeElement.textContent).toContain('Try again');expect(navigate).not.toHaveBeenCalled();
    logout.and.callFake(async () => { state.anonymous();return { confirmed: true, csrfReady: true }; });await fixture.componentInstance.signOut(true);
    expect(logout.calls.mostRecent().args).toEqual([true]);expect(navigate).toHaveBeenCalledWith('/login');
  });
});
