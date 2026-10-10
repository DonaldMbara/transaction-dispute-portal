import { TestBed } from '@angular/core/testing';
import { Customer } from './customer';

describe('Customer', () => {
  it('renders its inert page heading', async () => {
    await TestBed.configureTestingModule({ imports: [Customer] }).compileComponents();
    const fixture = TestBed.createComponent(Customer);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h1')?.textContent).toBe('Customer');
    expect(element.querySelector('form')).toBeNull();
  });
});
