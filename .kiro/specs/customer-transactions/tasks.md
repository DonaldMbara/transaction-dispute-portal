# Customer Transactions — Implementation Tasks

**Status:** Implementation complete; see [acceptance.md](acceptance.md) for executed checks and verification limits.
**Requirements:** [requirements.md](requirements.md) (approved revision 2, with the approved account-number clarification)  
**Design:** [design.md](design.md) (approved revision 2)  
**Scope:** TXN-01–TXN-08. Build the read-only customer transaction API, deterministic transaction fixtures and customer transaction experience by reusing the implemented Customer Accounts feature. Do not implement disputes, statements, exports, merchant logos or other routes/actions not approved here.

## Task 1 — Add V6 transaction persistence and domain model

**Requirements:** TXN-06.1–6, TXN-08.8, TXN-08.10  
**Depends on:** Implemented Customer Accounts (V5 `accounts` table); Authentication & RBAC (implemented)

- Add only `V6__create_bank_transactions.sql`. Do not edit V1–V5 and do not create or alter `accounts`. Create `bank_transactions` with the approved UUID, account FK, unique reference, merchant, positive `NUMERIC(19,2)` amount, constrained `IN`/`OUT` direction, ZAR-only currency, constrained transaction status and `TIMESTAMPTZ occurred_at` columns. Add the two approved indexes: `(account_id, occurred_at DESC, id DESC)` and `(occurred_at DESC, id DESC)`; rely on the unique reference constraint for reference lookup.
- Add the transaction-owned `BankTransaction` model, `TransactionDirection` and `TransactionStatus`, plus the transaction persistence mapping. Do not import or expose the account JPA entity/repository. Follow the approved backend Lombok guidance for routine constructor/logger boilerplate; keep invariants explicit and DTOs immutable.
- Add PostgreSQL/Testcontainers tests for clean migration, upgrade from V5 without V1–V5 changes, mapping validation, FK rejection, positive amount, direction/status/currency constraints and unique reference.
- **Acceptance:** V6 alone creates the transaction table and approved indexes; PostgreSQL enforces all transaction constraints and the existing V5 schema is unchanged.

## Task 2 — Parse and validate transaction list queries

**Requirements:** TXN-02.3, TXN-03.3, TXN-03.6, TXN-04.1–9, TXN-08.6  
**Depends on:** Task 1

- Implement the query parser and immutable `TransactionQuery` value, with explicit allowlists/defaults for `page`, `size`, `search`, `direction`, `status`, `fromDate`, `toDate`, `minAmount`, `maxAmount`, `sortBy`, `sortDirection` and optional `accountId`.
- Unit-test missing/default values; unknown and duplicate parameters (including duplicate `accountId`); blank values; malformed UUIDs; invalid enums, dates, ranges and pagination; inclusive 366-day maximum and rejection at 367 days; search normalization (maximal Unicode-whitespace runs to one ASCII space, trim, then 100-character limit); amount zero, precision, `NUMERIC(19,2)` bounds and reversed bounds; and overflow-safe page/offset calculations against the signed 32-bit database/JPA offset limit.
- Ensure `GET /transactions/{id}` query validation rejects `accountId` and other unsupported inputs. Query parsing must not query account ownership or silently rewrite malformed external values.
- **Acceptance:** every accepted query produces a complete immutable query object; every invalid, duplicate or unsupported input produces the standard validation failure (`400/1004`) without arithmetic overflow or a database request.

## Task 3 — Add the ownership-scoped JDBC read repository

**Requirements:** TXN-02.1–7, TXN-03.4, TXN-03.6, TXN-04.2–8, TXN-05.8, TXN-08.7–9  
**Depends on:** Tasks 1–2

