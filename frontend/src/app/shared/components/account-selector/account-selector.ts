import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AccountSummary } from '../../models/account-summary';
import { formatZar } from '../../../features/customer/accounts/balance-formatter';

@Component({
  selector: 'app-account-selector', standalone: true,
  templateUrl: './account-selector.html', styleUrl: './account-selector.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccountSelector {
  readonly accounts = input.required<readonly AccountSummary[]>();
  readonly selectedAccountId = input.required<string | null>();
  readonly selectionChange = output<string>();
  readonly formatBalance = formatZar;
  get selected(): AccountSummary | undefined {
    return this.accounts().find(account => account.id === this.selectedAccountId());
  }
  select(id: string): void { this.selectionChange.emit(id); }
}
