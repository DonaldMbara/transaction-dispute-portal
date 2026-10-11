# Customer Transactions — Design

**Status:** Approved — revision 2 — awaiting task plan — 2026-10-11
**Approval:** Requirements and design approved by the developer on 2026-10-11; implementation awaits an approved task plan.
**Requirements:** [requirements.md](requirements.md)
**Approved baselines:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

---

### Revision 2 — 2026-10-11

Previously approved design is superseded in place. This feature no longer owns the `accounts` table, account fixtures, dashboard tiles or the `account-selector` implementation.

Changed requirements: TXN-02.3, TXN-02.6, TXN-06.1–2, TXN-06.7–11, TXN-07.1, TXN-07.3–16, TXN-08.7–9. Changed design sections: §1, §2, §3.1–3.2, §4, §5.2–5.5, §6, §7, §8 and §9. Date: 2026-10-11.

### Revision 3 — 2026-10-11

Changed sections: §4 API/DTO ownership, §5.3 examples/traceability, §7.4 transaction detail presentation, §8 acceptance evidence.

Transaction and future dispute response DTOs carry only the account-feature `maskedNumber`; they never include the full number. The account selector's current-account summary follows Customer Accounts' full-number display requirement. No copy/reveal interaction is included. Acceptance evidence adds serialized HTTP body assertions for transaction and dispute-shaped responses.

| Section | Change |
|---|---|
| Status | Reset to draft pending re-approval with the revised requirements. |
| §1–2 | Accounts module already exists (customer-accounts). Transaction module reads `accounts.customer_id` in SQL only; fixture uses `AccountFixturePort`. |
| §3.1 | V6 creates `bank_transactions` and its indexes only. No `accounts` DDL. |
| §3.2 | Fixture seeds distinct transaction sets on the existing `MAIN` (primary) and `SAVINGS` accounts; it does not create accounts. |
| §4 | `BankAccount` persistence remains in `account`. The account feature owns displayName derivation and `AccountNumberMask`; transaction DTOs call that class and nest `{id,maskedNumber}` without reimplementing masking. |
| §5.2 | Adds optional `accountId` UUID selector; 404/1003 non-owned; 400/1004 malformed/duplicate. |
| §6 | Drops account fixture/repository from this feature’s package tree. |
| §7 | `/customer` is not redirected. List reuses shared selector; URL is selection state; month grouping; segmented direction control; no dispute CTA / statement / receipt / download; Main Account / Savings Account labels only. |
| §8–9 | Traceability and resolved decisions updated for V6, `accountId`, and fixture port. |

---

## 1. Scope and design decisions

This feature adds read-only customer transaction list and detail capabilities to the existing Java 21 / Spring Boot 3.5 modular monolith and Angular standalone application. It relies on the implemented opaque, PostgreSQL-backed session authentication and retains same-origin `/api/v1` requests.

It adds synthetic bank transactions (V6) against accounts already created by customer-accounts (V5). It does not add disputes, a dispute lookup, submission, idempotency, agent transaction access, banking integrations, transaction mutations, account tiles, or a second selector implementation. The initial dispute state is deliberately status-based: a completed transaction is eligible, and a pending or failed transaction is not. A later Dispute Submission feature will change the null dispute association into an actual lookup and enforce eligibility again when it accepts a command.

