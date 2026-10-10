import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth-service';
import { landing } from '../../../core/auth/auth-guards';
import { Button } from '../../../shared/components/button/button';
@Component({ selector: 'app-session-unavailable', standalone: true,
  imports: [Button],
  template: '<main><h1>Service unavailable</h1><p role="status">We could not check your session. Please try again.</p><button appButton="primary" type="button" [disabled]="pending()" (click)="retry()">Try again</button></main>',
  changeDetection: ChangeDetectionStrategy.OnPush })
export class SessionUnavailable {
  readonly auth = inject(AuthService); private readonly router = inject(Router); readonly pending = signal(false);
  async retry(): Promise<void> {
    if (this.pending()) return;
    this.pending.set(true);
    try {
      await this.auth.initialize(true);
      if (this.auth.state.phase() !== 'unavailable') await this.router.navigateByUrl(this.auth.state.identity() ? landing(this.auth.state.identity()!.role) : '/login');
    } finally { this.pending.set(false); }
  }
}
