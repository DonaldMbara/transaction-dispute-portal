# Customer Transactions — Requirements

**Status:** Approved — revision 2 — awaiting task plan — 2026-10-11
**Approval:** Requirements and design approved by the developer on 2026-10-11; implementation awaits an approved task plan.
**Feature:** Customer Transactions
**Prerequisites:** Project Foundation, Authentication & RBAC (implemented), **Customer Accounts (must be approved and implemented first)**
**Related approved baseline:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

---

### Revision 2 — 2026-10-11

The following requirements and sections were changed from the original approval. Original wording is replaced in place; this log records the reason for each change.

Changed requirements: TXN-02.3, TXN-02.6, TXN-06.1–2, TXN-06.7–11, TXN-07.1, TXN-07.3–16, TXN-08.7–9. Changed sections: §1, §2, §4, §5, §6, §7 and §8.

### Revision 3 — 2026-10-11

Changed requirement: TXN-02.8. Changed sections: §1 scope, §5 acceptance evidence, §7 UI requirements, §8 traceability.

Account-number disclosure is aligned with the Customer Accounts ruling. TXN-02.8 now permits only masked account data in transaction responses; list/detail and future dispute responses never carry `accountNumber`. Test evidence includes serialized HTTP response assertions that transaction and dispute-shaped bodies do not contain the full number. The account selector's current-account summary follows the Customer Accounts display rule; no copy, reveal or other number interaction is added.

| Changed ID / section | Change summary |
|---|---|
| Status line | Reset from "Approved" to "Draft – revision 2 awaiting approval"; this spec may not be tasked until re-approved. |
| Prerequisites | Added: Customer Accounts must be approved and implemented first (V5 migration, account fixture command, `AccountFixturePort`). |
| §1 In scope | Removed accounts-table ownership; accounts are owned by customer-accounts. Added `accountId` selector and reuse of the shared `account-selector` component. |
| §1 Out of scope | Account dashboard, `GET /accounts`, and selector *implementation* stay with customer-accounts. This feature reuses the selector. No dispute CTA, statement, receipt or download. |
| TXN-02.3 | Replaced the all-accounts / no-selector rule with an optional ownership-scoped `accountId` resource selector. |
| TXN-02.6 | Narrowed so a validated ownership-scoped `accountId` may narrow results; a client still cannot expand authorization. |
| TXN-06 | V6 creates `bank_transactions` only. Fixture obtains account IDs through `AccountFixturePort`. Each existing CUSTOMER fixture target already has `MAIN` (primary) and `SAVINGS`; this feature adds a distinct deterministic transaction set per account. |
| TXN-07 | `/customer` remains the accounts dashboard. Transactions is `/customer/transactions?accountId=`. Reuse shared selector. Include month grouping and the URL-bound segmented direction control; no AI-mockup labels or dispute CTA. |
| TXN-07.14–15 | Add direction URL/back-forward behavior, narrow-width Filters control, month grouping only for `sortBy=occurredAt`, deterministic ZAR display and Johannesburg time rendering. |
| §4 API contract table | No account endpoint is introduced here. Added `accountId` on GET /transactions. |
| §5 Acceptance evidence | Added `accountId` ownership, cross-customer `accountId`, omitted/duplicate `accountId`, selector switching / stale response, default-primary URL rewrite, not-owned accountId UI, distinct per-account fixture sets. |
| §6 Dependencies | Depends on customer-accounts. Shared selector and V5/`AccountFixturePort` are inputs, not reimplemented. |
| §7 Fixed requirements | Records explicit fixture, pagination and idle-activity constraints. Account types, primary and dashboard tiles are owned by customer-accounts. |
| §8 Traceability | Added `accountId` resource-selector row and MAIN/SAVINGS display-name row. |

---

## 1. Purpose and scope

Customers need a safe, read-only view of the bank transactions that belong to their own accounts. This feature delivers the customer transaction list and detail experience, backed by PostgreSQL data and the existing session-based authentication system.

