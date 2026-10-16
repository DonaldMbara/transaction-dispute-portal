import { formatZar } from '../../../shared/utils/zar-formatter';
import {
  AgentDisputeListItem,
  AgentDisputeStatus,
  AgentQueue,
  AgentReason,
  AgentSortField,
} from '../models/agent-dispute-models';

export interface AgentCaseQueuePresentation {
  label(status: AgentDisputeStatus): string;
  statusClass(status: AgentDisputeStatus): string;
  reasonLabel(reason: AgentReason): string;
  amount(amount: string): string;
  action(item: AgentDisputeListItem): 'Review case' | 'Investigate' | 'View case';
  actionVariant(item: AgentDisputeListItem): 'primary' | 'secondary';
  ariaSort(field: AgentSortField): 'ascending' | 'descending' | 'none';
  sortSummary(): string;
}

export const AGENT_QUEUES: readonly { value: AgentQueue; label: string }[] = [
  { value: 'unassigned', label: 'Unassigned' },
  { value: 'mine', label: 'My Cases' },
  { value: 'all', label: 'All Disputes' },
];

export const AGENT_REASONS: readonly { value: AgentReason; label: string }[] = [
  { value: 'UNRECOGNIZED_TRANSACTION', label: 'Unrecognised transaction' },
  { value: 'DUPLICATE_CHARGE', label: 'Duplicate charge' },
  { value: 'INCORRECT_AMOUNT', label: 'Incorrect amount' },
  { value: 'GOODS_OR_SERVICES_NOT_RECEIVED', label: 'Goods or services not received' },
  { value: 'OTHER', label: 'Other' },
];

const STATUS_LABELS: Readonly<Record<AgentDisputeStatus, string>> = {
  OPEN: 'Received',
  UNDER_REVIEW: 'Under review',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
};

export function agentStatusLabel(status: AgentDisputeStatus): string {
  return STATUS_LABELS[status];
}

export function agentStatusClass(status: AgentDisputeStatus): string {
  return status.toLowerCase().replace('_', '-');
}

export function agentReasonLabel(reason: AgentReason): string {
  return AGENT_REASONS.find((item) => item.value === reason)?.label ?? 'Other';
}

export function caseQueueAction(
  item: AgentDisputeListItem,
  agentId: string | undefined,
): 'Review case' | 'Investigate' | 'View case' {
  if (item.status === 'OPEN' && item.assignedAgent === null) return 'Review case';
  if (item.status === 'UNDER_REVIEW' && item.assignedAgent?.id === agentId) return 'Investigate';
  return 'View case';
}

export function caseQueueActionVariant(item: AgentDisputeListItem): 'primary' | 'secondary' {
  return item.status === 'OPEN' && item.assignedAgent === null ? 'primary' : 'secondary';
}

export function caseQueueAriaSort(
  current: AgentSortField,
  direction: 'ASC' | 'DESC',
  field: AgentSortField,
): 'ascending' | 'descending' | 'none' {
  return current !== field ? 'none' : direction === 'ASC' ? 'ascending' : 'descending';
}

export function caseQueueSortSummary(
  field: AgentSortField,
  direction: 'ASC' | 'DESC',
): string {
  if (field === 'status') return `Status · ${direction === 'ASC' ? 'A to Z' : 'Z to A'}`;
  if (field === 'updatedAt')
    return `Updated · ${direction === 'DESC' ? 'Newest first ↓' : 'Oldest first ↑'}`;
  return direction === 'DESC' ? 'Created date · Newest first ↓' : 'Created date · Oldest first ↑';
}

export function formatCaseQueueAmount(amount: string): string {
  return formatZar(amount);
}