import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';
import { AccountApi } from '../accounts/account-api';
import { AccountSummary } from '../accounts/account-contracts';
import { DisputeApi } from './dispute-api';
import { DisputeListItem, DisputeStatus } from './dispute-models';
import { AccountSelector } from '../../../shared/components/account-selector/account-selector';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import { Button } from '../../../shared/components/button/button';
import { formatZar } from '../../../shared/utils/zar-formatter';
import { DISPUTE_STATUS_ICON_PATHS, formatDisputeAmount, formatDisputeReason, formatDisputeReference, formatDisputeStatus } from './dispute-presentation';

type Tab = 'ALL' | 'ACTIVE' | 'RESOLVED';
type DatePreset = 'any' | 'last7' | 'last30' | 'custom';
type SortField = 'createdAt' | 'status';
const STATUS_OPTIONS: readonly { value: DisputeStatus; label: string }[] = [
  { value: 'OPEN', label: 'Received' }, { value: 'UNDER_REVIEW', label: 'Under review' },
  { value: 'ACCEPTED', label: 'Accepted' }, { value: 'REJECTED', label: 'Rejected' }
];
@Component({ selector: 'app-customer-disputes', standalone: true,
  imports: [RouterLink, ReactiveFormsModule, DatePipe, AccountSelector, LoadingSpinner, Button],
  templateUrl: './disputes.html', styleUrl: './disputes.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class Disputes {
  private readonly api = inject(DisputeApi);
  private readonly accountsApi = inject(AccountApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly search = new FormControl('', { nonNullable: true });
  readonly statusDropdown = new FormControl('ALL', { nonNullable: true });
  readonly datePreset = new FormControl<DatePreset>('any', { nonNullable: true });
  readonly fromDate = new FormControl('', { nonNullable: true });
  readonly toDate = new FormControl('', { nonNullable: true });
  readonly accounts = signal<readonly AccountSummary[]>([]);
  readonly selectedAccountId = signal<string | null>(null);
  readonly allAccounts = signal(false);
  readonly tab = signal<Tab>('ALL');
  readonly items = signal<readonly DisputeListItem[]>([]);
  readonly total = signal(0);
  readonly tabCounts = signal<Readonly<Record<Tab, number | null>>>({ ALL: null, ACTIVE: null, RESOLVED: null });
  readonly page = signal(1);
  readonly pageSize = 20;
  readonly totalPages = computed(() => Math.ceil(this.total() / this.pageSize));
  readonly loading = signal(true);
  readonly error = signal<'generic' | 'account' | ''>('');
  readonly sortDirection = signal<'ASC' | 'DESC'>('DESC');
  readonly sortBy = signal<SortField>('createdAt');
  readonly statusIconPaths = DISPUTE_STATUS_ICON_PATHS;
  readonly statuses = STATUS_OPTIONS;
  readonly tabs: readonly { value: Tab; label: string }[] = [{ value: 'ALL', label: 'All' }, { value: 'ACTIVE', label: 'Active' }, { value: 'RESOLVED', label: 'Resolved' }];
  readonly formatListAmount = (amount: string): string => formatZar(amount);
  readonly formatAmount = formatDisputeAmount;
  get currentUrl(): string { return this.router.url; }
  readonly formatDisputeRef = formatDisputeReference;
  readonly formatReason = formatDisputeReason;
  readonly displayStatus = formatDisputeStatus;
  private accountsLoaded = false;
  private requestGeneration = 0;
  private readonly requests = new Subject<{ generation: number; query: Parameters<DisputeApi['list']>[0] }>();

  constructor() {
    this.requests.pipe(
      switchMap(({ generation, query }) => forkJoin({
        page: this.api.list(query),
        all: this.countRequest(query, undefined),
        active: this.countRequest(query, 'ACTIVE'),
        resolved: this.countRequest(query, 'RESOLVED')
      }).pipe(
        map(({ page, all, active, resolved }) => ({ generation, response: page, counts: { ALL: all, ACTIVE: active, RESOLVED: resolved }, failure: null as unknown })),
        catchError((failure: unknown) => of({ generation, response: null, counts: null, failure }))
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      if (result.generation !== this.requestGeneration) return;
      this.loading.set(false);
      if (result.failure !== null) {
        this.error.set(result.failure instanceof HttpErrorResponse && result.failure.status === 404 && !this.allAccounts() ? 'account' : 'generic');
      } else if (!result.response?.status || !result.response.data) {
        this.items.set([]); this.total.set(0);
      } else {
        this.items.set(result.response.data.items); this.total.set(result.response.data.total);
        if (result.counts) this.tabCounts.set(result.counts);
      }
    });
    this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      this.page.set(1);
      void this.router.navigate([], { relativeTo: this.route, queryParams: { search: value || null, page: null }, queryParamsHandling: 'merge' });
      this.load();
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const accountId = params.get('accountId');
      const all = params.get('accounts') === 'all';
      this.allAccounts.set(all);
      this.selectedAccountId.set(all ? null : accountId);
      if (all && accountId) void this.router.navigate([], { relativeTo: this.route, queryParams: { accountId: null }, queryParamsHandling: 'merge', replaceUrl: true });
      this.page.set(Math.max(1, Number(params.get('page')) || 1));
      this.sortBy.set(params.get('sortBy') === 'status' ? 'status' : 'createdAt');
      this.sortDirection.set(params.get('sortDirection') === 'ASC' ? 'ASC' : 'DESC');
      const tabParam = params.get('tab') ?? params.get('status'); // Accept the earlier status-tab URL shape.
      this.tab.set(tabParam === 'ACTIVE' ? 'ACTIVE' : tabParam === 'RESOLVED' ? 'RESOLVED' : 'ALL');
      this.search.setValue(params.get('search') ?? '', { emitEvent: false });
      const individualStatus = params.get('status');
      this.statusDropdown.setValue(individualStatus && !['ALL', 'ACTIVE', 'RESOLVED'].includes(individualStatus) ? individualStatus : 'ALL', { emitEvent: false });
      this.fromDate.setValue(params.get('fromDate') ?? '', { emitEvent: false });
      this.toDate.setValue(params.get('toDate') ?? '', { emitEvent: false });
      this.datePreset.setValue(this.fromDate.value || this.toDate.value ? 'custom' : 'any', { emitEvent: false });
      this.load();
    });
    this.accountsApi.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.accountsLoaded = true;
        this.accounts.set(page.items);
        const current = this.route.snapshot.queryParamMap;
        if (!current.has('accountId') && current.get('accounts') !== 'all') {
          this.allAccounts.set(true);
          void this.router.navigate([], { relativeTo: this.route, queryParams: { accounts: 'all' }, replaceUrl: true });
        }
      },
      error: () => { this.accountsLoaded = true; this.error.set('generic'); this.loading.set(false); }
    });
  }

  selectAccount(id: string | null): void {
    this.page.set(1);
    this.allAccounts.set(id === null); this.selectedAccountId.set(id);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { accountId: id, accounts: id === null ? 'all' : null, page: null }, queryParamsHandling: 'merge' });
  }
  setTab(value: Tab): void {
    this.page.set(1);
    this.tab.set(value); this.statusDropdown.setValue('ALL');
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: value === 'ALL' ? null : value, status: null, page: null }, queryParamsHandling: 'merge' });
    this.load();
  }
  onTabKeydown(event: KeyboardEvent, current: Tab): void {
    const order: Tab[] = ['ALL', 'ACTIVE', 'RESOLVED'];
    let next = -1;
    if (event.key === 'ArrowRight') next = (order.indexOf(current) + 1) % order.length;
    if (event.key === 'ArrowLeft') next = (order.indexOf(current) + order.length - 1) % order.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = order.length - 1;
    if (next >= 0) { event.preventDefault(); this.setTab(order[next]); document.querySelector<HTMLElement>(`[data-dispute-tab="${order[next]}"]`)?.focus(); }
  }
  onStatusChange(): void {
    this.page.set(1);
    const status = this.statusDropdown.value === 'ALL' ? null : this.statusDropdown.value;
    const statusTab: Tab | null = status === null ? null
      : status === 'OPEN' || status === 'UNDER_REVIEW' ? 'ACTIVE' : 'RESOLVED';
    const tab = statusTab ?? (this.tab() === 'ALL' ? null : this.tab());
    if (statusTab) this.tab.set(statusTab);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { status, tab, page: null }, queryParamsHandling: 'merge' }); this.load();
  }
  onDatePresetChange(): void {
    const preset = this.datePreset.value;
    if (preset === 'any') { this.fromDate.setValue(''); this.toDate.setValue(''); }
    else if (preset !== 'custom') {
      const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
      const end = new Date(`${today}T12:00:00+02:00`); const days = preset === 'last7' ? 6 : 29;
      const start = new Date(end); start.setDate(start.getDate() - days);
      this.toDate.setValue(today); this.fromDate.setValue(new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit', day: '2-digit' }).format(start));
    }
    this.updateDates();
  }
  updateDates(): void {
    this.page.set(1);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { fromDate: this.fromDate.value || null, toDate: this.toDate.value || null, page: null }, queryParamsHandling: 'merge' }); this.load();
  }
  clearFilters(): void {
    this.page.set(1);
    this.search.setValue('', { emitEvent: false }); this.statusDropdown.setValue('ALL'); this.datePreset.setValue('any'); this.fromDate.setValue(''); this.toDate.setValue(''); this.tab.set('ALL');
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: null, status: null, search: null, fromDate: null, toDate: null, page: null }, queryParamsHandling: 'merge' }); this.load();
  }
  toggleSort(field: SortField): void {
    const direction = this.sortBy() === field && this.sortDirection() === 'DESC' ? 'ASC' : 'DESC';
    this.sortBy.set(field);
    this.sortDirection.set(direction);
    this.page.set(1);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { sortBy: field === 'createdAt' ? null : field, sortDirection: direction === 'DESC' ? null : direction, page: null }, queryParamsHandling: 'merge' });
    this.load();
  }
  ariaSort(field: SortField): 'ascending' | 'descending' | null {
    return this.sortBy() === field ? (this.sortDirection() === 'ASC' ? 'ascending' : 'descending') : null;
  }
  sortIcon(field: SortField): string {
    return this.sortBy() === field && this.sortDirection() === 'ASC'
      ? 'm7 14 5-5 5 5M12 19V5'
      : 'm7 10 5 5 5-5M12 5v14';
  }
  sortSummary(): string {
    if (this.sortBy() === 'status') return this.sortDirection() === 'ASC' ? 'Status · A to Z' : 'Status · Z to A';
    return this.sortDirection() === 'DESC' ? 'Newest first ↓' : 'Oldest first ↑';
  }
  tabLabel(tab: Tab, label: string): string {
    const count = this.tabCounts()[tab];
    return count === null ? label : `${label} · ${count}`;
  }
  changePage(value: number): void {
    const page = Math.min(Math.max(1, value), Math.max(1, Math.ceil(this.total() / this.pageSize)));
    this.page.set(page);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { page }, queryParamsHandling: 'merge' });
    this.load();
  }
  load(): void {
    if (!this.accountsLoaded && !this.route.snapshot.queryParamMap.has('accountId') && this.route.snapshot.queryParamMap.get('accounts') !== 'all') return;
    const generation = ++this.requestGeneration;
    this.loading.set(true); this.error.set('');
    const status = this.statusDropdown.value !== 'ALL' ? this.statusDropdown.value : this.tab() === 'ALL' ? undefined : this.tab();
    const dateRange = this.resolveDateRange();
    const query = { page: this.page(), size: this.pageSize, accountId: this.allAccounts() ? undefined : this.selectedAccountId() ?? undefined,
      status, search: this.search.value, fromDate: dateRange.fromDate, toDate: dateRange.toDate,
      sortBy: this.sortBy(), sortDirection: this.sortDirection() };
    this.requests.next({ generation, query });
  }
  private countRequest(query: Parameters<DisputeApi['list']>[0], status: 'ACTIVE' | 'RESOLVED' | undefined) {
    return this.api.list({ ...query, page: 1, size: 1, status }).pipe(
      map(response => response.status && response.data ? response.data.total : null),
      catchError(() => of(null))
    );
  }
  private resolveDateRange(): { fromDate?: string; toDate?: string } {
    if (this.datePreset.value === 'custom') return { fromDate: this.fromDate.value || undefined, toDate: this.toDate.value || undefined };
    return { fromDate: this.fromDate.value || undefined, toDate: this.toDate.value || undefined };
  }
}
