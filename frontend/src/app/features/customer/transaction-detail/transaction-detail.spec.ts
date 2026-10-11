import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { TransactionApi } from '../transactions/transaction-api';
import { TransactionDetail } from './transaction-detail';

@Component({ standalone: true, template: '<p>Transaction list test route</p>' })
class TransactionListRoute {}

describe('TransactionDetail', () => {
  const transaction = { id: '10000000-0000-0000-0000-000000000001', reference: 'TXN-001', merchant: 'Market', amount: '125.50', currency: 'ZAR' as const, direction: 'OUT' as const, status: 'COMPLETED' as const, occurredAt: '2025-03-10T08:15:00Z', account: { id: 'account-main', maskedNumber: '****6789' }, dispute: { id: null, eligible: true, ineligibilityReason: null } };
  let api: jasmine.SpyObj<TransactionApi>;
  beforeEach(async () => {
    api = jasmine.createSpyObj<TransactionApi>('TransactionApi', ['list', 'get']); api.get.and.returnValue(of(transaction));
    await TestBed.configureTestingModule({ imports: [TransactionDetail, TransactionListRoute], providers: [
      provideRouter([{ path: 'customer/transactions/:id', component: TransactionDetail }, { path: 'customer/transactions', component: TransactionListRoute }]),
      { provide: TransactionApi, useValue: api }
    ] }).compileComponents();
  });

  it('shows a masked account and returns to the allowlisted account/filter URL', async () => {
    const returnTo = '/customer/transactions?accountId=account-main&page=2&direction=OUT';
    const harness = await RouterTestingHarness.create(`/customer/transactions/${transaction.id}?returnTo=${encodeURIComponent(returnTo)}`);
    const component = harness.routeDebugElement!.componentInstance as TransactionDetail;
    harness.detectChanges(); await harness.fixture.whenStable(); harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('****6789');
    expect(harness.routeNativeElement?.textContent).not.toContain('0123456789');
    expect(component.returnTo()).toBe(returnTo);
    component.goBack(); await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe(returnTo);
  });

  it('rejects external and unapproved return routes', async () => {
    const externalHarness = await RouterTestingHarness.create(`/customer/transactions/${transaction.id}?returnTo=${encodeURIComponent('https://evil.example/')}`);
    const external = externalHarness.routeDebugElement!.componentInstance as TransactionDetail;
    expect(external.returnTo()).toBe('/customer/transactions');
  });
});
