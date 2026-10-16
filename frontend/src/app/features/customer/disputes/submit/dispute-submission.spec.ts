import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TransactionApi } from '../../transactions/api/transaction-api';
import { DisputeApi } from '../api/dispute-api';
import { DisputeSubmission } from './dispute-submission';

describe('DisputeSubmission', () => {
  const id = '10000000-0000-4000-8000-000000000001';
  const item = {
    id,
    reference: 'TXN-001',
    merchant: 'Market',
    amount: '125.50',
    currency: 'ZAR' as const,
    direction: 'OUT' as const,
    status: 'COMPLETED' as const,
    occurredAt: '2025-03-10T08:15:00Z',
    account: { id: 'account-main', maskedNumber: '****6789' },
    dispute: { id: null, eligible: true, ineligibilityReason: null },
  };
  let transactionApi: jasmine.SpyObj<TransactionApi>;
  let disputeApi: jasmine.SpyObj<DisputeApi>;

  beforeEach(async () => {
    transactionApi = jasmine.createSpyObj<TransactionApi>('TransactionApi', ['get', 'list']);
    transactionApi.get.and.returnValue(of(item));
    disputeApi = jasmine.createSpyObj<DisputeApi>('DisputeApi', ['submit']);
    await TestBed.configureTestingModule({
      imports: [DisputeSubmission],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({ transactionId: id })) },
        },
        { provide: TransactionApi, useValue: transactionApi },
        { provide: DisputeApi, useValue: disputeApi },
      ],
    }).compileComponents();
  });

  it('loads the masked summary, validates, reviews and submits a trimmed request', () => {
    const fixture = TestBed.createComponent(DisputeSubmission);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.textContent).toContain('****6789');
    expect(fixture.nativeElement.textContent).not.toContain('0123456789');
    component.submit();
    expect(disputeApi.submit).not.toHaveBeenCalled();
    component.form.controls.reason.setValue('DUPLICATE_CHARGE');
    component.form.controls.description.setValue('  Charged twice.  ');
    disputeApi.submit.and.returnValue(
      of({
        id: 'case-id',
        transactionId: id,
        reason: 'DUPLICATE_CHARGE',
        description: 'Charged twice.',
        status: 'OPEN',
        createdAt: '2026-10-11T00:00:00Z',
      }),
    );
    component.submit();
    expect(disputeApi.submit).toHaveBeenCalledWith(
      { transactionId: id, reason: 'DUPLICATE_CHARGE', description: 'Charged twice.' },
      jasmine.any(String),
    );
    expect(component.submission()?.id).toBe('case-id');
  });

  it('keeps an unchanged idempotency key for manual retry after an unavailable response', () => {
    const fixture = TestBed.createComponent(DisputeSubmission);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.form.controls.reason.setValue('OTHER');
    component.form.controls.description.setValue('Details');
    disputeApi.submit.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.submit();
    const key = disputeApi.submit.calls.mostRecent().args[1];
    component.retry();
    expect(disputeApi.submit.calls.mostRecent().args[1]).toBe(key);
  });

  it('blocks a direct dispute route for an incoming transaction', () => {
    transactionApi.get.and.returnValue(
      of({
        ...item,
        direction: 'IN' as const,
        dispute: { id: null, eligible: false, ineligibilityReason: 'NOT_OUTGOING' },
      }),
    );
    const fixture = TestBed.createComponent(DisputeSubmission);
    fixture.detectChanges();
    expect(fixture.componentInstance.loadState()).toBe('ineligible');
    expect(fixture.nativeElement.textContent).toContain(
      'Only payments leaving your account can be disputed.',
    );
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });
});