It establishes the transaction data needed by later dispute-submission work, but it does **not** implement dispute creation, dispute history, agent work queues, payment actions, or an account-management experience. Account persistence, `GET /api/v1/accounts`, the account dashboard and the shared `account-selector` component are owned by Customer Accounts.

### In scope

- Customer-only transaction list and transaction detail API endpoints under `/api/v1`.
- PostgreSQL-backed synthetic, read-only bank transactions (V6 migration, `bank_transactions` table only).
- Ownership-scoped list/detail queries, pagination, searching, filtering, allowlisted sorting, and an optional `accountId` resource selector.
- Angular customer transaction list and detail screens within the existing `DashboardLayout` and session-aware routing, reusing the shared `account-selector` from customer-accounts.
- Customer-safe display of whether a transaction is eligible for a future dispute action (advisory only; no dispute CTA in this phase).

### Out of scope

- Transaction creation, editing, deletion, imports, bank integrations, payment reversals, refunds, exports, statements, receipts, downloads and currency conversion.
- Public registration, account provisioning changes, authentication changes or an ADMIN application role.
- Dispute submission, dispute records, duplicate-dispute prevention, idempotency, customer dispute history, agent workflows and their UI routes. No dispute CTA in this phase.
- Account dashboard, account tiles, account creation or editing, `GET /api/v1/accounts`, the `accounts` table / V5 migration, and the `account-selector` implementation (all owned by customer-accounts). Merchant logos and a reusable design-system expansion beyond reusing that selector.
- Credit/overdraft accounts, negative-balance semantics, and any account type other than `MAIN` and `SAVINGS`.

## 2. Terms and invariants

- A **customer account** belongs to exactly one enabled or disabled CUSTOMER user. A banking account is distinct from an authentication account (`app_users`). MVP types are `MAIN` and `SAVINGS`; customer-visible names are "Main Account" and "Savings Account", derived from type by the accounts feature. This feature does not persist or invent display names.
- A **bank transaction** belongs to exactly one customer account. It has a positive monetary magnitude, a direction (`IN` or `OUT`), a status (`PENDING`, `COMPLETED` or `FAILED`) and an occurrence timestamp.
- The portal is read-only for transactions and accounts. The feature uses synthetic assessment data only.
- A transaction is eligible for later dispute submission only when it is `COMPLETED`. Under the current approved rule, both completed incoming and outgoing transactions are eligible.
- The backend derives the authenticated customer from the PostgreSQL-backed session. A client never supplies a customer ID to broaden the result set. An optional `accountId` query parameter is a resource selector validated against that session-derived ownership; it never establishes identity.

These preserve business rules BR-T01 through BR-T05 and the ownership and default-deny rules in steering.

## 3. Functional requirements

### TXN-01 — Customer access and role boundaries

1. The system shall require a valid, current server-side session for every transaction endpoint.
2. The system shall permit transaction list and detail access only to users with the `CUSTOMER` role.
3. The system shall return the existing standard unauthenticated response for missing, expired, invalid or revoked sessions.
4. The system shall return the existing standard forbidden response when an authenticated `AGENT` attempts a customer transaction endpoint.
5. The feature shall retain default-deny handling for unrelated paths. It shall not introduce authorization for future dispute or agent endpoints.

### TXN-02 — Ownership isolation

1. The system shall derive the authenticated customer's identity exclusively from the validated server-side security context.
2. The system shall return transactions only when their associated banking accounts belong to that authenticated customer.
3. The system shall accept an optional `accountId` query parameter on `GET /api/v1/transactions` as a resource selector. When present, it shall be validated against session-derived ownership through the account → customer join and shall narrow results to that one owned account. When omitted, the API shall return transactions across all owned accounts. A provided `accountId` that is malformed or duplicated shall return `400/1004`. A well-formed `accountId` that is absent or not owned by the authenticated customer shall return `404/1003`. The parameter shall never establish or extend the caller's identity or authorization.
4. Ownership constraints shall apply before counting, filtering, searching, sorting, pagination and returning transaction details.
5. A transaction that does not exist or does not belong to the authenticated customer shall return the same standard not-found response.
6. The system shall not accept a customer identifier as a means of expanding the caller's authorized result set. A banking `accountId` may be accepted only as the ownership-scoped resource selector defined in TXN-02.3.
7. Disabled authentication accounts shall not gain access through existing or stale sessions. Transaction access shall rely on the existing authentication and session-revocation guarantees.
8. Transaction list/detail responses shall expose only the shared `maskedNumber` value for account context and never `accountNumber` or any full account number. Later dispute responses follow the same masked-only rule. Customer A data, credentials, internal audit details and agent-only notes remain protected.

