import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AgentDisputeApi } from './agent-dispute-api';
import { AgentDisputeDetail } from './agent-dispute-models';

const detail: AgentDisputeDetail = {
  id: 'case-id', reference: 'DSP-CASE1234', transactionId: 'tx-id', merchant: 'Example', amount: '12.30', currency: 'ZAR',
  direction: 'OUT', occurredAt: '2026-10-11T08:00:00Z', transactionReference: 'TXN-123', accountId: 'account-id',
  accountDisplayName: 'Main Account', maskedAccountNumber: '****0146', reason: 'OTHER', status: 'OPEN', description: 'Example',
  createdAt: '2026-10-11T08:00:00Z', updatedAt: '2026-10-11T08:00:00Z', resolvedAt: null,
  assignedAt: null, assignedAgent: null, internalResolutionNotes: null, customerResolutionMessage: null, version: 0, events: []
};

describe('AgentDisputeApi', () => {
  let api: AgentDisputeApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AgentDisputeApi); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('encodes the selected queue and filters in the agent list request', () => {
    api.list({ queue: 'mine', page: 2, size: 5, search: ' DSP-123 ', status: 'ACTIVE', reason: 'OTHER',
      fromDate: '2026-10-01', toDate: '2026-10-12', sortBy: 'createdAt', sortDirection: 'DESC' }).subscribe();
    const request = http.expectOne(r => r.url === '/api/v1/agent/disputes');
    expect(request.request.params.get('queue')).toBe('mine');
    expect(request.request.params.get('search')).toBe('DSP-123');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('reason')).toBe('OTHER');
    request.flush({ status: true, error_code: 0, data: { total: 0, page: 2, size: 5, items: [] } });
  });

  it('retains the strong ETag from detail and sends it unchanged with claim', () => {
    let tag = '';
    api.detail('case-id').subscribe(value => tag = value.etag);
    http.expectOne('/api/v1/agent/disputes/case-id').flush({ status: true, error_code: 0, data: detail },
      { headers: { ETag: '"dispute-case-id-v0"' } });
    expect(tag).toBe('"dispute-case-id-v0"');
    api.claim('case-id', tag).subscribe();
    const request = http.expectOne('/api/v1/agent/disputes/case-id/claim');
    expect(request.request.headers.get('If-Match')).toBe(tag);
    expect(request.request.body).toEqual({});
    request.flush({ status: true, error_code: 0, data: { ...detail, status: 'UNDER_REVIEW', version: 1,
      assignedAgent: { id: 'agent-id', email: 'agent@example.test' } } }, { headers: { ETag: '"dispute-case-id-v1"' } });
  });

  it('rejects detail responses that omit a strong ETag', () => {
    let failed = false;
    api.detail('case-id').subscribe({ error: () => failed = true });
    http.expectOne('/api/v1/agent/disputes/case-id').flush({ status: true, error_code: 0, data: detail });
    expect(failed).toBeTrue();
  });
});
