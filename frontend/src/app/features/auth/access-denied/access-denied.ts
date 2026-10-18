import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-access-denied',
  standalone: true,
  imports: [RouterLink],
  template:
    '<main><h1>Access denied</h1><p>You do not have permission to view this area.</p><a routerLink="/">Return to portal</a></main>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessDenied {
}
