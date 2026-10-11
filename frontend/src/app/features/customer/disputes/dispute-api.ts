import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Envelope } from '../../../core/auth/auth-contracts';
import { DisputeSubmission, SubmitDisputeRequest } from './dispute-models';

@Injectable({ providedIn: 'root' })
export class DisputeApi {
  private readonly http = inject(HttpClient);
  submit(request: SubmitDisputeRequest, key: string): Observable<DisputeSubmission> {
    return this.http.post<Envelope<DisputeSubmission>>('/api/v1/disputes', request, { headers: { 'Idempotency-Key': key } }).pipe(
      map(response => {
        if (!response.status || !response.data || response.data.transactionId !== request.transactionId || response.data.status !== 'OPEN') throw new Error('Invalid dispute response.');
        return response.data;
      })
    );
  }
}