| Decision | Design |
|---|---|
| Account boundary | Unchanged from customer-accounts: the `account` module owns `accounts`, `BankAccount`, `GET /api/v1/accounts`, `AccountFixturePort` and the account fixture. This feature does not import that module’s JPA entity or Spring Data repository. |
| Transaction boundary | The `transaction` module owns `bank_transactions` (V6), query parsing, ownership-scoped read repository, DTO mapping, controller and the transaction fixture command. It joins `accounts.customer_id` in its own SQL. |
| Data lifecycle | Flyway V6 creates transaction structure only. An explicit, opt-in, non-web fixture command creates deterministic synthetic transactions for an existing CUSTOMER’s `MAIN` and `SAVINGS` accounts obtained via `AccountFixturePort`. It cannot create authentication users or banking accounts. |
| Query mechanism | An explicit query parser validates raw request parameters (including optional `accountId`) and produces an immutable query object. A native, parameterized PostgreSQL query/projection applies ownership before search/filter/count/order/page. |
| Idle activity | The existing `security.activity.AuthenticatedActivity` marker and `AuthenticationActivityInterceptor` are reused for list and detail handlers. Angular performs no automatic refresh, polling, prefetch, or retry that could renew activity. |
| Pagination | Offset pagination is retained as approved. It is deterministic for a fixed dataset, but is not a snapshot across independently provisioned data changes. |
| Account types / labels | `MAIN` → "Main Account", `SAVINGS` → "Savings Account". Display names are derived by the accounts mapper, not stored, and not re-derived here. No extra display-name column. No credit/overdraft types and no negative-balance semantics. |
| UI | Desktop-first public-web language per `ui-flows.md` and `frontend-standards.md`. Existing `DashboardLayout`, shared button variants, local SCSS, Lucide icons. An AI-generated mobile mockup is not a source of truth. |

No new dependency, service, cache, gateway, authentication mechanism, global frontend state library or CSS component library is needed.

## 2. Module boundaries and request flow

```text
Angular Transactions page
  -> GET /api/v1/accounts (via accounts feature service; selector data)
  -> GET /api/v1/transactions[?accountId=&...]
  -> Spring Security/session validation
  -> CUSTOMER route authorization
  -> @AuthenticatedActivity admission interceptor
  -> TransactionController
  -> TransactionQueryParser + TransactionQueryService
  -> TransactionReadRepository (ownership-scoped PostgreSQL query)
  -> Transaction DTO + ApiEnvelope
```

`auth` remains the owner of `app_users`, account-status/version lookup and session validation. `security` remains the owner of the filter chain, CSRF policy, current-account validation, error writing and strict idle activity. The transaction module receives the authenticated UUID from the validated Spring Security principal; it never accepts it from a route, query parameter or body.

The account module owns its persistence entity, list API and fixture-facing port. The transaction module's repository uses an ownership join against `accounts.customer_id` in SQL rather than accessing the account module's entity or Spring Data repository. `accountId`, when present, is applied after that ownership predicate as `accounts.id = :accountId AND accounts.customer_id = :authenticatedCustomerId`. This follows the repository-boundary rule while making the customer predicate inseparable from every list, count and detail read.

## 3. Persistence and synthetic data

### 3.1 Additive schema migration

Create one additive Flyway migration `V6__create_bank_transactions.sql`. It must not modify V1–V5. It does not create or alter `accounts`. It creates:

```text
bank_transactions
  id              UUID primary key
  account_id      UUID not null references accounts(id)
  reference       VARCHAR(80) not null unique
  merchant        VARCHAR(160) not null
  amount          NUMERIC(19,2) not null check (amount > 0)
  direction       VARCHAR(8) not null check (direction in ('IN','OUT'))
  currency        CHAR(3) not null default 'ZAR' check (currency = 'ZAR')
  status          VARCHAR(16) not null check (status in ('PENDING','COMPLETED','FAILED'))
  occurred_at     TIMESTAMPTZ not null
```

Create the baseline indexes `idx_transactions_account_time(account_id, occurred_at DESC, id DESC)` and `idx_transactions_time(occurred_at DESC, id DESC)`. The existing unique constraint supplies a lookup index for reference. These are starting indexes, not a claim of an optimal plan: integration verification captures `EXPLAIN (ANALYZE, BUFFERS)` for representative ownership-only, `accountId`-filtered, filtered and searched list queries. A follow-up additive index migration is permitted only when that evidence identifies a concrete regression.

The foreign key enforces the transaction → account link only. New fixture-created transactions require existing banking accounts on an **enabled CUSTOMER**, provisioned by the customer-accounts fixture. Disabling a CUSTOMER preserves banking records but immediately prevents access through existing session revocation; re-enabling the same CUSTOMER role can restore access after a fresh login. Runtime list/detail reads are both role-gated and ownership-scoped, so a corrupt association cannot reveal data to a different customer.

