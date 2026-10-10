import { TestBed } from '@angular/core/testing';
import { Agent } from './agent';

describe('Agent', () => {
  it('renders its inert page heading', async () => {
    await TestBed.configureTestingModule({ imports: [Agent] }).compileComponents();
    const fixture = TestBed.createComponent(Agent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h1')?.textContent).toBe('Agent');
    expect(element.querySelector('form')).toBeNull();
  });
});