### TXN-03 — Transaction list

1. The system shall provide `GET /api/v1/transactions` for the authenticated customer's transaction list.
2. The list shall use the standard success envelope and the established collection payload: `data.total`, 1-indexed `data.page`, `data.size` and `data.items`.
3. The list shall default to page 1 and size 20, and shall accept a size no greater than 100.
4. A valid page beyond the matching result set shall return HTTP 200 with an empty `items` array and the matching scoped total.
5. Each list item shall provide only the customer-safe transaction summary needed to identify and open the transaction: identifier, reference, merchant, positive amount, currency, direction, status, occurrence time and masked account context.
6. The list shall present deterministic ordering. The approved default is `occurredAt` descending with the UUID `id` as a same-direction tie-breaker; the design shall define the concrete allowlist mapping without changing those semantics.

### TXN-04 — Search, filtering and sorting

1. The transaction list shall support combined `direction` (`ALL`, `IN`, `OUT`), `status` (`ALL`, `PENDING`, `COMPLETED`, `FAILED`), occurrence-date and monetary-amount filters, together with the optional `accountId` selector in TXN-02.3.
2. Search shall be case-insensitive and literal, covering merchant and transaction reference only.
3. Sort fields and sort directions shall be allowlisted. Unsupported values shall not be interpreted as arbitrary database fields or expressions.
4. Search shall trim leading/trailing whitespace, collapse internal whitespace runs and use the approved maximum normalized length of 100 characters. Other filter inputs shall have explicit valid ranges; the design phase shall define any remaining resource limits consistent with the approved API contract.
5. Amount bounds shall accept zero, have at most two fractional digits and remain within `NUMERIC(19,2)` limits. Invalid dates, reversed date ranges, reversed amount ranges, malformed amounts, unsupported parameters, invalid pagination inputs and pages whose offset exceeds supported database/JPA bounds shall return the established HTTP 400 validation response with error code `1004`.
6. Valid filters that produce no matching transactions shall return HTTP 200 with an empty collection and the correct ownership-scoped total.
7. Search wildcards shall be treated literally. Query handling shall prevent user-supplied values from altering query structure.
8. Date-only filters shall use `Africa/Johannesburg` calendar boundaries converted to UTC. Amount bounds shall be inclusive and use exact decimal semantics.
9. The list shall retain the approved default page 1, default size 20 and maximum size 100. The design phase shall define any maximum date range and remaining resource limits before implementation; changing the approved pagination or search limits requires a requirements change.

### TXN-05 — Transaction detail

1. The system shall provide `GET /api/v1/transactions/{id}` for an owned transaction.
2. The detail response shall include the customer-safe transaction fields: identifier, reference, merchant, positive amount, currency, direction, status, occurrence time and masked account context.
3. The detail response shall expose initial dispute eligibility based on the current approved business rule: a transaction is eligible when its status is `COMPLETED`, regardless of whether its direction is `IN` or `OUT`.
4. A transaction that is not `COMPLETED` shall be marked ineligible with a stable reason indicating that the transaction has not completed.
5. This feature shall not query or depend on dispute records, dispute tables or dispute endpoints to determine whether a previous dispute exists.
6. The response contract shall preserve the approved customer-safe dispute container, but this feature shall report no associated dispute and shall use only status-based initial eligibility. This is an approved staged clarification of BR-T05: existing-dispute lookup and the `ALREADY_DISPUTED` state belong to the later Dispute Submission feature, which will introduce dispute-aware eligibility without requiring this feature to implement dispute creation.
7. Eligibility information shall be advisory UI information, not an authorization guarantee. The future dispute-submission operation must independently validate transaction ownership, status and dispute-specific rules when accepting a submission.
8. Detail access shall satisfy the ownership behavior in TXN-02.
9. `GET /api/v1/transactions/{id}` shall not accept an `accountId` query parameter.

