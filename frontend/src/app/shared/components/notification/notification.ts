import { ChangeDetectionStrategy, Component, Injectable, signal } from '@angular/core';

export interface Notification { message: string; tone: 'success' | 'info'; }

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly currentValue = signal<Notification | null>(null);
  private dismissTimer: ReturnType<typeof setTimeout> | null = null;
  readonly current = this.currentValue.asReadonly();

  show(message: string, tone: Notification['tone'] = 'info'): void {
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
    <section class="notification" [class.notification--success]="notification.tone === 'success'" role="status" aria-live="polite" aria-atomic="true">
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
