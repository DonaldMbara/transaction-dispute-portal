export type AgentQueue = 'unassigned' | 'mine' | 'all';
export type AgentDisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'ACCEPTED' | 'REJECTED';
export type AgentReason =
  | 'UNRECOGNIZED_TRANSACTION'
  | 'DUPLICATE_CHARGE'
  | 'INCORRECT_AMOUNT'
  | 'GOODS_OR_SERVICES_NOT_RECEIVED'
  | 'OTHER';
export type AgentSortField = 'createdAt' | 'updatedAt' | 'status';
export interface AgentIdentitySummary {
  id: string;
  email: string;
}
export interface AgentDisputeListItem {
  id: string;
  reference: string;
  merchant: string;
  amount: string;
  currency: string;
  direction: 'IN' | 'OUT';
  reason: AgentReason;
  status: AgentDisputeStatus;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  assignedAgent: AgentIdentitySummary | null;
  version: number;
}
export interface AgentDisputeEvent {
  id: string;
  disputeVersion: number;
  eventType: 'CREATED' | 'CLAIMED' | 'RELEASED' | 'RESOLVED_ACCEPTED' | 'RESOLVED_REJECTED';
  actorId: string;
  fromStatus: AgentDisputeStatus | null;
  toStatus: AgentDisputeStatus;
  previousAgentId: string | null;
  newAgentId: string | null;
  internalNotes: string | null;
  customerMessage: string | null;
  createdAt: string;
}
export interface AgentDisputeDetail extends AgentDisputeListItem {
  transactionId: string;
  occurredAt: string;
  transactionReference: string;
  accountId: string;
  accountDisplayName: string;
  maskedAccountNumber: string;
  description: string;
  assignedAt: string | null;
  internalResolutionNotes: string | null;
  customerResolutionMessage: string | null;
  events: AgentDisputeEvent[];
}
export interface AgentDisputePage {
  total: number;
  page: number;
  size: number;
  items: AgentDisputeListItem[];
}
export interface AgentDisputeQuery {
  queue: AgentQueue;
  page: number;
  size: number;
  search?: string;
  status?: string;
  reason?: string;
  fromDate?: string;
  toDate?: string;
  sortBy: AgentSortField;
  sortDirection: 'ASC' | 'DESC';
}
export interface AgentCaseView {
  detail: AgentDisputeDetail;
  etag: string;
}
export interface AgentReleaseRequest {
  reason: string;
}
export interface AgentResolveRequest {
  decision: 'ACCEPT' | 'REJECT';
  internalResolutionNotes: string;
  customerResolutionMessage: string;
}
