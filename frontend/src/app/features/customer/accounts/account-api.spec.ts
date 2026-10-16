import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AccountApi } from './account-api';

describe('AccountApi', () => {
  let api: AccountApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(AccountApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps the established envelope from the same-origin accounts endpoint', () => {
    let result: unknown;

    api.list().subscribe((value) => (result = value));

    const request = http.expectOne('/api/v1/accounts');

    expect(request.request.method).toBe('GET');

    request.flush({
      status: true,
      error_code: 0,
      data: {
        total: 1,
        page: 1,
        size: 20,
        items: [
          {
            id: 'id',
            accountType: 'MAIN',
            displayName: 'Main Account',
            accountNumber: '0123456789',
            maskedNumber: '****6789',
            balance: '120.00',
            currency: 'ZAR',
            primary: true,
          },
        ],
      },
    });

    expect(result).toEqual(jasmine.objectContaining({ total: 1, page: 1 }));
  });
});
