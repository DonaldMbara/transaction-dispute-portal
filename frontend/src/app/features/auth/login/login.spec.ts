import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { Login, passwordAdmission } from './login';
import { AuthService } from '../../../core/auth/auth-service';
import { SessionState, CsrfState } from '../../../core/auth/session-state';
import { AuthFailure } from '../../../core/auth/auth-contracts';
describe('Login', () => {
  let login: jasmine.Spy, bootstrap: jasmine.Spy, state: SessionState;
  beforeEach(() => {
    login = jasmine.createSpy('login');bootstrap = jasmine.createSpy('bootstrap').and.resolveTo();
    TestBed.configureTestingModule({ imports: [Login], providers: [provideRouter([]), { provide: AuthService, useFactory: () => ({ login, bootstrap, state: TestBed.inject(SessionState) }) }] });
    state = TestBed.inject(SessionState);TestBed.inject(CsrfState).set('fixture');
  });
  const setup = () => {
    const fixture = TestBed.createComponent(Login);fixture.detectChanges();
    fixture.componentInstance.form.setValue({ email: ' Mixed@Example.test ', password: ' exact password 123 ' });return fixture;
  };
  it('uses login admission bounds rather than the password-creation minimum', () => {
    for (const value of ['a', ' '.repeat(8), '😀'.repeat(128), 'é'.repeat(128)]) expect(passwordAdmission(new FormControl(value, { nonNullable: true }))).toBeNull();
    for (const value of ['', 'a'.repeat(129), '😀'.repeat(129), '\ud800', '\udc00']) expect(passwordAdmission(new FormControl(value, { nonNullable: true }))).not.toBeNull();
  });
  it('provides labelled keyboard-submit controls and accessible pending/errors', async () => {
    const fixture = setup();const element: HTMLElement = fixture.nativeElement;
    for (const id of ['login-email', 'login-password']) expect(element.querySelector(`label[for="${id}"]`)).not.toBeNull();
    expect(element.querySelector('input[type="password"]')?.getAttribute('autocomplete')).toBe('current-password');
    expect(element.querySelector('input[type="password"]')?.hasAttribute('maxlength')).toBeFalse();
    expect(element.querySelector('button[type="submit"]')).not.toBeNull();expect(element.querySelector('[role="alert"]')).toBeNull();
    login.and.rejectWith(new AuthFailure(401));element.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));await fixture.whenStable();fixture.detectChanges();
    expect(element.textContent).toContain('We could not sign you in. Check your email and password and try again.');
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(element.textContent).not.toContain('Try again');
    const toggle = element.querySelector<HTMLButtonElement>('.password-control__toggle')!;
    toggle.click();fixture.detectChanges();expect(element.querySelector<HTMLInputElement>('#login-password')!.type).toBe('text');
    expect(toggle.getAttribute('aria-label')).toBe('Hide password');
  });
  it('sends exact credentials once and routes each server-established role', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    for (const role of ['CUSTOMER', 'AGENT'] as const) {
      const fixture = setup();state.authenticated({ id: 'fixture', email: 'fixture@example.test', role });
      let finish!: (value: { confirmed: true; csrfReady: boolean }) => void;
      login.and.returnValue(new Promise(resolve => { finish = resolve; }));const submission = fixture.componentInstance.submit();await fixture.componentInstance.submit();
      expect(login.calls.mostRecent().args).toEqual([' Mixed@Example.test ', ' exact password 123 ']);expect(fixture.componentInstance.pending()).toBeTrue();
      finish({ confirmed: true, csrfReady: true });await submission;expect(navigate.calls.mostRecent().args[0]).toBe(role === 'CUSTOMER' ? '/customer' : '/agent');
      expect(fixture.componentInstance.form.controls.password.value).toBe('');fixture.destroy();
    }
    expect(login.calls.count()).toBe(2);
  });
  it('preserves confirmed login after failed token refresh and explicitly retries without credentials', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);const fixture = setup();
    state.authenticated({ id: 'fixture', email: 'fixture@example.test', role: 'CUSTOMER' });login.and.resolveTo({ confirmed: true, csrfReady: false });
    await fixture.componentInstance.submit();fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('You are signed in');expect(navigate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();await fixture.componentInstance.retrySecurity();expect(login.calls.count()).toBe(1);expect(navigate).toHaveBeenCalledWith('/customer');
  });
  it('uses safe messages for every failure and never automatically resubmits', async () => {
    for (const failure of [new AuthFailure(400), new AuthFailure(401), new AuthFailure(403, 1010), new AuthFailure(503), new AuthFailure(502), new AuthFailure(0), new Error('private diagnostics')]) {
      const fixture = setup();login.calls.reset();login.and.rejectWith(failure);await fixture.componentInstance.submit();fixture.detectChanges();
      expect(fixture.componentInstance.message()).not.toContain('private diagnostics');expect(fixture.componentInstance.message()).not.toBe('');expect(login.calls.count()).toBe(1);expect(fixture.componentInstance.pending()).toBeFalse();fixture.destroy();
    }
  });

  it('uses one non-specific message for malformed and incorrect login credentials', async () => {
    const messages: string[] = [];
    for (const failure of [new AuthFailure(400), new AuthFailure(401)]) {
      const fixture = setup();login.and.rejectWith(failure);
      await fixture.componentInstance.submit();messages.push(fixture.componentInstance.message());fixture.destroy();
    }
    expect(messages[0]).toBe('We could not sign you in. Check your email and password and try again.');
    expect(messages[1]).toBe(messages[0]);
  });

  it('shows contextual recovery only when secure token acquisition fails', async () => {
    const fixture = setup();login.and.rejectWith(new AuthFailure(503));
    await fixture.componentInstance.submit();fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Try again');
    expect(fixture.nativeElement.textContent).not.toContain('Refresh security verification');
    expect(login.calls.count()).toBe(1);
  });
});
