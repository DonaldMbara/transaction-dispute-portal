import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Envelope } from '../../../core/auth/auth-models';
import {
  AgentCaseView,
  AgentDisputeDetail,
  AgentDisputePage,
  AgentDisputeQuery,
  AgentReleaseRequest,
  AgentResolveRequest,
} from '../models/agent-dispute-models';

@Injectable({ providedIn: 'root' })
export class AgentDisputeApi {
  private readonly http = inject(HttpClient);

  list(query: AgentDisputeQuery): Observable<Envelope<AgentDisputePage>> {
    let params = new HttpParams()
      .set('queue', query.queue)
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('sortDirection', query.sortDirection);
    for (const key of ['search', 'status', 'reason', 'fromDate', 'toDate'] as const) {
      const value = query[key];
      if (value?.trim()) params = params.set(key, value.trim());
    }
    return this.http.get<Envelope<AgentDisputePage>>('/api/v1/agent/disputes', { params });
  }

  detail(id: string): Observable<AgentCaseView> {
    return this.http
      .get<Envelope<AgentDisputeDetail>>(`/api/v1/agent/disputes/${encodeURIComponent(id)}`, {
        observe: 'response',
      })
      .pipe(map((response) => this.mapCase(response)));
  }

  claim(id: string, etag: string): Observable<AgentCaseView> {
    return this.command(`/api/v1/agent/disputes/${encodeURIComponent(id)}/claim`, {}, etag);
  }
  release(id: string, etag: string, request: AgentReleaseRequest): Observable<AgentCaseView> {
    return this.command(`/api/v1/agent/disputes/${encodeURIComponent(id)}/release`, request, etag);
  }
  resolve(id: string, etag: string, request: AgentResolveRequest): Observable<AgentCaseView> {
    return this.command(`/api/v1/agent/disputes/${encodeURIComponent(id)}/resolve`, request, etag);
  }

  private command(url: string, body: object, etag: string): Observable<AgentCaseView> {
    return this.http
      .post<Envelope<AgentDisputeDetail>>(url, body, {
        headers: { 'If-Match': etag },
        observe: 'response',
      })
      .pipe(map((response) => this.mapCase(response)));
  }

  private mapCase(response: HttpResponse<Envelope<AgentDisputeDetail>>): AgentCaseView {
    const detail = response.body?.data;
    const etag = response.headers.get('ETag');
    if (!response.body?.status || !detail || !etag || etag.startsWith('W/'))
      throw new Error('Invalid agent case response.');
    return { detail, etag };
  }
}
