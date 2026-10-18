import {DisputeListItem, DisputeStatus} from '../models/dispute-models';

export interface DisputeListResultsPresentation {
  formatReference(id: string): string;

  formatReason(reason: DisputeListItem['reason']): string;

  formatAmount(amount: string): string;

  displayStatus(status: DisputeStatus): string;
}
