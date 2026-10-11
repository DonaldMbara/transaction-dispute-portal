import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AccountApi } from './account-api';
import { AccountDashboard } from './account-dashboard';
import { AccountPage } from './account-contracts';

describe('AccountDashboard', () => {
  let fixture: ComponentFixture<AccountDashboard>;
  let api: jasmine.SpyObj<AccountApi>;
  const page = (items: AccountPage['items']): AccountPage => ({ total: items.length, page: 1, size: 20, items });
  const main = { id: 'account-1', accountType: 'MAIN' as const, displayName: 'Main Account', accountNumber: '0123456789', maskedNumber: '****6789', balance: '1200.00', currency: 'ZAR', primary: true };

  beforeEach(async () => {
    api = jasmine.createSpyObj<AccountApi>('AccountApi', ['list']);
    api.list.and.returnValue(of(page([main])));
    await TestBed.configureTestingModule({
      imports: [AccountDashboard],
      providers: [provideRouter([{ path: 'customer', children: [] }]), { provide: AccountApi, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(AccountDashboard);
    fixture.detectChanges();
  });

  it('renders the stacked account row with owner-visible details and primary badge', () => {
    const element = fixture.nativeElement as HTMLElement;
    const row = element.querySelector('a.account-row');
    expect(row).toBeTruthy();
    expect(row?.querySelector('app-account-card')).toBeTruthy();
    expect(row?.textContent).toContain('Main Account');
    expect(row?.querySelector('.full-number')?.textContent).toContain('0123456789');
    expect(row?.textContent).toContain('0123456789');
    expect(row?.textContent).toContain('Primary');
    expect(row?.textContent).toContain('Available balance');
    expect(row?.textContent).toContain('R 1 200.00');
    expect(row?.querySelector('.account-icon svg')).toBeTruthy();
    expect(row?.querySelector('.account-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(row?.querySelector('.account-mark')).toBeNull();
    expect(row?.classList.contains('account-row--primary')).toBeFalse();
    expect(element.querySelector('nav[aria-label="Quick links"]')?.textContent).toContain('View transactions');
  });

  it('uses different Lucide icon shapes for MAIN and SAVINGS accounts', () => {
    const savings = { ...main, id: 'account-2', accountType: 'SAVINGS' as const, displayName: 'Savings Account', primary: false };
    api.list.and.returnValue(of(page([main, savings])));
    fixture.componentInstance.load();
    fixture.detectChanges();
    const icons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.account-icon svg'));
    expect(icons.length).toBe(2);
    expect(icons[0].innerHTML).not.toBe(icons[1].innerHTML);
    expect(icons.every(icon => icon.parentElement?.getAttribute('aria-hidden') === 'true')).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.primary-badge').length).toBe(1);
  });

  it('keeps the loading skeleton visible until the request completes', () => {
    const response = new Subject<AccountPage>();
    api.list.and.returnValue(response.asObservable());
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('loading');
    expect((fixture.nativeElement as HTMLElement).querySelector('[aria-busy="true"]')).toBeTruthy();
    response.next(page([main]));
    response.complete();
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('ready');
  });

  it('keeps existing accounts visible and marks the region busy during refresh', () => {
    const response = new Subject<AccountPage>();
    api.list.and.returnValue(response.asObservable());
    fixture.componentInstance.load();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.state()).toBe('busy');
    expect(element.querySelector('.dashboard-columns')?.getAttribute('aria-busy')).toBe('true');
    expect(element.querySelector('.account-row')).toBeTruthy();
  });

  it('renders the empty state without account rows or a quick-links panel', () => {
    api.list.and.returnValue(of(page([])));
    fixture.componentInstance.load();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('No accounts yet');
    expect(element.querySelector('.account-row')).toBeNull();
    expect(element.querySelector('nav[aria-label="Quick links"]')).toBeNull();
  });

  it('renders retry state on service failure', () => {
    api.list.and.returnValue(throwError(() => new Error('service down')));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('Something went wrong');
  });

  it('allows a deliberate retry after a failure', () => {
    api.list.and.returnValues(throwError(() => new Error('service down')), of(page([main])));
    fixture.componentInstance.load();
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('button')?.click();
    fixture.detectChanges();
    expect(api.list).toHaveBeenCalledTimes(3);
    expect(fixture.componentInstance.state()).toBe('ready');
  });

  it('links each row to its account transaction query target', () => {
    const anchor = (fixture.nativeElement as HTMLElement).querySelector('a.account-row');
    expect(anchor?.getAttribute('href')).toBe('/customer/transactions?accountId=account-1');
  });

  it('supports Space activation while retaining one anchor per account row', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    const row = (fixture.nativeElement as HTMLElement).querySelector('a.account-row')!;
    const event = new KeyboardEvent('keydown', { code: 'Space', cancelable: true });
    row.dispatchEvent(event);
    expect(event.defaultPrevented).toBeTrue();
    expect(navigate).toHaveBeenCalledWith(['/customer/transactions'], { queryParams: { accountId: 'account-1' } });
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('a.account-row').length).toBe(1);
  });

  it('adds a quick-links panel for the implemented primary-account route', () => {
    const columns = (fixture.nativeElement as HTMLElement).querySelector('.dashboard-columns')!;
    expect(columns.children.length).toBe(2);
    expect(columns.querySelector('.account-list')).toBeTruthy();
    const quickLink = columns.querySelector('nav[aria-label="Quick links"] a') as HTMLAnchorElement;
    expect(quickLink.textContent).toContain('View transactions');
    expect(quickLink.getAttribute('href')).toBe('/customer/transactions?accountId=account-1');
  });

  it('keeps account details before the balance in the responsive row structure', () => {
    const row = (fixture.nativeElement as HTMLElement).querySelector('a.account-row')!;
    const accountDetails = row.querySelector('.account-info')!;
    const balance = row.querySelector('.account-balance')!;
    expect(accountDetails.compareDocumentPosition(balance) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(row.querySelector('.chevron')).toBeTruthy();
    expect(getComputedStyle(balance).minWidth).toBe('0px');
  });

  it('uses high-contrast token colors for the account number and small labels', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(getComputedStyle(element.querySelector('.full-number')!).color).toBe('rgb(37, 59, 82)');
    expect(element.textContent).not.toContain('Account number');
    expect(getComputedStyle(element.querySelector('.balance-label')!).color).toBe('rgb(75, 91, 107)');
    expect(getComputedStyle(element.querySelector('.primary-badge')!).color).toBe('rgb(23, 76, 118)');
  });

  it('omits the account number when the backend returns null', () => {
    api.list.and.returnValue(of(page([{ ...main, accountNumber: null }])));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('0123456789');
  });
});
