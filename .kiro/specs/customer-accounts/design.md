# Customer Accounts — Design

**Status:** Approved for implementation — 2026-10-11
**Approval:** Requirements, design, and task plan approved by the developer on 2026-10-11.
**Requirements:** [requirements.md](requirements.md)
**Approved baselines:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

## 1. Scope and design decisions

This feature adds the customer account dashboard and the `GET /api/v1/accounts` endpoint to the existing Java 21 / Spring Boot 3.5 modular monolith and Angular standalone application. It relies on the implemented opaque, PostgreSQL-backed session authentication and retains same-origin `/api/v1` requests.

Backend implementation follows the project's Lombok convention for constructor injection, logging, and persistence accessors; Lombok remains a backend-only build dependency.

It introduces the V5 Flyway migration (`accounts` table), two fixture accounts per CUSTOMER, the shared `account-selector` Angular component, and the `AccountFixturePort` contract consumed by the later transaction fixture. The operational fixture command is the only account-row creation path in this phase. It does not add transactions, disputes, customer-facing account mutation, a single-account detail endpoint, or any payment operation.

| Decision | Design |
|---|---|
| Module ownership | The `account` feature module owns the `accounts` table (V5), the `BankAccount` domain model, `GET /api/v1/accounts`, the `AccountFixturePort`, and the account fixture command. It does not own `app_users`; those remain in `auth`. |
| Transaction module access | The `transaction` module will access account IDs through `AccountFixturePort` only. It must not import the `account` module's JPA entity or Spring Data repository. |
| Account types | `MAIN` and `SAVINGS` only, enforced by a database `CHECK` constraint and a Java `AccountType` enum. Display names are derived at the application layer; no display-name column is stored. |
| Balance | Synthetic `NUMERIC(19,2)` set by the fixture command only. Not derived from transactions. Serialized as a two-decimal string. No negative-balance semantics. |
| Primary account | Partial unique index `UNIQUE(customer_id) WHERE is_primary`; at most one primary per customer. Fixture sets `MAIN` as primary. No-primary fallback is UI-side: use first in GET /accounts order. |
| Account-number validation and masking | Well-formed iff ^[0-9]{10}$ (ASCII digits; leading zeroes allowed); no trimming/normalization. The account feature has one AccountNumberMask.ACCOUNT_NUMBER_LENGTH constant and one matching V5 CHECK. AccountNumberMask is the sole masking implementation. Only owner-scoped GET /accounts may return the full number; malformed values return null plus ****. The frontend displays accountNumber only when non-null and does not validate it. Transactions/disputes expose masked form only. |
| Ordering | Fixed: primary first, `created_at ASC`, `id ASC`. No sort/filter/search params; unknown params → 400/1004. |
| displayName | Derived by the mapper from `AccountType`: `MAIN` → "Main Account", `SAVINGS` → "Savings Account". Not stored. A separate display-name column is not justified: the mapping is a closed function of two enum values. |
| AccountFixturePort | A narrow interface in the `account` module exposes `Map<String, UUID> getFixtureAccountIds(String customerEmail)`, keyed by the fixed values `MAIN` and `SAVINGS`. Its implementation resolves the existing CUSTOMER through the auth-owned lookup contract and reads fixture IDs through the account-owned repository. The transaction fixture imports only this interface; no account-specific model, entity or Spring Data repository crosses the feature boundary. This makes the contract explicit and compiler-visible. |
| Idle activity | `GET /api/v1/accounts` is marked `@AuthenticatedActivity`, consistent with transaction GETs. Angular dashboard makes no polling, prefetch or automatic retry calls. |
| Backend boilerplate | Use the existing Lombok dependency selectively as described in backend standards; no additional dependency is required. |

## 2. Module boundaries and request flow

```text
Angular Accounts dashboard
  -> GET /api/v1/accounts[?page=&size=]
  -> Spring Security / session validation
  -> CUSTOMER route authorization
  -> @AuthenticatedActivity admission interceptor
  -> AccountController
  -> AccountQueryService
  -> AccountReadRepository (ownership-scoped query: customer_id = :authenticatedCustomerId)
  -> AccountResponse DTO + ApiEnvelope
```

`auth` remains the owner of `app_users`, account-status/version lookup and session validation. `security` remains the owner of the filter chain, CSRF policy, current-account validation, error writing and strict idle activity. `AccountController` obtains the authenticated UUID from the validated Spring Security principal; it never accepts it from a route, query parameter or body.

