import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AccountApi } from '../accounts/account-api';
import { DisputeApi } from './dispute-api';
import { Disputes } from './disputes';
import { DisputePage } from './dispute-models';

describe('Customer disputes list', () => {
  let fixture: ComponentFixture<Disputes>;
  let api: jasmine.SpyObj<DisputeApi>;
  let accounts: jasmine.SpyObj<AccountApi>;
  const page: DisputePage = { total: 1, page: 1, size: 100, items: [{
    id: '5f9868e0-1272-47ee-b0d2-ef33e4e1d331', reason: 'UNRECOGNIZED_TRANSACTION', status: 'UNDER_REVIEW', createdAt: '2026-10-11T08:00:00Z', updatedAt: '2026-10-11T08:00:00Z', resolvedAt: null,
    transaction: { id: 'tx-1', merchant: 'Checkers Hyper', reference: 'TXN-1', amount: '862.76', currency: 'ZAR', direction: 'OUT', occurredAt: '2026-10-10T10:42:00Z', account: { id: 'account-1', displayName: 'Main Account', maskedNumber: '****1467' } }
  }] };

  beforeEach(async () => {
    api = jasmine.createSpyObj<DisputeApi>('DisputeApi', ['list', 'get', 'submit']); api.list.and.returnValue(of({ status: true, data: page, error_code: 0 }));
    accounts = jasmine.createSpyObj<AccountApi>('AccountApi', ['list']); accounts.list.and.returnValue(of({ items: [{ id: 'account-1', accountType: 'MAIN', displayName: 'Main Account', accountNumber: null, maskedNumber: '****1467', balance: '1.00', currency: 'ZAR', primary: true }], page: 1, size: 20, total: 1 }));
    await TestBed.configureTestingModule({ imports: [Disputes], providers: [provideRouter([]), { provide: DisputeApi, useValue: api }, { provide: AccountApi, useValue: accounts }] }).compileComponents();
    fixture = TestBed.createComponent(Disputes); fixture.detectChanges();
  });

  it('renders the case row and derives the customer reference and reason', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Checkers Hyper'); expect(el.textContent).toContain('DSP-5F9868E0');
    expect(el.textContent).toContain('Unrecognised transaction'); expect(el.textContent).toContain('Under review');
    expect(el.textContent).toContain('R 862.76'); expect(el.textContent).not.toContain('−R 862.76');
    expect(el.querySelector('a.view-case')?.textContent).toContain('View case');
    expect(el.textContent).toContain('Reset filters');
    expect(el.textContent).toContain('All · 1');
  });

  it('requests ACTIVE and RESOLVED groups from their tabs', () => {
    const component = fixture.componentInstance;
    const mainQuery = () => api.list.calls.allArgs().find(([query]) => query.size === 20)?.[0];
    component.setTab('ACTIVE'); component.load(); expect(mainQuery()?.status).toBe('ACTIVE');
    component.setTab('RESOLVED'); component.load(); expect(mainQuery()?.status).toBe('RESOLVED');
    component.setTab('ALL'); component.load(); expect(mainQuery()?.status).toBeUndefined();
  });

  it('does not send accountId for the all-accounts selection', () => {
    fixture.componentInstance.selectAccount(null); fixture.componentInstance.load();
    expect(api.list.calls.allArgs().find(([query]) => query.size === 20)?.[0].accountId).toBeUndefined();
  });

  it('shows an empty state and a retry state', () => {
    api.list.and.returnValue(of({ status: true, data: { ...page, total: 0, items: [] }, error_code: 0 }));
    fixture.componentInstance.load(); fixture.detectChanges(); expect((fixture.nativeElement as HTMLElement).textContent).toContain('No disputes found');
    api.list.and.returnValue(throwError(() => new Error('offline'))); fixture.componentInstance.load(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain("We couldn't load your disputes");
  });
});
