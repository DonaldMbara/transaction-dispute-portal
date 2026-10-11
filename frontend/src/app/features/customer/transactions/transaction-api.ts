import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Envelope } from '../../../core/auth/auth-contracts';
import { TransactionItem, TransactionPage } from './transaction-models';

@Injectable({ providedIn: 'root' })
export class TransactionApi {
  private readonly http = inject(HttpClient);

  list(params: HttpParams): Observable<TransactionPage> {
    return this.http.get<Envelope<TransactionPage>>('/api/v1/transactions', { params }).pipe(
      map(response => {
        if (!response.status || !response.data || !Array.isArray(response.data.items)) throw new Error('Invalid transactions response.');
        return response.data;
      })
    );
  }

  get(id: string): Observable<TransactionItem> {
    return this.http.get<Envelope<TransactionItem>>(`/api/v1/transactions/${encodeURIComponent(id)}`).pipe(
      map(response => {
        if (!response.status || !response.data || response.data.id !== id) throw new Error('Invalid transaction response.');
        return response.data;
      })
    );
  }
}
