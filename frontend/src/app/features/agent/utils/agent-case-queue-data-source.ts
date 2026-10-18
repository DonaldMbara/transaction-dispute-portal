import {DestroyRef, inject, Injectable, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {catchError, forkJoin, map, of, Subject, switchMap} from 'rxjs';
import {AgentDisputeApi} from '../services/agent-dispute-api';
import {AgentDisputeListItem, AgentDisputeQuery, AgentQueue,} from '../models/agent-dispute-models';

@Injectable()
export class AgentCaseQueueDataSource {
  readonly items = signal<readonly AgentDisputeListItem[]>([]);
  readonly total = signal(0);
  readonly counts = signal<Record<AgentQueue, number | null>>({
    unassigned: null,
    mine: null,
    all: null,
  });
  readonly loading = signal(true);
  readonly error = signal(false);
  private readonly api = inject(AgentDisputeApi);
  private readonly destroyRef = inject(DestroyRef);
  private generation = 0;
  private readonly requests = new Subject<{ generation: number; query: AgentDisputeQuery }>();

  constructor() {
    this.requests
      .pipe(
        switchMap(({generation, query}) =>
          forkJoin({
            rows: this.api.list(query),
            unassigned: this.countRequest(query, 'unassigned'),
            mine: this.countRequest(query, 'mine'),
            all: this.countRequest(query, 'all'),
          }).pipe(
            map((result) => ({generation, result, failure: null as unknown})),
            catchError((failure: unknown) => of({generation, result: null, failure})),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({generation, result, failure}) => {
        if (generation !== this.generation) return;
        this.loading.set(false);
        if (failure || !result?.rows.status || !result.rows.data) {
          this.error.set(true);
          this.items.set([]);
          this.total.set(0);
          return;
        }
        this.error.set(false);
        this.items.set(result.rows.data.items);
        this.total.set(result.rows.data.total);
        this.counts.set({unassigned: result.unassigned, mine: result.mine, all: result.all});
      });
  }

  load(query: AgentDisputeQuery): void {
    this.loading.set(true);
    this.error.set(false);
    this.requests.next({generation: ++this.generation, query});
  }

  private countRequest(query: AgentDisputeQuery, queue: AgentQueue) {
    return this.api.list({...query, queue, page: 1, size: 1}).pipe(
      map((response) => (response.status && response.data ? response.data.total : null)),
      catchError(() => of(null)),
    );
  }
}
