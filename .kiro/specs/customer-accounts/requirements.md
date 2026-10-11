# Customer Accounts — Requirements

**Status:** Approved — awaiting task plan — 2026-10-11
**Approval:** Requirements and design approved by the developer on 2026-10-11; implementation awaits an approved task plan.
**Feature:** Customer Accounts
**Prerequisites:** Project Foundation and Authentication & RBAC (implemented)
**Related approved baseline:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

## 1. Purpose and scope

Customers need to see which bank accounts they hold before navigating to transactions or disputes. This feature delivers the customer account dashboard and account list API, backed by PostgreSQL and the existing session-based authentication system.

It establishes the `accounts` table (V5 migration) and the account fixture command needed by later transaction work, but it does **not** implement transaction listing, transaction detail, dispute actions, account creation, account editing, or any payment operation.

### In scope

- Customer-only account list API endpoint `GET /api/v1/accounts` under `/api/v1`.
- PostgreSQL `accounts` table (V5 migration) with `balance`, `is_primary`, a database CHECK constraint on `account_type`, and a partial unique index enforcing at most one primary account per customer.
- Ownership-scoped account list with fixed deterministic ordering; no search, filters or sort parameters.
- Angular account dashboard screen: a responsive tile grid, one tile per account, within the existing `DashboardLayout` and session-aware routing.
- Shared presentational `account-selector` Angular component in `shared/components`, accepting `accounts` and `selectedAccountId` as inputs and emitting a `selectionChange` output; reusable by the later Transactions and Disputes pages.
- Account fixture command: an explicit, opt-in, non-web, idempotent command that creates two deterministic accounts (`MAIN` primary, `SAVINGS`) for an existing CUSTOMER.
- A narrow `AccountFixturePort` exposed from the `account` module so the later transaction fixture can obtain account IDs without importing the account entity or Spring Data repository.

### Out of scope

- Transaction listing, transaction detail, dispute submission, dispute history, agent workflows.
- Customer-facing account creation, editing or deletion; transfers, payments, statements, exports, card management, currency conversion, balance derivation from transactions, and credit or overdraft accounts. The explicit synthetic fixture command is the only account-row creation path in this phase.
- A `GET /api/v1/accounts/{id}` single-account detail endpoint (no screen requires it in this phase).
- Full account number reveal, copy-to-clipboard, or any other account-number interaction; search by account number.
- Account type management or adding new types beyond `MAIN` and `SAVINGS`.
- Public registration, authentication changes, an ADMIN application role, or a provisioning HTTP endpoint.
- Negative-balance semantics; the `balance` field is synthetic display data only.

## 2. Terms and invariants

- A **customer account** belongs to exactly one CUSTOMER user (`app_users`). A banking account (`accounts`) is distinct from an authentication account.
- **`account_type`** is restricted to `MAIN` or `SAVINGS` in this phase, enforced by a database CHECK constraint and a Java enum. `displayName` is derived at the application layer: `MAIN` → "Main Account", `SAVINGS` → "Savings Account". No extra display-name column is stored.
- **`balance`** is a synthetic, read-only display value set only by the fixture command. It is not derived from transactions and carries no settlement meaning. It serializes as a string with exactly two decimal places.
- A stored account number is well-formed only when it matches `^[0-9]{10}$`: exactly 10 ASCII digits. Leading zeroes are valid. Store it as text and perform no trimming or normalization. The sole named length constant in the `account` feature is `AccountNumberMask.ACCOUNT_NUMBER_LENGTH`; V5 has one matching database CHECK. A future length change uses one additive migration.
- `maskedNumber` is produced only by the public account-feature class `AccountNumberMask`: for a well-formed value it is `****` plus the last four digits; otherwise it is exactly `****`. The class exposes neither an entity nor a repository. Malformed values are logged with account UUID and reason only, never the number.
- **Primary account:** at most one account per customer may have `is_primary = TRUE`, enforced by a partial unique index `UNIQUE(customer_id) WHERE is_primary`. The fixture marks the `MAIN` account as primary. If a customer has accounts but none is primary (data fault), the UI falls back to the first account in GET /accounts response order; this case must not prevent display.
- **Fixed order:** accounts are returned primary-first, then `created_at ASC`, then `id ASC`. There are no sort parameters, search or filter parameters; unknown query parameters return 400/1004.
- The backend derives the authenticated customer from the PostgreSQL-backed session. A client never supplies a customer ID or account ID to establish identity or expand authorization.
- Error code 2008 (`ACCOUNT_STATE_CONFLICT`) is an existing authentication-account code; it must not be reused or renamed by this feature.

