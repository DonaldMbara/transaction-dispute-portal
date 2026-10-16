import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { NotificationService } from '../../shared/components/notification/notification';
import { apiErrorInterceptor } from './api-error-interceptor';

describe('API error interceptor', () => {
  let http: HttpClient;
  let testing: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    testing = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });
  afterEach(() => testing.verify());

  it('shows a safe error toast for server failures without exposing response details', () => {
    http.get('/api/v1/accounts').subscribe({ error: () => undefined });
    testing
      .expectOne('/api/v1/accounts')
      .flush({ detail: 'database password leaked' }, { status: 503, statusText: 'Unavailable' });
    expect(notifications.current()?.tone).toBe('error');
    expect(notifications.current()?.message).toBe(
      'The service is temporarily unavailable. Please try again.',
    );
    expect(notifications.current()?.message).not.toContain('database password');
  });

  it('does not replace feature-owned validation and not-found states with generic toasts', () => {
    http.get('/api/v1/transactions').subscribe({ error: () => undefined });
    testing.expectOne('/api/v1/transactions').flush({}, { status: 404, statusText: 'Not Found' });
    expect(notifications.current()).toBeNull();
  });

  it('uses a safe forbidden message for API authorization failures', () => {
    http.get('/api/v1/transactions').subscribe({ error: () => undefined });
    testing
      .expectOne('/api/v1/transactions')
      .flush({ error_code: 1002 }, { status: 403, statusText: 'Forbidden' });
    expect(notifications.current()?.message).toBe(
      'You do not have permission to perform this operation.',
    );
  });
});