The `transaction` module will later join against `accounts.customer_id` in its own SQL queries, without importing the `account` module's JPA entity or Spring Data repository. The `AccountFixturePort` interface is in the `account` module's public contract; the transaction fixture calls it as a Spring bean.

## 3. Persistence and synthetic data

### 3.1 V5 additive schema migration

Create one additive Flyway migration `V5__create_accounts.sql`. It must not modify V1–V4. It creates:

```sql
CREATE TABLE accounts (
  id            UUID          PRIMARY KEY,
  customer_id   UUID          NOT NULL REFERENCES app_users(id),
  account_number VARCHAR(32)  NOT NULL UNIQUE CHECK (account_number ~ '^[0-9]{10}$'),
  account_type  VARCHAR(32)   NOT NULL CHECK (account_type IN ('MAIN', 'SAVINGS')),
  balance       NUMERIC(19,2) NOT NULL,
  is_primary    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX idx_accounts_primary
  ON accounts(customer_id) WHERE is_primary;

CREATE INDEX idx_accounts_customer
  ON accounts(customer_id);
```

The foreign key enforces referential integrity only. A service invariant ensures the `customer_id` refers to a CUSTOMER-role user; this cannot be expressed as an FK constraint. The `CHECK` constraint enforces allowed type values at the database level, complementing the Java `AccountType` enum. `ddl-auto: validate` continues to validate mapped schema; Flyway remains the only schema writer.

The later `V6__create_bank_transactions.sql` (owned by `customer-transactions`) must not recreate or alter the `accounts` table.

### 3.2 Fixture provisioning

Schema migration contains no account rows. Add an explicitly enabled Spring Boot non-web command under `account/cli`, analogous to the existing account-administration command, disabled by default and run only when the operator chooses the dedicated command configuration.

The command accepts an existing customer email, normalizes it through the existing auth-owned lookup contract, confirms the target is an enabled CUSTOMER, and creates two accounts in one database transaction:

| Field | MAIN account | SAVINGS account |
|---|---|---|
| `account_type` | `MAIN` | `SAVINGS` |
| `is_primary` | `TRUE` | `FALSE` |
| `balance` | Distinct deterministic value per CUSTOMER (documented) | Distinct deterministic value per CUSTOMER (documented) |
| `account_number` | A deterministic synthetic 10-digit value derived from the target customer UUID and account type | A distinct deterministic synthetic 10-digit value derived from the target customer UUID and account type |

Account IDs are deterministic UUIDs derived by the account fixture service from the existing customer UUID and account type using Java's standard `UUID.nameUUIDFromBytes` with the UTF-8 key `customer-accounts:<customer-uuid>:<MAIN|SAVINGS>`. Account numbers are generated as fixed-width ten-digit decimal candidates from a SHA-256 digest of a versioned fixture namespace, customer UUID, account type, and deterministic probe counter. Leading zeroes are preserved. The `account_number` UNIQUE constraint is the final global guard; when a candidate is already used by another row, the service deterministically advances the probe counter until an unused candidate is reserved. If the candidate is already held by this fixture's deterministic account UUID, it is accepted as the existing stable value. If the finite 10-digit value space is exhausted, the command fails safely without modifying rows. Existing fixture rows are verified against their deterministic expected values. Distinct customers therefore receive distinct account numbers and IDs, and reruns keep existing values stable. Fixture account `created_at` uses fixed UTC constants (`MAIN`: `2025-01-01T00:00:00Z`, `SAVINGS`: `2025-01-02T00:00:00Z`; never `now()`), so ordering and immutable-value comparison are deterministic. Synthetic values are disclosed only by the owner-scoped accounts endpoint. The account fixture command and `AccountFixturePort` resolve IDs through the same account-owned lookup; the transaction fixture does not duplicate this derivation.

The command uses `INSERT ... ON CONFLICT DO NOTHING` on stable PKs, then reads and locks the fixture rows and verifies that existing rows match their expected immutable values. A mismatch returns a safe operational error and rolls back without affecting other data. Re-running the command produces no duplicates and no destructive reseed. It never creates authentication users or accepts passwords as arguments. It is never invoked automatically at startup.

Tests create their own isolated data through feature repositories or SQL setup; they do not depend on the operational command.

## 4. Domain model and ownership invariants

