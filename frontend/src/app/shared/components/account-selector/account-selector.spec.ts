import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountSelector } from './account-selector';

describe('AccountSelector', () => {
  let fixture: ComponentFixture<AccountSelector>;
  const accounts = [
    { id: 'a', accountType: 'MAIN' as const, displayName: 'Main Account', accountNumber: '0123456789', maskedNumber: '****6789', balance: '120.00', currency: 'ZAR', primary: true },
    { id: 'b', accountType: 'SAVINGS' as const, displayName: 'Savings Account', accountNumber: '9876543210', maskedNumber: '****3210', balance: '50.00', currency: 'ZAR', primary: false }
  ];

  describe('with showAllAccounts=false (default)', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({ imports: [AccountSelector] }).compileComponents();
      fixture = TestBed.createComponent(AccountSelector);
      fixture.componentRef.setInput('accounts', accounts);
      fixture.componentRef.setInput('selectedAccountId', 'a');
      fixture.detectChanges();
    });

    it('shows a selected account card and full account numbers in both selector states', () => {
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      expect(trigger.getAttribute('aria-haspopup')).toBe('listbox');
      expect(trigger.querySelector('app-account-card')).toBeTruthy();
      expect(trigger.getAttribute('aria-expanded')).toBe('false');
      expect(trigger.textContent).toContain('Main Account');
      expect(trigger.textContent).toContain('Primary');
      expect(trigger.textContent).toContain('0123456789');
      expect(trigger.textContent).toContain('R 120.00');
      expect(getComputedStyle(trigger).minHeight).toBe('72px');
      expect(trigger.querySelector('.account-info')?.textContent).not.toContain('Available balance');
      expect(trigger.querySelector('.account-balance')?.textContent).toContain('Available balance');
      expect(trigger.querySelector('.account-icon[aria-hidden="true"] svg')).toBeTruthy();
      trigger.click(); fixture.detectChanges();
      const options = host.querySelectorAll<HTMLElement>('[role="option"]');
      expect(host.querySelector('[role="listbox"]')).toBeTruthy();
      expect(host.querySelectorAll('[role="option"] app-account-card').length).toBe(2);
      expect(options.length).toBe(2);
      expect(options[0].getAttribute('aria-selected')).toBe('true');
      expect(options[0].textContent).toContain('0123456789');
      expect(options[1].textContent).toContain('9876543210');
      expect(options[0].querySelector('.account-balance')?.textContent).toContain('R 120.00');
      expect(host.textContent).not.toContain('****6789');
      expect(host.querySelector('select')).toBeNull();
    });

    it('no "All your accounts" option present when showAllAccounts is false', () => {
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      trigger.click(); fixture.detectChanges();
      expect(host.querySelector('.all-accounts-option')).toBeNull();
      expect(host.textContent).not.toContain('All your accounts');
    });

    it('supports keyboard selection and closes with focus returned to the trigger', async () => {
      const emitted = jasmine.createSpy('selection');
      fixture.componentInstance.selectionChange.subscribe(emitted);
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      fixture.detectChanges();
      await new Promise(resolve => setTimeout(resolve));
      fixture.detectChanges();
      const listbox = host.querySelector<HTMLElement>('[role="listbox"]')!;
      expect(trigger.getAttribute('aria-expanded')).toBe('true');
      listbox.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
      fixture.detectChanges();
      expect(document.activeElement?.getAttribute('data-option-index')).toBe('1');
      listbox.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      fixture.detectChanges();
      expect(emitted).toHaveBeenCalledOnceWith('b');
      expect(host.querySelector('[role="listbox"]')).toBeNull();
      await new Promise(resolve => setTimeout(resolve));
      expect(document.activeElement).toBe(trigger);
    });

    it('supports Home, End and Escape, closes on outside click, and omits null numbers', () => {
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      trigger.click(); fixture.detectChanges();
      const listbox = host.querySelector<HTMLElement>('[role="listbox"]')!;
      listbox.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
      expect(document.activeElement?.getAttribute('data-option-index')).toBe('1');
      listbox.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
      expect(document.activeElement?.getAttribute('data-option-index')).toBe('0');
      listbox.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();
      expect(host.querySelector('[role="listbox"]')).toBeNull();
      trigger.click(); fixture.detectChanges();
      document.body.dispatchEvent(new MouseEvent('click', { bubbles: true })); fixture.detectChanges();
      expect(host.querySelector('[role="listbox"]')).toBeNull();
      fixture.componentRef.setInput('accounts', [{ ...accounts[0], accountNumber: null }]);
      fixture.detectChanges();
      expect(host.textContent).not.toContain('0123456789');
      expect(host.querySelector('.selector-trigger')).toBeTruthy();
      expect(host.querySelector('select')).toBeNull();
    });
  });

  describe('with showAllAccounts=true', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({ imports: [AccountSelector] }).compileComponents();
      fixture = TestBed.createComponent(AccountSelector);
      fixture.componentRef.setInput('accounts', accounts);
      fixture.componentRef.setInput('selectedAccountId', 'a');
      fixture.componentRef.setInput('showAllAccounts', true);
      fixture.detectChanges();
    });

    it('first option is "All your accounts"', () => {
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      trigger.click(); fixture.detectChanges();
      const options = host.querySelectorAll<HTMLElement>('[role="option"]');
      expect(options.length).toBe(3); // all accounts + 2 real accounts
      expect(options[0].textContent).toContain('All your accounts');
      expect(options[0].getAttribute('data-option-index')).toBe('0');
    });

    it('selecting "All your accounts" emits null', () => {
      const emitted = jasmine.createSpy('selection');
      fixture.componentInstance.selectionChange.subscribe(emitted);
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      trigger.click(); fixture.detectChanges();
      const allOption = host.querySelector<HTMLButtonElement>('.all-accounts-option')!;
      allOption.click(); fixture.detectChanges();
      expect(emitted).toHaveBeenCalledOnceWith(null);
    });

    it('selecting a real account emits the account id', () => {
      const emitted = jasmine.createSpy('selection');
      fixture.componentInstance.selectionChange.subscribe(emitted);
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      trigger.click(); fixture.detectChanges();
      const options = host.querySelectorAll<HTMLButtonElement>('[role="option"]');
      // index 1 is the first real account (Main Account = 'a')
      options[1].click(); fixture.detectChanges();
      expect(emitted).toHaveBeenCalledOnceWith('a');
    });

    it('when selectedAccountId is null, shows "All your accounts" in trigger', () => {
      fixture.componentRef.setInput('selectedAccountId', null);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;
      const trigger: HTMLButtonElement = host.querySelector('.selector-trigger')!;
      expect(trigger.textContent).toContain('All your accounts');
    });
  });
});
