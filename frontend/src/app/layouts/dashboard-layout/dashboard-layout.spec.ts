import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, RouterOutlet } from '@angular/router';
import { DashboardLayout } from './dashboard-layout';

describe('DashboardLayout', () => {
  it('hosts a child outlet inside the main landmark', async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardLayout], providers: [provideRouter([])]
    }).compileComponents();
    const fixture = TestBed.createComponent(DashboardLayout);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('main')).query(By.directive(RouterOutlet))).not.toBeNull();
  });
});
