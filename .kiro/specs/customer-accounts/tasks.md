# Customer Accounts — Implementation Tasks

**Status:** Implementation complete — 2026-10-11
**Requirements:** [requirements.md](requirements.md)
**Design:** [design.md](design.md)
**Scope:** ACC-01 through ACC-09. Implement the customer accounts dashboard and only the backend/data/fixture dependencies needed to support it. Transactions, disputes, agent workflows, and account mutation remain out of scope.

## Task 1 — Add the accounts schema and account-number rules

**Requirements:** ACC-04, ACC-05, ACC-06, ACC-09.8
**Depends on:** Authentication & RBAC (implemented)

- Add `V5__create_accounts.sql` without changing V1–V4. Explicitly define `balance NUMERIC(19,2) NOT NULL` and `created_at TIMESTAMPTZ NOT NULL`, along with the other accounts columns, exact 10-ASCII-digit account-number CHECK, account-type CHECK, ownership FK, customer index, and partial unique primary-account index.
- Add `BankAccount`, `AccountType`, and the repository-owned persistence mapping following the designed module boundary.
- Add public `AccountNumberMask` with the single `ACCOUNT_NUMBER_LENGTH` constant and the only masking implementation. Log malformed persisted values with account UUID and a safe reason, never the number.
- Add focused unit and PostgreSQL tests for mask edge cases, account mapping, schema constraints, and primary uniqueness.
- Acceptance: migration applies on PostgreSQL; malformed account numbers and a second primary account for one customer are rejected; all specified masking inputs return the expected output.

## Task 2 — Implement the customer-owned accounts list API

**Requirements:** ACC-01, ACC-02, ACC-03, ACC-04, ACC-05, ACC-06, ACC-09.1–4, ACC-09.6–8
**Depends on:** Task 1

- Add the ownership-scoped query/service, response mapper and immutable DTOs. Return both `accountNumber` and `maskedNumber` only from this owner-scoped customer endpoint; malformed values return null and `****`.
- Add the narrow `GET /api/v1/accounts` CUSTOMER security matcher before `anyRequest().denyAll()`, with `@AuthenticatedActivity`, the existing response envelope/correlation/no-store behavior, page/size validation, and fixed primary/created/id ordering. Reject unknown query parameters.
- Add isolated PostgreSQL HTTP tests for customer-only access, agent denial, session behavior, ownership, empty results, ordering, pagination, error envelopes/headers, unknown parameters and database failure mapping. Include an explicit serialized-response assertion proving CUSTOMER A's response never contains CUSTOMER B's account number.
- Acceptance: only a customer's own account rows contribute to its total/items; response behavior matches the approved API contract; existing auth/session rules remain unchanged.

## Task 3 — Add deterministic account fixtures and the fixture port

**Requirements:** ACC-07, ACC-09.5, ACC-09.8
**Depends on:** Task 1

- Add the narrow `AccountFixturePort` contract and account-owned implementation. Resolve targets through the existing auth lookup; expose only deterministic MAIN/SAVINGS IDs across the module boundary.
- Add the explicit opt-in non-web command. Derive stable account IDs from target customer UUID plus account type, and derive synthetic 10-digit account-number candidates from target customer UUID plus account type so separate customers do not share fixture numbers. Enforce global uniqueness in PostgreSQL and resolve a candidate collision deterministically (with a safe failure if the finite 10-digit space is exhausted). Keep the derivation in one fixture source.
- Set `created_at` to fixed UTC constants (`MAIN`: `2025-01-01T00:00:00Z`, `SAVINGS`: `2025-01-02T00:00:00Z`; never `now()`), so ordering and idempotency comparisons remain stable. Keep deterministic balances and transactional insert/verify behavior; refuse mismatched existing rows safely.
- Document the account fixture command's purpose in `backend/README.md` and its opt-in use, target restrictions, uniqueness/collision handling, idempotence, mismatch behavior, and safe recovery in a dedicated `docs/operations/customer-account-fixtures.md` runbook.
- Add tests for enabled-CUSTOMER targeting, multiple customers receiving distinct globally unique account numbers/IDs, rerun idempotence, deterministic timestamps, collision handling, mismatch rollback, no authentication-user creation, and stable IDs returned by the port.
- Acceptance: no startup seeding or web provisioning path exists; repeat invocation creates no duplicates or destructive changes.

## Task 4 — Build typed Angular account data support

**Requirements:** ACC-03, ACC-05, ACC-08, ACC-09.2
**Depends on:** Task 2

- Add account wire/view types and an `AccountApi` using same-origin `/api/v1/accounts` through the existing interceptors.
- Add a deterministic decimal-string ZAR formatter shared by the account row and selector; do not convert balances through floating point or locale-dependent currency output.
- Add focused tests for API envelope mapping and balance formatting, including large values and exactly two decimal places.
- Acceptance: the client uses the established envelope and returns correctly typed account summaries without adding dependencies or storage/polling behavior.