`BankAccount` is the `account` module's domain model:

```text
BankAccount {
  UUID   id
  UUID   customerId
  String accountNumber      // owner-only accounts DTO when non-null
  AccountType accountType   // MAIN | SAVINGS
  BigDecimal balance
  boolean isPrimary
  Instant createdAt
}
```

`AccountType` is a Java enum: `MAIN`, `SAVINGS`. `displayName()` method returns "Main Account" or "Savings Account".

**Account number validation and masking** use `AccountNumberMask`, one specifically named public class in `account`; it exposes no entity or repository. The only named length constant in the `account` feature is `AccountNumberMask.ACCOUNT_NUMBER_LENGTH = 10`. A value is well-formed iff it matches `^[0-9]{10}$` (ASCII digits; leading zeroes allowed), with no trimming or normalization. The same pattern appears in the single V5 account-number CHECK. A future length change updates that constant and uses one additive migration to replace the CHECK.

- `mask(String)` returns `****` plus the last four digits for a well-formed value; null, malformed or otherwise invalid input returns exactly `****`.
- When malformed persisted data is encountered, log a warning containing account UUID and a safe reason only. Never log the raw number.
- `GET /api/v1/accounts` returns `accountNumber` (full value when well-formed, otherwise null) and `maskedNumber` to the owning CUSTOMER only. The raw malformed value is never returned.
- Transaction and all future dispute DTOs expose only `maskedNumber`. No other endpoint, error, log, audit entry, URL or search predicate may contain a full account number.
- The dashboard account row and account selector's current-account summary display the full value when present. No copy button, reveal toggle or other interaction is included.


**Ownership predicate**: every read begins with `accounts.customer_id = :authenticatedCustomerId`. The same predicate applies to the count and the item query.

**No-primary fallback**: the backend returns accounts in the fixed order (primary first, `created_at ASC`, `id ASC`). If no account has `is_primary = TRUE`, the order is simply `created_at ASC`, `id ASC`. The backend does not error; the UI uses the first item as the default. This case indicates a data fault and should be noted in operations documentation.

## 5. API design

### 5.1 Endpoint and authorization

| Method/path | Spring Security rule | Activity | Result |
|---|---|---|---|
| `GET /api/v1/accounts` | `hasRole('CUSTOMER')` | `@AuthenticatedActivity` eligible | paginated customer-owned account summaries |

Add this narrow GET matcher before the existing `anyRequest().denyAll()` matcher. Do not create a broad allowlist. Existing session validation handles disabled, revoked and expired sessions before authorization. Missing/invalid session → `401/1001`; authenticated AGENT → `403/1002`.

GET /accounts does not require CSRF. It uses the existing envelope, `application/json`, `Cache-Control: no-store` and correlation-ID filter.

There is no `GET /api/v1/accounts/{id}` endpoint in this feature; no screen requires it.

### 5.2 List contract

`GET /api/v1/accounts` accepts only `page` (integer, default 1, minimum 1) and `size` (integer, default 20, range 1..100). Any other query parameter (including `search`, `filter`, `sortBy`, `sortDirection`, `accountId`) returns `400/1004`. Duplicate or non-integer values for `page`/`size` return `400/1004`.

Successful response:

```json
{
  "status": true,
  "data": {
    "total": 2,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "a1b2c3d4-0001-0000-0000-000000000001",
        "accountType": "MAIN",
        "displayName": "Main Account",
        "accountNumber": "0123456789",
        "maskedNumber": "****6789",
        "balance": "12500.00",
        "currency": "ZAR",
        "primary": true
      },
      {
        "id": "a1b2c3d4-0001-0000-0000-000000000002",
        "accountType": "SAVINGS",
        "displayName": "Savings Account",
        "accountNumber": "9876543210",
        "maskedNumber": "****3210",
        "balance": "4750.00",
        "currency": "ZAR",
        "primary": false
      }
    ]
  },
  "error_code": 0
}
```

`total` uses the identical ownership predicate. A valid page beyond results returns 200 with empty `items` and the correct `total`. Empty ownership (no accounts) returns `total: 0, items: []`; it is not an error.

### 5.3 Error and availability behavior

| Situation | HTTP / `error_code` |
|---|---|
| Missing, revoked or expired session | `401 / 1001` |
| Authenticated wrong role (AGENT) | `403 / 1002` |
| Invalid page/size or unknown query parameter | `400 / 1004` |
| Store/database failure before commitment | `503 / 3002` |

