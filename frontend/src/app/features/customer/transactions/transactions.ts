import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse, HttpParams } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';
import { AccountApi } from '../accounts/account-api';
import { AccountSummary } from '../../../shared/models/account-summary';
import { AccountSelector } from '../../../shared/components/account-selector/account-selector';
import { formatZar } from '../../../shared/utils/zar-formatter';
import { TransactionApi } from './transaction-api';
import { DirectionFilter, SortDirection, SortField, StatusFilter, TransactionItem, TransactionPage } from './transaction-models';
import { AsyncViewState } from '../../../shared/models/async-state';

type TransactionsViewState = AsyncViewState<TransactionPage, 'validation' | 'account-unavailable' | 'no-accounts'>;
@Component({
  selector: 'app-transactions', standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AccountSelector],
  templateUrl: './transactions.html', styleUrl: './transactions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Transactions implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly accountsApi = inject(AccountApi);
  private readonly api = inject(TransactionApi);
  private readonly destroyRef = inject(DestroyRef);
  private accountsCache: AccountSummary[] | null = null;
  private generation = 0;
  private requestSubscription?: Subscription;
  private readonly viewModel = signal<TransactionsViewState>({ status: 'loading' });
  readonly state = computed(() => this.viewModel().status);
  readonly accounts = signal<AccountSummary[]>([]);
  readonly selectedAccountId = signal<string | null>(null);
  readonly page = computed(() => {
    const view = this.viewModel();
    return 'value' in view ? view.value : null;
  });
  readonly requestPending = computed(() => this.state() === 'loading' || this.state() === 'busy');
  readonly pageNumber = signal(1);
  readonly returnTo = signal('/customer/transactions');
  readonly filtersOpen = signal(false);
  readonly form = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    direction: new FormControl<DirectionFilter>('ALL', { nonNullable: true }),
    status: new FormControl<StatusFilter>('ALL', { nonNullable: true }),
    fromDate: new FormControl('', { nonNullable: true }),
    toDate: new FormControl('', { nonNullable: true }),
    minAmount: new FormControl('', { nonNullable: true }),
    maxAmount: new FormControl('', { nonNullable: true }),
    sortBy: new FormControl<SortField>('occurredAt', { nonNullable: true }),
    sortDirection: new FormControl<SortDirection>('DESC', { nonNullable: true }),
    size: new FormControl('20', { nonNullable: true })
  });

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.pageNumber.set(Number(params.get('page') ?? '1'));
      this.selectedAccountId.set(params.get('accountId'));
      this.syncForm(params);
      if (this.accountsCache) this.resolveQuery();
      else this.loadAccounts();
    });
    this.form.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.applyFilters());
  }

  defaultAccountId(): string | null {
    const list = this.accounts();
    return (list.find(account => account.primary) ?? list[0])?.id ?? null;
  }

  onAccountChange(id: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { accountId: id, page: 1 }, queryParamsHandling: 'merge' });
  }

  retry(): void {
    if (this.accountsCache === null) this.loadAccounts();
    else this.resolveQuery();
  }

  previousPage(): void {
    if (this.pageNumber() > 1) void this.router.navigate([], { relativeTo: this.route, queryParams: { page: this.pageNumber() - 1 }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  nextPage(): void {
    const result = this.page();
    if (result && this.pageNumber() * result.size < result.total) void this.router.navigate([], { relativeTo: this.route, queryParams: { page: this.pageNumber() + 1 }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('en-ZA', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Johannesburg' }).format(new Date(value));
  }

  formatAmount(item: TransactionItem): string { return `${item.direction === 'OUT' ? '−' : '+'}${formatZar(item.amount)}`; }

  isNewMonth(item: TransactionItem, index: number): boolean {
    if (this.form.controls.sortBy.value !== 'occurredAt') return false;
    const items = this.page()?.items ?? [];
    if (index === 0) return true;
    const previous = new Date(items[index - 1].occurredAt);
    const current = new Date(item.occurredAt);
    const parts = (date: Date) => new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', timeZone: 'Africa/Johannesburg' }).format(date);
    return parts(previous) !== parts(current);
  }

  monthLabel(value: string): string {
    return new Intl.DateTimeFormat('en-ZA', { month: 'short', year: 'numeric', timeZone: 'Africa/Johannesburg' }).format(new Date(value));
  }

  statusLabel(status: TransactionItem['status']): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }

  isSorted(field: SortField): boolean { return this.form.controls.sortBy.value === field; }

  ariaSort(field: SortField): 'ascending' | 'descending' | null {
    return this.isSorted(field) ? (this.form.controls.sortDirection.value === 'ASC' ? 'ascending' : 'descending') : null;
  }

  sortIcon(field: SortField): string {
    return this.isSorted(field) && this.form.controls.sortDirection.value === 'ASC'
      ? 'm7 14 5-5 5 5M12 19V5'
      : 'm7 10 5 5 5-5M12 5v14';
  }

  sortBy(field: Exclude<SortField, 'reference'>): void {
    const active = this.form.controls.sortBy.value === field;
    const direction = active ? (this.form.controls.sortDirection.value === 'ASC' ? 'DESC' : 'ASC')
      : field === 'merchant' ? 'ASC' : 'DESC';
    this.form.patchValue({ sortBy: field, sortDirection: direction }, { emitEvent: false });
    void this.router.navigate([], { relativeTo: this.route, queryParams: { sortBy: field === 'occurredAt' ? null : field, sortDirection: direction === 'DESC' ? null : direction, page: 1 }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  activeFilterCount(): number {
    const value = this.form.getRawValue();
    return [value.search.trim() !== '', value.direction !== 'ALL', value.status !== 'ALL',
      value.fromDate !== '' || value.toDate !== '', value.minAmount !== '' || value.maxAmount !== '']
      .filter(Boolean).length;
  }

  toggleFilters(): void { this.filtersOpen.update(open => !open); }

  filterError(): string | null {
    const value = this.form.getRawValue();
    if (value.fromDate && value.toDate) {
      const from = Date.parse(`${value.fromDate}T00:00:00Z`);
      const to = Date.parse(`${value.toDate}T00:00:00Z`);
      if (from > to) return 'The start date must be on or before the end date.';
      if ((to - from) / 86_400_000 > 366) return 'Choose a date range of 366 days or less.';
    }
    const min = value.minAmount.trim() === '' ? null : Number(value.minAmount);
    const max = value.maxAmount.trim() === '' ? null : Number(value.maxAmount);
    if (min !== null && max !== null && Number.isFinite(min) && Number.isFinite(max) && min > max) {
      return 'The minimum amount must be less than or equal to the maximum amount.';
    }
    return null;
  }

  clearFilters(): void {
    this.form.patchValue({ search: '', direction: 'ALL', status: 'ALL', fromDate: '', toDate: '', minAmount: '', maxAmount: '' }, { emitEvent: false });
    const params: Params = { search: null, direction: null, status: null, fromDate: null, toDate: null, minAmount: null, maxAmount: null, page: 1 };
    void this.router.navigate([], { relativeTo: this.route, queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }

  changePageSize(size: string): void {
    this.form.controls.size.setValue(size, { emitEvent: false });
    void this.router.navigate([], { relativeTo: this.route, queryParams: { size: size === '20' ? null : size, page: 1 }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  private loadAccounts(): void {
    this.viewModel.set({ status: 'loading' });
    this.accountsApi.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => { this.accountsCache = page.items; this.accounts.set(page.items); this.resolveQuery(); },
      error: () => this.viewModel.set({ status: 'error' })
    });
  }

  private resolveQuery(): void {
    const params = this.route.snapshot.queryParamMap;
    const accountId = params.get('accountId');
    if (!accountId) {
      if (!this.accountsCache?.length) { this.viewModel.set({ status: 'no-accounts' }); return; }
      const account = this.accountsCache.find(value => value.primary) ?? this.accountsCache[0];
      void this.router.navigate([], { relativeTo: this.route, queryParams: { accountId: account.id }, queryParamsHandling: 'merge', replaceUrl: true });
      return;
    }
    this.selectedAccountId.set(accountId);
    this.returnTo.set(this.router.url);
    this.requestTransactions();
  }

  private requestTransactions(): void {
    this.requestSubscription?.unsubscribe();
    const generation = ++this.generation;
    const previousPage = this.page();
    this.viewModel.set(previousPage === null ? { status: 'loading' } : { status: 'busy', value: previousPage });
    const params = this.route.snapshot.queryParamMap.keys.reduce((query, key) => {
      for (const value of this.route.snapshot.queryParamMap.getAll(key)) query = query.append(key, value);
      return query;
    }, new HttpParams());
    this.requestSubscription = this.api.list(params).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (generation !== this.generation) return;
        if (!this.accountsCache?.some(account => account.id === this.selectedAccountId())) {
          this.viewModel.set({ status: 'account-unavailable' });
          return;
        }
        this.viewModel.set(result.items.length ? { status: 'ready', value: result } : { status: 'none', value: result });
      },
      error: (failure: unknown) => {
        if (generation !== this.generation) return;
        if (failure instanceof HttpErrorResponse && failure.status === 404) this.viewModel.set({ status: 'account-unavailable' });
        else if (failure instanceof HttpErrorResponse && failure.status === 400) this.viewModel.set({ status: 'validation' });
        else this.viewModel.set({ status: 'error' });
      }
    });
  }

  private syncForm(params: import('@angular/router').ParamMap): void {
    const values = {
      search: params.get('search') ?? '',
      direction: (params.get('direction') ?? 'ALL') as DirectionFilter,
      status: (params.get('status') ?? 'ALL') as StatusFilter,
      fromDate: params.get('fromDate') ?? '', toDate: params.get('toDate') ?? '',
      minAmount: params.get('minAmount') ?? '', maxAmount: params.get('maxAmount') ?? '',
      sortBy: (params.get('sortBy') ?? 'occurredAt') as SortField,
      sortDirection: (params.get('sortDirection') ?? 'DESC') as SortDirection,
      size: params.get('size') ?? '20'
    };
    this.form.patchValue(values, { emitEvent: false });
  }

  private applyFilters(): void {
    if (this.filterError()) return;
    const value = this.form.getRawValue();
    const queryParams: Params = { page: 1 };
    for (const key of ['search', 'direction', 'status', 'fromDate', 'toDate', 'minAmount', 'maxAmount', 'sortBy', 'sortDirection', 'size'] as const) {
      const current = key === 'search' ? value.search.replace(/[\s\u00a0]+/gu, ' ').trim() : value[key];
      const defaultValue = key === 'direction' || key === 'status' ? 'ALL' : key === 'sortDirection' ? 'DESC' : key === 'sortBy' ? 'occurredAt' : key === 'size' ? '20' : '';
      queryParams[key] = current === defaultValue ? null : current;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
