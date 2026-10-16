import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AccountApi } from '../../accounts/account-api';
import { AccountSummary } from '../../accounts/account-models';
import { DisputeListDataSource } from './data/dispute-list-data-source';
import {
  DisputeListResults,
} from './components/dispute-list-results';
import { DisputeListResultsPresentation } from './components/dispute-list-results.interface';
import {
  DisputeListSortField,
  DisputeListTab,
  DISPUTE_STATUS_OPTIONS,
  disputeListAriaSort,
  disputeListSortIcon,
  disputeListSortSummary,
  disputeListTabLabel,
  formatDisputeListAmount,
} from './presentation/dispute-list-presentation';
import {
  DisputeDatePreset,
  disputeListRouteState,
} from './query/dispute-list-query';
import { DisputeListInteractions } from './query/dispute-list-interactions';
import { AccountSelector } from '../../../../shared/components/account-selector/account-selector';
import { Button } from '../../../../shared/components/button/button';
import {
  DISPUTE_STATUS_ICON_PATHS,
  formatDisputeReason,
  formatDisputeReference,
  formatDisputeStatus,
} from '../models/dispute-presentation';

@Component({
  selector: 'app-customer-disputes',
  standalone: true,
  imports: [ReactiveFormsModule, AccountSelector, Button, DisputeListResults],
  templateUrl: './disputes.html',
  styleUrl: './disputes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DisputeListDataSource],
})
export class Disputes {
  private readonly accountsApi = inject(AccountApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly search = new FormControl('', { nonNullable: true });
  readonly statusDropdown = new FormControl('ALL', { nonNullable: true });
  readonly datePreset = new FormControl<DisputeDatePreset>('any', { nonNullable: true });
  readonly fromDate = new FormControl('', { nonNullable: true });
  readonly toDate = new FormControl('', { nonNullable: true });
  readonly accounts = signal<readonly AccountSummary[]>([]);
  readonly selectedAccountId = signal<string | null>(null);
  readonly allAccounts = signal(false);
  readonly tab = signal<DisputeListTab>('ALL');
  private readonly data = inject(DisputeListDataSource);
  readonly items = this.data.items;
  readonly total = this.data.total;
  readonly tabCounts = this.data.tabCounts;
  readonly page = signal(1);
  readonly pageSize = 20;
  readonly totalPages = computed(() => Math.ceil(this.total() / this.pageSize));
  readonly loading = this.data.loading;
  readonly error = this.data.error;
  readonly sortDirection = signal<'ASC' | 'DESC'>('DESC');
  readonly sortBy = signal<DisputeListSortField>('createdAt');
  readonly statusIconPaths = DISPUTE_STATUS_ICON_PATHS;
  readonly statuses = DISPUTE_STATUS_OPTIONS;
  readonly tabs: readonly { value: DisputeListTab; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'RESOLVED', label: 'Resolved' },
  ];
  get currentUrl(): string {
    return this.router.url;
  }
  readonly presentation = {
    formatAmount: formatDisputeListAmount,
    ariaSort: (field: DisputeListSortField) =>
      disputeListAriaSort(this.sortBy(), this.sortDirection(), field),
    sortIcon: (field: DisputeListSortField) =>
      disputeListSortIcon(this.sortBy(), this.sortDirection(), field),
    sortSummary: () => disputeListSortSummary(this.sortBy(), this.sortDirection()),
    tabLabel: (tab: DisputeListTab, label: string) =>
      disputeListTabLabel(tab, label, this.tabCounts()),
  };
  readonly resultsPresentation: DisputeListResultsPresentation = {
    formatAmount: formatDisputeListAmount,
    formatReference: formatDisputeReference,
    formatReason: formatDisputeReason,
    displayStatus: formatDisputeStatus,
  };
  readonly interactions = new DisputeListInteractions({
    route: this.route,
    router: this.router,
    search: this.search,
    status: this.statusDropdown,
    datePreset: this.datePreset,
    fromDate: this.fromDate,
    toDate: this.toDate,
    page: this.page,
    total: this.total,
    tab: this.tab,
    sortBy: this.sortBy,
    sortDirection: this.sortDirection,
    pageSize: this.pageSize,
    load: () => this.load(),
  });
  private accountsLoaded = false;

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.page.set(1);
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { search: value || null, page: null },
          queryParamsHandling: 'merge',
        });
        this.load();
      });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const state = disputeListRouteState(params);
      this.allAccounts.set(state.allAccounts);
      this.selectedAccountId.set(state.allAccounts ? null : state.accountId);
      if (state.allAccounts && state.accountId)
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { accountId: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      this.page.set(state.page);
      this.sortBy.set(state.sortBy);
      this.sortDirection.set(state.sortDirection);
      this.tab.set(state.tab);
      this.search.setValue(state.search, { emitEvent: false });
      this.statusDropdown.setValue(state.status, { emitEvent: false });
      this.fromDate.setValue(state.fromDate, { emitEvent: false });
      this.toDate.setValue(state.toDate, { emitEvent: false });
      this.datePreset.setValue(state.datePreset, { emitEvent: false });
      this.load();
    });
    this.accountsApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.accountsLoaded = true;
          this.accounts.set(page.items);
          const current = this.route.snapshot.queryParamMap;
          if (!current.has('accountId') && current.get('accounts') !== 'all') {
            this.allAccounts.set(true);
            void this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { accounts: 'all' },
              replaceUrl: true,
            });
          }
          // Load immediately after account discovery. Route navigation updates the URL,
          // but the list should not depend on a query-param emission to start its first request.
          this.load();
        },
        error: () => {
          this.accountsLoaded = true;
          this.error.set('generic');
          this.loading.set(false);
        },
      });
  }

  selectAccount(id: string | null): void {
    this.page.set(1);
    this.allAccounts.set(id === null);
    this.selectedAccountId.set(id);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { accountId: id, accounts: id === null ? 'all' : null, page: null },
      queryParamsHandling: 'merge',
    });
  }

  load(): void {
    if (
      !this.accountsLoaded &&
      !this.route.snapshot.queryParamMap.has('accountId') &&
      this.route.snapshot.queryParamMap.get('accounts') !== 'all'
    )
      return;
    const status =
      this.statusDropdown.value !== 'ALL'
        ? this.statusDropdown.value
        : this.tab() === 'ALL'
          ? undefined
          : this.tab();
    const query = {
      page: this.page(),
      size: this.pageSize,
      accountId: this.allAccounts() ? undefined : (this.selectedAccountId() ?? undefined),
      status,
      search: this.search.value,
      fromDate: this.fromDate.value || undefined,
      toDate: this.toDate.value || undefined,
      sortBy: this.sortBy(),
      sortDirection: this.sortDirection(),
    };
    this.data.load(query, this.allAccounts());
  }
}