The database cannot express the CUSTOMER-role invariant with the foreign key. Customer-accounts already treats that invariant as enforced at fixture-account creation. This feature enforces it at every customer read via the `accounts.customer_id` join. Changing an account-owning CUSTOMER to AGENT would violate the business meaning of `customer_id`; the currently approved authentication/account-maintenance scope does not define that cross-module role-change guard. Before such a role transition is implemented, an approved follow-up must decide whether to reject it or migrate/reassign banking accounts. No transaction feature code silently changes the existing operational account-maintenance behavior.

JPA maps `bank_transactions` only inside `transaction`. `ddl-auto: validate` continues to validate mapped schema; Flyway remains the only schema writer.

### 3.2 Fixture provisioning

Schema migration contains no transaction or user rows. Add an explicitly enabled Spring Boot non-web command under `transaction/cli`, analogous to the existing account-administration command. It is disabled by default and runs only when the operator chooses the dedicated command configuration.

The command accepts an existing customer email as an operator argument, normalizes it through the existing auth-owned lookup contract, confirms that it is a CUSTOMER, and resolves that customer’s fixture account IDs through `AccountFixturePort.getFixtureAccountIds(email)`. It requires both a `MAIN` (primary) and a `SAVINGS` account to already exist. It then creates that customer's fixed synthetic transaction sets — a distinct deterministic set per account — in one database transaction. Passwords, password hashes, session cookies and credentials are never command arguments or fixture data. It never inserts into `accounts`.

Fixtures use stable UUIDs and unique transaction references derived from a documented fixture set keyed by customer email and account type. In one transaction, the service uses `INSERT ... ON CONFLICT DO NOTHING` on the stable primary/unique keys, then reads and locks the fixture rows and verifies that existing rows match their expected immutable values. A concurrent command therefore converges on one set of rows; a mismatch returns a safe operational error and rolls back its own work. It never overwrites unrelated data. Re-running the command produces no duplicates and no destructive reseed. It may be used in a disposable assessment/local database only; production deployment does not invoke it automatically.

Tests create their own isolated users and accounts through existing test support and persist feature fixtures directly through feature services/repositories or SQL setup. They do not depend on the operational command or startup data.

## 4. Domain model and ownership invariants

`BankAccount` remains the account-module model. This feature does not duplicate it. Transaction list/detail DTOs nest only `{id, maskedNumber}` from the join; they do not include `displayName`, `balance` or `primary`. Those fields belong to GET /accounts and the shared selector, which the Angular list loads separately. Only the selector current-account summary may display the owner-visible `accountNumber`; transaction rows/detail and all future dispute DTOs remain masked-only. No number interaction is provided.

`BankTransaction` is a transaction-module model: `id`, `accountId`, `reference`, `merchant`, positive `BigDecimal amount`, `currency`, `TransactionDirection`, `TransactionStatus`, and `Instant occurredAt`. Enums contain only `IN`/`OUT` and `PENDING`/`COMPLETED`/`FAILED` respectively.

The transaction service derives initial eligibility as follows:

| Status | `dispute.id` | `eligible` | `ineligibilityReason` |
|---|---|---:|---|
| `COMPLETED` | `null` | true | `null` |
| `PENDING` | `null` | false | `NOT_COMPLETED` |
| `FAILED` | `null` | false | `NOT_COMPLETED` |

This is display information only. A future submission command independently checks the current transaction, owner and dispute rule under its own transaction boundary. This feature has no dependency on a dispute table.

For all reads, the repository begins with the effective predicate `accounts.customer_id = :authenticatedCustomerId`. The same predicate is used for list items and `total`; detail is selected through that predicate. An empty result is interpreted as the same `ResourceNotFoundException` for an absent or inaccessible ID.

When `accountId` is present, the list additionally requires `accounts.id = :accountId`. If that account does not exist or is not owned, the list operation returns `404/1003` (not an empty 200 page). Omitting `accountId` returns all owned accounts’ transactions.

## 5. API design

### 5.1 Endpoints and authorization

| Method/path | Spring Security rule | Activity | Result |
|---|---|---|---|
| `GET /api/v1/transactions` | `hasRole('CUSTOMER')` | eligible | paginated customer-owned transaction summaries |
| `GET /api/v1/transactions/{id}` | `hasRole('CUSTOMER')` | eligible | customer-owned transaction detail |

