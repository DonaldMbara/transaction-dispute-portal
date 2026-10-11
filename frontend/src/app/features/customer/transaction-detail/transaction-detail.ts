import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TransactionApi } from '../transactions/transaction-api';
import { TransactionItem } from '../transactions/transaction-models';
import { formatZar } from '../../../shared/utils/zar-formatter';
import { AsyncViewState } from '../../../shared/models/async-state';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import { Button } from '../../../shared/components/button/button';

type DetailState = AsyncViewState<TransactionItem, 'not-found'>;
const ALLOWED_RETURN_PARAMETERS = new Set(['accountId', 'page', 'size', 'search', 'direction', 'status', 'fromDate', 'toDate', 'minAmount', 'maxAmount', 'sortBy', 'sortDirection']);

@Component({
  selector: 'app-transaction-detail', standalone: true, imports: [RouterLink, LoadingSpinner, Button],
  templateUrl: './transaction-detail.html', styleUrl: './transaction-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TransactionDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(TransactionApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly state = signal<DetailState>({ status: 'loading' });
  readonly status = computed(() => this.state().status);
  readonly transaction = computed(() => {
    const state = this.state();
    return 'value' in state ? state.value : null;
  });
  readonly returnTo = signal('/customer/transactions');
  readonly formatZar = formatZar;

  ngOnInit(): void {
    this.returnTo.set(this.validReturnTo(this.route.snapshot.queryParamMap.get('returnTo')) ?? '/customer/transactions');
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = params.get('id');
      if (id) this.load(id);
      else this.state.set({ status: 'not-found' });
    });
  }

  retry(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.load(id);
  }

  goBack(): void { void this.router.navigateByUrl(this.returnTo()); }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('en-ZA', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Johannesburg' }).format(new Date(value));
  }

  private load(id: string): void {
    this.state.set({ status: 'loading' });
    this.api.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: transaction => { this.state.set({ status: 'ready', value: transaction }); },
      error: (failure: unknown) => this.state.set(failure instanceof HttpErrorResponse && failure.status === 404 ? { status: 'not-found' } : { status: 'error' })
    });
  }

  private validReturnTo(value: string | null): string | null {
    if (!value || !value.startsWith('/customer/transactions?') || value.startsWith('//') || value.includes('\\')) return null;
    try {
      const candidate = new URL(value, window.location.origin);
      if (candidate.origin !== window.location.origin || candidate.pathname !== '/customer/transactions' || candidate.hash) return null;
      const seen = new Set<string>();
      for (const [key] of candidate.searchParams) {
        if (!ALLOWED_RETURN_PARAMETERS.has(key) || seen.has(key)) return null;
        seen.add(key);
      }
      return candidate.pathname + candidate.search;
    } catch { return null; }
  }
}