- Implement JDBC count, list and detail reads. Every read must bind the authenticated customer ID into the `accounts.customer_id` ownership join; an optional `accountId` is an additional owned-account restriction, never an authorization identity. Apply the same ownership and all active filter predicates to count and list before sort/page. Detail uses transaction ID plus the same ownership predicate.
- Bind all search/filter values. Search only merchant/reference, normalize as Task 2 specifies, and escape `%`, `_` and the escape character so matching is literal. Map public sort fields to fixed SQL expressions only; use the requested direction for both the selected sort and UUID ID tie-breaker. Never concatenate user-provided search or sort text into SQL.
- Convert inclusive Johannesburg date-only bounds to UTC using `Africa/Johannesburg` start-of-day and the next day for a half-open interval. Keep amount bounds inclusive and exact.
- Add PostgreSQL integration tests using CUSTOMER A, CUSTOMER B and an AGENT, each with suitable existing accounts: cross-customer detail, foreign `accountId`, omitted `accountId`, combined filters, scoped count, escaped literal search, Johannesburg boundaries, empty page, deterministic tie sorting, and stable count/list behavior for a fixed fixture.
- **Acceptance:** customer A cannot count, list or detail customer B transactions; all filters narrow only within ownership; empty valid pages return empty items and the correct scoped total.

## Task 4 — Add transaction service, masked DTOs and customer API

**Requirements:** TXN-01.1–5, TXN-02.8, TXN-03.1–6, TXN-05.1–9, TXN-08.1–5, TXN-08.7, TXN-08.10  
**Depends on:** Tasks 1–3; existing `AccountNumberMask`, `ApiEnvelope`, correlation/error handling, session security and activity interceptor

- Add the read service, response mapper and immutable DTOs for list/detail. Eligibility is status-only: `COMPLETED` is eligible; `PENDING`/`FAILED` return `NOT_COMPLETED`; the dispute object remains present with null ID. Do not query or depend on dispute storage.
- Use the public account-feature `AccountNumberMask` as the only masking implementation. Transaction DTOs expose only account `{id, maskedNumber}`; no transaction or later dispute response includes `accountNumber` or a full number. Do not import account entities/repositories.
- Add the narrow `GET /api/v1/transactions` and `GET /api/v1/transactions/{id}` CUSTOMER security matchers before `anyRequest().denyAll()`. Mark both handlers with `@AuthenticatedActivity`. Keep the controller thin and preserve the shared envelope, `application/json`, `X-Correlation-ID`, `Cache-Control: no-store`, existing exception mapping and no mutation headers.
- Add HTTP contract tests for success fields/envelopes/headers and `401/1001`, `403/1002`, `404/1003`, `400/1004`, `503/3002`; cover list/detail ownership, malformed/duplicate/unsupported inputs, eligibility and handler activity admission. Add an isolated-container database-failure test proving failures map to `503/3002`, not an empty list or false 404.
- Assert serialized transaction responses contain no full account number, including transaction-list/detail and a dispute-shaped response fixture. Assert safe logs and error bodies contain no full number or sensitive query text.
- **Acceptance:** only owned CUSTOMER reads succeed; AGENT and anonymous outcomes use established codes; both handlers are marked; every serialized transaction/dispute-shaped response remains masked-only; database failures preserve the established unavailable contract.

## Task 5 — Add the transaction fixture command through `AccountFixturePort`

**Requirements:** TXN-06.7–11, TXN-08.8  
**Depends on:** Task 1; implemented account fixture command/`AccountFixturePort`; existing auth-owned customer lookup

- Add an explicit opt-in non-web transaction fixture command extending the account fixture workflow. Resolve account UUIDs only via `AccountFixturePort`; do not import the account entity or Spring Data repository, create accounts/users, or accept credentials.
- Provision distinct, deterministic MAIN and SAVINGS transaction sets per existing CUSTOMER target, with stable IDs/references and fixed `occurred_at` timestamps. Use a single transaction, conflict-safe insert then immutable-row verification; refuse mismatches safely and roll back. Repeated invocation must be idempotent and must not overwrite unrelated rows.
- Document the command, exact invocation, disposable/local-only scope, target restrictions, `AccountFixturePort` boundary, deterministic data, idempotence, mismatch behavior and safe recovery in `backend/README.md` and `docs/operations/customer-transaction-fixtures.md`.
- Add command/service tests for multiple target customers, distinct deterministic data per customer and account type, idempotence, mismatch rollback, disabled/non-CUSTOMER refusal, and proof that no auth users or accounts are created.
- **Acceptance:** only explicit invocation provisions data for an already provisioned CUSTOMER with MAIN and SAVINGS accounts; reruns converge without duplicates or destructive changes.

