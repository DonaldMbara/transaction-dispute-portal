import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { Envelope } from '../../../core/auth/auth-contracts';
import { AccountPage } from './account-contracts';

@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);
  list(): Observable<AccountPage> {
    return this.http.get<Envelope<AccountPage>>('/api/v1/accounts').pipe(
      map(response => {
        if (!response.status || !response.data || !Array.isArray(response.data.items)) {
          throw new Error('Invalid accounts response.');
        }
        return response.data;
      })
    );
  }
}