### TXN-06 — Read-only data and integrity

1. Transaction persistence shall be introduced through one additive Flyway migration `V6__create_bank_transactions.sql`. Existing migration history (V1–V5) shall remain unchanged. V6 shall not recreate or alter the `accounts` table.
2. Foreign keys and appropriate database constraints shall enforce transaction-to-account referential integrity and valid transaction values. Account-to-customer integrity and the CUSTOMER-role service invariant remain those established by customer-accounts.
3. Transaction amounts shall use positive `NUMERIC(19,2)` values in PostgreSQL and `BigDecimal` in Java. Direction shall determine the displayed sign; stored amounts shall not be negative.
4. Monetary values shall use ZAR in the MVP and serialize with exactly two fractional digits.
5. Direction and status values shall be restricted to the approved enumerations.
6. Customer-facing functionality shall not provide account or transaction creation, update or deletion endpoints. Transaction GET operations shall not mutate their business data.
7. Synthetic transaction data shall be deterministic, repeatable and explicitly provisioned for the assessment environment.
8. Repeated data provisioning shall not create duplicate fixtures or destructively overwrite unrelated existing records.
9. Synthetic-data provisioning shall never implicitly create authentication users, banking accounts, or weaken account-provisioning controls. The transaction fixture shall obtain account IDs only through the customer-accounts `AccountFixturePort` (or the documented equivalent of that port). It shall not import the account module's JPA entity or Spring Data repository.
10. For each existing CUSTOMER fixture target that already has a `MAIN` (primary) account and a `SAVINGS` account, the command shall attach a distinct deterministic transaction set to each account. It shall not create a third account type or mark a non-`MAIN` account as primary.
11. The design phase shall select the provisioning mechanism and define its execution safeguards.

### TXN-07 — Angular customer experience

1. An authenticated `CUSTOMER` shall reach the Transactions screen at `/customer/transactions` through the existing dashboard layout and session-aware routing. `/customer` remains the account dashboard owned by customer-accounts; this feature shall not redirect `/customer` to transactions.
2. An authenticated `AGENT` shall not be offered customer transaction navigation and shall not be authorized to access customer transaction endpoints.
3. The transaction list shall support the approved search, filter, sort and pagination capabilities, including the `accountId` selector.
4. Query controls shall be represented in the browser URL without placing credentials, session identifiers or private notes in URL state. The URL is the only account-selection state (no `localStorage` / `sessionStorage`).
5. Selecting an owned transaction shall open its detail view. The back link shall return to `/customer/transactions` preserving `accountId` and other allowlisted query parameters.
6. Loading, empty, validation-error, authentication-expiry and service-failure states shall be communicated clearly without exposing internal errors. The list shall include skeleton loading, empty and manual retry states. `401` relies on the existing session-clearing flow; `503` is a manual retry with no automatic retry, polling or prefetch.
7. The interface shall display monetary values, transaction direction, status, occurrence time and masked account context consistently.
8. Initial dispute eligibility shall be displayed based on the API response. The UI shall not infer eligibility independently, shall not imply that a dispute can already be submitted, and shall not render a dispute CTA, statement, receipt or download action.
9. The feature shall reuse existing authentication, routing, dashboard and API-response conventions rather than introducing a parallel application structure. UI shall follow `docs/design/ui-flows.md` and `.kiro/steering/frontend-standards.md`: desktop-first Capitec public-web language (not the mobile app), simplified authenticated header, existing `DashboardLayout`, shared button variants, local component styles, Lucide icons, accessible and responsive behaviour.
10. The list shall reuse the shared presentational `account-selector` component. It shall pass `accounts` and `selectedAccountId` as inputs and handle `selectionChange`. The selector reuses account summary fields and formatting; its current-account summary follows the Customer Accounts full-number display rule, while transaction rows/detail remain masked-only. It is not a separate visual design. No copy, reveal or other number interaction is added.
11. Opening `/customer/transactions` without `accountId` shall load GET /accounts, select the primary account (fallback: first in GET /accounts order), and rewrite the URL with `history.replace` to `?accountId=<id>`. Changing the selector shall update `accountId`, reset pagination to page 1, and retain other active filters.
12. A supplied `accountId` UUID that is absent or not owned (API `404/1003`) shall show an "Account not available" state with a link to the default (primary or first) account. The UI shall not redirect silently. If GET /accounts returns no accounts while the route has no accountId, show the no-accounts empty state without a selector.
13. Changing the selector shall cancel in-flight list requests and discard stale responses.
14. The list shall present an accessible segmented direction control (All / Money In / Money Out) bound to `direction`, reflecting the URL on load and browser back/forward; changing it resets pagination to page 1. Presentational month grouping of the current page's rows shall use Africa/Johannesburg calendar month only when sorted by `occurredAt`; it shall not change API pagination or sort. Advanced filters shall appear behind an accessible Filters control at narrow widths.
15. Monetary values shall use the deterministic ZAR format `R 12 500.00` (ordinary spaces for thousands, period decimal separator, two decimals); direction signs may be added in the display, while wire amounts remain decimal strings. Times shall render in `Africa/Johannesburg`.
16. Goal and account labels shall use "Main Account" and "Savings Account" only. The UI shall not reproduce labels, field names, merchant categories, card details, account names or number formats from an AI-generated mobile mockup.