## Task 6 — Add customer Transactions navigation and role-aware active state

**Requirements:** TXN-01.2, TXN-07.1–2, TXN-07.9, TXN-08.10  
**Depends on:** Existing `DashboardLayout`, CUSTOMER/AGENT route structure and account dashboard

- Add a CUSTOMER-only **Transactions** item to the existing labelled header `<nav>`, targeting `/customer/transactions` with no `accountId`. Keep the existing Accounts item, focus styling and design tokens. Do not add My Disputes or any item whose route is not implemented.
- Register lazy CUSTOMER routes for `/customer/transactions` and `/customer/transactions/:id` with small route-shell views so both navigation destinations exist at this checkpoint; Task 7 replaces the shells with the complete list/detail experience.
- Make Accounts active only at `/customer`. Make Transactions active with `aria-current="page"` for `/customer/transactions` and `/customer/transactions/:id` using prefix matching; Accounts must not remain active on either transaction route. Preserve the existing AGENT navigation unchanged and omit the Transactions link for AGENT.
- Update `docs/design/plantuml/frontend-navigation.puml` and the navigation section of `docs/design/ui-flows.md` to describe these exact role-specific links and active-route behavior.
- Add component/router tests for CUSTOMER vs AGENT visibility, route-shell resolution, link target without query parameters, active state on list and detail paths, Accounts inactive on transaction paths, `aria-current`, keyboard focus and unchanged AGENT navigation.
- **Acceptance:** navigation links only to implemented routes; role visibility, path prefix behavior and accessibility state match the approved contract.

## Task 7 — Build transaction list/detail UX, URL state and return navigation

**Requirements:** TXN-02.3, TXN-03–05, TXN-07.3–16, TXN-08.2–3, TXN-08.6  
**Depends on:** Tasks 2, 4 and 6; existing `AccountApi`, shared `account-selector`, `formatZar` and dashboard layout

- Implement `/customer/transactions` and `/customer/transactions/:id` as lazy routes and reuse the existing account API, shared presentational selector and balance formatter. The selector fetches no data; do not reimplement account models, masking, account formatting or account fetching.
- Keep account choice and filter/page state in the URL. Use a typed Reactive Form and component-local Angular signals for transient loading, errors, result data and request generations. Do not add Signal Store, a global state library, local/session storage, polling, prefetch, auto-refresh or automatic retry. Subscribe to route query changes so reload and browser Back/Forward restore the represented selection and filters.
- On a route without `accountId`, resolve primary (fallback: first account in API order), then replace browser history with the selected `accountId` before requesting transactions. With no accounts, render the no-accounts state and no selector. With one account, still render the selector with that account selected. For an explicit absent/non-owned account, show “Account not available” and an explicit link to the default account; never silently fall back. Cancel/ignore stale requests after account changes.
- Account selection updates `accountId`, resets `page` to 1, retains direction/status/date/search/amount/sort filters, and uses normal push navigation so Back returns to the previous account selection. Default-primary resolution alone uses replace for that account transition. Other filter edits reset page as required and replace query state. Add router tests proving the history distinction, filter retention, page reset, Back/Forward restoration, direct URL and refresh behavior.
- Implement search (300 ms debounce), direction/status/date/amount filters, allowlisted sort, pagination, accessible responsive Filters control, skeleton/loading, empty, safe validation, account-unavailable, 401/session-clearing and manual 503 retry states. Detail return uses a validated same-origin `returnTo` allowlist containing only `/customer/transactions` and the approved query parameters; preserve `accountId` and the other allowlisted filters, reject external/invalid paths, and fall back to the list when absent/invalid.
- Show transaction and dispute context using masked account numbers only. Use the existing deterministic `R 12 500.00` ZAR formatter and `Africa/Johannesburg` time display. Optional current-page month headings appear only for `sortBy=occurredAt`. Keep eligibility advisory and API-derived. UI-only exclusions: no dispute CTA, statements, exports/downloads, receipts, merchant logos or implied submission action.
- Add component/router/API tests for URL parse/render, default-primary rewrite, selector event, one/no account, foreign/stale `accountId`, filters, debounce, stale response cancellation, pagination, detail `returnTo`, display formatting/time, month grouping, errors/loading/empty, and absence of excluded actions.
- **Acceptance:** direct links, reload and navigation reproduce URL state; account switch is a push, default selection is a replace; list/detail UI reuses Customer Accounts boundaries and exposes no unapproved actions.

