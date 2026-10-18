import type {DisputeReason, DisputeStatus} from '../../../../shared/models/domain-enums';
import {formatZar} from '../../../../shared/utils/zar-formatter';

/** Shared customer-facing labels and formatting for dispute dispute-list/case-detail views. */
const REASON_LABELS: Readonly<Record<DisputeReason, string>> = {
  UNRECOGNIZED_TRANSACTION: 'Unrecognised transaction',
  DUPLICATE_CHARGE: 'Duplicate charge',
  INCORRECT_AMOUNT: 'Incorrect amount',
  GOODS_OR_SERVICES_NOT_RECEIVED: 'Goods or services not received',
  OTHER: 'Other',
};

const STATUS_LABELS: Readonly<Record<DisputeStatus, string>> = {
  OPEN: 'Received',
  UNDER_REVIEW: 'Under review',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
};

export const DISPUTE_STATUS_ICON_PATHS: Readonly<Record<DisputeStatus, readonly string[]>> = {
  OPEN: ['M4 5h16v14H4z', 'M4 9h4l2 3h4l2-3h4'],
  UNDER_REVIEW: ['M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18', 'M12 7v5l3 2'],
  ACCEPTED: ['M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18', 'm8 12 2.5 2.5L16 9'],
  REJECTED: ['M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18', 'm9 9 6 6', 'm15 9-6 6'],
};

export function formatDisputeReference(id: string): string {
  return `DSP-${id.replace(/-/gu, '').slice(0, 8).toUpperCase()}`;
}

export function formatDisputeReason(reason: DisputeReason): string {
  return REASON_LABELS[reason];
}

export function formatDisputeStatus(status: DisputeStatus): string {
  return STATUS_LABELS[status];
}

export function formatDisputeAmount(amount: string, direction: 'IN' | 'OUT'): string {
  return `${direction === 'OUT' ? '−' : '+'}${formatZar(amount)}`;
}
