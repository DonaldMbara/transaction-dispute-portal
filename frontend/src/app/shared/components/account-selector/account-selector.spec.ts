import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountSelector } from './account-selector';

describe('AccountSelector', () => {
  let fixture: ComponentFixture<AccountSelector>;
  const accounts = [
    { id: 'a', accountType: 'MAIN' as const, displayName: 'Main Account', accountNumber: '0123456789', maskedNumber: '****6789', balance: '120.00', currency: 'ZAR', primary: true },
    { id: 'b', accountType: 'SAVINGS' as const, displayName: 'Savings Account', accountNumber: '9876543210', maskedNumber: '****3210', balance: '50.00', currency: 'ZAR', primary: false }
  ];
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AccountSelector] }).compileComponents();
    fixture = TestBed.createComponent(AccountSelector);
    fixture.componentRef.setInput('accounts', accounts);
    fixture.componentRef.setInput('selectedAccountId', 'a');
    fixture.detectChanges();
  });
  it('shows full number only in current summary and masked choices', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('0123456789');
    expect(text).toContain('****6789');
    expect(text).toContain('****3210');
    expect(fixture.nativeElement.querySelectorAll('option')[0].textContent).toContain('****6789');
  });
  it('emits the native keyboard-select change and omits a null full number', () => {
    const emitted = jasmine.createSpy('selection');
    fixture.componentInstance.selectionChange.subscribe(emitted);
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    expect(fixture.nativeElement.querySelector('label')?.getAttribute('for')).toBe(select.id);
    select.value = 'b'; select.dispatchEvent(new Event('change'));
    expect(emitted).toHaveBeenCalledOnceWith('b');
    fixture.componentRef.setInput('selectedAccountId', 'b');
    fixture.componentRef.setInput('accounts', [{ ...accounts[1], accountNumber: null }]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('9876543210');
    expect(fixture.nativeElement.querySelector('select')).toBeTruthy();
  });
});
