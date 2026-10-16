import { Injectable, signal } from '@angular/core';
import { Identity } from './auth-models';
export type SessionPhase =
  'unresolved' | 'anonymous' | 'authenticated' | 'unavailable' | 'logout-unconfirmed';
@Injectable({ providedIn: 'root' })
export class SessionState {
  private readonly identityValue = signal<Identity | null>(null);
  private readonly noticeValue = signal('');
  readonly notice = this.noticeValue.asReadonly();
  setNotice(value: string): void {
    this.noticeValue.set(value);
  }
  private readonly phaseValue = signal<SessionPhase>('unresolved');
  readonly identity = this.identityValue.asReadonly();
  readonly phase = this.phaseValue.asReadonly();
  generation = 0;
  authenticated(identity: Identity): void {
    this.generation++;
    this.noticeValue.set('');
    this.identityValue.set(identity);
    this.phaseValue.set('authenticated');
  }
  anonymous(): void {
    this.generation++;
    this.noticeValue.set('');
    this.identityValue.set(null);
    this.phaseValue.set('anonymous');
  }
  unavailable(): void {
    this.phaseValue.set('unavailable');
  }
  logoutUnconfirmed(): void {
    this.generation++;
    this.phaseValue.set('logout-unconfirmed');
  }
}
@Injectable({ providedIn: 'root' })
export class CsrfState {
  private readonly value = signal<string | null>(null);
  readonly token = this.value.asReadonly();
  generation = 0;
  clear(): void {
    this.generation++;
    this.value.set(null);
  }
  set(token: string): void {
    this.value.set(token);
  }
}