## Task 8 — Connect account dashboard links and remove the route limitation

**Requirements:** TXN-07.1, TXN-07.3, TXN-07.11; Customer Accounts ACC-08 dashboard integration  
**Depends on:** Tasks 6–7; implemented customer dashboard and accounts acceptance evidence

- Verify every account tile's existing `/customer/transactions?accountId=<id>` target resolves to the implemented list route and selects that account without losing the approved default/filter behavior.
- Add a “View transactions” shortcut for the primary account in the dashboard's existing approved quick-links slot, using `<nav aria-label="Quick links">` only when the slot renders; if no primary exists, follow the approved account-list fallback order. Do not introduce a new panel/design or other shortcuts/actions.
- Remove the obsolete known-limitation note from `.kiro/specs/customer-accounts/acceptance.md` once the route and shortcut are verified. Update the relevant account dashboard flow/evidence reference only as needed to record that the route is implemented.
- Add integration/router tests for tile navigation, selected account, shortcut destination/primary selection, no dead links and panel behavior when no qualifying shortcut exists.
- **Acceptance:** account tile and quick-link destinations resolve to the customer transaction list; no stale “no target route” limitation remains.

## Task 9 — Run required verification and record actual evidence

**Requirements:** TXN-01–TXN-08  
**Depends on:** Tasks 1–8

- Run and record actual results for the exact commands:
  - From `backend/`: `mvn --batch-mode clean verify`
  - From `frontend/`: `npm run lint`, `npm run test:ci`, and `npm run build`
- The current `frontend/package.json` and `angular.json` do not define a lint script/builder. Verify this gap before implementation; do not claim `npm run lint` passed. Use already-approved lint tooling if it becomes available; otherwise record the command as unavailable and seek approval before adding dependencies or changing toolchain scope.
- Capture and review `EXPLAIN (ANALYZE, BUFFERS)` output for representative ownership-only, `accountId`-filtered, combined-filter and searched list queries against PostgreSQL. Record query shape and observed plans; do not add speculative indexes. Any index change requires a separate additive migration and evidence of a concrete regression.
- Verify `@AuthenticatedActivity` renews on admitted list/detail handler requests and that unmarked/poll-like, health, CSRF bootstrap and rejected requests do not renew activity. Include the existing isolated database-failure coverage.
- Run a privacy check across serialized success/error responses, logs and URLs proving the full 10-digit fixture account number is absent from all transaction/dispute-shaped paths and search predicates; transaction data exposes masked values only.
- Record only actually executed commands, outputs/results, EXPLAIN captures, privacy/activity evidence and remaining limitations in `.kiro/specs/customer-transactions/acceptance.md`. Do not mark unexecuted checks as passing.
- **Acceptance:** required backend and available frontend checks pass, all requested evidence is factual and reviewable, no test uses H2 in place of PostgreSQL, and no out-of-scope action or route was introduced.

