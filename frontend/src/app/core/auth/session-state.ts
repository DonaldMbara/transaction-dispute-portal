import {Injectable, signal} from '@angular/core';
import {Identity} from './models/auth-models';

export type SessionPhase =
  'unresolved' | 'anonymous' | 'authenticated' | 'unavailable' | 'logout-unconfirmed';

@Injectable({providedIn: 'root'})
export class SessionState {
  generation = 0;
  private readonly identityValue = signal<Identity | null>(null);
  readonly identity = this.identityValue.asReadonly();
  private readonly noticeValue = signal('');
  readonly notice = this.noticeValue.asReadonly();
  private readonly phaseValue = signal<SessionPhase>('unresolved');
  readonly phase = this.phaseValue.asReadonly();

  setNotice(value: string): void {
    this.noticeValue.set(value);
  }

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

@Injectable({providedIn: 'root'})
export class CsrfState {
  generation = 0;
  private readonly value = signal<string | null>(null);
  readonly token = this.value.asReadonly();

  clear(): void {
    this.generation++;
    this.value.set(null);
  }

  set(token: string): void {
    this.value.set(token);
  }
}