### TXN-08 — Reliability, security and maintainability

1. All feature responses shall use the existing `{status, data, error_code}` envelope, meaningful HTTP statuses, structured safe error details, `X-Correlation-ID` and `Cache-Control: no-store` conventions.
2. The Angular application shall continue to use same-origin `/api/v1` requests through the existing development proxy and future reverse-proxy path. The feature shall not add browser CORS configuration.
3. Safe transaction GET requests shall not require CSRF tokens. Existing session validation, enabled-state checks and CSRF handling for unsafe requests remain unchanged.
4. The design shall distinguish explicit user-initiated transaction viewing or query actions from background requests when applying the existing eligible-handler-admission policy. Only explicitly designated, authenticated and authorized user-initiated handlers may renew strict-idle activity; health, CSRF bootstrap, logout, rejected requests, automatic refresh, prefetch and background polling must never become keepalives.
5. Database failures shall not be converted into successful empty transaction lists or misleading not-found responses; before response commitment they shall return the established unavailable response, HTTP 503 with error code `3002`.
6. Query inputs shall be bounded and validated to limit avoidable resource exhaustion.
7. Tests shall demonstrate customer isolation, including attempts to retrieve another customer's transaction by identifier, attempts to select another customer's `accountId`, and attempts to manipulate list filters.
8. Tests shall use the project's established PostgreSQL integration-testing approach and shall not rely on H2 as a substitute for PostgreSQL behavior.
9. Tests shall cover pagination, combined filters, literal search, allowlisted sorting, monetary boundaries, date boundaries, role restrictions, `accountId` selector behaviour and failure responses.
10. Existing authentication, session security, authorization and migration behavior shall remain unchanged.
11. Implementation shall follow the repository's approved specification workflow: requirements approval, design approval, task approval, then implementation.

## 4. API contract acceptance

The approved architecture already reserves these endpoints. The design phase shall make their request DTOs, response DTOs, validation annotations and error mappings precise without changing the established envelope or URL base.

| Endpoint | Required access | Required behavior |
|---|---|---|
| `GET /api/v1/transactions` | CUSTOMER session | Scoped, searchable/filterable/sortable, paginated transaction summaries; optional `accountId` resource selector |
| `GET /api/v1/transactions/{id}` | CUSTOMER session and ownership | Customer-safe owned transaction detail; 404 for absent or non-owned ID |

No mutation, agent transaction endpoint or account endpoint is introduced by this feature. `GET /api/v1/accounts` remains owned by customer-accounts.

