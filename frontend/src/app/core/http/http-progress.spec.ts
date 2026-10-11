import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HttpProgress, httpProgressInterceptor } from './http-progress';
import { HttpProgressIndicator } from './http-progress-indicator';

describe('HTTP progress indicator', () => {
  let http: HttpClient;
  let testing: HttpTestingController;
  let progress: HttpProgress;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([httpProgressInterceptor])),
      provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpClient);
    testing = TestBed.inject(HttpTestingController);
    progress = TestBed.inject(HttpProgress);
  });

  afterEach(() => testing.verify());

  it('does not flash for a response faster than the display delay', fakeAsync(() => {
    http.get('/api/v1/fast').subscribe();
    const request = testing.expectOne('/api/v1/fast');
    tick(149);
    expect(progress.visible()).toBeFalse();
    request.flush({ status: true });
    tick(500);
    expect(progress.visible()).toBeFalse();
    testing.expectNone('/api/v1/fast');
  }));

  it('shows a slow request and remains visible for at least 300ms after it completes', fakeAsync(() => {
    http.get('/api/v1/slow').subscribe();
    const request = testing.expectOne('/api/v1/slow');
    tick(150);
    expect(progress.visible()).toBeTrue();
    request.flush({ status: true });
    tick(299);
    expect(progress.visible()).toBeTrue();
    tick(1);
    expect(progress.visible()).toBeFalse();
  }));

  it('does not retry a failed API request automatically', fakeAsync(() => {
    http.get('/api/v1/failure').subscribe({ error: () => undefined });
    testing.expectOne('/api/v1/failure').flush({}, { status: 503, statusText: 'Unavailable' });
    tick(500);
    expect(progress.visible()).toBeFalse();
    testing.expectNone('/api/v1/failure');
  }));

  it('renders an accessible polite status only while the delayed progress indicator is active', () => {
    const fixture = TestBed.createComponent(HttpProgressIndicator);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.progress-track')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="status"]').getAttribute('aria-live')).toBe('polite');
    progress.visible.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.progress-track')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Loading');
  });
});
