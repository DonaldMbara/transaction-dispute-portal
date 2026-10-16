import { FormControl, FormGroup } from '@angular/forms';
import { ParamMap, Params } from '@angular/router';
import {
  DirectionFilter,
  SortDirection,
  SortField,
  StatusFilter,
} from '../../models/transaction-models';

export interface TransactionFilterControls {
  search: FormControl<string>;
  direction: FormControl<DirectionFilter>;
  status: FormControl<StatusFilter>;
  fromDate: FormControl<string>;
  toDate: FormControl<string>;
  minAmount: FormControl<string>;
  maxAmount: FormControl<string>;
  sortBy: FormControl<SortField>;
  sortDirection: FormControl<SortDirection>;
  size: FormControl<string>;
}

export type TransactionFilterForm = FormGroup<TransactionFilterControls>;

export interface TransactionFilterValues {
  search: string;
  direction: DirectionFilter;
  status: StatusFilter;
  fromDate: string;
  toDate: string;
  minAmount: string;
  maxAmount: string;
  sortBy: SortField;
  sortDirection: SortDirection;
  size: string;
}

export function transactionFilterValues(params: ParamMap): TransactionFilterValues {
  return {
    search: params.get('search') ?? '',
    direction: (params.get('direction') ?? 'ALL') as DirectionFilter,
    status: (params.get('status') ?? 'ALL') as StatusFilter,
    fromDate: params.get('fromDate') ?? '',
    toDate: params.get('toDate') ?? '',
    minAmount: params.get('minAmount') ?? '',
    maxAmount: params.get('maxAmount') ?? '',
    sortBy: (params.get('sortBy') ?? 'occurredAt') as SortField,
    sortDirection: (params.get('sortDirection') ?? 'DESC') as SortDirection,
    size: params.get('size') ?? '20',
  };
}

export function transactionFilterError(value: TransactionFilterValues): string | null {
  if (value.fromDate && value.toDate) {
    const from = Date.parse(`${value.fromDate}T00:00:00Z`);
    const to = Date.parse(`${value.toDate}T00:00:00Z`);
    if (from > to) return 'The start date must be on or before the end date.';
    if ((to - from) / 86_400_000 > 366) return 'Choose a date range of 366 days or less.';
  }
  const min = value.minAmount.trim() === '' ? null : Number(value.minAmount);
  const max = value.maxAmount.trim() === '' ? null : Number(value.maxAmount);
  if (min !== null && max !== null && Number.isFinite(min) && Number.isFinite(max) && min > max)
    return 'The minimum amount must be less than or equal to the maximum amount.';
  return null;
}

export function activeTransactionFilterCount(value: TransactionFilterValues): number {
  return [
    value.search.trim() !== '',
    value.direction !== 'ALL',
    value.status !== 'ALL',
    value.fromDate !== '' || value.toDate !== '',
    value.minAmount !== '' || value.maxAmount !== '',
  ].filter(Boolean).length;
}

export function transactionFilterParams(value: TransactionFilterValues): Params {
  const params: Params = { page: 1 };
  for (const key of [
    'search',
    'direction',
    'status',
    'fromDate',
    'toDate',
    'minAmount',
    'maxAmount',
    'sortBy',
    'sortDirection',
    'size',
  ] as const) {
    const current =
      key === 'search' ? value.search.replace(/[\s\u00a0]+/gu, ' ').trim() : value[key];
    const defaultValue =
      key === 'direction' || key === 'status'
        ? 'ALL'
        : key === 'sortDirection'
          ? 'DESC'
          : key === 'sortBy'
            ? 'occurredAt'
            : key === 'size'
              ? '20'
              : '';
    params[key] = current === defaultValue ? null : current;
  }
  return params;
}
