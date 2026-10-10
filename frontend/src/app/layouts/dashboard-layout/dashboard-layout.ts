import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { safeMessage } from '../../core/auth/auth-contracts';
import { Button } from '../../shared/components/button/button';
import { NotificationService } from '../../shared/components/notification/notification';
@Component({ selector: 'app-dashboard-layout', standalone: true, imports: [RouterOutlet, RouterLink, Button],
  templateUrl: './dashboard-layout.html', styleUrl: './dashboard-layout.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class DashboardLayout {
  readonly auth = inject(AuthService);private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  readonly pending = signal(false);readonly message = signal('');
  async signOut(retry = false): Promise<void> {
    if (this.pending()) return;
    this.pending.set(true);this.message.set('');
    try { await this.auth.logout(retry);this.notifications.show('You are signed out.', 'success');await this.router.navigateByUrl('/login'); }
    catch (failure: unknown) { this.message.set('Sign-out was not confirmed. ' + safeMessage(failure)); }
    finally { this.pending.set(false); }
  }
}
