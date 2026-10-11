import { AccountSummary } from '../../../shared/models/account-summary';

export type { AccountSummary } from '../../../shared/models/account-summary';

export interface AccountPage {
  items: AccountSummary[];
  page: number;
  size: number;
  total: number;
}
