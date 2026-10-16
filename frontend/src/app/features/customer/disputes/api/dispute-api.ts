import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Envelope } from '../../../../core/auth/auth-models';
import {
  DisputeDetail,
  DisputeListQuery,
  DisputePage,
  DisputeSubmission,
  SubmitDisputeRequest,
} from '../models/dispute-models';

// ApiResponse wraps the envelope data for callers that need full error-code access
export type ApiResponse<T> = Envelope<T>;

@Injectable({ providedIn: 'root' })
export class DisputeApi {
  private readonly http = inject(HttpClient);

  submit(request: SubmitDisputeRequest, key: string): Observable<DisputeSubmission> {
    return this.http
      .post<Envelope<DisputeSubmission>>('/api/v1/disputes', request, {
        headers: { 'Idempotency-Key': key },
      })
      .pipe(
        map((response) => {
          if (
            !response.status ||
            !response.data ||
            response.data.transactionId !== request.transactionId ||
            response.data.status !== 'OPEN'
          )
            throw new Error('Invalid dispute response.');
          return response.data;
        }),
      );
  }

  list(query: DisputeListQuery): Observable<ApiResponse<DisputePage>> {
    let params = new HttpParams();
    if (query.page !== undefined) params = params.set('page', String(query.page));
    if (query.size !== undefined) params = params.set('size', String(query.size));
    if (query.accountId !== undefined && query.accountId !== '')
      params = params.set('accountId', query.accountId);
    if (query.status !== undefined && query.status !== '' && query.status !== 'ALL')
      params = params.set('status', query.status);
    if (query.reason !== undefined && query.reason !== '' && query.reason !== 'ALL')
      params = params.set('reason', query.reason);
    if (query.fromDate !== undefined && query.fromDate !== '')
      params = params.set('fromDate', query.fromDate);
    if (query.toDate !== undefined && query.toDate !== '')
      params = params.set('toDate', query.toDate);
    if (query.search !== undefined && query.search.trim() !== '')
      params = params.set('search', query.search.trim());
    if (query.sortBy !== undefined && query.sortBy !== '')
      params = params.set('sortBy', query.sortBy);
    if (query.sortDirection !== undefined && query.sortDirection !== '')
      params = params.set('sortDirection', query.sortDirection);
    return this.http.get<ApiResponse<DisputePage>>('/api/v1/disputes', { params });
  }

  get(id: string): Observable<ApiResponse<DisputeDetail>> {
    return this.http.get<ApiResponse<DisputeDetail>>(`/api/v1/disputes/${id}`);
  }
}