## 5. Acceptance evidence

Implementation must provide evidence that covers the following outcomes before this feature is complete:

1. Domain/service tests for ownership predicates, eligibility mapping, supported statuses/directions and validation of query inputs.
2. PostgreSQL integration tests that apply the additive V6 Flyway migration (without altering V5/`accounts`), verify JPA mappings/constraints and prove scoped list totals and detail lookup across at least two customers, each with a `MAIN` and a `SAVINGS` account.
3. HTTP tests for the success envelope, pagination, combined filters, deterministic sort, malformed queries, unauthenticated access, AGENT denial and cross-customer not-found behavior.
4. HTTP tests for `accountId`: owned account narrows results; omitted `accountId` returns all owned accounts' transactions; malformed/duplicate `accountId` → `400/1004`; non-owned or absent `accountId` → `404/1003`; `accountId` combined with other filters; `GET /transactions/{id}` rejects `accountId`.
5. Fixture tests: existing-CUSTOMER-only target; uses `AccountFixturePort` rather than account-entity imports; idempotent re-run; mismatch refusal; no auth-user or account creation; distinct deterministic transaction sets on the `MAIN` and `SAVINGS` accounts.
6. Frontend tests for the customer guard/navigation, URL query state, loading/error/empty states, display of customer-safe data, stale-request handling, selector switching, default-primary URL rewrite via `history.replace`, not-owned `accountId` UI state, month grouping of the current page, segmented direction control, and absence of dispute/statement/receipt/download actions.
7. End-to-end or integration evidence that session enforcement and strict idle-activity marking follow the approved authentication policy without weakening it.

## 6. Dependencies and future work

- This feature depends on implemented session authentication, CUSTOMER/AGENT role checks, correlation/error envelope, Angular HTTP handling and `DashboardLayout`.
- This feature depends on Customer Accounts: V5 `accounts` table, `GET /api/v1/accounts`, account fixture (`MAIN` primary + `SAVINGS`), `AccountFixturePort`, and the shared `account-selector` component.
- Future dispute submission will use the transaction ID and eligibility state produced here. It will own dispute persistence, idempotency, the `Dispute this transaction` action and a concrete link to an existing dispute.
- Customer dispute history will add `accountId` to customer `GET /api/v1/disputes` using the same ownership rule. Agent workflows remain a separate specification.

## 7. Fixed requirements for design

1. **Synthetic transaction data lifecycle:** controlled, repeatable synthetic transactions are loaded only through the explicitly invoked non-web fixture command, which requires existing customer-accounts MAIN and SAVINGS accounts. It does not auto-provision login or banking accounts and does not run at startup.
2. **Pagination concurrency:** offset pagination has no snapshot guarantee; rows may move between pages if data changes between requests.
3. **Idle activity marking:** both transaction GET handlers are eligible for `@AuthenticatedActivity`; Angular issues no background polling, prefetch, auto-refresh or automatic retry requests.

Account types (`MAIN` / `SAVINGS`), display-name derivation, primary-account marking, dashboard tiles and selector implementation are decided in customer-accounts and are not reopened here.

## 8. Traceability

| Requirement area | Approved source |
|---|---|
| Ownership, read-only transactions, amounts, statuses, details | BR-T01 through BR-T05 |
| MAIN/SAVINGS types; Main Account / Savings Account display names; primary | BR-T01, customer-accounts ACC-04–ACC-06 |
| `accountId` resource selector | `docs/design/api-contract.md` §3, `.kiro/steering/api-contracts.md`, `.kiro/steering/security.md` |
| Customer isolation and non-enumeration | `.kiro/steering/security.md` |
| Pagination, filtering, sorting, date/search semantics and envelopes | `docs/design/api-contract.md` |
| Dashboard tiles, selector, month grouping, direction control | `docs/design/ui-flows.md`, `.kiro/steering/frontend-standards.md` |
| Modular boundaries, Flyway and DTO/persistence practices | `.kiro/steering/backend-standards.md`, `.kiro/steering/structure.md` |
| Feature order | `.kiro/steering/spec-workflow.md` |
