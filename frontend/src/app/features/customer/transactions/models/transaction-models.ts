import type { TransactionDirection } from '../../../../shared/models/domain-enums';

export type { TransactionDirection };
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED';
export type DirectionFilter = 'ALL' | TransactionDirection;
export type StatusFilter = 'ALL' | TransactionStatus;
export type SortField = 'occurredAt' | 'amount' | 'merchant' | 'reference';
export type SortDirection = 'ASC' | 'DESC';

export interface TransactionAccount {
  id: string;
  maskedNumber: string;
}
export interface DisputeEligibility {
  id: string | null;
  eligible: boolean;
  ineligibilityReason: string | null;
}
export interface TransactionItem {
  id: string;
  reference: string;
  merchant: string;
  amount: string;
  currency: 'ZAR';
  direction: TransactionDirection;
  status: TransactionStatus;
  occurredAt: string;
  account: TransactionAccount;
  dispute: DisputeEligibility;
}
export interface TransactionPage {
  total: number;
  page: number;
  size: number;
  items: TransactionItem[];
}
export interface TransactionQueryState {
  page: number;
  size: number;
  search: string;
  direction: DirectionFilter;
  status: StatusFilter;
  fromDate: string;
  toDate: string;
  minAmount: string;
  maxAmount: string;
  sortBy: SortField;
  sortDirection: SortDirection;
  accountId: string;
}
