import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { DisputeApi } from '../api/dispute-api';
import { DisputeDetail } from './dispute-detail';
import { DisputeDetail as Dispute } from '../models/dispute-models';

describe('Customer dispute detail', () => {
  let fixture: ComponentFixture<DisputeDetail>;
  let api: jasmine.SpyObj<DisputeApi>;
  const item: Dispute = {
    id: '5f9868e0-1272-47ee-b0d2-ef33e4e1d331',
    reason: 'DUPLICATE_CHARGE',
    status: 'ACCEPTED',
    createdAt: '2026-10-06T07:15:00Z',
    updatedAt: '2026-10-09T14:00:00Z',
    resolvedAt: '2026-10-09T14:00:00Z',
    description: 'Charged twice.',
    decision: { outcome: 'ACCEPTED', message: 'A duplicate charge was confirmed.' },
    timeline: [
      {
        type: 'SUBMITTED',
        message: 'We received your dispute.',
        occurredAt: '2026-10-06T07:15:00Z',
      },
      { type: 'IN_REVIEW', message: 'Review underway.', occurredAt: '2026-10-07T08:00:00Z' },
      {
        type: 'RETURNED_TO_QUEUE',
        message: 'Waiting for review.',
        occurredAt: '2026-10-07T12:30:00Z',
      },
      { type: 'IN_REVIEW', message: 'Review restarted.', occurredAt: '2026-10-08T08:00:00Z' },
      { type: 'RESOLVED', message: 'Accepted.', occurredAt: '2026-10-09T14:00:00Z' },
    ],
    transaction: {
      id: 'tx-1',
      merchant: 'Woolworths',
      reference: 'TXN-1',
      amount: '417.38',
      currency: 'ZAR',
      direction: 'OUT',
      occurredAt: '2026-10-05T12:00:00Z',
      account: { id: 'account-1', displayName: 'Main Account', maskedNumber: '****1467' },
    },
  };
  beforeEach(async () => {
    api = jasmine.createSpyObj<DisputeApi>('DisputeApi', ['get', 'list', 'submit']);
    api.get.and.returnValue(of({ status: true, data: item, error_code: 0 }));
    await TestBed.configureTestingModule({
      imports: [DisputeDetail],
      providers: [
        provideRouter([]),
        { provide: DisputeApi, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: item.id })),
            snapshot: {
              queryParamMap: convertToParamMap({}),
              paramMap: convertToParamMap({ id: item.id }),
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DisputeDetail);
    fixture.detectChanges();
  });
  it('shows the masked account, safe decision and dispute details', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('DSP-5F9868E0');
    expect(el.textContent).toContain('Main Account · ****1467');
    expect(el.textContent).toContain('This decision applies to your dispute only');
    expect(el.textContent).toContain('What you told us');
    expect(el.textContent).not.toContain('internal');
  });
  it('labels first and later review milestones', () => {
    const component = fixture.componentInstance;
    expect(component.timelineLabel(item.timeline[1], 1, item.timeline)).toBe('Review began');
    expect(component.timelineLabel(item.timeline[3], 3, item.timeline)).toBe('Review resumed');
  });
});
