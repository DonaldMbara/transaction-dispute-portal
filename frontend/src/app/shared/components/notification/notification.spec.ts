import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationOutlet, NotificationService } from './notification';

describe('NotificationService', () => {
  it('expires a notification after five seconds', fakeAsync(() => {
    const notifications = TestBed.inject(NotificationService);
    notifications.show('Signed out.', 'success');
    expect(notifications.current()?.message).toBe('Signed out.');
    tick(4_999);
    expect(notifications.current()).not.toBeNull();
    tick(1);
    expect(notifications.current()).toBeNull();
  }));

  it('restarts the timeout and supports dismissal', fakeAsync(() => {
    const notifications = TestBed.inject(NotificationService);
    notifications.show('First');
    tick(4_000);
    notifications.show('Second');
    tick(1_001);
    expect(notifications.current()?.message).toBe('Second');
    notifications.dismiss();
    expect(notifications.current()).toBeNull();
  }));

  it('supports an assertive error notification tone', () => {
    TestBed.configureTestingModule({ imports: [NotificationOutlet] });
    const fixture = TestBed.createComponent(NotificationOutlet);
    const notifications = TestBed.inject(NotificationService);
    notifications.show('Service unavailable.', 'error');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.getAttribute('aria-live')).toBe(
      'assertive',
    );
    expect(fixture.nativeElement.querySelector('.notification--error')).not.toBeNull();
  });
});