Add these narrow GET matchers before the existing `anyRequest().denyAll()` matcher. Do not create a broad `/api/v1/**` allowlist. Existing session validation handles disabled, revoked and expired sessions before authorization. Missing/invalid session returns `401/1001`; an authenticated AGENT receives `403/1002`; absent/non-owned detail IDs return `404/1003`.

GET requests do not require CSRF. They use the existing envelope, `application/json`, `Cache-Control: no-store` and correlation-ID filter. They do not issue `ETag`, `Location`, or transaction mutation headers.

### 5.2 List contract

`GET /api/v1/transactions` accepts exactly these single-value query parameters:

| Parameter | Values/default | Meaning |
|---|---|---|
| `page` | integer, default `1`, minimum `1` | 1-indexed requested page |
| `size` | integer, default `20`, range `1..100` | page size |
| `search` | normalized max 100 characters | literal merchant/reference substring |
| `direction` | `ALL` (default), `IN`, `OUT` | direction filter |
| `status` | `ALL` (default), `PENDING`, `COMPLETED`, `FAILED` | status filter |
| `fromDate`, `toDate` | ISO local date | inclusive date range in Africa/Johannesburg |
| `minAmount`, `maxAmount` | ZAR decimal, 0..`NUMERIC(19,2)` | inclusive positive-magnitude range |
| `sortBy` | `occurredAt` (default), `amount`, `merchant`, `reference` | allowlisted sort field |
| `sortDirection` | `DESC` (default), `ASC` | primary and ID tie-breaker direction |
| `accountId` | optional UUID | ownership-scoped resource selector, validated through account → customer |

The parser rejects unknown parameter names, duplicate values, blank enum/number/date/UUID values, malformed UUID paths or `accountId`, invalid ranges, more than two decimal places, negative amounts, values outside `NUMERIC(19,2)`, and an offset greater than the signed 32-bit JPA/database range. It validates `page - 1` and `(page - 1) * size` with overflow-safe arithmetic before creating a `PageRequest` or SQL limit/offset. Each failure is `400/1004`. A date range may span at most **366 calendar days**, inclusive, to bound query cost. Duplicate `accountId` or a non-UUID `accountId` is `400/1004`. A well-formed UUID that is not an owned account is `404/1003`. `GET /transactions/{id}` rejects `accountId` as an unknown parameter (`400/1004`).

Search normalization is defined once for frontend and backend: replace every maximal run of Unicode whitespace with one ASCII space, remove leading/trailing resulting spaces, then measure the 100-character limit. The normalized text is the literal search string; the application does not normalize merchant/reference values in storage or use token/full-text matching. Thus `ABC  Store` intentionally means the normalized literal substring `ABC Store`. An empty normalized search applies no search predicate.

Search uses case-insensitive literal substring matching against `merchant` and `reference` only. The adapter binds values as parameters and escapes `%`, `_` and the escape character for PostgreSQL `LIKE`; it never concatenates user text or sort text into SQL. The sort enum maps each public value to a fixed SQL expression. Direction and status `ALL` omit their predicates. All supplied filters combine with `AND` after ownership. Date conversion uses `LocalDate.atStartOfDay(ZoneId.of("Africa/Johannesburg"))` for `fromDate` and the day after `toDate`, producing `[fromInclusiveUtc, toExclusiveUtc)`; it never assumes a fixed UTC offset. Tests use zone-rule conversion around representative historical/standard boundaries and assert half-open inclusion, while recognizing that Johannesburg currently has no daylight-saving transition.

The list returns HTTP 200. Wire amounts remain decimal strings with exactly two fractional digits; the frontend displays ZAR deterministically as `R 12 500.00` (ordinary space grouping, period decimal separator) and adds the direction sign where shown. Angular `CurrencyPipe` with `en-ZA` was executed against the installed Angular packages and returned `R12 500,00`, so this UI format must use the shared formatter defined by Customer Accounts rather than locale-specific pipe output.

