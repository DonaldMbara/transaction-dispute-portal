import {inject, Injectable, signal} from '@angular/core';
import {HttpInterceptorFn} from '@angular/common/http';
import {finalize} from 'rxjs';
import {isOwnApi} from '../auth/interceptors/auth-interceptors';

@Injectable({providedIn: 'root'})
export class HttpProgress {
  readonly visible = signal(false);
  private inFlight = 0;
  private shownAt = 0;
  private showTimer?: ReturnType<typeof setTimeout>;
  private hideTimer?: ReturnType<typeof setTimeout>;

  requestStarted(): void {
    this.inFlight++;
    if (this.hideTimer) clearTimeout(this.hideTimer);
    this.hideTimer = undefined;
    if (this.inFlight === 1 && !this.visible()) {
      this.showTimer = setTimeout(() => {
        this.showTimer = undefined;
        if (this.inFlight > 0) {
          this.shownAt = Date.now();
          this.visible.set(true);
        }
      }, 150);
    }
  }

  requestFinished(): void {
    if (this.inFlight === 0) return;
    this.inFlight--;
    if (this.inFlight > 0) return;
    if (this.showTimer) clearTimeout(this.showTimer);
    this.showTimer = undefined;
    if (!this.visible()) return;
    const remaining = Math.max(0, 300 - (Date.now() - this.shownAt));
    this.hideTimer = setTimeout(() => {
      this.hideTimer = undefined;
      if (this.inFlight === 0) this.visible.set(false);
    }, remaining);
  }
}

export const httpProgressInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isOwnApi(request.url)) return next(request);
  const progress = inject(HttpProgress);
  progress.requestStarted();
  return next(request).pipe(finalize(() => progress.requestFinished()));
};
