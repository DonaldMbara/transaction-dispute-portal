import {WritableSignal} from '@angular/core';
import {FormControl} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {DisputeDatePreset, disputeDatePresetRange} from './dispute-list-query';
import {DisputeListSortField, DisputeListTab,} from '../presentation/dispute-list-presentation';

interface DisputeListInteractionState {
  route: ActivatedRoute;
  router: Router;
  search: FormControl<string>;
  status: FormControl<string>;
  datePreset: FormControl<DisputeDatePreset>;
  fromDate: FormControl<string>;
  toDate: FormControl<string>;
  page: WritableSignal<number>;
  total: WritableSignal<number>;
  tab: WritableSignal<DisputeListTab>;
  sortBy: WritableSignal<DisputeListSortField>;
  sortDirection: WritableSignal<'ASC' | 'DESC'>;
  pageSize: number;
  load: () => void;
}

export class DisputeListInteractions {
  constructor(private readonly state: DisputeListInteractionState) {
  }

  setTab(value: DisputeListTab): void {
    const {state} = this;
    state.page.set(1);
    state.tab.set(value);
    state.status.setValue('ALL');
    void state.router.navigate([], {
      relativeTo: state.route,
      queryParams: {tab: value === 'ALL' ? null : value, status: null, page: null},
      queryParamsHandling: 'merge',
    });
    state.load();
  }

  onTabKeydown(event: KeyboardEvent, current: DisputeListTab): void {
    const order: DisputeListTab[] = ['ALL', 'ACTIVE', 'RESOLVED'];
    let next = -1;
    if (event.key === 'ArrowRight') next = (order.indexOf(current) + 1) % order.length;
    if (event.key === 'ArrowLeft')
      next = (order.indexOf(current) + order.length - 1) % order.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = order.length - 1;
    if (next >= 0) {
      event.preventDefault();
      this.setTab(order[next]);
      document.querySelector<HTMLElement>(`[data-dispute-tab="${order[next]}"]`)?.focus();
    }
  }

  onStatusChange(): void {
    const {state} = this;
    state.page.set(1);
    const status = state.status.value === 'ALL' ? null : state.status.value;
    const statusTab: DisputeListTab | null =
      status === null
        ? null
        : status === 'OPEN' || status === 'UNDER_REVIEW'
          ? 'ACTIVE'
          : 'RESOLVED';
    const tab = statusTab ?? (state.tab() === 'ALL' ? null : state.tab());
    if (statusTab) state.tab.set(statusTab);
    void state.router.navigate([], {
      relativeTo: state.route,
      queryParams: {status, tab, page: null},
      queryParamsHandling: 'merge',
    });
    state.load();
  }

  onDatePresetChange(): void {
    const {state} = this;
    const range = disputeDatePresetRange(state.datePreset.value);
    if (range) {
      state.fromDate.setValue(range.fromDate);
      state.toDate.setValue(range.toDate);
    }
    this.updateDates();
  }

  updateDates(): void {
    const {state} = this;
    state.page.set(1);
    void state.router.navigate([], {
      relativeTo: state.route,
      queryParams: {
        fromDate: state.fromDate.value || null,
        toDate: state.toDate.value || null,
        page: null,
      },
      queryParamsHandling: 'merge',
    });
    state.load();
  }

  clearFilters(): void {
    const {state} = this;
    state.page.set(1);
    state.search.setValue('', {emitEvent: false});
    state.status.setValue('ALL');
    state.datePreset.setValue('any');
    state.fromDate.setValue('');
    state.toDate.setValue('');
    state.tab.set('ALL');
    void state.router.navigate([], {
      relativeTo: state.route,
      queryParams: {
        tab: null,
        status: null,
        search: null,
        fromDate: null,
        toDate: null,
        page: null,
      },
      queryParamsHandling: 'merge',
    });
    state.load();
  }

  toggleSort(field: DisputeListSortField): void {
    const {state} = this;
    const direction = state.sortBy() === field && state.sortDirection() === 'DESC' ? 'ASC' : 'DESC';
    state.sortBy.set(field);
    state.sortDirection.set(direction);
    state.page.set(1);
    void state.router.navigate([], {
      relativeTo: state.route,
      queryParams: {
        sortBy: field === 'createdAt' ? null : field,
        sortDirection: direction === 'DESC' ? null : direction,
        page: null,
      },
      queryParamsHandling: 'merge',
    });
    state.load();
  }

  changePage(value: number): void {
    const {state} = this;
    const page = Math.min(
      Math.max(1, value),
      Math.max(1, Math.ceil(state.total() / state.pageSize)),
    );
    state.page.set(page);
    void state.router.navigate([], {
      relativeTo: state.route,
      queryParams: {page},
      queryParamsHandling: 'merge',
    });
    state.load();
  }
}
