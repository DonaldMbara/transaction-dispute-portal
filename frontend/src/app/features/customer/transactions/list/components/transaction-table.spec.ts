import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import {
  formatTransactionAmount,
  formatTransactionDate,
  formatTransactionMonth,
  isNewTransactionMonth,
  transactionAmountAriaLabel,
  transactionStatusLabel,
} from '../presentation/transaction-presentation';
import { TransactionTable } from './transaction-table';
import {
  configureTransactionsTest,
  startTransactionsTest,
  transaction,
  transactionPage,
} from '../testing/transactions-test-support.spec';

describe('Transaction table presentation', () => {
  it('formats amount, status, and Johannesburg dates', () => {
    expect(formatTransactionAmount(transaction)).toBe('−R 125.50');
    expect(formatTransactionDate('2025-03-10T08:15:00Z')).toContain('10 Mar 2025');
    expect(formatTransactionDate('2025-03-10T08:15:00Z')).toContain('10:15');
    expect(formatTransactionMonth('2025-03-10T08:15:00Z')).toContain('Mar 2025');
    expect(transactionStatusLabel('COMPLETED')).toBe('Completed');
    expect(isNewTransactionMonth(transaction, 0, [transaction], 'occurredAt')).toBeTrue();
    expect(isNewTransactionMonth(transaction, 0, [transaction], 'merchant')).toBeFalse();
  });

  it('provides accessible amount labels for each status and direction', () => {
    expect(transactionAmountAriaLabel(transaction)).toBe('minus R 125.50');
    expect(transactionAmountAriaLabel({ ...transaction, direction: 'IN' })).toBe('plus R 125.50');
    expect(transactionAmountAriaLabel({ ...transaction, status: 'FAILED' })).toBe(
      'R 125.50, failed, no money moved',
    );
    expect(transactionAmountAriaLabel({ ...transaction, status: 'PENDING' })).toBe(
      'minus R 125.50, pending',
    );
  });

  it('expands rows without changing the merchant detail link', async () => {
    await configureTransactionsTest();
    const { harness } = await startTransactionsTest();
    const element = harness.routeNativeElement!;
    const row = element.querySelector('.transaction-row') as HTMLTableRowElement;
    expect(row.querySelector('a')?.getAttribute('href')).toContain(
      `/customer/transactions/${transaction.id}`,
    );
    row.querySelector('td:nth-child(2)')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    harness.detectChanges();
    const table = harness.fixture.debugElement.query(By.directive(TransactionTable))
      .componentInstance as TransactionTable;
    expect(table.isExpanded(transaction.id)).toBeTrue();
    expect(element.querySelector('.transaction-expansion')?.textContent).toContain('Raise dispute');
    row.querySelector('.expand-button')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    harness.detectChanges();
    expect(table.isExpanded(transaction.id)).toBeFalse();
  });

  it('does not offer a dispute for an incoming transaction', async () => {
    const { transactionApi } = await configureTransactionsTest();
    const incoming = {
      ...transaction,
      direction: 'IN' as const,
      dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_OUTGOING' },
    };
    transactionApi.list.and.returnValue(of({ ...transactionPage, items: [incoming] }));
    const { harness } = await startTransactionsTest();
    const element = harness.routeNativeElement!;
    (element.querySelector('.transaction-row td:nth-child(2)') as HTMLElement).click();
    harness.detectChanges();
    expect(element.querySelector('.transaction-expansion')?.textContent).toContain(
      'Only payments leaving your account can be disputed',
    );
    expect(element.querySelector('.transaction-expansion button')).toBeNull();
  });

  it('renders failed and pending amounts with distinct accessible status treatment', async () => {
    const { transactionApi } = await configureTransactionsTest();
    const failed = {
      ...transaction,
      status: 'FAILED' as const,
      dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_COMPLETED' as const },
    };
    transactionApi.list.and.returnValue(of({ ...transactionPage, items: [failed] }));
    const { harness, component } = await startTransactionsTest();
    let amount = harness.routeNativeElement!.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amount.classList).toContain('failed');
    expect(amount.querySelector('.visually-hidden')?.textContent).toBe(
      'R 125.50, failed, no money moved',
    );
    transactionApi.list.and.returnValue(
      of({
        ...transactionPage,
        items: [
          {
            ...transaction,
            status: 'PENDING' as const,
            dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_COMPLETED' as const },
          },
        ],
      }),
    );
    component.retry();
    harness.detectChanges();
    amount = harness.routeNativeElement!.querySelector('.transaction-row .amount') as HTMLElement;
    expect(amount.classList).toContain('pending');
    expect(amount.querySelector('.visually-hidden')?.textContent).toBe('minus R 125.50, pending');
  });
});
