import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, throwError, of } from 'rxjs';
import { TransactionPage } from '../models/transaction-models';
import {
  configureTransactionsTest,
  mainAccount,
  savingsAccount,
  startTransactionsTest,
  transaction,
  transactionPage,
} from './testing/transactions-test-support.spec';

describe('Transactions query workflows', () => {
  it('loads the URL-selected non-primary account without exposing account data in rows', async () => {
    const { transactionApi } = await configureTransactionsTest();
    const { component, harness } = await startTransactionsTest(
      '/customer/transactions?accountId=account-savings',
    );
    const element = harness.routeNativeElement!;
    expect(component.selectedAccountId()).toBe('account-savings');
    expect(element.querySelector('app-account-selector')).toBeTruthy();
    expect(element.querySelector('.selector-trigger')?.textContent).toContain('Savings Account');
    expect(element.querySelector('.transaction-row')?.textContent).not.toContain('0123456789');
    expect(transactionApi.list.calls.mostRecent().args[0].get('accountId')).toBe('account-savings');
  });

  it('keeps the selector for one account and handles no accounts', async () => {
    await configureTransactionsTest([mainAccount]);
    const first = await startTransactionsTest('/customer/transactions');
    expect(first.harness.routeNativeElement?.querySelector('app-account-selector')).toBeTruthy();

    TestBed.resetTestingModule();
    await configureTransactionsTest([]);
    const empty = await startTransactionsTest('/customer/transactions');
    expect(empty.component.state()).toBe('no-accounts');
    expect(empty.harness.routeNativeElement?.querySelector('app-account-selector')).toBeNull();
  });

  it('rewrites a missing accountId to the primary account', async () => {
    await configureTransactionsTest();
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    await startTransactionsTest('/customer/transactions');
    expect(navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({ queryParams: { accountId: mainAccount.id }, replaceUrl: true }),
    );
  });

  it('switches accounts while retaining filters and restores prior URL selection', async () => {
    await configureTransactionsTest();
    const { component, harness } = await startTransactionsTest(
      '/customer/transactions?accountId=account-main&direction=OUT&status=COMPLETED&page=3',
    );
    component.navigation.onAccountChange(savingsAccount.id);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const router = TestBed.inject(Router);
    expect(router.url).toContain('accountId=account-savings');
    expect(router.url).toContain('direction=OUT');
    expect(router.url).toContain('page=1');
    await router.navigateByUrl(
      '/customer/transactions?accountId=account-main&direction=OUT&status=COMPLETED&page=3',
    );
    expect(component.selectedAccountId()).toBe('account-main');
  });

  it('shows account unavailable rather than silently selecting the primary account', async () => {
    const { transactionApi } = await configureTransactionsTest();
    transactionApi.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const { component, harness } = await startTransactionsTest(
      '/customer/transactions?accountId=foreign-id',
    );
    expect(component.state()).toBe('account-unavailable');
    expect(harness.routeNativeElement?.textContent).toContain('View default account');
  });

  it('sorts, validates filters and clears them without changing account selection', async () => {
    await configureTransactionsTest();
    const { component, harness } = await startTransactionsTest(
      '/customer/transactions?accountId=account-savings&fromDate=2025-01-01&toDate=2026-02-02',
    );
    expect(component.filterError()).toContain('366 days');
    component.form.patchValue({ fromDate: '2025-04-01', toDate: '2025-03-01' });
    expect(component.filterError()).toContain('start date');
    component.form.patchValue({ fromDate: '', toDate: '', minAmount: '25', maxAmount: '10' });
    expect(component.filterError()).toContain('minimum amount');
    component.navigation.clearFilters();
    component.navigation.sortBy('amount');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toContain('sortBy=amount');
    expect(component.form.controls.sortDirection.value).toBe('DESC');
  });

  it('discards stale list responses after rapid account changes', async () => {
    const { transactionApi } = await configureTransactionsTest();
    const stale = new Subject<TransactionPage>();
    const latest = new Subject<TransactionPage>();
    transactionApi.list.and.returnValues(of(transactionPage), stale, latest);
    const { component, harness } = await startTransactionsTest();
    component.navigation.onAccountChange(savingsAccount.id);
    await harness.fixture.whenStable();
    component.navigation.onAccountChange(mainAccount.id);
    await harness.fixture.whenStable();
    stale.next({ ...transactionPage, total: 9 });
    latest.next({ ...transactionPage, items: [{ ...transaction, id: 'newest' }] });
    harness.detectChanges();
    expect(component.page()?.items[0].id).toBe('newest');
    expect(component.requestPending()).toBeFalse();
  });

  it('clears busy state on failure without retrying automatically', async () => {
    const { transactionApi } = await configureTransactionsTest();
    transactionApi.list.and.returnValues(
      of(transactionPage),
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    const { component, harness } = await startTransactionsTest();
    component.navigation.onAccountChange(savingsAccount.id);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(component.state()).toBe('error');
    expect(component.requestPending()).toBeFalse();
    expect(transactionApi.list).toHaveBeenCalledTimes(2);
  });
});
