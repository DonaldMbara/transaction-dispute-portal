import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthFailure, CsrfBootstrap, Envelope, Identity, Transition } from './auth-contracts';
import { CsrfState, SessionState } from './session-state';
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly state = inject(SessionState);
  readonly csrf = inject(CsrfState);
  private bootstrapFlight: Promise<void> | null = null;
  private initialization: Promise<void> | null = null;
  private operation = false;
  bootstrap(): Promise<void> {
    if (this.bootstrapFlight) return this.bootstrapFlight;
    this.csrf.clear();
    const generation = this.csrf.generation;
    const flight = firstValueFrom(this.http.get<Envelope<CsrfBootstrap>>('/api/v1/auth/csrf')).then(response => {
      if (generation !== this.csrf.generation) throw new AuthFailure(409);
      if (!response.status || !response.data.csrfToken || response.data.headerName !== 'X-CSRF-TOKEN') throw new AuthFailure(503);
      this.csrf.set(response.data.csrfToken);
    }).finally(() => { if (this.bootstrapFlight === flight) this.bootstrapFlight = null; });
    this.bootstrapFlight = flight;
    return flight;
  }
  initialize(force = false): Promise<void> {
    if (this.initialization) return this.initialization;
    if (!force && this.state.phase() !== 'unresolved') return Promise.resolve();
    const generation = this.state.generation;
    const flight = this.bootstrap().then(() => firstValueFrom(this.http.get<Envelope<Identity>>('/api/v1/auth/me')))
      .then(response => { if (generation === this.state.generation) this.state.authenticated(this.identity(response)); })
      .catch((failure: unknown) => {
        if (failure instanceof AuthFailure && failure.status === 401) return;
        if (generation === this.state.generation) this.state.unavailable();
      }).finally(() => { if (this.initialization === flight) this.initialization = null; });
    this.initialization = flight;
    return flight;
  }
  async login(email: string, password: string): Promise<Transition> {
    if (this.operation) throw new AuthFailure(409);
    this.operation = true;
    try {
      if (!this.csrf.token()) await this.bootstrap(); // First deliberate submission, never replay of an unsafe request.
      const response = await firstValueFrom(this.http.post<Envelope<Identity>>('/api/v1/auth/login', { email, password }));
      this.state.authenticated(this.identity(response)); this.csrf.clear();
      return { confirmed: true, csrfReady: await this.refreshAfterTransition() };
    } finally { this.operation = false; }
  }
  async logout(retry = false): Promise<Transition> {
    if (this.operation) throw new AuthFailure(409);
    this.operation = true;
    try {
      if (retry) await this.bootstrap();
      const response = await firstValueFrom(this.http.post<Envelope<null>>('/api/v1/auth/logout', null));
      if (!response.status || response.error_code !== 0 || response.data !== null) throw new AuthFailure(503);
      this.state.anonymous(); this.csrf.clear();
      const csrfReady = await this.refreshAfterTransition();
      if (!csrfReady) this.state.setNotice('You are signed out. Please try again before signing in again.');
      return { confirmed: true, csrfReady };
    } catch (failure: unknown) {
      this.state.logoutUnconfirmed(); throw failure;
    } finally { this.operation = false; }
  }
  private async refreshAfterTransition(): Promise<boolean> {
    try { await this.bootstrap(); return true; } catch { return false; }
  }
  private identity(response: Envelope<Identity>): Identity {
    const value = response.data;
    if (!response.status || !value || typeof value.id !== 'string' || typeof value.email !== 'string' || !['CUSTOMER', 'AGENT'].includes(value.role)) throw new AuthFailure(503);
    return { id: value.id, email: value.email, role: value.role };
  }
}
