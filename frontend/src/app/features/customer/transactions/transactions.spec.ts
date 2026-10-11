import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { AccountApi } from '../accounts/account-api';
import { AccountPage } from '../accounts/account-contracts';
import { TransactionApi } from './transaction-api';
import { TransactionPage } from './transaction-models';
import { Transactions } from './transactions';

describe('Transactions', () => {
  let harness: RouterTestingHarness;
  let component: Transactions;
  let accountApi: jasmine.SpyObj<AccountApi>;
  let transactionApi: jasmine.SpyObj<TransactionApi>;
  const main = { id: 'account-main', accountType: 'MAIN' as const, displayName: 'Main Account', accountNumber: '0123456789', maskedNumber: '****6789', balance: '12500.00', currency: 'ZAR', primary: true };
  const savings = { ...main, id: 'account-savings', accountType: 'SAVINGS' as const, displayName: 'Savings Account', accountNumber: '0012345678', maskedNumber: '****5678', primary: false };
  const item = { id: '10000000-0000-0000-0000-000000000001', reference: 'TXN-001', merchant: 'Market', amount: '125.50', currency: 'ZAR' as const, direction: 'OUT' as const, status: 'COMPLETED' as const, occurredAt: '2025-03-10T08:15:00Z', account: { id: main.id, maskedNumber: main.maskedNumber }, dispute: { id: null, eligible: true, ineligibilityReason: null } };
  const page: TransactionPage = { total: 1, page: 1, size: 20, items: [item] };
  const accountPage: AccountPage = { total: 2, page: 1, size: 20, items: [main, savings] };

  beforeEach(async () => {
    accountApi = jasmine.createSpyObj<AccountApi>('AccountApi', ['list']);
    transactionApi = jasmine.createSpyObj<TransactionApi>('TransactionApi', ['list', 'get']);
    accountApi.list.and.returnValue(of(accountPage));
    transactionApi.list.and.returnValue(of(page));
    await TestBed.configureTestingModule({
      imports: [Transactions],
      providers: [provideRouter([{ path: 'customer/transactions', component: Transactions }]), provideLocationMocks(),
        { provide: AccountApi, useValue: accountApi }, { provide: TransactionApi, useValue: transactionApi }]
    }).compileComponents();
  });

  async function start(url = '/customer/transactions?accountId=account-main'): Promise<void> {
    harness = await RouterTestingHarness.create(url);
    component = harness.routeDebugElement!.componentInstance as Transactions;
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  it('loads the URL-selected non-primary account and renders no account column', async () => {
    await start('/customer/transactions?accountId=account-savings');
    const element = harness.routeNativeElement!;
    expect(component.selectedAccountId()).toBe('account-savings');
    expect(element.querySelector('app-account-selector')).toBeTruthy();
    expect(element.querySelector('.selector-trigger')?.textContent).toContain('Savings Account');
    expect(element.querySelector('.selector-trigger')?.textContent).toContain('0012345678');
    expect(element.querySelector('.transaction-row')?.textContent).not.toContain('****6789');
    expect(element.querySelector('.transaction-row')?.textContent).not.toContain('0123456789');
    expect(element.querySelector('.transaction-row a')?.getAttribute('href')).toContain('returnTo=');
    expect(transactionApi.list.calls.mostRecent().args[0].get('accountId')).toBe('account-savings');
  });

  it('expands a transaction without changing the existing merchant detail link', async () => {
    await start();
    const element = harness.routeNativeElement!;
    const row = element.querySelector('.transaction-row') as HTMLTableRowElement;
    const detailLink = row.querySelector('a');
    expect(detailLink?.getAttribute('href')).toContain(`/customer/transactions/${item.id}`);
    row.querySelector('td:nth-child(2)')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    harness.detectChanges();
    expect(component.isExpanded(item.id)).toBeTrue();
    const expanded = element.querySelector('.transaction-expansion') as HTMLTableRowElement;
    expect(expanded.classList).toContain('open');
    expect(expanded.querySelector('button')?.textContent).toContain('Raise dispute');
    expect(row.querySelector('.expand-button')?.getAttribute('aria-expanded')).toBe('true');
    row.querySelector('.expand-button')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    harness.detectChanges();
    expect(component.isExpanded(item.id)).toBeFalse();
    expect(expanded.getAttribute('inert')).not.toBeNull();
  });

  it('does not offer a dispute for an incoming transaction', async () => {
    const incoming = { ...item, direction: 'IN' as const, dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_OUTGOING' } };
    transactionApi.list.and.returnValue(of({ ...page, items: [incoming] }));
    await start();
    const element = harness.routeNativeElement!;
    (element.querySelector('.transaction-row td:nth-child(2)') as HTMLElement).click();
    harness.detectChanges();
    expect(element.querySelector('.transaction-expansion')?.textContent).toContain('Only payments leaving your account can be disputed');
    expect(element.querySelector('.transaction-expansion button')).toBeNull();
  });

  it('keeps the selector for a single account and omits it with no accounts', async () => {
    accountApi.list.and.returnValue(of({ total: 1, page: 1, size: 20, items: [main] }));
    await start('/customer/transactions');
    expect(harness.routeNativeElement?.querySelector('app-account-selector')).toBeTruthy();

    TestBed.resetTestingModule();
    accountApi = jasmine.createSpyObj<AccountApi>('AccountApi', ['list']);
    transactionApi = jasmine.createSpyObj<TransactionApi>('TransactionApi', ['list', 'get']);
    accountApi.list.and.returnValue(of({ total: 0, page: 1, size: 20, items: [] }));
    await TestBed.configureTestingModule({ imports: [Transactions], providers: [
      provideRouter([{ path: 'customer/transactions', component: Transactions }]), provideLocationMocks(),
      { provide: AccountApi, useValue: accountApi }, { provide: TransactionApi, useValue: transactionApi }
    ] }).compileComponents();
    await start('/customer/transactions');
    expect(component.state()).toBe('no-accounts');
    expect(harness.routeNativeElement?.querySelector('app-account-selector')).toBeNull();
  });

  it('rewrites missing accountId to the primary account with replaceUrl', async () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    await start('/customer/transactions');
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { accountId: 'account-main' }, replaceUrl: true }));
  });

  it('switches accounts with push navigation, retains filters, resets page and restores a prior URL selection', async () => {
    await start('/customer/transactions?accountId=account-main&direction=OUT&status=COMPLETED&page=3');
    component.onAccountChange('account-savings');
    await harness.fixture.whenStable(); harness.detectChanges();
    const router = TestBed.inject(Router);
    expect(router.url).toContain('accountId=account-savings');
    expect(router.url).toContain('direction=OUT');
    expect(router.url).toContain('status=COMPLETED');
    expect(router.url).toContain('page=1');
    await router.navigateByUrl('/customer/transactions?accountId=account-main&direction=OUT&status=COMPLETED&page=3');
    harness.detectChanges();
    expect(router.url).toContain('accountId=account-main');
    expect(router.url).toContain('page=3');
    expect(component.selectedAccountId()).toBe('account-main');
  });

  it('shows account unavailable for a foreign account and does not silently select primary', async () => {
    transactionApi.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await start('/customer/transactions?accountId=foreign-id');
    expect(component.state()).toBe('account-unavailable');
    expect(component.selectedAccountId()).toBe('foreign-id');
    expect(harness.routeNativeElement?.textContent).toContain('View default account');
  });

  it('renders safe validation and empty states', async () => {
    transactionApi.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    await start();
    expect(component.state()).toBe('validation');
    transactionApi.list.and.returnValue(of({ ...page, total: 0, items: [] }));
    component.retry(); harness.detectChanges();
    expect(component.state()).toBe('none');
  });

  it('formats deterministic ZAR, signs directions, uses Johannesburg time and groups only date-sorted rows', async () => {
    await start();
    expect(component.formatAmount(item)).toBe('−R 125.50');
    expect(component.formatDate('2025-03-10T08:15:00Z')).toContain('10 Mar 2025');
    expect(component.formatDate('2025-03-10T08:15:00Z')).toContain('10:15');
    expect(component.monthLabel('2025-03-10T08:15:00Z')).toContain('Mar 2025');
    expect(component.statusLabel('COMPLETED')).toBe('Completed');
    expect(component.isNewMonth(item, 0)).toBeTrue();
    component.form.controls.sortBy.setValue('merchant', { emitEvent: false });
    expect(component.isNewMonth(item, 0)).toBeFalse();
  });

  it('renders COMPLETED OUT amount with sign, outgoing colour class, and no failed-note', async () => {
    await start();
    const element = harness.routeNativeElement!;
    const amountCell = element.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amountCell.classList).toContain('outgoing');
    expect(amountCell.classList).not.toContain('failed');
    expect(amountCell.classList).not.toContain('pending');
    expect(amountCell.querySelector('[aria-hidden]')?.textContent).toBe('−R 125.50');
    expect(amountCell.querySelector('.visually-hidden')?.textContent).toBe('minus R 125.50');
    expect(element.querySelector('.failed-note')).toBeNull();
  });

  it('renders COMPLETED IN amount with incoming colour and plus sign', async () => {
    const incoming = { ...item, direction: 'IN' as const, dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_OUTGOING' as const } };
    transactionApi.list.and.returnValue(of({ ...page, items: [incoming] }));
    await start();
    const element = harness.routeNativeElement!;
    const amountCell = element.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amountCell.classList).toContain('incoming');
    expect(amountCell.classList).not.toContain('outgoing');
    expect(amountCell.querySelector('[aria-hidden]')?.textContent).toBe('+R 125.50');
    expect(amountCell.querySelector('.visually-hidden')?.textContent).toBe('plus R 125.50');
    expect(element.querySelector('.failed-note')).toBeNull();
  });

  it('renders FAILED OUT amount with strikethrough class, no sign, accessible label and Not debited note', async () => {
    const failed = { ...item, status: 'FAILED' as const, dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_COMPLETED' as const } };
    transactionApi.list.and.returnValue(of({ ...page, items: [failed] }));
    await start();
    const element = harness.routeNativeElement!;
    const amountCell = element.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amountCell.classList).toContain('failed');
    expect(amountCell.classList).not.toContain('outgoing');
    expect(amountCell.classList).not.toContain('incoming');
    expect(amountCell.classList).not.toContain('pending');
    expect(amountCell.querySelector('[aria-hidden]')?.textContent).toBe('R 125.50');
    expect(amountCell.querySelector('.visually-hidden')?.textContent).toBe('R 125.50, failed, no money moved');
    const failedNote = element.querySelector('.failed-note');
    expect(failedNote).toBeTruthy();
    expect(failedNote?.textContent).toContain('Not debited');
  });

  it('renders FAILED IN amount with accessible label and Not credited note', async () => {
    const failed = { ...item, direction: 'IN' as const, status: 'FAILED' as const, dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_COMPLETED' as const } };
    transactionApi.list.and.returnValue(of({ ...page, items: [failed] }));
    await start();
    const element = harness.routeNativeElement!;
    const failedNote = element.querySelector('.failed-note');
    expect(failedNote?.textContent).toContain('Not credited');
    const amountCell = element.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amountCell.querySelector('[aria-hidden]')?.textContent).toBe('R 125.50');
    expect(amountCell.querySelector('.visually-hidden')?.textContent).toBe('R 125.50, failed, no money moved');
  });

  it('renders PENDING amount with pending colour class, sign and accessible label', async () => {
    const pending = { ...item, status: 'PENDING' as const, dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_COMPLETED' as const } };
    transactionApi.list.and.returnValue(of({ ...page, items: [pending] }));
    await start();
    const element = harness.routeNativeElement!;
    const amountCell = element.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amountCell.classList).toContain('pending');
    expect(amountCell.classList).not.toContain('failed');
    expect(amountCell.querySelector('[aria-hidden]')?.textContent).toBe('−R 125.50');
    expect(amountCell.querySelector('.visually-hidden')?.textContent).toBe('minus R 125.50, pending');
    expect(element.querySelector('.failed-note')).toBeNull();
  });

  it('amountAriaLabel returns correct strings for each status and direction', async () => {
    await start();
    expect(component.amountAriaLabel(item)).toBe('minus R 125.50');
    const inItem = { ...item, direction: 'IN' as const };
    expect(component.amountAriaLabel(inItem)).toBe('plus R 125.50');
    const failed = { ...item, status: 'FAILED' as const };
    expect(component.amountAriaLabel(failed)).toBe('R 125.50, failed, no money moved');
    const pendingOut = { ...item, status: 'PENDING' as const };
    expect(component.amountAriaLabel(pendingOut)).toBe('minus R 125.50, pending');
    const pendingIn = { ...item, direction: 'IN' as const, status: 'PENDING' as const };
    expect(component.amountAriaLabel(pendingIn)).toBe('plus R 125.50, pending');
  });

  it('switches sort field defaults and flips active direction in the URL', async () => {
    await start();
    const router = TestBed.inject(Router);
    component.sortBy('amount'); await harness.fixture.whenStable();
    expect(router.url).toContain('sortBy=amount');
    expect(component.form.controls.sortDirection.value).toBe('DESC');
    expect(component.ariaSort('amount')).toBe('descending');
    component.sortBy('amount'); await harness.fixture.whenStable();
    expect(router.url).toContain('sortDirection=ASC');
    component.sortBy('merchant'); await harness.fixture.whenStable();
    expect(component.form.controls.sortDirection.value).toBe('ASC');
    expect(router.url).toContain('sortBy=merchant');
  });

  it('shows inline range errors, tracks active filters and clears without changing account', async () => {
    await start('/customer/transactions?accountId=account-savings&direction=OUT&fromDate=2025-01-01&toDate=2026-02-02');
    expect(component.filterError()).toContain('366 days');
    expect(component.activeFilterCount()).toBe(2);
    component.form.patchValue({ fromDate: '2025-04-01', toDate: '2025-03-01' });
    expect(component.filterError()).toContain('start date');
    component.form.patchValue({ fromDate: '', toDate: '', minAmount: '25', maxAmount: '10' });
    expect(component.filterError()).toContain('minimum amount');
    component.clearFilters(); await harness.fixture.whenStable();
    const url = TestBed.inject(Router).url;
    expect(url).toContain('accountId=account-savings');
    expect(url).not.toContain('direction=');
    expect(url).not.toContain('fromDate=');
    expect(component.activeFilterCount()).toBe(0);
  });

  it('cancels stale list responses when the account changes quickly', async () => {
    const stale = new Subject<TransactionPage>();
    const latest = new Subject<TransactionPage>();
    transactionApi.list.and.returnValues(of(page), stale, latest);
    await start('/customer/transactions?accountId=account-main');
    component.onAccountChange('account-savings');
    await harness.fixture.whenStable(); harness.detectChanges();
    expect(component.requestPending()).toBeTrue();
    expect(component.state()).toBe('busy');
    expect(harness.routeNativeElement?.querySelector('.results-region')?.getAttribute('aria-busy')).toBe('true');
    expect(harness.routeNativeElement?.querySelector('.transaction-row')).toBeTruthy();
    expect(harness.routeNativeElement?.querySelector('.results-region')?.classList).toContain('refreshing');
    component.onAccountChange('account-main');
    await harness.fixture.whenStable(); harness.detectChanges();
    stale.next({ ...page, total: 9 });
    latest.next({ ...page, items: [{ ...item, id: 'newest' }] });
    harness.detectChanges();
    expect(component.selectedAccountId()).toBe('account-main');
    expect(component.page()?.items[0].id).toBe('newest');
    expect(component.requestPending()).toBeFalse();
  });

  it('clears busy state on an error without automatically retrying', async () => {
    transactionApi.list.and.returnValues(of(page), throwError(() => new HttpErrorResponse({ status: 503 })));
    await start('/customer/transactions?accountId=account-main');
    component.onAccountChange('account-savings');
    await harness.fixture.whenStable(); harness.detectChanges();
    expect(component.state()).toBe('error');
    expect(component.requestPending()).toBeFalse();
    expect(transactionApi.list).toHaveBeenCalledTimes(2);
    expect(harness.routeNativeElement?.querySelector('.results-region')?.getAttribute('aria-busy')).toBe('false');
  });
});
