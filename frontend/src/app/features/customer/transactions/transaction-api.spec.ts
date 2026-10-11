import { TestBed } from '@angular/core/testing';
import { HttpParams, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TransactionApi } from './transaction-api';

describe('TransactionApi', () => {
  let api: TransactionApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TransactionApi); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('uses the relative transaction endpoints and maps the application envelope', () => {
    let items = 0;
    api.list(new HttpParams({ fromString: 'accountId=account-a' })).subscribe(page => items = page.items.length);
    const request = http.expectOne('/api/v1/transactions?accountId=account-a');
    request.flush({ status: true, error_code: 0, data: { total: 0, page: 1, size: 20, items: [] } });
    expect(items).toBe(0);
  });
});