These preserve business rules BR-A02 and BR-T01 and the ownership and default-deny rules in steering.

## 3. Functional requirements

### ACC-01 — Customer access and role boundaries

1. The system shall require a valid, current server-side session for the accounts endpoint.
2. The system shall permit account list access only to users with the `CUSTOMER` role.
3. The system shall return the existing standard unauthenticated response (`401/1001`) for missing, expired, invalid or revoked sessions.
4. The system shall return the existing standard forbidden response (`403/1002`) when an authenticated `AGENT` attempts the accounts endpoint.
5. This feature shall retain default-deny handling for unrelated paths. It shall not introduce authorization for future transaction, dispute or agent endpoints.
6. The accounts endpoint shall be marked `@AuthenticatedActivity` so that an authenticated, authorized CUSTOMER request renews strict idle activity at handler admission, consistent with the existing policy applied to transaction and other customer-read handlers.

### ACC-02 — Ownership isolation

1. The system shall derive the authenticated customer's identity exclusively from the validated server-side security context.
2. The system shall return only accounts whose `customer_id` matches the authenticated customer's UUID.
3. Ownership constraints shall apply before counting, ordering and returning accounts.
4. A customer with no owned accounts shall receive HTTP 200 with an empty collection, not an error.
5. Only `GET /api/v1/accounts` may return the full account number, and only to its owning CUSTOMER. It returns `accountNumber` and `maskedNumber`; malformed stored values produce `accountNumber: null` and `maskedNumber: "****"`. No other endpoint, error body, log line, audit entry, URL or search predicate may contain a full account number. Transaction and later dispute responses contain only `maskedNumber`.
6. Disabled authentication accounts shall not gain access through existing or stale sessions. Account access shall rely on the existing authentication and session-revocation guarantees.

### ACC-03 — Account list

1. The system shall provide `GET /api/v1/accounts` for the authenticated customer's account list.
2. The list shall use the standard success envelope and the established collection payload: `data.total`, 1-indexed `data.page`, `data.size` and `data.items`.
3. The list shall default to page 1 and size 20, and shall accept a size no greater than 100.
4. A valid page beyond the matching result set shall return HTTP 200 with an empty `items` array and the matching ownership-scoped total.
5. Each list item shall include: `id` (UUID), `accountType` (`MAIN` or `SAVINGS`), `displayName` (derived string), `maskedNumber`, `balance` (two-decimal string), `currency` (`"ZAR"`), `primary` (boolean).
6. The list shall use fixed deterministic ordering: primary account first, then `created_at ASC`, then `id ASC` as a tie-breaker. There are no sort parameters.
7. The list shall not accept `search`, `filter`, `sort`, `sortBy`, `sortDirection` or any parameter beyond `page` and `size`. Unknown parameters shall return `400/1004`.

### ACC-04 — Account types and display names

1. The system shall restrict `account_type` to `MAIN` and `SAVINGS`, enforced by a database CHECK constraint and a Java enum.
2. The system shall derive `displayName` at the application layer without storing it: `MAIN` maps to "Main Account", `SAVINGS` maps to "Savings Account".
3. The system shall not use any external bank trademark as a type value, display name or fixture label.
4. The system shall not model credit or overdraft accounts, and `balance` shall have no negative-balance semantics in this phase.
5. Adding a new account type requires an additive migration to alter the CHECK constraint and a Java enum addition; no data column rename or other non-additive schema change is permitted.

### ACC-05 — Balance and masking

1. `balance` shall be a synthetic `NUMERIC(19,2)` value stored in the database, set only by the fixture command.
2. `balance` shall not be derived from, or affect, transaction records. It has no settlement or business meaning.
3. `balance` shall serialize as a positive string with exactly two decimal places, e.g. `"12500.00"`.
4. `account_number` is well-formed only when it matches `^[0-9]{10}$` (ASCII digits; leading zeroes allowed); it is stored as text with no trimming or normalization.
5. Only GET /api/v1/accounts may disclose the full well-formed value to its owning CUSTOMER as `accountNumber`; malformed stored values yield null and `maskedNumber: "****"`. Transactions and all later dispute responses expose only the masked value.
6. `AccountNumberMask` is the sole masking implementation, a specifically named public class in `account` with no entity/repository exposure. For well-formed values it returns `****` plus the last four digits; all other values, including null, return exactly `****`. On malformed stored data, log a warning with account UUID and reason, never the value.
7. The account feature has one named length constant, `AccountNumberMask.ACCOUNT_NUMBER_LENGTH`; V5 has one CHECK using the same exact pattern. Future length changes require one additive migration. No trimming or normalization is performed.

### ACC-06 — Primary account

