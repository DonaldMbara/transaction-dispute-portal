import {ParamMap} from '@angular/router';
import {DisputeListSortField, DisputeListTab} from '../presentation/dispute-list-presentation';

export type DisputeDatePreset = 'any' | 'last7' | 'last30' | 'custom';

export interface DisputeListRouteState {
  accountId: string | null;
  allAccounts: boolean;
  page: number;
  sortBy: DisputeListSortField;
  sortDirection: 'ASC' | 'DESC';
  tab: DisputeListTab;
  search: string;
  status: string;
  fromDate: string;
  toDate: string;
  datePreset: DisputeDatePreset;
}

export function disputeListRouteState(params: ParamMap): DisputeListRouteState {
  const fromDate = params.get('fromDate') ?? '';
  const toDate = params.get('toDate') ?? '';
  const rawStatus = params.get('status');
  const rawTab = params.get('tab') ?? rawStatus;
  return {
    accountId: params.get('accountId'),
    allAccounts: params.get('accounts') === 'all',
    page: Math.max(1, Number(params.get('page')) || 1),
    sortBy: params.get('sortBy') === 'status' ? 'status' : 'createdAt',
    sortDirection: params.get('sortDirection') === 'ASC' ? 'ASC' : 'DESC',
    tab: rawTab === 'ACTIVE' || rawTab === 'RESOLVED' ? rawTab : 'ALL',
    search: params.get('search') ?? '',
    status:
      rawStatus && !['ALL', 'ACTIVE', 'RESOLVED'].includes(rawStatus) ? rawStatus : 'ALL',
    fromDate,
    toDate,
    datePreset: fromDate || toDate ? 'custom' : 'any',
  };
}

export function disputeDatePresetRange(
  preset: DisputeDatePreset,
  now = new Date(),
): { fromDate: string; toDate: string } | null {
  if (preset === 'custom') return null;
  if (preset === 'any') return {fromDate: '', toDate: ''};
  const dateFormat = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Johannesburg',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  const today = dateFormat.format(now);
  const start = new Date(`${today}T12:00:00+02:00`);
  start.setDate(start.getDate() - (preset === 'last7' ? 6 : 29));
  return {fromDate: dateFormat.format(start), toDate: today};
}
