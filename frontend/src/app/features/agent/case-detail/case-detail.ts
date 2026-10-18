import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal,} from '@angular/core';
import {DatePipe} from '@angular/common';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {FormControl, ReactiveFormsModule, Validators} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {catchError, of, switchMap} from 'rxjs';
import {AuthService} from '../../../core/auth/services/auth-service';
import {Button} from '../../../shared/components/button/button';
import {LoadingSpinner} from '../../../shared/components/loading-spinner/loading-spinner';
import {AgentDisputeApi} from '../services/agent-dispute-api';
import {AgentCaseView, AgentDisputeEvent} from '../models/agent-dispute-models';
import {
  AgentCaseDetailPresentation,
  agentCaseReasonLabel,
  agentCaseReturnQueryParams,
  agentCaseStatusClass,
  agentCaseStatusLabel,
  agentEventActor,
  agentEventLabel,
  formatAgentCaseAmount,
  formatAgentTransactionAmount,
} from '../utils/agent-case-detail-presentation';
import {CaseSummary} from '../case-summary/case-summary';

@Component({
  selector: 'app-case-detail',
  standalone: true,
  imports: [DatePipe, RouterLink, ReactiveFormsModule, Button, LoadingSpinner, CaseSummary],
  templateUrl: './case-detail.html',
  styleUrl: './case-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaseDetail {
  readonly current = signal<AgentCaseView | null>(null);
  readonly loading = signal(true);
  readonly pageError = signal<'not-found' | 'generic' | ''>('');
  readonly actionError = signal('');
  readonly busy = signal(false);
  readonly formMode = signal<'release' | 'resolve' | null>(null);
  readonly confirmMode = signal<'release' | 'accept' | 'reject' | null>(null);
  readonly releaseReason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });
  readonly decision = new FormControl<'ACCEPT' | 'REJECT' | ''>('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly internalNotes = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(4000)],
  });
  readonly customerMessage = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });
  readonly detail = computed(() => this.current()?.detail ?? null);
  readonly canClaim = computed(
    () => this.detail()?.status === 'OPEN' && this.detail()?.assignedAgent === null,
  );
  readonly assignedToMe = computed(
    () =>
      this.detail()?.status === 'UNDER_REVIEW' &&
      this.detail()?.assignedAgent?.id === this.identity()?.id,
  );
  readonly terminal = computed(
    () => this.detail()?.status === 'ACCEPTED' || this.detail()?.status === 'REJECTED',
  );
  readonly presentation: AgentCaseDetailPresentation = {
    statusLabel: agentCaseStatusLabel,
    statusClass: agentCaseStatusClass,
    reasonLabel: agentCaseReasonLabel,
    formatAmount: formatAgentCaseAmount,
    transactionAmount: formatAgentTransactionAmount,
    eventActor: (event: AgentDisputeEvent) => agentEventActor(event, this.identity()?.id),
    eventLabel: agentEventLabel,
  };
  private readonly api = inject(AgentDisputeApi);
  private readonly route = inject(ActivatedRoute);
  readonly backQueryParams = agentCaseReturnQueryParams(this.route.snapshot.queryParamMap);
  private readonly auth = inject(AuthService);
  readonly identity = this.auth.state.identity;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          const id = params.get('id');
          if (!id) return of({view: null, failure: new HttpErrorResponse({status: 404})});
          this.loading.set(true);
          this.pageError.set('');
          this.actionError.set('');
          return this.api.detail(id).pipe(
            switchMap((view) => of({view, failure: null as unknown})),
            catchError((failure: unknown) => of({view: null, failure})),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({view, failure}) => {
        this.loading.set(false);
        if (failure) {
          this.pageError.set(
            failure instanceof HttpErrorResponse && failure.status === 404
              ? 'not-found'
              : 'generic',
          );
          return;
        }
        this.current.set(view);
      });
  }

  refresh(): void {
    const id = this.detail()?.id;
    if (id) this.loadCurrent(id);
  }

  claim(): void {
    const view = this.current();
    if (view) this.runMutation(() => this.api.claim(view.detail.id, view.etag));
  }

  askRelease(): void {
    this.releaseReason.markAsTouched();
    if (this.releaseReason.invalid || !this.releaseReason.value.trim()) return;
    this.confirmMode.set('release');
  }

  askResolve(): void {
    this.decision.markAsTouched();
    this.internalNotes.markAsTouched();
    this.customerMessage.markAsTouched();
    if (
      this.decision.invalid ||
      this.internalNotes.invalid ||
      this.customerMessage.invalid ||
      !this.internalNotes.value.trim() ||
      !this.customerMessage.value.trim()
    )
      return;
    this.confirmMode.set(this.decision.value === 'ACCEPT' ? 'accept' : 'reject');
  }

  cancelForm(): void {
    this.formMode.set(null);
    this.actionError.set('');
  }

  cancelConfirmation(): void {
    this.confirmMode.set(null);
  }

  confirmAction(): void {
    const action = this.confirmMode();
    const view = this.current();
    if (!action || !view || this.busy()) return;
    this.confirmMode.set(null);
    if (action === 'release')
      this.runMutation(() =>
        this.api.release(view.detail.id, view.etag, {reason: this.releaseReason.value.trim()}),
      );
    else
      this.runMutation(() =>
        this.api.resolve(view.detail.id, view.etag, {
          decision: action === 'accept' ? 'ACCEPT' : 'REJECT',
          internalResolutionNotes: this.internalNotes.value.trim(),
          customerResolutionMessage: this.customerMessage.value.trim(),
        }),
      );
  }

  retryPage(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadCurrent(id);
  }

  private runMutation(operation: () => import('rxjs').Observable<AgentCaseView>): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.actionError.set('');
    operation()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.current.set(result);
          this.busy.set(false);
          this.formMode.set(null);
        },
        error: (failure: unknown) => {
          this.busy.set(false);
          if (
            failure instanceof HttpErrorResponse &&
            (failure.status === 409 || failure.status === 412)
          ) {
            this.actionError.set(
              'This case changed while you were reviewing it. The latest case is shown below; your unsent text is still here. Review the details before trying again.',
            );
            const id = this.detail()?.id;
            if (id) this.loadCurrent(id, true);
          } else
            this.actionError.set(
              'The change could not be confirmed. Check the case before trying again.',
            );
        },
      });
  }

  private loadCurrent(id: string, preserveError = false): void {
    this.api
      .detail(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.current.set(result);
          if (!preserveError) this.pageError.set('');
          this.loading.set(false);
        },
        error: (failure: unknown) => {
          this.pageError.set(
            failure instanceof HttpErrorResponse && failure.status === 404
              ? 'not-found'
              : 'generic',
          );
          this.loading.set(false);
        },
      });
  }
}
