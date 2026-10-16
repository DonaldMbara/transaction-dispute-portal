import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AuthService } from '../../../core/auth/auth-service';
import { Button } from '../../../shared/components/button/button';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import {
  AgentDisputeQuery,
  AgentQueue,
  AgentSortField,
} from '../models/agent-dispute-models';
import { AgentCaseQueueDataSource } from './agent-case-queue-data-source';
import {
  AgentCaseQueuePresentation,
  AGENT_QUEUES,
  AGENT_REASONS,
  agentReasonLabel,
  agentStatusClass,
  agentStatusLabel,
  caseQueueAction,
  caseQueueActionVariant,
  caseQueueAriaSort,
  caseQueueSortSummary,
  formatCaseQueueAmount,
} from './agent-case-queue-presentation';
import { CaseQueueResults } from './case-queue-results';

@Component({
  selector: 'app-agent-case-queue',
  standalone: true,
  imports: [ReactiveFormsModule, Button, LoadingSpinner, CaseQueueResults],
  templateUrl: './case-queue.html',
  styleUrl: './case-queue.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [AgentCaseQueueDataSource],
})
export class CaseQueue {
  private readonly data = inject(AgentCaseQueueDataSource);
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
  readonly items = this.data.items;
  readonly total = this.data.total;
  readonly counts = this.data.counts;
  readonly page = signal(1);
  readonly pageSize = 5;
  readonly sortBy = signal<AgentSortField>('createdAt');
  readonly sortDirection = signal<'ASC' | 'DESC'>('DESC');
  readonly loading = this.data.loading;
  readonly error = this.data.error;
  readonly dateError = signal('');
  readonly totalPages = computed(() => Math.ceil(this.total() / this.pageSize));
  readonly identity = this.auth.state.identity;
  readonly detailQueryParams = signal<Record<string, string>>({});
  readonly queues = AGENT_QUEUES;
  readonly reasons = AGENT_REASONS;
  readonly presentation: AgentCaseQueuePresentation = {
    label: agentStatusLabel,
    statusClass: agentStatusClass,
    reasonLabel: agentReasonLabel,
    amount: formatCaseQueueAmount,
    action: (item: import('../models/agent-dispute-models').AgentDisputeListItem) =>
      caseQueueAction(item, this.identity()?.id),
    actionVariant: caseQueueActionVariant,
    ariaSort: (field: AgentSortField) =>
      caseQueueAriaSort(this.sortBy(), this.sortDirection(), field),
    sortSummary: () => caseQueueSortSummary(this.sortBy(), this.sortDirection()),
  };

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.updateQuery({ search: this.search.value || null, page: null }));
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const allowlisted = [
        'queue',
        'search',
        'status',
        'reason',
        'fromDate',
        'toDate',
        'sortBy',
        'sortDirection',
        'page',
        'size',
      ];
      const preserved: Record<string, string> = {};
      for (const key of allowlisted) {
        const value = params.get(key);
        if (value !== null) preserved[key] = value;
      }
      this.detailQueryParams.set(preserved);
      const queue = params.get('queue');
      this.queue.set(
        AGENT_QUEUES.some((option) => option.value === queue)
          ? (queue as AgentQueue)
          : 'unassigned',
      );
      this.page.set(Math.max(1, Number(params.get('page')) || 1));
      this.sortBy.set(
        ['createdAt', 'updatedAt', 'status'].includes(params.get('sortBy') ?? '')
          ? (params.get('sortBy') as AgentSortField)
          : 'createdAt',
      );
      this.sortDirection.set(params.get('sortDirection') === 'ASC' ? 'ASC' : 'DESC');
      this.search.setValue(params.get('search') ?? '', { emitEvent: false });
      this.status.setValue(params.get('status') ?? 'ALL', { emitEvent: false });
      this.reason.setValue(params.get('reason') ?? 'ALL', { emitEvent: false });
      this.fromDate.setValue(params.get('fromDate') ?? '', { emitEvent: false });
      this.toDate.setValue(params.get('toDate') ?? '', { emitEvent: false });
      this.dateError.set(
        this.fromDate.value && this.toDate.value && this.fromDate.value > this.toDate.value
          ? 'From date must be on or before to date.'
          : '',
      );
      this.load();
    });
  }

  setQueue(queue: AgentQueue): void {
    this.queue.set(queue);
    this.page.set(1);
    this.updateQuery({ queue, page: null });
  }
  updateFilters(): void {
    this.dateError.set(
      this.fromDate.value && this.toDate.value && this.fromDate.value > this.toDate.value
        ? 'From date must be on or before to date.'
        : '',
    );
    if (this.dateError()) return;
    this.page.set(1);
    this.updateQuery({
      status: this.status.value === 'ALL' ? null : this.status.value,
      reason: this.reason.value === 'ALL' ? null : this.reason.value,
      fromDate: this.fromDate.value || null,
      toDate: this.toDate.value || null,
      page: null,
    });
  }
  resetFilters(): void {
    this.search.setValue('', { emitEvent: false });
    this.status.setValue('ALL');
    this.reason.setValue('ALL');
    this.fromDate.setValue('');
    this.toDate.setValue('');
    this.page.set(1);
    this.dateError.set('');
    this.updateQuery({
      search: null,
      status: null,
      reason: null,
      fromDate: null,
      toDate: null,
      page: null,
    });
  }
  toggleSort(field: AgentSortField): void {
    const direction = this.sortBy() === field && this.sortDirection() === 'DESC' ? 'ASC' : 'DESC';
    this.sortBy.set(field);
    this.sortDirection.set(direction);
    this.page.set(1);
    this.updateQuery({
      sortBy: field === 'createdAt' ? null : field,
      sortDirection: direction === 'DESC' ? null : direction,
      page: null,
    });
  }
  setSortField(value: string): void {
    if (!['createdAt', 'updatedAt', 'status'].includes(value)) return;
    const field = value as AgentSortField;
    this.sortBy.set(field);
    this.page.set(1);
    this.updateQuery({ sortBy: field === 'createdAt' ? null : field, page: null });
  }
  changePage(page: number): void {
    this.page.set(Math.min(Math.max(1, page), Math.max(1, this.totalPages())));
    this.updateQuery({ page: this.page() });
  }
  private updateQuery(queryParams: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
    this.load();
  }
  load(): void {
    if (this.dateError()) {
      this.loading.set(false);
      return;
    }
    const query: AgentDisputeQuery = {
      queue: this.queue(),
      page: this.page(),
      size: this.pageSize,
      search: this.search.value || undefined,
      status: this.status.value === 'ALL' ? undefined : this.status.value,
      reason: this.reason.value === 'ALL' ? undefined : this.reason.value,
      fromDate: this.fromDate.value || undefined,
      toDate: this.toDate.value || undefined,
      sortBy: this.sortBy(),
      sortDirection: this.sortDirection(),
    };
    this.data.load(query);
  }
}