Database failures must propagate to the existing unavailable mapping. They must never become an empty account list or a misleading not-found response.

### 5.4 Contract-decision traceability

| Contract detail | Source |
|---|---|
| `GET /api/v1/accounts`, CUSTOMER-only, no agent variant | API contract §3 and ACC-01–ACC-03 |
| `{status,data,error_code}` envelope; 401/1001, 403/1002, 400/1004, 503/3002; no-store/correlation | API contract §§1, 7 |
| Page 1, size 20, max 100; empty valid page is 200 | API contract §2 and ACC-03 |
| Fixed order: primary first, `created_at ASC`, `id ASC` | API contract §3 and ACC-03.6 |
| No sort/search/filter params; unknown → 400/1004 | API contract §3 and ACC-03.7 |
| `displayName` derived from `AccountType`; no stored column | ACC-04.2 |
| `balance` as two-decimal string; currency `"ZAR"` | ACC-05.3, API contract §3 |
| `maskedNumber` rule | ACC-05.4, API contract §4 |
| `primary` boolean field | ACC-06.3 |
| No `GET /accounts/{id}` | ACC out-of-scope §1 |
| Error code 2008 (`ACCOUNT_STATE_CONFLICT`) untouched | API contract §7 note |

## 6. Backend components

```text
account/
  cli/
    AccountFixtureCommand.java
    AccountFixtureConfiguration.java
  controller/
    AccountController.java
  model/
    BankAccount.java
    AccountType.java          // enum: MAIN, SAVINGS; displayName() method
    dto/
      AccountResponse.java    // id, accountType, displayName, maskedNumber, balance, currency, primary
      AccountPageResponse.java
  mapper/
    AccountResponseMapper.java  // maps owner-only accountNumber and maskedNumber; displayName derivation
      AccountNumberMask.java      // sole masking class and ACCOUNT_NUMBER_LENGTH constant
  repository/
    AccountReadRepository.java          // interface
    JpaAccountReadRepository.java       // Spring Data JPA
    AccountReadRepositoryAdapter.java   // ownership-scoped adapter; maps to BankAccount
    AccountFixtureRepository.java       // used by fixture command only
  service/
    AccountQueryService.java    // read-only @Transactional; owns ownership predicate
    AccountFixtureService.java  // fixture creation and deterministic ID derivation; implements AccountFixturePort
  port/
    AccountFixturePort.java     // narrow public interface for transaction fixture
```

`AccountController` is thin: it obtains `SessionPrincipal`, calls `AccountQueryService`, maps the returned domain summaries to response DTOs with `AccountResponseMapper`, and returns `ApiEnvelope.success(...)`. It contains no business rules. Services do not call mappers. `AccountQueryService` owns the read-only transaction boundary. `AccountReadRepositoryAdapter` maps JPA projections to `BankAccount`; the JPA entity never leaves the `repository` subpackage.

`AccountFixturePort` is the only thing the `transaction` module may import from `account`. Its method resolves the named customer's already provisioned MAIN and SAVINGS account IDs through account-owned persistence. The transaction fixture never reproduces the account ID derivation.

No additional Maven dependency. Backend code follows the existing Lombok policy for routine boilerplate; entity and domain invariants stay explicit.

## 7. Angular design

### 7.1 Routes and components

```text
features/customer/
  accounts/
    account-dashboard.ts|html|scss|spec.ts   # class AccountDashboard
    account-api.ts                           # typed HttpClient; GET /api/v1/accounts
    account-models.ts                        # wire + view types: AccountSummary, AccountPage

shared/
  components/
    account-selector/
      account-selector.ts|html|scss|spec.ts  # presentational; @Input accounts, selectedAccountId; @Output selectionChange
```

Route `/customer` maps to `AccountDashboard`. The existing `DashboardLayout` wraps it. The later transactions feature will register `/customer/transactions` as a sibling route; `/customer` does not redirect to it.

Header navigation for CUSTOMER is: **Accounts** / Transactions / My Disputes. The Accounts link points to `/customer`. AGENT navigation is unchanged.

`account-selector` is a standalone Angular component in `shared/components`. It accepts:
- `@Input() accounts: AccountSummary[]`
- `@Input() selectedAccountId: string`
- `@Output() selectionChange = new EventEmitter<string>()`

