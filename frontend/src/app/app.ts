import {Component, effect, inject} from '@angular/core';
import {Router, RouterOutlet} from '@angular/router';
import {AuthService} from './core/auth/services/auth-service';
import {SessionUnavailable} from './features/auth/session-unavailable/session-unavailable';
import {NotificationOutlet} from './shared/components/notification/notification';
import {HttpProgressIndicator} from './core/http/http-progress-indicator';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SessionUnavailable, NotificationOutlet, HttpProgressIndicator],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      if (this.auth.state.phase() === 'anonymous' && this.isProtectedRoute())
        void this.router.navigateByUrl('/login');
    });
  }

  isProtectedRoute(): boolean {
    return /^\/(customer|agent)(?:[/?#]|$)/.test(this.router.url);
  }
}