## Task 5 — Implement the customer accounts dashboard

**Requirements:** ACC-08, ACC-09.2, ACC-09.5
**Depends on:** Task 4

- Replace the customer placeholder route component with `AccountDashboard` at `/customer` inside the existing `DashboardLayout`.
- Load accounts once on entry; implement skeleton, populated, empty, 503/manual-retry states. Let the existing 401 session-clearing flow handle unauthorized responses. Do not poll or retry automatically.
- Update the CUSTOMER header navigation label/link to **Accounts** at `/customer`.
- Render one full-width stacked row link per account in API order; rows are never laid out side by side. Each row has a Lucide account icon, display name, optional Primary badge, labelled account number on the left, and right-aligned Available balance with visually dominant formatted ZAR amount followed by a chevron. Reserve a fixed-height title row so badge presence does not shift row alignment. Do not use letter avatars. The backend alone validates well-formedness and returns null for malformed stored values; the frontend performs no number validation. Keep required copy limited to Main Account and Savings Account.
- Use a desktop two-column page layout: account list about two thirds, optional quick-links panel about one third. On narrow screens the panel stacks below the account list. Keep accounts first in DOM order. The panel, when present, is `<nav aria-label="Quick links">` and contains only shortcuts to existing implemented routes. Omit it entirely while no qualifying shortcut exists and let accounts take the full width; no dead links or disabled "coming soon" rows. This feature defines only the slot. Later features add their own shortcuts in their own tasks (customer-transactions: view transactions for the primary account; dispute features: dispute shortcuts). Never add transfer, payment, statement, card, or other out-of-scope actions without an approved requirements change.
- Check dashboard small-text contrast against WCAG AA.
- Add Angular tests for loading, populated/empty/error states, stacked rows, responsive stacking, account-list-before-panel DOM order, empty-panel omission/full-width behavior, implemented-route-only shortcuts, primary badges, title-row alignment, and exact `/customer/transactions?accountId=<id>` navigation.
- Acceptance: route guards/layout remain intact; dashboard layout and states match ACC-08 and the approved UI flow. Record that row links to `/customer/transactions?accountId=<id>` do not have a target route until the separate customer-transactions feature is implemented; do not add that route here.

## Task 6 — Add the reusable presentational account selector

**Requirements:** ACC-08.12, ACC-09.2
**Depends on:** Task 4

**Amendment (2026-10-11):** ACC-08.12's selector interaction is updated to a custom accessible card dropdown at regular widths and native `<select>` fallback at narrow widths. Both selected summary and all option cards show full `accountNumber` when non-null, matching dashboard formatting; backend owns well-formedness. No number interaction is added.

- Add standalone `shared/components/account-selector` with `accounts` and `selectedAccountId` inputs and a `selectionChange` output. It does not fetch data.
- Show the selected card and every option card with Lucide account-type icon, display name, Primary badge when applicable, full number when `accountNumber` is non-null, and available balance. Use the same formatting as dashboard rows; add no copy, reveal or other number interaction.
- Use a button trigger and accessible listbox/options with expanded/selected state, keyboard support (Enter/Space, Arrow Up/Down, Home/End, Enter selection, Escape returning focus), outside-click close, visible focus and reduced-motion support. Keep a native `<select>` fallback at narrow widths.
- Add component tests for selected/option cards, emitted ID, full-number/null display, role and ARIA state, keyboard navigation, Escape focus return and outside-click close.
- Acceptance: component is reusable by later features without introducing any transaction/dispute page or route now.

## Task 7 — Verify feature boundaries and acceptance evidence

**Requirements:** ACC-01–ACC-09
**Depends on:** Tasks 1–6

- Review changed source/docs against the account requirements, API contract, data model, security rules and UI flow. Confirm account numbers do not enter transaction/dispute response-shaped bodies or logs, URLs, audit entries or search predicates.
- Run these verification commands from the indicated directories and record actual results: from `backend/`, `mvn --batch-mode clean verify`; from `frontend/`, `npm run lint`, `npm run test:ci`, and `npm run build`. The frontend currently has no `lint` script or linter dependency; resolve that verification gap within existing project constraints or record clearly that the command is unavailable (do not silently claim it passed).
- Record as a known limitation that the dashboard's `/customer/transactions?accountId=<id>` row targets are unavailable until the separate customer-transactions feature adds that route.
- Record actual commands/results and any remaining limitations in the feature acceptance evidence; do not claim unrun checks.
- Acceptance: all required tests pass, no out-of-scope feature has been implemented, and the worktree diff is ready for review.
