import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth-service';
import { landing } from '../../../core/auth/auth-guards';
import { Button } from '../../../shared/components/button/button';
import { AsyncActionStatus } from '../../../shared/models/async-state';
@Component({
  selector: 'app-session-unavailable',
  standalone: true,
  imports: [Button],
  template:
    '<main><h1>Service unavailable</h1><p role="status">We could not check your session. Please try again.</p><button appButton="primary" type="button" [disabled]="pending()" (click)="retry()">Try again</button></main>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionUnavailable {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly actionState = signal<AsyncActionStatus>('idle');
  readonly pending = computed(() => this.actionState() === 'busy');
  async retry(): Promise<void> {
    if (this.pending()) return;
    this.actionState.set('busy');
    try {
      await this.auth.initialize(true);
      if (this.auth.state.phase() !== 'unavailable')
        await this.router.navigateByUrl(
          this.auth.state.identity() ? landing(this.auth.state.identity()!.role) : '/login',
        );
      this.actionState.set('succeeded');
    } catch (failure: unknown) {
      this.actionState.set('failed');
      throw failure;
    } finally {
      if (this.actionState() === 'busy') this.actionState.set('failed');
    }
  }
}
