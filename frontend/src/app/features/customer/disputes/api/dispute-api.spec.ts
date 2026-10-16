import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DisputeApi } from './dispute-api';
import { DisputeDetail, DisputePage } from '../models/dispute-models';

describe('DisputeApi', () => {
  let api: DisputeApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), DisputeApi],
    });
    api = TestBed.inject(DisputeApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/disputes with correct params', () => {
    const mockPage: DisputePage = { total: 1, page: 1, size: 20, items: [] };

    api.list({ page: 1, size: 20, status: 'ACTIVE', accountId: 'acc-123' }).subscribe((result) => {
      expect(result.status).toBeTrue();
      expect(result.data).toEqual(mockPage);
    });

    const req = httpMock.expectOne((r) => r.url === '/api/v1/disputes');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('accountId')).toBe('acc-123');
    req.flush({ status: true, data: mockPage, error_code: 0 });
  });

  it('list() omits status param when status is ALL', () => {
    api.list({ status: 'ALL' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/disputes');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ status: true, data: { total: 0, page: 1, size: 20, items: [] }, error_code: 0 });
  });

  it('list() omits accountId when not provided', () => {
    api.list({}).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/disputes');
    expect(req.request.params.has('accountId')).toBeFalse();
    req.flush({ status: true, data: { total: 0, page: 1, size: 20, items: [] }, error_code: 0 });
  });

  it('get(id) calls GET /api/v1/disputes/:id', () => {
    const mockDetail: DisputeDetail = {
      id: 'dispute-uuid',
      transaction: {
        id: 'tx-id',
        merchant: 'Merchant',
        reference: 'REF',
        amount: '100.00',
        currency: 'ZAR',
        direction: 'OUT',
        occurredAt: '2026-10-10T10:00:00Z',
        account: { id: 'acc-id', displayName: 'Main Account', maskedNumber: '****1234' },
      },
      reason: 'DUPLICATE_CHARGE',
      status: 'OPEN',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      resolvedAt: null,
      description: 'Test',
      decision: null,
      timeline: [],
    };

    api.get('dispute-uuid').subscribe((result) => {
      expect(result.status).toBeTrue();
      expect(result.data).toEqual(mockDetail);
    });

    const req = httpMock.expectOne('/api/v1/disputes/dispute-uuid');
    expect(req.request.method).toBe('GET');
    req.flush({ status: true, data: mockDetail, error_code: 0 });
  });

  it('get() handles success envelope correctly', () => {
    let resolved: DisputeDetail | undefined;
    api.get('some-id').subscribe((response) => {
      resolved = response.data;
    });

    const req = httpMock.expectOne('/api/v1/disputes/some-id');
    const mockData: DisputeDetail = {
      id: 'some-id',
      transaction: {
        id: 'tx',
        merchant: 'Shop',
        reference: 'R1',
        amount: '50.00',
        currency: 'ZAR',
        direction: 'OUT',
        occurredAt: '2026-10-10T10:00:00Z',
        account: { id: 'a', displayName: 'Main Account', maskedNumber: '****5678' },
      },
      reason: 'OTHER',
      status: 'OPEN',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      resolvedAt: null,
      description: 'desc',
      decision: null,
      timeline: [],
    };
    req.flush({ status: true, data: mockData, error_code: 0 });
    expect(resolved).toEqual(mockData);
  });
});
