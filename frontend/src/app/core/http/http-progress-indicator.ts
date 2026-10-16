import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { HttpProgress } from './http-progress';

@Component({
  selector: 'app-http-progress',
  standalone: true,
  template: `@if (progress.visible()) {
      <div class="progress-track" aria-hidden="true"><span></span></div>
    }
    <span class="visually-hidden" role="status" aria-live="polite" aria-atomic="true">{{
      progress.visible() ? 'Loading…' : ''
    }}</span>`,
  styles: [
    `
      .progress-track {
        position: fixed;
        z-index: 10000;
        inset: 0 0 auto;
        height: 3px;
        overflow: hidden;
        background: var(--color-primary-soft, #eaf1ff);
      }
      .progress-track span {
        display: block;
        width: 35%;
        height: 100%;
        background: var(--color-primary, #2f70ef);
        animation: progress-slide 1s ease-in-out infinite alternate;
      }
      @keyframes progress-slide {
        from {
          transform: translateX(-10%);
        }
        to {
          transform: translateX(210%);
        }
      }
      .visually-hidden {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
      }
      @media (prefers-reduced-motion: reduce) {
        .progress-track span {
          width: 100%;
          animation: none;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HttpProgressIndicator {
  readonly progress = inject(HttpProgress);
}
