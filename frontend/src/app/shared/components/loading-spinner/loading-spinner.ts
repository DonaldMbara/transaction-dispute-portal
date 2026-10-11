import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  template: `<span class="loading" [class.loading--small]="size() === 'small'" role="status" aria-live="polite">
    <span class="loading__icon" aria-hidden="true"></span>
    <span>{{ label() }}</span>
  </span>`,
  styles: [
    `:host{display:block}.loading{display:flex;min-height:48px;align-items:center;justify-content:center;gap:10px;color:var(--color-text,#3A3A3A);font-size:14px;font-weight:650}.loading__icon{width:22px;height:22px;flex:0 0 22px;border:3px solid var(--color-primary-soft,#EAF1FF);border-top-color:var(--color-primary,#2F70EF);border-radius:50%;animation:spin .75s linear infinite}.loading--small{min-height:32px;font-size:13px}.loading--small .loading__icon{width:18px;height:18px;flex-basis:18px;border-width:2px}@keyframes spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.loading__icon{animation:none;border-color:var(--color-primary,#2F70EF)}}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoadingSpinner {
  readonly label = input('Loading');
  readonly size = input<'regular' | 'small'>('regular');
}
