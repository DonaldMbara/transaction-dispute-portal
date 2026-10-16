import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth-service';
import { AgentDisputeApi } from '../api/agent-dispute-api';
import { CaseQueue } from './case-queue';
import { AgentDisputeListItem } from '../models/agent-dispute-models';
import { caseQueueAction } from './agent-case-queue-presentation';

const row = (overrides: Partial<AgentDisputeListItem> = {}): AgentDisputeListItem => ({
  id: 'case-1',
  reference: 'DSP-CASE1234',
  merchant: 'Example merchant',
  amount: '42.50',
  currency: 'ZAR',
  direction: 'OUT',
  reason: 'OTHER',
  status: 'OPEN',
  createdAt: '2026-10-11T08:00:00Z',
  updatedAt: '2026-10-11T08:00:00Z',
  resolvedAt: null,
  assignedAgent: null,
  version: 0,
  ...overrides,
});
const auth = {
  state: {
    identity: signal({ id: 'agent-1', email: 'agent@example.test', role: 'AGENT' as const }),
  },
};

describe('CaseQueue', () => {
  let api: jasmine.SpyObj<AgentDisputeApi>;
  beforeEach(() => {
    api = jasmine.createSpyObj<AgentDisputeApi>('AgentDisputeApi', ['list']);
    api.list.and.returnValue(
      of({ status: true, error_code: 0, data: { total: 1, page: 1, size: 5, items: [row()] } }),
    );
    TestBed.configureTestingModule({
      imports: [CaseQueue],
      providers: [
        provideRouter([]),
        { provide: AgentDisputeApi, useValue: api },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('loads the unassigned queue with scoped counts and renders the case action', async () => {
    const fixture = TestBed.createComponent(CaseQueue);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.list).toHaveBeenCalled();
    expect(
      api.list.calls.allArgs().some(([query]) => query.queue === 'unassigned' && query.page === 1),
    ).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Example merchant');
    expect(fixture.nativeElement.textContent).toContain('Review case');
  });

  it('uses queue and assignment state to select Investigate and View case actions', () => {
    expect(caseQueueAction(row(), 'agent-1')).toBe('Review case');
    expect(
      caseQueueAction(
        row({
          status: 'UNDER_REVIEW',
          assignedAgent: { id: 'agent-1', email: 'agent@example.test' },
        }),
        'agent-1',
      ),
    ).toBe('Investigate');
    expect(
      caseQueueAction(
        row({
          status: 'UNDER_REVIEW',
          assignedAgent: { id: 'other-agent', email: 'other@example.test' },
        }),
        'agent-1',
      ),
    ).toBe('View case');
    expect(
      caseQueueAction(
        row({ status: 'ACCEPTED', assignedAgent: { id: 'agent-1', email: 'agent@example.test' } }),
        'agent-1',
      ),
    ).toBe('View case');
  });

  it('shows a retry state after a queue request fails', async () => {
    api.list.and.returnValue(throwError(() => new Error('offline')));
    const fixture = TestBed.createComponent(CaseQueue);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cases could not be loaded.');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
