import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AccountApi } from './account-api';
import { AccountSummary } from './account-contracts';
import { formatZar } from './balance-formatter';

interface DashboardQuickLink {
  label: string;
  routerLink: string[];
  queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-account-dashboard', standalone: true, imports: [RouterLink],
  templateUrl: './account-dashboard.html', styleUrl: './account-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccountDashboard implements OnInit {
  private readonly api = inject(AccountApi);
  private readonly router = inject(Router);
  readonly state = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly accounts = signal<AccountSummary[]>([]);
  readonly quickLinks = signal<DashboardQuickLink[]>([]);
  readonly formatBalance = formatZar;
  ngOnInit(): void { this.load(); }
  load(): void {
    this.state.set('loading');
    this.api.list().subscribe({ next: page => { this.accounts.set(page.items); this.state.set('loaded'); }, error: () => this.state.set('error') });
  }
  openTransactions(event: KeyboardEvent, id: string): void {
    if (event.code !== 'Space') return;
    event.preventDefault();
    void this.router.navigate(['/customer/transactions'], { queryParams: { accountId: id } });
  }
}
