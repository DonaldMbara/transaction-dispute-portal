import type {DisputeReason, DisputeStatus} from '../../../../shared/models/domain-enums';

export type {DisputeReason, DisputeStatus};

// Existing interfaces used by dispute submission — preserved
export interface SubmitDisputeRequest {
  transactionId: string;
  reason: DisputeReason;
  description: string;
}

export interface DisputeSubmission {
  id: string;
  transactionId: string;
  reason: DisputeReason;
  description: string;
  status: 'OPEN';
  createdAt: string;
}

// New interfaces for dispute history
export interface DisputeAccountSummary {
  id: string;
  displayName: string;
  maskedNumber: string;
}

export interface DisputeTransactionSummary {
  id: string;
  merchant: string;
  reference: string;
  amount: string;
  currency: string;
  direction: 'IN' | 'OUT';
  occurredAt: string;
  account: DisputeAccountSummary;
}

export type TimelineType = 'SUBMITTED' | 'IN_REVIEW' | 'RETURNED_TO_QUEUE' | 'RESOLVED';

export interface TimelineEntry {
  type: TimelineType;
  message: string;
  occurredAt: string;
}

export interface DisputeDecision {
  outcome: 'ACCEPTED' | 'REJECTED';
  message: string;
}

export interface DisputeListItem {
  id: string;
  transaction: DisputeTransactionSummary;
  reason: DisputeReason;
  status: DisputeStatus;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface DisputeDetail extends DisputeListItem {
  description: string;
  decision: DisputeDecision | null;
  timeline: TimelineEntry[];
}

export interface DisputePage {
  total: number;
  page: number;
  size: number;
  items: DisputeListItem[];
}

export interface DisputeListQuery {
  page?: number;
  size?: number;
  accountId?: string;
  status?: string;
  reason?: string;
  fromDate?: string;
  toDate?: string;
  search?: string;
  sortBy?: string;
  sortDirection?: string;
  // Note: 'accounts=all' is a URL-only marker; it is never sent as a query param to the API.
}
