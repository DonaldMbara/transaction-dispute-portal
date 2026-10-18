import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {HttpErrorResponse, HttpParams} from '@angular/common/http';
import {ActivatedRoute, ParamMap, Router} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription} from 'rxjs';
import {AccountApi} from '../../../accounts/serviecs/account-api';
import {AccountSummary} from '../../../../../shared/models/account-summary';
import {AsyncViewState} from '../../../../../shared/models/async-state';
import {TransactionApi} from '../../services/transaction-api';
import {TransactionPage} from '../../models/transaction-models';

type TransactionsViewState = AsyncViewState<
  TransactionPage,
  'validation' | 'account-unavailable' | 'no-accounts'
>;

@Injectable()
export class TransactionListDataSource {
  readonly accounts = signal<AccountSummary[]>([]);
  readonly defaultAccountId = computed(() => {
    const list = this.accounts();
    return (list.find((account) => account.primary) ?? list[0])?.id ?? null;
  });
  readonly selectedAccountId = signal<string | null>(null);
  readonly requestPending = computed(() => this.state() === 'loading' || this.state() === 'busy');
  readonly returnTo = signal('/customer/transactions');
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly accountsApi = inject(AccountApi);
  private readonly api = inject(TransactionApi);
  private readonly destroyRef = inject(DestroyRef);
  private accountsCache: AccountSummary[] | null = null;
  private generation = 0;
  private requestSubscription?: Subscription;
  private readonly viewModel = signal<TransactionsViewState>({status: 'loading'});
  readonly state = computed(() => this.viewModel().status);
  readonly page = computed(() => {
    const view = this.viewModel();
    return 'value' in view ? view.value : null;
  });

  routeChanged(params: ParamMap): void {
    this.selectedAccountId.set(params.get('accountId'));
    if (this.accountsCache) this.resolveQuery();
    else this.loadAccounts();
  }

  retry(): void {
    if (this.accountsCache === null) this.loadAccounts();
    else this.resolveQuery();
  }

  private loadAccounts(): void {
    this.viewModel.set({status: 'loading'});
    this.accountsApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.accountsCache = page.items;
          this.accounts.set(page.items);
          this.resolveQuery();
        },
        error: () => this.viewModel.set({status: 'error'}),
      });
  }

  private resolveQuery(): void {
    const params = this.route.snapshot.queryParamMap;
    const accountId = params.get('accountId');
    if (!accountId) {
      if (!this.accountsCache?.length) {
        this.viewModel.set({status: 'no-accounts'});
        return;
      }
      const account = this.accountsCache.find((value) => value.primary) ?? this.accountsCache[0];
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {accountId: account.id},
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
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
    this.viewModel.set(
      previousPage === null ? {status: 'loading'} : {status: 'busy', value: previousPage},
    );
    const params = this.route.snapshot.queryParamMap.keys.reduce((query, key) => {
      for (const value of this.route.snapshot.queryParamMap.getAll(key))
        query = query.append(key, value);
      return query;
    }, new HttpParams());
    this.requestSubscription = this.api
      .list(params)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (generation !== this.generation) return;
          if (!this.accountsCache?.some((account) => account.id === this.selectedAccountId())) {
            this.viewModel.set({status: 'account-unavailable'});
            return;
          }
          this.viewModel.set(
            result.items.length
              ? {status: 'ready', value: result}
              : {status: 'none', value: result},
          );
        },
        error: (failure: unknown) => {
          if (generation !== this.generation) return;
          if (failure instanceof HttpErrorResponse && failure.status === 404)
            this.viewModel.set({status: 'account-unavailable'});
          else if (failure instanceof HttpErrorResponse && failure.status === 400)
            this.viewModel.set({status: 'validation'});
          else this.viewModel.set({status: 'error'});
        },
      });
  }
}
