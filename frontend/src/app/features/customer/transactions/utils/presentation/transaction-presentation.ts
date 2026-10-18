import {formatZar} from '../../../../../shared/utils/zar-formatter';
import {SortDirection, SortField, TransactionItem} from '../../models/transaction-models';

export interface TransactionListPresentation {
  formatDate(value: string): string;

  formatAmount(item: TransactionItem): string;

  amountAriaLabel(item: TransactionItem): string;

  isNewMonth(item: TransactionItem, index: number): boolean;

  monthLabel(value: string): string;

  statusLabel(status: TransactionItem['status']): string;

  isSorted(field: SortField): boolean;

  ariaSort(field: SortField): 'ascending' | 'descending' | null;

  sortIcon(field: SortField): string;

  sortSummary(): string;
}

const TRANSACTION_DATE_FORMAT = new Intl.DateTimeFormat('en-ZA', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Africa/Johannesburg',
});

const TRANSACTION_MONTH_FORMAT = new Intl.DateTimeFormat('en-ZA', {
  month: 'short',
  year: 'numeric',
  timeZone: 'Africa/Johannesburg',
});

const TRANSACTION_MONTH_KEY_FORMAT = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  timeZone: 'Africa/Johannesburg',
});

export function formatTransactionDate(value: string): string {
  return TRANSACTION_DATE_FORMAT.format(new Date(value));
}

export function formatTransactionAmount(item: TransactionItem): string {
  if (item.status === 'FAILED') return formatZar(item.amount);
  return `${item.direction === 'OUT' ? '−' : '+'}${formatZar(item.amount)}`;
}

export function transactionAmountAriaLabel(item: TransactionItem): string {
  const amount = formatZar(item.amount);
  if (item.status === 'FAILED') return `${amount}, failed, no money moved`;
  const direction = item.direction === 'OUT' ? 'minus ' : 'plus ';
  return item.status === 'PENDING'
    ? `${direction}${amount}, pending`
    : `${direction}${amount}`;
}

export function isNewTransactionMonth(
  item: TransactionItem,
  index: number,
  items: readonly TransactionItem[],
  sortBy: SortField,
): boolean {
  if (sortBy !== 'occurredAt') return false;
  if (index === 0) return true;
  const previous = TRANSACTION_MONTH_KEY_FORMAT.format(new Date(items[index - 1].occurredAt));
  const current = TRANSACTION_MONTH_KEY_FORMAT.format(new Date(item.occurredAt));
  return previous !== current;
}

export function formatTransactionMonth(value: string): string {
  return TRANSACTION_MONTH_FORMAT.format(new Date(value));
}

export function transactionStatusLabel(status: TransactionItem['status']): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

export function isTransactionSortActive(current: SortField, field: SortField): boolean {
  return current === field;
}

export function transactionSortAria(
  current: SortField,
  direction: SortDirection,
  field: SortField,
): 'ascending' | 'descending' | null {
  return current !== field ? null : direction === 'ASC' ? 'ascending' : 'descending';
}

export function transactionSortIcon(
  current: SortField,
  direction: SortDirection,
  field: SortField,
): string {
  return current === field && direction === 'ASC'
    ? 'm7 14 5-5 5 5M12 19V5'
    : 'm7 10 5 5 5-5M12 5v14';
}
