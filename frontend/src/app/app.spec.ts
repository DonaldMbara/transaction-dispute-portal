import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, RouterOutlet } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth-service';
import { SessionState } from './core/auth/session-state';

// Route behavior is covered separately by app.routes.spec.ts.
describe('App', () => {
  it('hosts the root router outlet', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: AuthService, useFactory: () => ({ state: TestBed.inject(SessionState) }) }]
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(SessionState).anonymous();
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(RouterOutlet))).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-http-progress')).not.toBeNull();
  });
});
