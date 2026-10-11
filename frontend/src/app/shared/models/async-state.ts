export type AsyncViewState<T, ExtraStatus extends string = never> =
  | { readonly status: 'loading' }
  | { readonly status: 'busy'; readonly value: T }
  | { readonly status: 'ready'; readonly value: T }
  | { readonly status: 'none'; readonly value: T }
  | { readonly status: 'error' }
  | { readonly status: ExtraStatus };

export type AsyncActionStatus = 'idle' | 'busy' | 'succeeded' | 'failed';
