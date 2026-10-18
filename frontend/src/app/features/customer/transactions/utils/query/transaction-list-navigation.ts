import {Signal, WritableSignal} from '@angular/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {SortDirection, SortField, TransactionPage,} from '../../models/transaction-models';
import {TransactionFilterForm} from './transaction-list-query';

export class TransactionListNavigation {
  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly form: TransactionFilterForm,
    private readonly page: Signal<TransactionPage | null>,
    private readonly pageNumber: WritableSignal<number>,
  ) {
  }

  onAccountChange(id: string | null): void {
    if (id === null) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {accountId: id, page: 1},
      queryParamsHandling: 'merge',
    });
  }

  previousPage(): void {
    if (this.pageNumber() > 1)
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {page: this.pageNumber() - 1},
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
  }

  nextPage(): void {
    const result = this.page();
    if (result && this.pageNumber() * result.size < result.total)
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {page: this.pageNumber() + 1},
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
  }

  sortBy(field: Exclude<SortField, 'reference'>): void {
    const currentField = this.form.controls.sortBy.value;
    const currentDirection = this.form.controls.sortDirection.value;
    const direction: SortDirection =
      currentField === field
        ? currentDirection === 'ASC'
          ? 'DESC'
          : 'ASC'
        : field === 'merchant'
          ? 'ASC'
          : 'DESC';
    this.form.patchValue({sortBy: field, sortDirection: direction}, {emitEvent: false});
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        sortBy: field === 'occurredAt' ? null : field,
        sortDirection: direction === 'DESC' ? null : direction,
        page: 1,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  clearFilters(): void {
    this.form.patchValue(
      {
        search: '',
        direction: 'ALL',
        status: 'ALL',
        fromDate: '',
        toDate: '',
        minAmount: '',
        maxAmount: '',
      },
      {emitEvent: false},
    );
    const queryParams: Params = {
      search: null,
      direction: null,
      status: null,
      fromDate: null,
      toDate: null,
      minAmount: null,
      maxAmount: null,
      page: 1,
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  changePageSize(size: string): void {
    this.form.controls.size.setValue(size, {emitEvent: false});
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {size: size === '20' ? null : size, page: 1},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