```json
{"status":true,"data":{"total":2,"page":1,"size":20,"items":[{"id":"uuid","reference":"TXN-001","merchant":"Example Store","amount":"125.50","currency":"ZAR","direction":"OUT","status":"COMPLETED","occurredAt":"2026-10-10T08:00:00Z","account":{"id":"uuid","maskedNumber":"****1234"},"dispute":{"id":null,"eligible":true,"ineligibilityReason":null}}]},"error_code":0}
```

Amounts serialize as strings with exactly two decimal places and direction determines the UI sign. `total` is the count using the identical ownership/search/filter/`accountId` predicate. A valid page beyond results returns the same shape with `items: []`. Offset pagination is stable for a fixed data set; data provisioned concurrently between requests can move rows between pages, which is acceptable for the assessment's synthetic read-only data.

### 5.3 Detail contract

`GET /api/v1/transactions/{id}` returns the same transaction fields and nested account/dispute shapes as a list item in `data`, not a collection. The path ID must be a UUID. A malformed UUID is `400/1004`; an absent/non-owned UUID is `404/1003` with the standard generic detail. It never includes `accountNumber` or any full account number, or raw customer ID beyond the account DTO's opaque UUID.

No `ALREADY_DISPUTED` response is produced in this feature. The contract retains the nullable dispute object so the later dispute feature can fill its ID and compute `ALREADY_DISPUTED` after it owns the dispute read model. This is the approved staged BR-T05 clarification recorded in the requirements.

### 5.4 Error and availability behavior

Controllers use the existing `ApiEnvelope`, correlation filter and exception advice. Transaction query parsing throws the existing validation exception; inaccessible detail or a non-owned list `accountId` translates to the existing not-found exception. The future feature-specific exception mapping must retain these numeric codes:

| Situation | HTTP / `error_code` |
|---|---|
| Missing, revoked or expired session | `401 / 1001` |
| Authenticated wrong role | `403 / 1002` |
| Absent or non-owned detail, or non-owned/absent list `accountId` | `404 / 1003` |
| Invalid path/query input | `400 / 1004` |
| Store/database failure before commitment | `503 / 3002` |

Read-query failures must propagate to the existing unavailable mapping. They must never become an empty page or a not-found result. The controller/service does not catch persistence failures merely to substitute a successful response.

### 5.5 Contract-decision traceability

| Contract detail | Source and status |
|---|---|
| `/api/v1/transactions` and `/{id}`, CUSTOMER ownership, optional `accountId` selector | API contract §3 and requirements TXN-01–TXN-03 |
| `{status,data,error_code}` envelope; 401/1001, 403/1002, 404/1003, 400/1004, 503/3002; no-store/correlation | Existing approved API contract §§1 and 7, requirements TXN-08. Existing `ApiExceptionHandler` already maps `DataAccessException`/`TransactionException` to 503/3002; transaction code adds no competing exception advice. |
| Page 1, size 20, maximum 100; empty valid page is 200; `occurredAt DESC, id DESC` | Existing approved API contract §2 and requirements TXN-03 |
| Query names, filter enums, sort fields, `ASC`/`DESC`, search max 100, literal escaping, Johannesburg half-open dates, amount precision | Existing approved API contract §§2–3 and requirements TXN-04. This design only makes their parsing/mapping behavior concrete. |
| `data.items`, `total/page/size`, account `{id,maskedNumber}`, transaction field set, decimal-string amounts, nullable dispute container | Existing approved API contract §§2 and 4. The design does not add a different response representation. |
| `NOT_COMPLETED` eligibility and completed IN/OUT eligibility | Existing BR-T02/API contract §4 and requirements TXN-05 |
| No `ALREADY_DISPUTED` in this release | Approved staged BR-T05 clarification in requirements TXN-05.6; later Dispute Submission owns it. |
| Rejection of duplicate query parameters and 366-day range | Design decisions authorized by TXN-04's requirement for bounded, validated query input. |
| `returnTo` allowlist and 300 ms debounce | Angular UX decisions within TXN-07; they do not alter backend authorization or API semantics. |
| Month grouping and segmented direction control | Presentational TXN-07 choices justified in `ui-flows.md`; month grouping applies only for `sortBy=occurredAt`, and neither adds API parameters. |
| Filter retention on account switch | `accountId` updates and `page` resets to 1; other active filters are retained. The selector emits only `selectionChange(accountId)`. |

