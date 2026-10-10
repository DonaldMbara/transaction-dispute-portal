import { TestBed } from '@angular/core/testing';
import { Login } from './login';

describe('Login', () => {
  it('renders its inert page heading', async () => {
    await TestBed.configureTestingModule({ imports: [Login] }).compileComponents();
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h1')?.textContent).toBe('Sign in');
    expect(element.querySelector('form')).toBeNull();
  });
});
