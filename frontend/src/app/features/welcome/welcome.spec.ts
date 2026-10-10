import { TestBed } from '@angular/core/testing';
import { Welcome } from './welcome';

describe('Welcome', () => {
  it('renders its inert page heading', async () => {
    await TestBed.configureTestingModule({ imports: [Welcome] }).compileComponents();
    const fixture = TestBed.createComponent(Welcome);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h1')?.textContent).toBe('Transaction Dispute Portal');
    expect(element.querySelector('form')).toBeNull();
  });
});
