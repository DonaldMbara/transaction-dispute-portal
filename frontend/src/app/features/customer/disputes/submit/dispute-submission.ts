import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TransactionApi } from '../../transactions/api/transaction-api';
import { TransactionItem } from '../../transactions/models/transaction-models';
import { formatZar } from '../../../../shared/utils/zar-formatter';
import { LoadingSpinner } from '../../../../shared/components/loading-spinner/loading-spinner';
import { Button } from '../../../../shared/components/button/button';
import { DisputeApi } from '../api/dispute-api';
import {
  DISPUTE_REASONS,
  DISPUTE_TRANSACTION_ID_PATTERN,
} from './dispute-submission.constant';
import {
  DisputeReason,
  DisputeSubmission as Submission,
  SubmitDisputeRequest,
} from '../models/dispute-models';

@Component({
  selector: 'app-dispute-submission',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LoadingSpinner, Button],
  templateUrl: './dispute-submission.html',
  styleUrl: './dispute-submission.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisputeSubmission {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly transactions = inject(TransactionApi);
  private readonly disputes = inject(DisputeApi);
  readonly transaction = signal<TransactionItem | null>(null);
  readonly loadState = signal<'loading' | 'ready' | 'missing' | 'ineligible' | 'error'>('loading');
  readonly busy = signal(false);
  readonly attempted = signal(false);
  readonly error = signal('');
  readonly submission = signal<Submission | null>(null);
  readonly reasons = DISPUTE_REASONS;
  readonly descriptionCount = computed(() => this.form.controls.description.value.trim().length);
  readonly form = this.fb.nonNullable.group({
    reason: this.fb.nonNullable.control<DisputeReason | ''>('', Validators.required),
    description: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/\S/),
      Validators.maxLength(2000),
    ]),
  });
  private transactionId = '';
  private idempotencyKey = this.newKey();
  private keyedPayload = '';

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.rotateKeyForChangedPayload());
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('transactionId') ?? '';
      this.transactionId = id;
      if (!DISPUTE_TRANSACTION_ID_PATTERN.test(id)) {
        this.loadState.set('missing');
        return;
      }
      this.loadState.set('loading');
      this.transactions
        .get(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (item) => {
            this.transaction.set(item);
            if (!item.dispute.eligible || item.dispute.id || item.direction !== 'OUT')
              this.loadState.set('ineligible');
            else this.loadState.set('ready');
          },
          error: (failure: unknown) =>
            this.loadState.set(
              failure instanceof HttpErrorResponse && failure.status === 404 ? 'missing' : 'error',
            ),
        });
    });
  }

  submit(): void {
    this.attempted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const transaction = this.transaction();
    const reason = this.form.controls.reason.value;
    const description = this.form.controls.description.value.trim();
    if (!transaction || !reason || !description || this.busy()) return;
    const request: SubmitDisputeRequest = {
      transactionId: this.transactionId,
      reason,
      description,
    };
    this.keyedPayload = JSON.stringify(request);
    this.busy.set(true);
    this.error.set('');
    this.disputes
      .submit(request, this.idempotencyKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.submission.set(result);
          this.busy.set(false);
        },
        error: (failure: unknown) => {
          this.error.set(this.errorMessage(failure));
          this.busy.set(false);
        },
      });
  }
  retry(): void {
    this.submit();
  }
  formatZar = formatZar;
  formatDate(value: string): string {
    return new Intl.DateTimeFormat('en-ZA', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'Africa/Johannesburg',
    }).format(new Date(value));
  }
  cancel(): void {
    void this.router.navigate(['/customer/transactions', this.transactionId]);
  }
  private rotateKeyForChangedPayload(): void {
    const reason = this.form.controls.reason.value;
    const description = this.form.controls.description.value.trim();
    if (!reason || !description) return;
    const payload = JSON.stringify({ transactionId: this.transactionId, reason, description });
    if (this.keyedPayload && this.keyedPayload !== payload) this.idempotencyKey = this.newKey();
    this.keyedPayload = payload;
  }
  private newKey(): string {
    return crypto.randomUUID();
  }
  private errorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse))
      return 'We could not confirm your dispute. Retry the same details safely.';
    const code = error.error?.error_code;
    if (error.status === 409 && code === 2003)
      return 'A dispute already exists for this transaction. Return to the transaction details to review it.';
    if (error.status === 409 && code === 2004) {
      this.idempotencyKey = this.newKey();
      return 'The submission changed. Review the details and submit again.';
    }
    if (error.status === 422 && code === 2002)
      return 'Only completed transactions can be disputed.';
    if (error.status === 404) return 'This transaction is no longer available.';
    if (error.status === 403 && code === 1010)
      return 'We could not verify this request. Refresh the page and try again.';
    return 'We could not confirm your dispute. Retry the same details safely.';
  }
}
