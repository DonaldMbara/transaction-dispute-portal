# Async state standard

Use `AsyncViewState<T>` in a signal as the single source of truth for an asynchronous page read. The state carries the status and any current value together, so a loading/error status cannot drift from separately stored data.

- `loading`: the first read is in progress and there is no value yet.
- `busy`: a refresh is in progress; keep rendering the included previous value and mark the region `aria-busy`.
- `ready`: the read succeeded with usable data.
- `none`: the read succeeded but returned no items; render the feature's empty state.
- `error`: the read failed; render a safe error state and explicit retry where supported.
- Feature-only states such as `validation`, `not-found`, or `account-unavailable` may be added through the type's second parameter.

Use `computed()` for values derived from the state. Keep route and form streams in RxJS where cancellation, debounce, or router integration is useful; subscriptions must update the signal state and discard stale responses. Do not use effects to copy state between signals.

For user-triggered operations, use `AsyncActionStatus`: `idle`, `busy`, `succeeded`, or `failed`. Derive button disabled/pending state with `computed()` and keep safe, user-facing messages separate from the action status.

The app-wide `HttpProgress` signal is the passive network indicator. It tracks only in-flight first-party API calls and never starts or retries requests. Page-level state remains responsible for the page's content and recovery UI.
