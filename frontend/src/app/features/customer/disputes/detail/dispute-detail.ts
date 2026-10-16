import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DisputeApi } from '../api/dispute-api';
import { DisputeDetail as Dispute, TimelineEntry } from '../models/dispute-models';
import { LoadingSpinner } from '../../../../shared/components/loading-spinner/loading-spinner';
import { Button } from '../../../../shared/components/button/button';
import {
  formatDisputeAmount,
  formatDisputeReason,
  formatDisputeReference,
  formatDisputeStatus,
} from '../models/dispute-presentation';

@Component({
  selector: 'app-customer-dispute-detail',
  standalone: true,
  imports: [DatePipe, RouterLink, LoadingSpinner, Button],
  templateUrl: './dispute-detail.html',
  styleUrl: './dispute-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisputeDetail {
  private readonly api = inject(DisputeApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly dispute = signal<Dispute | null>(null);
  readonly state = signal<'loading' | 'ready' | 'not-found' | 'error'>('loading');
  readonly displayRef = computed(() => {
    const dispute = this.dispute();
    return dispute ? formatDisputeReference(dispute.id) : '';
  });
  readonly formatAmount = formatDisputeAmount;
  readonly statusLabel = formatDisputeStatus;
  readonly backLink = computed(() => {
    const requested = this.route.snapshot.queryParamMap.get('returnTo');
    return this.safeReturnTo(requested);
  });
  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id') ?? '';
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(id)) {
        this.state.set('not-found');
        return;
      }
      this.state.set('loading');
      this.api
        .get(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response) => {
            if (!response.status || !response.data) {
              this.state.set('error');
              return;
            }
            this.dispute.set(response.data);
            this.state.set('ready');
          },
          error: (failure: unknown) =>
            this.state.set(
              failure instanceof HttpErrorResponse && failure.status === 404
                ? 'not-found'
                : 'error',
            ),
        });
    });
  }
  timelineLabel(entry: TimelineEntry, index: number, all: readonly TimelineEntry[]): string {
    if (entry.type === 'IN_REVIEW')
      return all.slice(0, index).some((item) => item.type === 'IN_REVIEW')
        ? 'Review resumed'
        : 'Review began';
    if (entry.type === 'SUBMITTED') return 'Dispute submitted';
    if (entry.type === 'RETURNED_TO_QUEUE') return 'Returned to the queue';
    const outcome = this.dispute()?.decision?.outcome;
    return outcome === 'ACCEPTED'
      ? 'Dispute accepted'
      : outcome === 'REJECTED'
        ? 'Dispute rejected'
        : 'Dispute resolved';
  }
  reasonLabel = formatDisputeReason;
  private safeReturnTo(requested: string | null): string {
    if (!requested || !requested.startsWith('/customer/disputes')) return '/customer/disputes';
    const [path, query = ''] = requested.split('?', 2);
    if (path !== '/customer/disputes') return '/customer/disputes';
    const input = new URLSearchParams(query);
    const allowed = new URLSearchParams();
    for (const [key, value] of input.entries()) {
      const valid =
        (key === 'accountId' && /^[0-9a-f-]{36}$/iu.test(value)) ||
        (key === 'accounts' && value === 'all') ||
        (key === 'status' &&
          ['ACTIVE', 'RESOLVED', 'OPEN', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED'].includes(value)) ||
        (key === 'tab' && ['ACTIVE', 'RESOLVED'].includes(value)) ||
        (key === 'search' && value.length <= 100) ||
        ((key === 'fromDate' || key === 'toDate') && /^\d{4}-\d{2}-\d{2}$/u.test(value)) ||
        (key === 'page' && /^[1-9]\d{0,8}$/u.test(value)) ||
        (key === 'sortBy' && ['createdAt', 'updatedAt', 'status'].includes(value)) ||
        (key === 'sortDirection' && ['ASC', 'DESC'].includes(value));
      if (valid && !allowed.has(key)) allowed.set(key, value);
    }
    if (allowed.has('accountId') && allowed.has('accounts')) return '/customer/disputes';
    const serialized = allowed.toString();
    return serialized ? `/customer/disputes?${serialized}` : '/customer/disputes';
  }
  retry(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.state.set('loading');
      this.api
        .get(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (result) => {
            this.dispute.set(result.data);
            this.state.set(result.status && result.data ? 'ready' : 'error');
          },
          error: () => this.state.set('error'),
        });
    }
  }
  openTransactions(): void {
    void this.router.navigateByUrl('/customer/transactions');
  }
}
