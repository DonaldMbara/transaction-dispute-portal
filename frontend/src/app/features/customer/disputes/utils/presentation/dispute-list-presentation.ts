import {formatZar} from '../../../../../shared/utils/zar-formatter';
import {DisputeStatus} from '../../models/dispute-models';

export type DisputeListTab = 'ALL' | 'ACTIVE' | 'RESOLVED';
export type DisputeListSortField = 'createdAt' | 'status';

export const DISPUTE_STATUS_OPTIONS: readonly { value: DisputeStatus; label: string }[] = [
  {value: 'OPEN', label: 'Received'},
  {value: 'UNDER_REVIEW', label: 'Under review'},
  {value: 'ACCEPTED', label: 'Accepted'},
  {value: 'REJECTED', label: 'Rejected'},
];

export function formatDisputeListAmount(amount: string): string {
  return formatZar(amount);
}

export function disputeListAriaSort(
  sortBy: DisputeListSortField,
  direction: 'ASC' | 'DESC',
  field: DisputeListSortField,
): 'ascending' | 'descending' | null {
  return sortBy !== field ? null : direction === 'ASC' ? 'ascending' : 'descending';
}

export function disputeListSortIcon(
  sortBy: DisputeListSortField,
  direction: 'ASC' | 'DESC',
  field: DisputeListSortField,
): string {
  return sortBy === field && direction === 'ASC'
    ? 'm7 14 5-5 5 5M12 19V5'
    : 'm7 10 5 5 5-5M12 5v14';
}

export function disputeListSortSummary(
  sortBy: DisputeListSortField,
  direction: 'ASC' | 'DESC',
): string {
  if (sortBy === 'status') return direction === 'ASC' ? 'Status · A to Z' : 'Status · Z to A';
  return direction === 'DESC' ? 'Newest first ↓' : 'Oldest first ↑';
}

export function disputeListTabLabel(
  tab: DisputeListTab,
  label: string,
  counts: Readonly<Record<DisputeListTab, number | null>>,
): string {
  const count = counts[tab];
  return count === null ? label : `${label} · ${count}`;
}
