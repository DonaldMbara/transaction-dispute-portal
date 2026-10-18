import {HttpErrorResponse} from '@angular/common/http';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';
import {AuthService} from '../../../core/auth/services/auth-service';
import {AgentDisputeApi} from '../services/agent-dispute-api';
import {CaseDetail} from './case-detail';
import {AgentCaseView, AgentDisputeDetail} from '../models/agent-dispute-models';

const detail = (overrides: Partial<AgentDisputeDetail> = {}): AgentDisputeDetail => ({
  id: 'case-1',
  reference: 'DSP-CASE1234',
  transactionId: 'tx-1',
  merchant: 'Example merchant',
  amount: '42.50',
  currency: 'ZAR',
  direction: 'OUT',
  occurredAt: '2026-10-11T08:00:00Z',
  transactionReference: 'TXN-123',
  accountId: 'account-1',
  accountDisplayName: 'Main Account',
  maskedAccountNumber: '****0146',
  reason: 'OTHER',
  status: 'OPEN',
  description: 'Customer text',
  createdAt: '2026-10-11T08:00:00Z',
  updatedAt: '2026-10-11T08:00:00Z',
  resolvedAt: null,
  assignedAt: null,
  assignedAgent: null,
  internalResolutionNotes: null,
  customerResolutionMessage: null,
  version: 0,
  events: [],
  ...overrides,
});
const view = (
  value: AgentDisputeDetail = detail(),
  etag = '"dispute-case-1-v0"',
): AgentCaseView => ({detail: value, etag});
const auth = {
  state: {
    identity: signal({id: 'agent-1', email: 'agent@example.test', role: 'AGENT' as const}),
  },
};

describe('CaseDetail', () => {
  let api: jasmine.SpyObj<AgentDisputeApi>;
  beforeEach(() => {
    api = jasmine.createSpyObj<AgentDisputeApi>('AgentDisputeApi', [
      'detail',
      'claim',
      'release',
      'resolve',
    ]);
    api.detail.and.returnValue(of(view()));
    const route = {
      paramMap: of(convertToParamMap({id: 'case-1'})),
      snapshot: {
        paramMap: convertToParamMap({id: 'case-1'}),
        queryParams: {queue: 'mine', page: '2', ignored: 'value'},
        queryParamMap: convertToParamMap({queue: 'mine', page: '2', ignored: 'value'}),
      },
    };
    TestBed.configureTestingModule({
      imports: [CaseDetail],
      providers: [
        provideRouter([]),
        {provide: ActivatedRoute, useValue: route},
        {provide: AgentDisputeApi, useValue: api},
        {provide: AuthService, useValue: auth},
      ],
    });
  });

  it('shows masked transaction context and offers claim only for an unassigned open case', async () => {
    const fixture = TestBed.createComponent(CaseDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('****0146');
    expect(fixture.nativeElement.textContent).toContain('Claim case');
    expect(fixture.nativeElement.textContent).not.toContain('account-1');
    expect(fixture.componentInstance.backQueryParams).toEqual({queue: 'mine', page: '2'});
  });

  it('sends the current ETag on claim and updates state from the response', async () => {
    const fixture = TestBed.createComponent(CaseDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    const claimed = detail({
      status: 'UNDER_REVIEW',
      version: 1,
      assignedAt: '2026-10-11T09:00:00Z',
      assignedAgent: {id: 'agent-1', email: 'agent@example.test'},
    });
    api.claim.and.returnValue(of(view(claimed, '"dispute-case-1-v1"')));
    fixture.componentInstance.claim();
    fixture.detectChanges();
    expect(api.claim).toHaveBeenCalledWith('case-1', '"dispute-case-1-v0"');
    expect(fixture.componentInstance.detail()?.status).toBe('UNDER_REVIEW');
  });

  it('requires a review confirmation before resolving a dispute', async () => {
    const assigned = detail({
      status: 'UNDER_REVIEW',
      assignedAt: '2026-10-11T09:00:00Z',
      assignedAgent: {id: 'agent-1', email: 'agent@example.test'},
    });
    api.detail.and.returnValue(of(view(assigned)));
    const fixture = TestBed.createComponent(CaseDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    component.formMode.set('resolve');
    component.decision.setValue('ACCEPT');
    component.internalNotes.setValue('Evidence supports the claim.');
    component.customerMessage.setValue('Your dispute was accepted.');
    component.askResolve();
    expect(component.confirmMode()).toBe('accept');
    expect(api.resolve).not.toHaveBeenCalled();
    api.resolve.and.returnValue(
      of(
        view(
          detail({
            status: 'ACCEPTED',
            version: 1,
            assignedAt: assigned.assignedAt,
            assignedAgent: assigned.assignedAgent,
            resolvedAt: '2026-10-11T10:00:00Z',
            internalResolutionNotes: 'Evidence supports the claim.',
            customerResolutionMessage: 'Your dispute was accepted.',
          }),
          '"dispute-case-1-v1"',
        ),
      ),
    );
    component.confirmAction();
    expect(api.resolve).toHaveBeenCalledWith('case-1', '"dispute-case-1-v0"', {
      decision: 'ACCEPT',
      internalResolutionNotes: 'Evidence supports the claim.',
      customerResolutionMessage: 'Your dispute was accepted.',
    });
  });

  it('refreshes after a stale release without clearing the unsent reason', async () => {
    const assigned = detail({
      status: 'UNDER_REVIEW',
      assignedAt: '2026-10-11T09:00:00Z',
      assignedAgent: {id: 'agent-1', email: 'agent@example.test'},
    });
    api.detail.and.returnValue(of(view(assigned)));
    const fixture = TestBed.createComponent(CaseDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    component.formMode.set('release');
    component.releaseReason.setValue('Needs specialist review');
    component.askRelease();
    api.release.and.returnValue(throwError(() => new HttpErrorResponse({status: 412})));
    component.confirmAction();
    await fixture.whenStable();
    expect(api.release).toHaveBeenCalledWith('case-1', '"dispute-case-1-v0"', {
      reason: 'Needs specialist review',
    });
    expect(api.detail).toHaveBeenCalledTimes(2);
    expect(component.releaseReason.value).toBe('Needs specialist review');
    expect(component.actionError()).toContain('case changed');
  });
});
