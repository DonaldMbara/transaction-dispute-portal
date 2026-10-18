import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {provideLocationMocks} from '@angular/common/testing';
import {of} from 'rxjs';
import {AccountApi} from '../../accounts/serviecs/account-api';
import {AccountPage, AccountSummary} from '../../accounts/models/account-models';
import {TransactionApi} from '../services/transaction-api';
import {TransactionPage} from '../models/transaction-models';
import {Transactions} from '../transactions';

export const mainAccount: AccountSummary = {
  id: 'account-main',
  accountType: 'MAIN',
  displayName: 'Main Account',
  accountNumber: '0123456789',
  maskedNumber: '****6789',
  balance: '12500.00',
  currency: 'ZAR',
  primary: true,
};

export const savingsAccount: AccountSummary = {
  ...mainAccount,
  id: 'account-savings',
  accountType: 'SAVINGS',
  displayName: 'Savings Account',
  accountNumber: '0012345678',
  maskedNumber: '****5678',
  primary: false,
};

export const transaction = {
  id: '10000000-0000-0000-0000-000000000001',
  reference: 'TXN-001',
  merchant: 'Market',
  amount: '125.50',
  currency: 'ZAR' as const,
  direction: 'OUT' as const,
  status: 'COMPLETED' as const,
  occurredAt: '2025-03-10T08:15:00Z',
  account: {id: mainAccount.id, maskedNumber: mainAccount.maskedNumber},
  dispute: {id: null, eligible: true, ineligibilityReason: null},
};

export const transactionPage: TransactionPage = {
  total: 1,
  page: 1,
  size: 20,
  items: [transaction],
};

export interface TransactionsTestContext {
  accountApi: jasmine.SpyObj<AccountApi>;
  transactionApi: jasmine.SpyObj<TransactionApi>;
}

export async function configureTransactionsTest(
  accounts: readonly AccountSummary[] = [mainAccount, savingsAccount],
): Promise<TransactionsTestContext> {
  const accountApi = jasmine.createSpyObj<AccountApi>('AccountApi', ['list']);
  const transactionApi = jasmine.createSpyObj<TransactionApi>('TransactionApi', ['list', 'get']);
  const accountPage: AccountPage = {total: accounts.length, page: 1, size: 20, items: [...accounts]};
  accountApi.list.and.returnValue(of(accountPage));
  transactionApi.list.and.returnValue(of(transactionPage));
  await TestBed.configureTestingModule({
    imports: [Transactions],
    providers: [
      provideRouter([{path: 'customer/transactions', component: Transactions}]),
      provideLocationMocks(),
      {provide: AccountApi, useValue: accountApi},
      {provide: TransactionApi, useValue: transactionApi},
    ],
  }).compileComponents();
  return {accountApi, transactionApi};
}

export async function startTransactionsTest(
  url = '/customer/transactions?accountId=account-main',
): Promise<{ harness: RouterTestingHarness; component: Transactions }> {
  const harness = await RouterTestingHarness.create(url);
  const component = harness.routeDebugElement!.componentInstance as Transactions;
  harness.detectChanges();
  await harness.fixture.whenStable();
  harness.detectChanges();
  return {harness, component};
}