1. At most one account per customer shall have `is_primary = TRUE`. This invariant shall be enforced by the database partial unique index `UNIQUE(customer_id) WHERE is_primary`.
2. The fixture shall set the `MAIN` account's `is_primary` to `TRUE` and the `SAVINGS` account's `is_primary` to `FALSE`.
3. The API shall surface `primary` as a boolean field in each account list item.
4. When no primary account exists for a customer (data fault), the UI shall fall back to the first account in GET /accounts response order. The backend shall not return an error for this case.

### ACC-07 — Account fixture

1. The feature shall provide an explicit, opt-in, non-web fixture command, analogous to the existing account-administration command, that creates two deterministic accounts for a specified existing CUSTOMER: a `MAIN` account (`is_primary = TRUE`) and a `SAVINGS` account (`is_primary = FALSE`), each with a distinct deterministic balance.
2. The command shall accept an existing customer email as an operator argument, verify the target is an enabled CUSTOMER, and create the fixture accounts in one database transaction using `INSERT ... ON CONFLICT DO NOTHING` on stable primary/unique keys.
3. The command shall verify that any pre-existing rows for those stable keys match their expected immutable values; a mismatch shall return a safe operational error and roll back without affecting other data.
4. Re-running the command shall produce no duplicates and no destructive reseed.
5. The command shall never create authentication users, accept passwords or credentials as arguments, or run automatically at startup.
6. The feature shall expose a narrow `AccountFixturePort` from the `account` module so the transaction fixture can obtain account IDs without importing the account module's entity or Spring Data repository.
7. Tests shall create their own isolated data through feature services/repositories or SQL setup; they shall not depend on the operational command or startup data.

### ACC-08 — Angular account dashboard

1. An authenticated CUSTOMER shall land on the account dashboard at `/customer` after authentication.
2. The dashboard shall display a responsive tile grid — 2–3 columns on desktop, stacked on mobile — one tile per owned account in the GET /accounts fixed order.
3. Each tile shall be a single keyboard-operable link navigating to `/customer/transactions?accountId=<id>`, activated by Enter and Space with visible focus.
4. Each tile shall display: `displayName`, a "Primary" badge when `primary = true`, the full owner-visible `accountNumber` for well-formed account values (omit it when null), and `balance` formatted as ZAR currency (e.g. "R 12 500.00").
5. Dashboard goal copy shall name the accounts as (Main Account, Savings Account).
6. The dashboard shall display a skeleton loading state while GET /accounts is in flight.
7. When the customer has no accounts, the dashboard shall display an empty state with helpful copy and no tiles or selector.
8. On GET /accounts failure, a `401` shall rely on the existing session-clearing flow; a `503` shall display a manual retry state with no automatic retry.
9. The dashboard shall not poll, prefetch, auto-refresh or auto-retry the accounts request.
10. The dashboard shall not display a dispute CTA, statement, receipt, download, card management or transfer action.
11. The `account-selector` shared component shall be implemented in this feature and made available to the later Transactions page. It shall be purely presentational: it shall not fetch account data; it accepts `accounts` and `selectedAccountId` as inputs and emits a `selectionChange` output. It shall show the current account summary (including full `accountNumber` only when well-formed), with no copy button, reveal toggle or other account-number interaction; it shall use ARIA `listbox`/`combobox` semantics, or a native `<select>` fallback on narrow widths.

### ACC-09 — Reliability, security and maintainability

1. All feature responses shall use the existing `{status, data, error_code}` envelope, meaningful HTTP statuses, structured safe error details, `X-Correlation-ID` and `Cache-Control: no-store` conventions.
2. The Angular application shall continue to use same-origin `/api/v1` requests. The feature shall not add CORS configuration.
3. Safe transaction GET requests shall not require CSRF tokens. Existing session validation, enabled-state checks and CSRF handling for unsafe requests remain unchanged.
4. Database failures shall propagate to the existing unavailable mapping (`503/3002`). They shall never become an empty account list or a misleading not-found response.
5. The feature shall not introduce any new library, service, cache, gateway, role, or authentication mechanism.
6. Tests shall demonstrate customer isolation: a CUSTOMER receiving only their own accounts; a cross-customer account ID never accessible; an AGENT receiving `403/1002`.
7. Tests shall use the project's established PostgreSQL Testcontainers integration-testing approach and shall not rely on H2.
8. Existing authentication, session security, authorization and migration behavior (V1–V4) shall remain unchanged.
9. Implementation shall follow the repository's approved specification workflow: requirements approval, design approval, task approval, then implementation.

## 4. API contract acceptance

The approved architecture reserves these contracts. The design phase shall make request DTOs, response DTOs, validation annotations and error mappings precise without changing the established envelope or URL base.

