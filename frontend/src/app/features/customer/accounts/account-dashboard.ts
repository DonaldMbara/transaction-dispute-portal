import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal,} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AccountApi} from './serviecs/account-api';
import {AccountSummary} from './models/account-models';
import {DashboardQuickLink} from './interfaces/account-dashboard.interface';
import {formatZar} from '../../../shared/utils/zar-formatter';
import {AsyncViewState} from '../../../shared/models/async-state';
import {AccountCard} from '../../../shared/components/account-card/account-card';
import {LoadingSpinner} from '../../../shared/components/loading-spinner/loading-spinner';
import {Button} from '../../../shared/components/button/button';

@Component({
  selector: 'app-account-dashboard',
  standalone: true,
  imports: [RouterLink, AccountCard, LoadingSpinner, Button],
  templateUrl: './account-dashboard.html',
  styleUrl: './account-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDashboard implements OnInit {
  readonly quickLinks = computed<DashboardQuickLink[]>(() => {
    const accounts = this.accounts();
    const primary = accounts.find((account) => account.primary) ?? accounts[0];
    return primary
      ? [
        {
          label: 'View transactions',
          routerLink: ['/customer/transactions'],
          queryParams: {accountId: primary.id},
        },
        {label: 'View disputes', routerLink: ['/customer/disputes']},
      ]
      : [];
  });
  readonly formatBalance = formatZar;
  private readonly api = inject(AccountApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly view = signal<AsyncViewState<AccountSummary[]>>({status: 'loading'});
  readonly state = computed(() => this.view().status);
  readonly accounts = computed(() => {
    const view = this.view();
    return 'value' in view ? view.value : [];
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const current = this.accounts();
    this.view.set(current.length ? {status: 'busy', value: current} : {status: 'loading'});
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.view.set(
            page.items.length
              ? {status: 'ready', value: page.items}
              : {status: 'none', value: []},
          );
        },
        error: () => this.view.set({status: 'error'}),
      });
  }

  openTransactions(event: KeyboardEvent, id: string): void {
    if (event.code !== 'Space') return;
    event.preventDefault();
    void this.router.navigate(['/customer/transactions'], {queryParams: {accountId: id}});
  }
}
