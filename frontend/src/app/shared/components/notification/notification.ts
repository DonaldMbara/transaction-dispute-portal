import { ChangeDetectionStrategy, Component, Injectable, signal } from '@angular/core';

export type NotificationTone = 'success' | 'info' | 'error';
export interface Notification { message: string; tone: NotificationTone; }

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly currentValue = signal<Notification | null>(null);
  private dismissTimer: ReturnType<typeof setTimeout> | null = null;
  readonly current = this.currentValue.asReadonly();

  show(message: string, tone: NotificationTone = 'info'): void {
    this.clearTimer();
    this.currentValue.set({ message, tone });
    this.dismissTimer = setTimeout(() => this.dismiss(), 5_000);
  }
  dismiss(): void { this.clearTimer(); this.currentValue.set(null); }
  private clearTimer(): void {
    if (this.dismissTimer !== null) clearTimeout(this.dismissTimer);
    this.dismissTimer = null;
  }
}

@Component({
  selector: 'app-notification-outlet',
  standalone: true,
  template: `@if (notifications.current(); as notification) {
    <section class="notification" [class.notification--success]="notification.tone === 'success'"
      [class.notification--error]="notification.tone === 'error'"
      [attr.role]="notification.tone === 'error' ? 'alert' : 'status'"
      [attr.aria-live]="notification.tone === 'error' ? 'assertive' : 'polite'" aria-atomic="true">
      <p>{{ notification.message }}</p>
      <button type="button" class="notification__dismiss" (click)="notifications.dismiss()" aria-label="Dismiss notification">Dismiss</button>
    </section>
  }`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationOutlet {
  readonly notifications: NotificationService;
  constructor(notifications: NotificationService) { this.notifications = notifications; }
}