## 6. Backend components and transactions

```text
transaction/
  cli/TransactionFixtureCommand, TransactionFixtureConfiguration
  controller/TransactionController
  model/BankTransaction, TransactionDirection, TransactionStatus
  model/dto/TransactionResponse, TransactionPageResponse, AccountSummary,
            DisputeEligibility, TransactionQuery
  repository/TransactionReadRepository, JdbcTransactionReadRepository
  service/TransactionQueryParser, TransactionQueryService, TransactionFixtureService
  mapper/TransactionResponseMapper
  validation/TransactionQueryValidation
```

The transaction fixture injects `AccountFixturePort` from the `account` module. That is the only account-module type it may import.

The final package names may be simplified where one class has no meaningful second responsibility, but classes remain feature-owned and follow concise Java names. `TransactionController` is thin: it obtains `SessionPrincipal`, delegates parsing/service work, maps returned domain models to response DTOs with `TransactionResponseMapper`, and returns `ApiEnvelope.success(...)`. It contains no business rules. `TransactionQueryService` owns read-only transactional boundaries and does not call mappers. The JDBC adapter owns SQL/projections and maps persistence values into transaction-domain records; no JPA entity leaks through a controller.

The list adapter executes a count and data query under the same ownership/filter/`accountId` predicate. This is a read-only operation; no explicit transaction is required for a single query, but a read-only Spring transaction may wrap the paired count/list operation. It must not promise snapshot consistency across them or across requests. The detail adapter is one ownership-scoped query.

`@AuthenticatedActivity` is applied to the two controller methods, reusing the existing `AuthenticationActivityInterceptor`; this feature adds no second interceptor, filter or renewal store. Its `preHandle` path runs after the session-validation filter and Spring Security authorization, before MVC binds query/path values or invokes the controller. Its guarded renewal uses the existing `SessionActivityStore` record and current-account/version check.

Consequently, an authenticated CUSTOMER request that reaches either marked handler renews at handler admission even when later query binding/validation returns `400` or an ownership-scoped detail lookup returns `404`. This is intentional and matches the approved authentication policy: activity means authorized admission to an eligible interaction, not successful completion. A malformed URL cannot reach the marked handler without first passing session, enabled-account and CUSTOMER checks, and a non-owned ID does not disclose data. Requests rejected before admission—anonymous, expired, disabled, wrong-role, CSRF-rejected unsafe requests, unmatched paths, health, bootstrap and logout—never renew. The feature adds no unsafe endpoint, so CSRF is not part of its GET path.

The Angular page makes only user-driven requests; it contains no timer, polling, background refresh, prefetch or automatic retry. The server marker remains authoritative—no HTTP input says whether a human initiated a request. A script can make the same request as a person, which is an accepted limitation of HTTP; the policy prevents application-generated keepalives rather than claiming to detect human intent.

## 7. Angular design

### 7.1 Routes and components

Register a lazy transaction feature route beside the accounts dashboard. Do not redirect `/customer` to transactions.

```text
features/customer/
  transactions/
    transactions.ts|html|scss|spec.ts # class Transactions: list/filter/page + selector host
    transaction-api.ts                # typed HttpClient read service
    transaction-models.ts             # wire/query/view types
  transaction-detail/
    transaction-detail.ts|html|scss|spec.ts # class TransactionDetail
```

Routes are `/customer/transactions` and `/customer/transactions/:id`. Existing top-level role guards continue to govern these child routes; no client guard grants backend access. CUSTOMER header navigation is Accounts (`/customer`) / Transactions (`/customer/transactions`) / My Disputes. AGENT navigation remains unchanged and has no customer transaction link.

Account list loading uses the customer-accounts `AccountApi` (or a thin wrapper that calls it). The shared `account-selector` is imported from `shared/components/account-selector`. This feature does not reimplement the selector.

### 7.2 State, requests and UI behavior

