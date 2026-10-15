import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { Button } from '../../shared/components/button/button';
import { LoadingSpinner } from '../../shared/components/loading-spinner/loading-spinner';
import { formatZar } from '../../shared/utils/zar-formatter';
import { AgentDisputeApi } from './agent-dispute-api';
import { AgentDisputeListItem, AgentDisputeQuery, AgentDisputeStatus, AgentQueue, AgentReason, AgentSortField } from './agent-dispute-models';

const QUEUES: readonly AgentQueue[] = ['unassigned', 'mine', 'all'];
const REASONS: readonly { value: AgentReason; label: string }[] = [
  { value: 'UNRECOGNIZED_TRANSACTION', label: 'Unrecognised transaction' },
  { value: 'DUPLICATE_CHARGE', label: 'Duplicate charge' },
  { value: 'INCORRECT_AMOUNT', label: 'Incorrect amount' },
  { value: 'GOODS_OR_SERVICES_NOT_RECEIVED', label: 'Goods or services not received' },
  { value: 'OTHER', label: 'Other' }
];

@Component({ selector: 'app-agent-case-queue', standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, Button, LoadingSpinner],
  templateUrl: './case-queue.html', styleUrl: './case-queue.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class CaseQueue {
  private readonly api = inject(AgentDisputeApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly search = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('ALL', { nonNullable: true });
  readonly reason = new FormControl('ALL', { nonNullable: true });
  readonly fromDate = new FormControl('', { nonNullable: true });
  readonly toDate = new FormControl('', { nonNullable: true });
  readonly queue = signal<AgentQueue>('unassigned');
  readonly items = signal<readonly AgentDisputeListItem[]>([]);
  readonly total = signal(0);
  readonly counts = signal<Record<AgentQueue, number | null>>({ unassigned: null, mine: null, all: null });
  readonly page = signal(1);
  readonly pageSize = 5;
  readonly sortBy = signal<AgentSortField>('createdAt');
  readonly sortDirection = signal<'ASC' | 'DESC'>('DESC');
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly dateError = signal('');
  readonly totalPages = computed(() => Math.ceil(this.total() / this.pageSize));
  readonly identity = this.auth.state.identity;
  readonly detailQueryParams = signal<Record<string, string>>({});
  readonly queues: readonly { value: AgentQueue; label: string }[] = [
    { value: 'unassigned', label: 'Unassigned' }, { value: 'mine', label: 'My Cases' }, { value: 'all', label: 'All Disputes' }
  ];
  readonly reasons = REASONS;
  private generation = 0;
  private readonly requests = new Subject<{ generation: number; query: AgentDisputeQuery }>();

  constructor() {
    this.requests.pipe(switchMap(({ generation, query }) => forkJoin({
      rows: this.api.list(query),
      unassigned: this.countRequest(query, 'unassigned'), mine: this.countRequest(query, 'mine'), all: this.countRequest(query, 'all')
    }).pipe(map(result => ({ generation, result, failure: null as unknown })),
      catchError((failure: unknown) => of({ generation, result: null, failure })))), takeUntilDestroyed(this.destroyRef))
      .subscribe(({ generation, result, failure }) => {
        if (generation !== this.generation) return;
        this.loading.set(false);
        if (failure || !result?.rows.status || !result.rows.data) { this.error.set(true); this.items.set([]); this.total.set(0); return; }
        this.error.set(false); this.items.set(result.rows.data.items); this.total.set(result.rows.data.total);
        this.counts.set({ unassigned: result.unassigned, mine: result.mine, all: result.all });
      });

    this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.updateQuery({ search: this.search.value || null, page: null }));
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const allowlisted = ['queue', 'search', 'status', 'reason', 'fromDate', 'toDate', 'sortBy', 'sortDirection', 'page', 'size'];
      const preserved: Record<string, string> = {};
      for (const key of allowlisted) { const value = params.get(key); if (value !== null) preserved[key] = value; }
      this.detailQueryParams.set(preserved);
      const queue = params.get('queue'); this.queue.set(QUEUES.includes(queue as AgentQueue) ? queue as AgentQueue : 'unassigned');
      this.page.set(Math.max(1, Number(params.get('page')) || 1));
      this.sortBy.set(['createdAt', 'updatedAt', 'status'].includes(params.get('sortBy') ?? '') ? params.get('sortBy') as AgentSortField : 'createdAt');
      this.sortDirection.set(params.get('sortDirection') === 'ASC' ? 'ASC' : 'DESC');
      this.search.setValue(params.get('search') ?? '', { emitEvent: false });
      this.status.setValue(params.get('status') ?? 'ALL', { emitEvent: false });
      this.reason.setValue(params.get('reason') ?? 'ALL', { emitEvent: false });
      this.fromDate.setValue(params.get('fromDate') ?? '', { emitEvent: false });
      this.toDate.setValue(params.get('toDate') ?? '', { emitEvent: false });
      this.dateError.set(this.fromDate.value && this.toDate.value && this.fromDate.value > this.toDate.value
        ? 'From date must be on or before to date.' : '');
      this.load();
    });
  }

  setQueue(queue: AgentQueue): void { this.queue.set(queue); this.page.set(1); this.updateQuery({ queue, page: null }); }
  updateFilters(): void {
    this.dateError.set(this.fromDate.value && this.toDate.value && this.fromDate.value > this.toDate.value
      ? 'From date must be on or before to date.' : '');
    if (this.dateError()) return;
    this.page.set(1); this.updateQuery({ status: this.status.value === 'ALL' ? null : this.status.value,
    reason: this.reason.value === 'ALL' ? null : this.reason.value, fromDate: this.fromDate.value || null,
    toDate: this.toDate.value || null, page: null }); }
  resetFilters(): void {
    this.search.setValue('', { emitEvent: false }); this.status.setValue('ALL'); this.reason.setValue('ALL');
    this.fromDate.setValue(''); this.toDate.setValue(''); this.page.set(1);
    this.dateError.set('');
    this.updateQuery({ search: null, status: null, reason: null, fromDate: null, toDate: null, page: null });
  }
  toggleSort(field: AgentSortField): void {
    const direction = this.sortBy() === field && this.sortDirection() === 'DESC' ? 'ASC' : 'DESC';
    this.sortBy.set(field); this.sortDirection.set(direction); this.page.set(1);
    this.updateQuery({ sortBy: field === 'createdAt' ? null : field, sortDirection: direction === 'DESC' ? null : direction, page: null });
  }
  setSortField(value: string): void {
    if (!['createdAt', 'updatedAt', 'status'].includes(value)) return;
    const field = value as AgentSortField;
    this.sortBy.set(field); this.page.set(1);
    this.updateQuery({ sortBy: field === 'createdAt' ? null : field, page: null });
  }
  sortSummary(): string {
    if (this.sortBy() === 'status') return `Status · ${this.sortDirection() === 'ASC' ? 'A to Z' : 'Z to A'}`;
    if (this.sortBy() === 'updatedAt') return `Updated · ${this.sortDirection() === 'DESC' ? 'Newest first ↓' : 'Oldest first ↑'}`;
    return this.sortDirection() === 'DESC' ? 'Created date · Newest first ↓' : 'Created date · Oldest first ↑';
  }
  changePage(page: number): void { this.page.set(Math.min(Math.max(1, page), Math.max(1, this.totalPages()))); this.updateQuery({ page: this.page() }); }
  label(value: AgentDisputeStatus): string { return ({ OPEN: 'Received', UNDER_REVIEW: 'Under review', ACCEPTED: 'Accepted', REJECTED: 'Rejected' })[value]; }
  statusClass(value: AgentDisputeStatus): string { return value.toLowerCase().replace('_', '-'); }
  reasonLabel(value: AgentReason): string { return REASONS.find(item => item.value === value)?.label ?? 'Other'; }
  amount(value: string): string { return formatZar(value); }
  action(item: AgentDisputeListItem): 'Review case' | 'Investigate' | 'View case' {
    if (item.status === 'OPEN' && item.assignedAgent === null) return 'Review case';
    if (item.status === 'UNDER_REVIEW' && item.assignedAgent?.id === this.identity()?.id) return 'Investigate';
    return 'View case';
  }
  actionVariant(item: AgentDisputeListItem): 'primary' | 'secondary' {
    return item.status === 'OPEN' && item.assignedAgent === null ? 'primary' : 'secondary';
  }
  trackById(_index: number, item: AgentDisputeListItem): string { return item.id; }
  ariaSort(field: AgentSortField): 'ascending' | 'descending' | 'none' {
    return this.sortBy() !== field ? 'none' : this.sortDirection() === 'ASC' ? 'ascending' : 'descending';
  }

  private updateQuery(queryParams: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
    this.load();
  }
  load(): void {
    if (this.dateError()) { this.loading.set(false); return; }
    const query: AgentDisputeQuery = { queue: this.queue(), page: this.page(), size: this.pageSize,
      search: this.search.value || undefined, status: this.status.value === 'ALL' ? undefined : this.status.value,
      reason: this.reason.value === 'ALL' ? undefined : this.reason.value,
      fromDate: this.fromDate.value || undefined, toDate: this.toDate.value || undefined,
      sortBy: this.sortBy(), sortDirection: this.sortDirection() };
    this.loading.set(true); this.error.set(false); this.requests.next({ generation: ++this.generation, query });
  }
  private countRequest(query: AgentDisputeQuery, queue: AgentQueue) {
    return this.api.list({ ...query, queue, page: 1, size: 1 }).pipe(map(response => response.status && response.data ? response.data.total : null), catchError(() => of(null)));
  }
}
