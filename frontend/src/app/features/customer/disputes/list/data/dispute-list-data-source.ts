import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Subject,
  catchError,
  forkJoin,
  map,
  of,
  switchMap,
} from 'rxjs';
import { DisputeApi } from '../../api/dispute-api';
import { DisputeListItem } from '../../models/dispute-models';

type DisputeTab = 'ALL' | 'ACTIVE' | 'RESOLVED';
type DisputeListQuery = Parameters<DisputeApi['list']>[0];

@Injectable()
export class DisputeListDataSource {
  private readonly api = inject(DisputeApi);
  private readonly destroyRef = inject(DestroyRef);
  private generation = 0;
  private readonly requests = new Subject<{
    generation: number;
    query: DisputeListQuery;
    allAccounts: boolean;
  }>();
  readonly items = signal<readonly DisputeListItem[]>([]);
  readonly total = signal(0);
  readonly tabCounts = signal<Readonly<Record<DisputeTab, number | null>>>({
    ALL: null,
    ACTIVE: null,
    RESOLVED: null,
  });
  readonly loading = signal(true);
  readonly error = signal<'generic' | 'account' | ''>('');

  constructor() {
    this.requests
      .pipe(
        switchMap(({ generation, query, allAccounts }) =>
          forkJoin({
            page: this.api.list(query),
            all: this.countRequest(query, undefined),
            active: this.countRequest(query, 'ACTIVE'),
            resolved: this.countRequest(query, 'RESOLVED'),
          }).pipe(
            map(({ page, all, active, resolved }) => ({
              generation,
              response: page,
              counts: { ALL: all, ACTIVE: active, RESOLVED: resolved },
              failure: null as unknown,
              allAccounts,
            })),
            catchError((failure: unknown) =>
              of({ generation, response: null, counts: null, failure, allAccounts }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (result.generation !== this.generation) return;
        this.loading.set(false);
        if (result.failure !== null) {
          this.error.set(
            result.failure instanceof HttpErrorResponse &&
              result.failure.status === 404 &&
              !result.allAccounts
              ? 'account'
              : 'generic',
          );
        } else if (!result.response?.status || !result.response.data) {
          this.items.set([]);
          this.total.set(0);
        } else {
          this.items.set(result.response.data.items);
          this.total.set(result.response.data.total);
          if (result.counts) this.tabCounts.set(result.counts);
        }
      });
  }

  load(query: DisputeListQuery, allAccounts: boolean): void {
    this.loading.set(true);
    this.error.set('');
    this.requests.next({ generation: ++this.generation, query, allAccounts });
  }

  private countRequest(query: DisputeListQuery, status: 'ACTIVE' | 'RESOLVED' | undefined) {
    return this.api.list({ ...query, page: 1, size: 1, status }).pipe(
      map((response) => (response.status && response.data ? response.data.total : null)),
      catchError(() => of(null)),
    );
  }
}