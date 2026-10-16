export interface ApiEnvelope<T> {
  status: boolean;
  data: T;
  error_code: number;
}
