import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { safeMessage } from '../../core/auth/auth-contracts';
import { NotificationService } from '../../shared/components/notification/notification';
import { Button } from '../../shared/components/button/button';
import { AsyncActionStatus } from '../../shared/models/async-state';
@Component({ selector: 'app-dashboard-layout', standalone: true, imports: [RouterOutlet, RouterLink, RouterLinkActive, Button],
  templateUrl: './dashboard-layout.html', styleUrl: './dashboard-layout.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class DashboardLayout {
  readonly auth = inject(AuthService);private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  readonly actionState = signal<AsyncActionStatus>('idle');readonly pending = computed(() => this.actionState() === 'busy');readonly message = signal('');
  isCustomerRoute(): boolean {
    const path = this.router.url.split('?')[0].replace(/\/+$/u, '');
    return path === '/customer' || path.startsWith('/customer/');
  }
  async signOut(retry = false): Promise<void> {
    if (this.pending()) return;
    this.actionState.set('busy');this.message.set('');
    try { await this.auth.logout(retry);this.notifications.show('You are signed out.', 'success');await this.router.navigateByUrl('/login');this.actionState.set('succeeded'); }
    catch (failure: unknown) {
      this.actionState.set('failed');
      const message = 'Sign-out was not confirmed. ' + safeMessage(failure);
      this.message.set(message);
      this.notifications.show(message, 'error');
    }
    finally { if (this.actionState() === 'busy') this.actionState.set('failed'); }
  }
}