**Amendment (2026-10-11):** The transaction list removes its separate current-account summary and account table column; the shared selector is the sole current-account display and shows the full account number when non-null in both its selected card and option cards. The table uses sortable Transaction, Date and Amount headers; `reference` remains API-sortable without a UI control. Search, Direction and Status occupy the main filter row, while date and amount ranges stay in the collapsed Filters panel. Active filter count and Clear filters are shown as specified in `ui-flows.md`. Rows per page moves to the pagination footer. Dates use one short-month format (`dd MMM yyyy, HH:mm`) in list and detail, with `MMM yyyy` month group headings. Transaction rows contain only Transaction, Date, Amount and Status; merchant is the detail link, and status labels are sentence case with accessible tints.

`TransactionApi` uses relative `/api/v1/transactions` URLs and typed envelope interfaces. It uses the existing HttpClient interceptors: session cookies remain browser-managed; GET requests have no CSRF header; a 401 clears the existing in-memory identity; no bearer token is added.

The list component uses a typed Reactive Form for filters plus signals for loading, error, page data, accounts and the latest request generation. Query parameters are the authoritative browser-visible state. It subscribes to route query-parameter changes so browser back/forward causes one request for that historical state. A malformed externally supplied URL is sent unchanged to the backend and renders the safe `400/1004` query-correction state; the UI does not silently reinterpret it as a different search. User changes reset to page 1 and replace the query state, except that an account-selector change updates only `accountId` and `page` (other filters retained). Merchant/reference typing is debounced by 300 ms; `switchMap`/generation guarding cancels or ignores prior responses. It does not issue requests for every keystroke before debounce, or retry a failed request automatically.

**Missing `accountId`:** on entering `/customer/transactions` without `accountId`, load GET /accounts, pick the primary account (fallback: first in GET /accounts order), and `history.replace` the URL with `?accountId=<id>` before requesting transactions. If GET /accounts is empty, render the no-accounts empty state without a selector. **Supplied but absent or non-owned `accountId`:** show "Account not available" with a link that navigates to the default account; do not redirect silently.

Detail navigation carries a validated, same-origin `returnTo` value containing only `/customer/transactions` and the current allowlisted query parameters (including `accountId`). The detail back link uses it; an absent or invalid value returns to `/customer/transactions`. It never accepts an external URL or unvalidated application route.

The template provides a Transactions heading and the shared account-selector as the sole current-account display; it does not repeat account context in each row. The table has Transaction, Date, Amount and Status columns; merchant is the detail link and reference is smaller muted text. The Transaction, Date and Amount headers are sortable; reference remains supported by the API without a UI control. Search, Direction and Status are in the main row; date and amount range fields sit in a collapsed-by-default Filters panel. It shows an active-filter count and Clear filters while any search/filter/non-default direction is active, with inline plain-language inverted-range and over-366-day errors. Rows per page is in the pagination footer. The direction control is an accessible radio group styled as a segmented pill without visible native indicators. Presentational month headings appear for current-page rows only when sorted by `occurredAt` (Africa/Johannesburg calendar month; a heading may repeat on the next page); dates consistently use `dd MMM yyyy, HH:mm` in list and detail, and month headings use `MMM yyyy`. It uses semantic table markup at desktop widths and stacked, still-linkable rows at narrower widths. Loading uses an accessible busy state/skeleton; no result is a clear empty state; `400` presents safe query correction feedback; `503` gives an explicit manual retry; `401` relies on existing session clearing/routing. Responses never expose raw server stack details.

Month grouping is a scan aid for a chronological list and appears only when sorted by `occurredAt`. It is not an API grouping parameter and is not copied from any external mockup. The segmented direction control is a first-class binding of the existing `direction` filter.

The detail component loads once for a route-ID change, renders status/direction/amount/date/reference/merchant/masked account and a status-derived eligibility explanation. Transaction rows and detail never show the full number; the account selector current-account summary follows the account feature display rule. It does not render a dispute-submission CTA, statement, receipt, download, link, or implied submission flow. It offers a visible return to the list that preserves the last query parameters.

Account labels in this feature are "Main Account" and "Savings Account" only, taken from GET /accounts `displayName`. Do not invent type strings.

Component styles remain local. Shared button variants and global token/reset/typography styles are reused; no generic transaction table/card library is introduced. Lucide icons only.

