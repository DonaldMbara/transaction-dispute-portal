import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AccountSummary } from '../../models/account-summary';
import { formatZar } from '../../utils/zar-formatter';
import { ACCOUNT_ICON_PATHS } from './account-card.constant';
import { AccountCardTrailing } from './account-card.interface';

@Component({
  selector: 'app-account-card',
  standalone: true,
  templateUrl: './account-card.html',
  styleUrl: './account-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
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
