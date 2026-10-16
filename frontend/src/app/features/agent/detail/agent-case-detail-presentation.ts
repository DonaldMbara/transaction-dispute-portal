import { formatZar } from '../../../shared/utils/zar-formatter';
import { ParamMap } from '@angular/router';
import {
  AgentDisputeEvent,
  AgentDisputeStatus,
} from '../models/agent-dispute-models';
import { AgentDisputeDetail } from '../models/agent-dispute-models';

export interface AgentCaseDetailPresentation {
  statusLabel(status: AgentDisputeStatus): string;
  statusClass(status: AgentDisputeStatus): string;
  reasonLabel(reason: string): string;
  formatAmount(amount: string): string;
  transactionAmount(amount: string, direction: 'IN' | 'OUT'): string;
  eventActor(event: AgentDisputeEvent): string;
  eventLabel(eventType: AgentDisputeEvent['eventType']): string;
}

export type AgentCaseSummary = Pick<
  AgentDisputeDetail,
  | 'merchant'
  | 'amount'
  | 'direction'
  | 'accountDisplayName'
  | 'maskedAccountNumber'
  | 'occurredAt'
  | 'transactionReference'
  | 'reason'
  | 'description'
  | 'createdAt'
  | 'internalResolutionNotes'
  | 'events'
>;

const STATUS_LABELS: Readonly<Record<AgentDisputeStatus, string>> = {
  OPEN: 'Received',
  UNDER_REVIEW: 'Under review',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
};

const REASON_LABELS: Readonly<Record<string, string>> = {
  UNRECOGNIZED_TRANSACTION: 'Unrecognised transaction',
  DUPLICATE_CHARGE: 'Duplicate charge',
  INCORRECT_AMOUNT: 'Incorrect amount',
  GOODS_OR_SERVICES_NOT_RECEIVED: 'Goods or services not received',
  OTHER: 'Other',
};

const EVENT_LABELS: Readonly<Record<AgentDisputeEvent['eventType'], string>> = {
  CREATED: 'Case created',
  CLAIMED: 'Claimed',
  RELEASED: 'Released to queue',
  RESOLVED_ACCEPTED: 'Accepted',
  RESOLVED_REJECTED: 'Rejected',
};

export function agentCaseStatusLabel(status: AgentDisputeStatus): string {
  return STATUS_LABELS[status];
}

export function agentCaseStatusClass(status: AgentDisputeStatus): string {
  return status.toLowerCase().replace('_', '-');
}

export function agentCaseReasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? 'Other';
}

export function formatAgentCaseAmount(amount: string): string {
  return formatZar(amount);
}

export function formatAgentTransactionAmount(amount: string, direction: 'IN' | 'OUT'): string {
  return `${direction === 'OUT' ? '−' : '+'}${formatZar(amount)}`;
}

export function agentEventActor(event: AgentDisputeEvent, agentId: string | undefined): string {
  if (event.eventType === 'CREATED') return 'Customer';
  return event.actorId === agentId ? 'You' : 'Agent';
}

export function agentEventLabel(eventType: AgentDisputeEvent['eventType']): string {
  return EVENT_LABELS[eventType];
}

export function agentCaseReturnQueryParams(params: ParamMap): Record<string, string> {
  const allowed = [
    'queue',
    'search',
    'status',
    'reason',
    'fromDate',
    'toDate',
    'sortBy',
    'sortDirection',
    'page',
    'size',
  ];
  const result: Record<string, string> = {};
  for (const key of allowed) {
    const value = params.get(key);
    if (value !== null) result[key] = value;
  }
  return result;
}