| Endpoint | Required access | Required behavior |
|---|---|---|
| `GET /api/v1/accounts` | CUSTOMER session | Ownership-scoped, deterministically ordered, paginated account summaries |

No mutation endpoint, agent endpoint, account-detail endpoint (`GET /accounts/{id}`), or transaction endpoint is introduced by this feature.

## 5. Acceptance evidence

Implementation must provide evidence covering the following outcomes before this feature is complete:

1. Unit tests for masking: valid 10 digits, leading zeroes, 9 digits, 11 digits, empty, blank, null, letters, hyphen, internal space, trailing newline, Unicode digits and emoji; plus ownership predicate, displayName, balance serialization, primary ordering and no-primary fallback.
2. PostgreSQL integration tests that apply the V5 additive Flyway migration, prove its account-number CHECK rejects malformed inserts, verify JPA mappings and constraints (including the partial unique index), and prove scoped list totals across at least two customers with multiple accounts.
3. HTTP tests for the success envelope, fixed ordering, pagination, unknown-parameter rejection, unauthenticated access (`401/1001`), AGENT denial (`403/1002`), and cross-customer isolation, including proof CUSTOMER A never receives CUSTOMER B’s full number.
4. Fixture command tests proving: existing-CUSTOMER-only target, idempotence (re-run produces no duplicates), mismatch refusal, no auth-user creation, and that `AccountFixturePort` returns the deterministic IDs for the customer's MAIN and SAVINGS accounts.
5. Angular component/unit tests for: tile grid render, skeleton loading, empty state, 401 session-clearing, 503 manual retry, tile navigation to correct `?accountId`, Primary badge visibility, and `account-selector` input/output contract.
6. HTTP coverage shall serialize transaction and dispute-shaped responses and assert neither contains the full account number.
7. End-to-end or integration evidence that session enforcement and strict idle-activity marking follow the approved authentication policy.

## 6. Dependencies and future work

- This feature depends on the implemented session authentication, CUSTOMER/AGENT role checks, correlation/error envelope, Angular HTTP handling and `DashboardLayout`.
- The later Customer Transactions feature depends on this feature's V5 migration, account fixture command and `AccountFixturePort` to obtain account IDs for the transaction fixture.
- The later Customer Transactions feature will add the `accountId` query parameter to `GET /api/v1/transactions` and reuse the shared `account-selector` component built here.
- The later customer Disputes page will reuse the shared account selector, default to the primary account (or the first account in GET /accounts order if none is primary), and add `accountId` to customer `GET /api/v1/disputes`; ownership is checked through transaction → account → customer.
- Dispute submission, agent workflows and all further features remain separate specifications.

## 7. Resolved design decisions

1. **Fixture account IDs:** the transaction fixture calls the narrow `AccountFixturePort` in the `account` feature; the contract returns the existing customer's MAIN and SAVINGS IDs and exposes no entity or Spring Data repository. The interface keeps fixture ownership inside its feature and gives the transaction feature a compiler-checked boundary.
2. **Balance display format:** Angular `CurrencyPipe` with locale `en-ZA` was executed against the installed Angular packages and produced `R12 500,00` (no space after R, a non-breaking space group separator and comma decimal separator). The UI therefore uses the deterministic format `R 12 500.00`, implemented by a formatter that groups the decimal-string integer digits with ordinary spaces and always uses a period plus two fractional digits. It does not rely on locale-specific CurrencyPipe output.
3. **Pagination idle activity:** GET /accounts is eligible for `@AuthenticatedActivity`; the dashboard makes no polling, prefetch, automatic refresh or automatic retry calls.

## 8. Traceability

| Requirement area | Approved source |
|---|---|
| Customer ownership, account-to-customer join | BR-A02, BR-T01 |
| Balance is synthetic display value; primary meaning; MAIN/SAVINGS types | BR-T01 (updated), `docs/design/database-design.md` §2 |
| Masking rule | `docs/design/api-contract.md` §4 |
| Pagination, envelopes, error codes | `docs/design/api-contract.md` §§1–3, 7 |
| accountId as resource selector (transactions, disputes) — forward constraint | `docs/design/api-contract.md` §3, `.kiro/steering/api-contracts.md` |
| Dashboard tile grid, selector component, routing | `docs/design/ui-flows.md` §§3–4, 7 |
| Shared account-selector component location and contract | `.kiro/steering/structure.md` |
| Module boundaries, Flyway, DTO/persistence practices | `.kiro/steering/backend-standards.md`, `.kiro/steering/structure.md` |
| Feature order | `.kiro/steering/spec-workflow.md` |