### 7.3 Accessibility and responsive behavior

- Selector: ARIA `listbox`/`combobox` as specified by customer-accounts, or native `<select>` at narrow widths.
- Direction control: radio group or tablist with `aria-selected`; not colour-alone.
- Month headings are semantic headings associated with the following rows.
- Skeleton sets `aria-busy="true"`; empty/error use `role="status"` or `aria-live="polite"`.
- Desktop-first; stacked rows and adequate touch targets on mobile. No hover-only essential actions.

## 8. Testing strategy and traceability

| Requirements | Evidence |
|---|---|
| TXN-01, TXN-02 | Real PostgreSQL HTTP tests: anonymous/revoked/disabled `401/1001`, AGENT `403/1002`, customer owns-only list/count, cross-customer detail `404/1003`, owned `accountId` narrows, omitted `accountId` returns all owned, non-owned `accountId` `404/1003`, malformed/duplicate `accountId` `400/1004`. |
| TXN-03, TXN-04 | Parser unit tests plus PostgreSQL integration tests for defaults/bounds, empty page, supported and unknown parameters, normalized literal search, escaped wildcard search, combined predicates including `accountId`, 0/two-decimal/Numeric-limit amounts, Johannesburg boundary dates, deterministic sorting and offset overflow. |
| TXN-05 | Service/API tests for both completed directions eligible and pending/failed `NOT_COMPLETED`; assert nullable dispute ID and no dispute repository/table query; detail rejects `accountId`. |
| TXN-06 | Flyway upgrade and clean-schema tests for V6 constraints/indexes without altering V5; mappings validate against PostgreSQL; fixture command tests prove existing-CUSTOMER-only target, `AccountFixturePort` usage, no account/auth-user creation, distinct MAIN vs SAVINGS transaction sets, idempotence and mismatch refusal. |
| TXN-07 | Angular Karma/Jasmine HttpTestingController/router tests for role navigation, query URL synchronization, debounce/stale-response protection, selector `selectionChange`, default-primary `history.replace`, not-owned accountId UI, loading, empty, validation, 401 and 503 states, list/detail fields, month grouping of current-page rows, direction control, responsive template behaviour and no dispute/statement/receipt/download CTA. |
| TXN-08 | HTTP envelope/header tests, database failure isolated suite returning `503/3002`, and activity integration tests proving designated list/detail admission renews activity while poll-like/unmarked paths, health, CSRF bootstrap and rejected requests do not. |

Integration tests use the existing PostgreSQL Testcontainers setup, never H2. Database outage tests run in a dedicated isolated context/container and never stop the developer Compose database. Authentication regression tests remain part of the full suite. Test fixtures use customer A/customer B (each with MAIN + SAVINGS) plus an AGENT to prove ownership and role boundaries.

## 9. Risks and decisions resolved

- **Synthetic data:** resolved through the explicit idempotent transaction fixture command plus `AccountFixturePort`, not a startup seed or migration data insert. It requires an existing provisioned CUSTOMER with `MAIN` and `SAVINGS` accounts and is safe to rerun.
- **Future dispute state:** resolved as status-only eligibility and a null dispute association. This intentionally stages the baseline's existing-dispute behavior to Dispute Submission; that feature must update the shared API documentation when it introduces the dispute model.
- **Pagination concurrency:** resolved as offset paging with no snapshot guarantee. It is adequate for fixed synthetic data and is documented for a later cursor-pagination decision if live changing data is introduced.
- **Strict idle:** resolved as explicit server-side marking of both transaction GET handlers, with a frontend ban on automated keepalives. This follows the existing authenticated-handler-admission policy and does not rely on detecting human input from HTTP.
- **All-accounts vs selector:** the API still supports omitting `accountId` (all owned accounts). The UI always binds one owned account via the shared selector and URL rewrite, matching `ui-flows.md`.
- **Filter retention:** changing account resets `page` only; other filters remain. That logic lives in the Transactions host, not in the presentational selector.
- **AI mockup:** not a source of truth. Tile/selector fields, month grouping and the direction control are justified in `ui-flows.md` against existing frontend standards.

No unresolved blocker prevents planning once this design is approved.
