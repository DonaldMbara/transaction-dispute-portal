import {Router} from '@angular/router';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, Subject, throwError} from 'rxjs';
import {TransactionPage} from './models/transaction-models';
import {Transactions} from './transactions';
import {
  configureTransactionsTest,
  startTransactionsTest,
  transaction,
  transactionPage,
} from './testing/transactions-test-support.spec';

describe('Transactions', () => {
  let harness: Awaited<ReturnType<typeof startTransactionsTest>>['harness'];
  let component: Transactions;
  let transactionApi: Awaited<ReturnType<typeof configureTransactionsTest>>['transactionApi'];

  beforeEach(async () => {
    ({transactionApi} = await configureTransactionsTest());
  });

  async function start(url = '/customer/transactions?accountId=account-main'): Promise<void> {
    const context = await startTransactionsTest(url);
    harness = context.harness;
    component = context.component;
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
    expect(element.querySelector('.transaction-row a')?.getAttribute('href')).toContain(
      'returnTo=',
    );
    expect(transactionApi.list.calls.mostRecent().args[0].get('accountId')).toBe('account-savings');
  });

  it('rewrites missing accountId to the primary account with replaceUrl', async () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    await start('/customer/transactions');
    expect(navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({queryParams: {accountId: 'account-main'}, replaceUrl: true}),
    );
  });

  it('switches accounts with push navigation, retains filters, resets page and restores a prior URL selection', async () => {
    await start(
      '/customer/transactions?accountId=account-main&direction=OUT&status=COMPLETED&page=3',
    );
    component.navigation.onAccountChange('account-savings');
    await harness.fixture.whenStable();
    harness.detectChanges();
    const router = TestBed.inject(Router);
    expect(router.url).toContain('accountId=account-savings');
    expect(router.url).toContain('direction=OUT');
    expect(router.url).toContain('status=COMPLETED');
    expect(router.url).toContain('page=1');
    await router.navigateByUrl(
      '/customer/transactions?accountId=account-main&direction=OUT&status=COMPLETED&page=3',
    );
    harness.detectChanges();
    expect(router.url).toContain('accountId=account-main');
    expect(router.url).toContain('page=3');
    expect(component.selectedAccountId()).toBe('account-main');
  });

  it('shows account unavailable for a foreign account and does not silently select primary', async () => {
    transactionApi.list.and.returnValue(throwError(() => new HttpErrorResponse({status: 404})));
    await start('/customer/transactions?accountId=foreign-id');
    expect(component.state()).toBe('account-unavailable');
    expect(component.selectedAccountId()).toBe('foreign-id');
    expect(harness.routeNativeElement?.textContent).toContain('View default account');
  });

  it('renders safe validation and empty states', async () => {
    transactionApi.list.and.returnValue(throwError(() => new HttpErrorResponse({status: 400})));
    await start();
    expect(component.state()).toBe('validation');
    transactionApi.list.and.returnValue(of({...transactionPage, total: 0, items: []}));
    component.retry();
    harness.detectChanges();
    expect(component.state()).toBe('none');
  });

  it('switches sort field defaults and flips active direction in the URL', async () => {
    await start();
    const router = TestBed.inject(Router);
    component.navigation.sortBy('amount');
    await harness.fixture.whenStable();
    expect(router.url).toContain('sortBy=amount');
    expect(component.form.controls.sortDirection.value).toBe('DESC');
    expect(component.presentation.ariaSort('amount')).toBe('descending');
    component.navigation.sortBy('amount');
    await harness.fixture.whenStable();
    expect(router.url).toContain('sortDirection=ASC');
    component.navigation.sortBy('merchant');
    await harness.fixture.whenStable();
    expect(component.form.controls.sortDirection.value).toBe('ASC');
    expect(router.url).toContain('sortBy=merchant');
  });

  it('shows inline range errors, tracks active filters and clears without changing account', async () => {
    await start(
      '/customer/transactions?accountId=account-savings&direction=OUT&fromDate=2025-01-01&toDate=2026-02-02',
    );
    expect(component.filterError()).toContain('366 days');
    expect(component.activeFilterCount()).toBe(2);
    component.form.patchValue({fromDate: '2025-04-01', toDate: '2025-03-01'});
    expect(component.filterError()).toContain('start date');
    component.form.patchValue({fromDate: '', toDate: '', minAmount: '25', maxAmount: '10'});
    expect(component.filterError()).toContain('minimum amount');
    component.navigation.clearFilters();
    await harness.fixture.whenStable();
    const url = TestBed.inject(Router).url;
    expect(url).toContain('accountId=account-savings');
    expect(url).not.toContain('direction=');
    expect(url).not.toContain('fromDate=');
    expect(component.activeFilterCount()).toBe(0);
  });

  it('cancels stale dispute-list responses when the account changes quickly', async () => {
    const stale = new Subject<TransactionPage>();
    const latest = new Subject<TransactionPage>();
    transactionApi.list.and.returnValues(of(transactionPage), stale, latest);
    await start('/customer/transactions?accountId=account-main');
    component.navigation.onAccountChange('account-savings');
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(component.requestPending()).toBeTrue();
    expect(component.state()).toBe('busy');
    expect(
      harness.routeNativeElement?.querySelector('.results-region')?.getAttribute('aria-busy'),
    ).toBe('true');
    expect(harness.routeNativeElement?.querySelector('.transaction-row')).toBeTruthy();
    expect(harness.routeNativeElement?.querySelector('.results-region')?.classList).toContain(
      'refreshing',
    );
    component.navigation.onAccountChange('account-main');
    await harness.fixture.whenStable();
    harness.detectChanges();
    stale.next({...transactionPage, total: 9});
    latest.next({...transactionPage, items: [{...transaction, id: 'newest'}]});
    harness.detectChanges();
    expect(component.selectedAccountId()).toBe('account-main');
    expect(component.page()?.items[0].id).toBe('newest');
    expect(component.requestPending()).toBeFalse();
  });

  it('clears busy state on an error without automatically retrying', async () => {
    transactionApi.list.and.returnValues(
      of(transactionPage),
      throwError(() => new HttpErrorResponse({status: 503})),
    );
    await start('/customer/transactions?accountId=account-main');
    component.navigation.onAccountChange('account-savings');
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(component.state()).toBe('error');
    expect(component.requestPending()).toBeFalse();
    expect(transactionApi.list).toHaveBeenCalledTimes(2);
    expect(
      harness.routeNativeElement?.querySelector('.results-region')?.getAttribute('aria-busy'),
    ).toBe('false');
  });
});