It renders a compact card-style switcher showing the currently selected account's `displayName`, full `accountNumber` when non-null, `maskedNumber` and formatted balance. The backend alone decides well-formedness and returns null for malformed stored values; the frontend performs no account-number validation. When opened it lists accounts with `accountNumber` omitted and masked context only. It reuses the dashboard row's summary fields and formatting; it is not a separate visual design. It will use ARIA `listbox`/`combobox` semantics, or a native `<select>` fallback at narrow widths. It does not fetch data; account fetching stays in the feature service that uses it.

The later customer Disputes page also reuses this component and defaults to the primary account, falling back to the first GET /accounts item if no primary exists. Its collection `accountId` is an ownership-validated resource selector; dispute ownership is checked through transaction → account → customer.

### 7.2 Dashboard state and behavior

`AccountApi` uses relative `/api/v1/accounts` URLs. It uses the existing `HttpClient` interceptors; no bearer token is added. It is injected into `AccountDashboard` only.

`AccountDashboard` uses:
- A signal `accounts` for the loaded account list.
- A signal `loadState: 'loading' | 'loaded' | 'empty' | 'error'` for UI state.

On init: call `GET /api/v1/accounts`. On 401: rely on the existing session-clearing flow. On 503: set `loadState = 'error'`; display "Something went wrong. Please try again." with a manual retry button. On success with no items: `loadState = 'empty'`. No automatic retry, no polling, no prefetch.

Dashboard goal copy names the accounts as (Main Account, Savings Account). The account list is a single vertical stack of full-width rows at every viewport size; account rows never form a side-by-side grid. Each row is a single keyboard-operable `<a routerLink="/customer/transactions" [queryParams]="{accountId: account.id}">` with visible focus. Its left side contains a Lucide account icon, display name, optional "Primary" chip, and a labelled "Account number" with the full owner-visible `accountNumber` when non-null. Its right side contains the "Available balance" label, the dominant ZAR-formatted balance, and a chevron. Reserve a fixed-height title row so display names align whether or not the Primary chip is present. Do not use letter avatars. Check all small text (labels and badges included) for WCAG AA contrast.

The page layout has an account-list column at about two thirds width and an optional quick-links column at about one third on desktop. At narrow widths, the quick-links column stacks below the account list. The account list comes first in DOM order. A populated quick-links slot is a labelled `<nav aria-label="Quick links">`; it renders only shortcuts to routes that exist and are implemented. No dead links or disabled "coming soon" entries are rendered. If no qualifying shortcut exists, omit the panel and give the account list the full width. This feature provides the slot only; it currently has no shortcut. Later feature tasks own their shortcuts (customer-transactions may add "View transactions" for the primary account; dispute features may add dispute shortcuts). The panel must never contain transfer, payment, statement, card, or other out-of-scope actions without an approved requirements change. UI follows `docs/design/ui-flows.md` and `.kiro/steering/frontend-standards.md`; do not copy labels or layouts from an AI-generated mobile mockup.

**Balance formatting decision:** Angular `CurrencyPipe` with locale `en-ZA` was executed against the installed Angular packages and produced `R12 500,00` (no space after R, a non-breaking space thousands separator and comma decimal separator). The approved display is the deterministic `R 12 500.00` format. Use one shared formatter for the account row, selector and transaction amounts: accept decimal-string wire amounts, preserve the two fractional digits, group integer digits with ordinary spaces, use a period decimal separator, and prefix `R `. Do not route through JS floating point or locale-specific currency output. Transaction display may add its direction sign around this formatted value; wire amounts remain decimal strings.

Skeleton loading: display `<div class="skeleton">` placeholders in the stacked account-row positions while `loadState = 'loading'`. Empty state: a centered message with no account rows or selector. Retry state: error message with a "Try again" button that re-calls `AccountApi.getAccounts()`.

### 7.3 account-selector reuse

The `account-selector` component will be implemented in this feature and registered in `shared/components`; the later `TransactionList` component will import it. Its internal display uses the same balance format as the account row (ZAR space-thousands). It has no dependency on `AccountApi` or any feature service.

### 7.4 Accessibility and responsive behavior

- Each account row is a focusable, keyboard-operable `<a>` element; `Enter` and `Space` both activate navigation (Space prevents page scrolling).
- The "Primary" badge is conveyed with both text and a visually distinct chip; it is not colour-alone.
- The skeleton loading state sets `aria-busy="true"` on the account-list container.
- The empty and error states are announced via `role="status"` or `aria-live="polite"`.
- Account rows remain full-width at mobile widths with adequate touch targets (min 44px height); the optional quick-links panel stacks below the account list.

