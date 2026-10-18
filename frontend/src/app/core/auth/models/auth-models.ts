export type Role = 'CUSTOMER' | 'AGENT';

export interface Identity {
  id: string;
  email: string;
  role: Role;
}

export interface Envelope<T> {
  status: boolean;
  data: T;
  error_code: number;
}

export interface CsrfBootstrap {
  csrfToken: string;
  headerName: 'X-CSRF-TOKEN';
}

export interface Transition {
  confirmed: true;
  csrfReady: boolean;
}

export class AuthFailure extends Error {
  constructor(
    readonly status: number,
    readonly code = 0,
  ) {
    super('The request could not be completed safely.');
  }
}

export function safeMessage(error: unknown): string {
  if (!(error instanceof AuthFailure))
    return 'The service is temporarily unavailable. Please try again.';
  if (error.status === 401) return 'Unable to sign in with these credentials.';
  if (error.code === 1010) return 'We could not verify this request. Try again.';
  if (error.status === 400) return 'Check your email and password and try again.';
  if (error.status === 403) return 'You do not have permission to perform this operation.';
  return 'The service is temporarily unavailable. Please try again.';
}
