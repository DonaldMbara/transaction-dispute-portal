import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AccountSummary } from '../../models/account-summary';
import { formatZar } from '../../utils/zar-formatter';

export type AccountCardTrailing = 'chevron' | 'selected' | 'none';

const ACCOUNT_ICON_PATHS: Record<AccountSummary['accountType'], string> = {
  MAIN: 'M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4',
  SAVINGS: 'M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1zM16 10h.01M2 8v1a2 2 0 0 0 2 2h1'
};

@Component({
  selector: 'app-account-card',
  standalone: true,
  templateUrl: './account-card.html',
  styleUrl: './account-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccountCard {
  readonly account = input.required<AccountSummary>();
  readonly trailing = input<AccountCardTrailing>('chevron');
  readonly highlighted = input(false);
  readonly expanded = input(false);
  readonly formatBalance = formatZar;

  iconPath(type: AccountSummary['accountType']): string {
    return ACCOUNT_ICON_PATHS[type];
  }
}