## 8. Testing strategy and traceability

| Requirements | Evidence |
|---|---|
| ACC-01, ACC-02 | Real PostgreSQL HTTP tests: anonymous/revoked/disabled `401/1001`, AGENT `403/1002`, customer A owns only customer A's accounts, cross-customer UUID returns empty (not 404 — it simply doesn't appear in the list). |
| ACC-03 | HTTP tests for defaults, bounds, unknown-param → 400, empty page 200, correct scoped total, primary-first ordering verified with two customers of two accounts each. |
| ACC-04 | Service/API tests: `MAIN` → "Main Account", `SAVINGS` → "Savings Account"; rejected non-enum value in integration. |
| ACC-05 | `AccountNumberMask` unit tests: valid 10 digits, leading zeroes, 9 digits, 11 digits, empty, blank, null, letters, hyphen, internal space, trailing newline, a Unicode digit string and an emoji; verify malformed inputs return exactly `****`. Also verify balance serialization as a two-decimal string. HTTP tests assert serialized transaction and dispute-shaped bodies never contain a fixture full number, and CUSTOMER A never receives CUSTOMER B’s account number. |
| ACC-06 | PostgreSQL constraint test: inserting a second `is_primary=TRUE` account for the same customer violates the partial unique index. No-primary-fallback test: GET /accounts returns accounts in `created_at ASC` order when none is primary; Angular selects the first returned account as the default. |
| ACC-07 | Fixture command tests: existing-CUSTOMER-only target; idempotent re-run (no duplicate rows); mismatch refusal and rollback; no auth-user creation; `AccountFixturePort.getFixtureAccountIds` returns the documented stable UUIDs. |
| ACC-08 | Angular component tests (HttpTestingController): skeleton on load; full-width stacked rows; responsive account-list/quick-links layout; account list precedes the optional panel in DOM order; empty-panel case omits the panel and gives the account list full width; each row links to `/customer/transactions?accountId=<id>`; fixed title-row alignment with and without Primary badge; only implemented-route shortcuts render; 503 retry; no polling. Verify WCAG AA contrast for small text. `account-selector` unit tests: correct display of selected account; emits `selectionChange` on selection; keyboard/ARIA contract. |
| ACC-09 | HTTP envelope/header tests; database failure isolated suite returning `503/3002`; activity integration test confirming `GET /api/v1/accounts` renews activity while health/CSRF/bootstrap paths do not. |

Integration tests use the existing PostgreSQL Testcontainers setup, never H2. Database outage tests run in an isolated context and never stop the developer Compose database. Tests use customer A / customer B (with 2 accounts each) plus an AGENT to prove ownership and role boundaries.

## 9. Risks and decisions resolved

- **AccountFixturePort vs stable UUIDs:** resolved as an interface (`AccountFixturePort`), making the cross-module contract compiler-visible. Documented stable UUIDs alone would allow silent coupling via copied constants.
- **Balance format:** resolved as a custom ZAR formatter ensuring "R 12 500.00" with ordinary-space thousands grouping and a period decimal separator. Installed Angular `CurrencyPipe` with `en-ZA` returned `R12 500,00` (no space after R, non-breaking-space grouping and comma decimal separator), so both account row and selector use the shared deterministic formatter.
- **Filter retention on account switch (in Transactions, for the forward constraint):** when the account selector changes on the Transactions page, `accountId` is updated and `page` resets to 1; other active filters (direction, status, date range, search, sort) are retained unless the user explicitly clears them. This avoids losing context on account switch. It is recorded here because the account-selector component must emit only `selectionChange(accountId)`, not reset the parent's filters — that logic belongs to the Transactions feature.
- **No `GET /accounts/{id}`:** no screen requires a single-account detail endpoint. The list is sufficient for the dashboard and selector. Introducing it without a use case would create an untested surface.
- **No new error code:** no new numeric error code is introduced by this feature. The not-found case (non-owned accountId in a later feature) reuses 404/1003; the validation case reuses 400/1004. Error code 2008 (`ACCOUNT_STATE_CONFLICT`) is an existing authentication-account code and is untouched.

No unresolved blocker prevents planning once this design is approved.
