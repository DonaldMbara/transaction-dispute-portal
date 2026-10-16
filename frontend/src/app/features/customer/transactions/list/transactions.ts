import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AccountSelector } from '../../../../shared/components/account-selector/account-selector';
import {
  DirectionFilter,
  SortDirection,
  SortField,
  StatusFilter,
  TransactionItem,
} from '../models/transaction-models';
import { LoadingSpinner } from '../../../../shared/components/loading-spinner/loading-spinner';
import { Button } from '../../../../shared/components/button/button';
import { TransactionTable } from './components/transaction-table';
import { TransactionListDataSource } from './data/transaction-list-data-source';
import {
  activeTransactionFilterCount,
  TransactionFilterValues,
  transactionFilterError,
  transactionFilterParams,
  transactionFilterValues,
} from './query/transaction-list-query';
import { TransactionListNavigation } from './query/transaction-list-navigation';
import {
  formatTransactionAmount,
  formatTransactionDate,
  formatTransactionMonth,
  isTransactionSortActive,
  isNewTransactionMonth,
  TransactionListPresentation,
  transactionAmountAriaLabel,
  transactionSortAria,
  transactionSortIcon,
  transactionStatusLabel,
} from './presentation/transaction-presentation';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [ReactiveFormsModule, AccountSelector, LoadingSpinner, Button, TransactionTable],
  templateUrl: './transactions.html',
  styleUrl: './transactions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [TransactionListDataSource],
})
export class Transactions implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly data = inject(TransactionListDataSource);
  readonly state = this.data.state;
  readonly accounts = this.data.accounts;
  readonly defaultAccountId = this.data.defaultAccountId;
  readonly selectedAccountId = this.data.selectedAccountId;
  readonly page = this.data.page;
  readonly requestPending = this.data.requestPending;
  readonly pageNumber = signal(1);
  readonly returnTo = this.data.returnTo;
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
    size: new FormControl('20', { nonNullable: true }),
  });
  readonly navigation = new TransactionListNavigation(
    this.route,
    this.router,
    this.form,
    this.page,
    this.pageNumber,
  );
  readonly presentation: TransactionListPresentation = {
    formatDate: formatTransactionDate,
    formatAmount: formatTransactionAmount,
    amountAriaLabel: transactionAmountAriaLabel,
    isNewMonth: (item: TransactionItem, index: number) =>
      isNewTransactionMonth(
        item,
        index,
        this.page()?.items ?? [],
        this.form.controls.sortBy.value,
      ),
    monthLabel: formatTransactionMonth,
    statusLabel: transactionStatusLabel,
    isSorted: (field: SortField) => isTransactionSortActive(this.form.controls.sortBy.value, field),
    ariaSort: (field: SortField) =>
      transactionSortAria(
        this.form.controls.sortBy.value,
        this.form.controls.sortDirection.value,
        field,
      ),
    sortIcon: (field: SortField) =>
      transactionSortIcon(
        this.form.controls.sortBy.value,
        this.form.controls.sortDirection.value,
        field,
      ),
    sortSummary: () =>
      this.form.controls.sortBy.value === 'occurredAt'
        ? 'Date · ' +
          (this.form.controls.sortDirection.value === 'DESC' ? 'Newest first' : 'Oldest first')
        : 'Transaction · ' +
          (this.form.controls.sortDirection.value === 'DESC' ? 'Z to A' : 'A to Z'),
  };

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.pageNumber.set(Number(params.get('page') ?? '1'));
      this.syncForm(params);
      this.data.routeChanged(params);
    });
    this.form.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.applyFilters());
  }

  retry(): void {
    this.data.retry();
  }

  activeFilterCount(): number {
    return activeTransactionFilterCount(this.form.getRawValue());
  }

  toggleFilters(): void {
    this.filtersOpen.update((open) => !open);
  }

  filterError(): string | null {
    return transactionFilterError(this.form.getRawValue());
  }

  private syncForm(params: import('@angular/router').ParamMap): void {
    this.form.patchValue(transactionFilterValues(params), { emitEvent: false });
  }

  private applyFilters(): void {
    if (this.filterError()) return;
    const queryParams = transactionFilterParams(this.form.getRawValue() as TransactionFilterValues);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
