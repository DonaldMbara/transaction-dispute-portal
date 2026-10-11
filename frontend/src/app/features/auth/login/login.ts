import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth-service';
import { safeReturnUrl } from '../../../core/auth/auth-guards';
import { AuthFailure, safeMessage } from '../../../core/auth/auth-contracts';
import { CsrfState } from '../../../core/auth/session-state';
import { Button } from '../../../shared/components/button/button';
import { AsyncActionStatus } from '../../../shared/models/async-state';

export function passwordAdmission(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value;
  let points = 0;
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(++index);
      if (!(next >= 0xdc00 && next <= 0xdfff)) return { passwordPolicy: true };
    } else if (code >= 0xdc00 && code <= 0xdfff) return { passwordPolicy: true };
    points++;
  }
  return points >= 1 && points <= 128 ? null : { passwordAdmission: true };
}
function loginEmail(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.replace(/^[\t-\r ]+|[\t-\r ]+$/g, '').toLowerCase();
  return !value || value.length > 254 ? { email: true } : Validators.email(new FormControl(value));
}
@Component({ selector: 'app-login', standalone: true, imports: [ReactiveFormsModule, Button],
  templateUrl: './login.html', styleUrl: './login.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class Login implements OnDestroy {
  readonly auth = inject(AuthService); readonly csrf = inject(CsrfState);
  private readonly router = inject(Router); private readonly route = inject(ActivatedRoute);
  readonly actionState = signal<AsyncActionStatus>('idle'); readonly pending = computed(() => this.actionState() === 'busy');
  readonly message = signal(''); readonly confirmed = signal(false);
  readonly recoveryRequired = signal(false); readonly passwordVisible = signal(false);
  readonly form = new FormGroup({ email: new FormControl('', { nonNullable: true, validators: [loginEmail] }),
    password: new FormControl('', { nonNullable: true, validators: [passwordAdmission] }) });
  async submit(): Promise<void> {
    if (this.pending() || this.confirmed()) return;
    this.form.markAllAsTouched();if (this.form.invalid) return;
    this.actionState.set('busy');this.message.set('');this.recoveryRequired.set(false);
    const { email, password } = this.form.getRawValue();
    try {
      const result = await this.auth.login(email, password);
      this.confirmed.set(true);this.form.controls.password.reset();
      if (result.csrfReady) await this.navigate();
      else { this.message.set('You are signed in. Please try again to finish setting up this session.');this.recoveryRequired.set(true); }
      this.actionState.set('succeeded');
    } catch (failure: unknown) {
      this.actionState.set('failed');
      this.recoveryRequired.set(this.requiresRecovery(failure));
      this.message.set(this.recoveryRequired() ? 'We could not prepare a secure sign-in. Try again.' : this.loginFailureMessage(failure));
    }
    finally { if (this.actionState() === 'busy') this.actionState.set('failed'); }
  }
  togglePassword(): void { this.passwordVisible.update(visible => !visible); }
  fieldInvalid(name: 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }
  async retrySecurity(): Promise<void> {
    if (this.pending()) return;
    this.actionState.set('busy');
    try {
      await this.auth.bootstrap();this.message.set('');this.recoveryRequired.set(false);
      if (this.confirmed()) await this.navigate();
      this.actionState.set('succeeded');
    } catch (failure: unknown) { this.actionState.set('failed');this.message.set('We could not restore secure sign-in. Try again.');this.recoveryRequired.set(true); }
    finally { if (this.actionState() === 'busy') this.actionState.set('failed'); }
  }
  private async navigate(): Promise<void> {
    const identity = this.auth.state.identity();
    if (identity) await this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'), identity.role));
  }
  private requiresRecovery(failure: unknown): boolean {
    return failure instanceof AuthFailure && (failure.code === 1010 || failure.status === 0 || failure.status >= 500);
  }
  private loginFailureMessage(failure: unknown): string {
    if (failure instanceof AuthFailure && (failure.status === 400 || failure.status === 401)) {
      return 'We could not sign you in. Check your email and password and try again.';
    }
    return safeMessage(failure);
  }
  ngOnDestroy(): void { this.form.controls.password.reset(); }
}
