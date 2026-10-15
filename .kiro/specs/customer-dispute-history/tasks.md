# Customer Dispute History — Tasks

**Status:** Complete — implementation and verification passed on 2026-10-12
**Design:** [design.md](design.md)
**Requirements:** [requirements.md](requirements.md)

> Tasks are sequenced backend-first, then Angular, then navigation wiring. Each task references the requirement IDs and design sections it satisfies. Mark tasks complete only after their verification criteria pass.

---

## Task 0 — Schema pre-check (gate)

**Satisfies:** requirements §6 dependency, design §8 schema pre-check

Before writing any feature code, verify that the dispute-submission migrations provide the schema this feature relies on.

### Checklist

0.1 Locate and read all Flyway migrations that create `dispute_events`.

0.2 Confirm `dispute_events` has a `dispute_version` column (integer/bigint, not null).

0.3 Confirm the application code (or migration seed comments) uses the event type values: `CREATED`, `CLAIMED`, `RELEASED`, `RESOLVED_ACCEPTED`, `RESOLVED_REJECTED`. These are the values `CustomerTimelineMapper` will receive.

0.4 If any of the above is missing: **stop, do not add a migration inside this feature**. Instead document the gap clearly (e.g. open a follow-up task or amend the dispute-submission spec) and get developer approval before continuing.

0.5 If all checks pass: document the confirmed migration version and column names in a brief comment at the top of `CustomerTimelineMapper`.

### Verification
- Implementer has explicitly confirmed all three points in task notes or a commit message before Task 1 begins.
- No new Flyway migration is created by this task.

---

## Task 1 — Backend DTOs and timeline mapper

**Satisfies:** DSH-02, DSH-05, design §§4.3–4.4 and §5

Create the customer-facing read DTOs and the curated timeline mapper inside the existing `dispute` module. No database queries yet.

### Subtasks

1.1 Add Java records/classes in `dispute/model/dto/`:
  - `CustomerAccountSummary(UUID id, String displayName, String maskedNumber)`.
  - `CustomerTransactionSummary(UUID id, String merchant, String reference, BigDecimal amount, String currency, String direction, CustomerAccountSummary account)`.
  - `CustomerTimelineEntry(String type, String message, Instant occurredAt)`.
  - `CustomerDecision(String outcome, String message)`.
  - `CustomerDisputeListItemDto(UUID id, CustomerTransactionSummary transaction, String reason, String status, Instant createdAt, Instant updatedAt, Instant resolvedAt)`.
  - `CustomerDisputeDetailDto` with all list-item fields plus `String description`, `CustomerDecision decision` (nullable), `List<CustomerTimelineEntry> timeline`.
  - `DisputeQuery` — immutable record including `authenticatedCustomerId`, `accountId` (nullable), a `StatusPredicate` sealed type (NONE/INDIVIDUAL/IN_LIST), `reason`, `fromUtc`, `toExclusiveUtc`, `refSearchPattern`, `subPattern`, `sortField`, `sortDirection`, `pageRequest`.

1.2 Create `CustomerTimelineMapper` in `dispute/mapper/`. Accepts `List<EventProjection>` (record with `disputeVersion`, `eventType`, `customerMessage`, `createdAt`; no agent/internal fields) and returns `List<CustomerTimelineEntry>`:
  - `CREATED` → `SUBMITTED`, "We received your dispute."
  - `CLAIMED` (claimCount == 0) → `IN_REVIEW`, "Your transaction was being reviewed."
  - `CLAIMED` (claimCount > 0) → `IN_REVIEW`, "Review of your dispute restarted."
  - `RELEASED` → `RETURNED_TO_QUEUE`, "Your dispute was waiting for review again." (no release reason)
  - `RESOLVED_ACCEPTED` → `RESOLVED`, `customerMessage` from event row.
  - `RESOLVED_REJECTED` → `RESOLVED`, `customerMessage` from event row.
  - Unknown event types skipped (forward-compat).

1.3 Unit-test `CustomerTimelineMapper`:
  - Single `CREATED` → one `SUBMITTED`.
  - Single `CLAIMED` → `IN_REVIEW` with "being reviewed".
  - Two `CLAIMED` events (intervening `RELEASED`) → first "being reviewed", `RETURNED_TO_QUEUE`, second "restarted".
  - `RESOLVED_ACCEPTED` → `RESOLVED` with `customerMessage`.
  - `RESOLVED_REJECTED` → `RESOLVED` with `customerMessage`.
  - Empty input → empty list.
  - Unknown event type skipped.

### Verification
- All unit tests pass.
- No import from any other feature module's entity or Spring Data repository.
- `CustomerDisputeDetailDto` has no field for any agent/internal column.
- `EventProjection` has no field for `internalNotes`, `actorId`, `previousAgentId` or `newAgentId`.
- `CustomerAccountSummary` has `displayName` and `maskedNumber`; no `accountNumber` field.

---

## Task 2 — Backend repository and query parser

**Satisfies:** DSH-03, DSH-04, DSH-11, design §§3 and 5

Implement the ownership-scoped JDBC read repository and the request parser, including `ACTIVE`/`RESOLVED` group translation.

### Subtasks

2.1 Create `CustomerDisputeReadRepository` interface in `dispute/repository/`:
  - `Page<CustomerDisputeListItemDto> findOwned(DisputeQuery query)`
  - `Optional<CustomerDisputeDetailDto> findOwnedById(UUID disputeId, UUID customerId)`
  - `List<EventProjection> findEventsByDisputeId(UUID disputeId)`

2.2 Implement `JdbcCustomerDisputeReadRepository`:
  - **List SELECT:** `d.id, d.reason, d.status, d.created_at, d.updated_at, d.resolved_at, t.id, t.merchant, t.reference, t.amount, t.currency, t.direction, a.id, a.account_type, a.account_number`. No other columns.
  - **Detail SELECT:** `d.id, d.reason, d.description, d.status, d.customer_resolution_message, d.resolved_at, d.version, d.created_at, d.updated_at, t.id, t.merchant, t.reference, t.amount, t.currency, t.direction, a.id, a.account_type, a.account_number`. No other columns; `assigned_agent_id`, `assigned_at`, `internal_resolution_notes` excluded.
  - **Event SELECT:** `dispute_version, event_type, customer_message, created_at`. No other columns; `internal_notes`, `actor_id`, `previous_agent_id`, `new_agent_id` excluded.
  - **Status predicate from `DisputeQuery.StatusPredicate`:**
    - `NONE` (ALL) → no status clause.
    - `INDIVIDUAL` → `d.status = :status`.
    - `IN_LIST` with `['OPEN','UNDER_REVIEW']` → `d.status IN ('OPEN','UNDER_REVIEW')`.
    - `IN_LIST` with `['ACCEPTED','REJECTED']` → `d.status IN ('ACCEPTED','REJECTED')`.
  - `accountId` supplied: if no row matches ownership + accountId → throw `ResourceNotFoundException`.
  - Adapter derives `displayName` from `account_type`; derives `maskedNumber` via `AccountNumberMask.mask(account_number)`. Raw values never assigned to DTO fields.

2.3 Create `DisputeQueryParser` in `dispute/service/`:
  - Accepts raw `status` string; translates:
    - `ALL` or absent → `StatusPredicate.NONE`
    - `ACTIVE` → `StatusPredicate.IN_LIST(['OPEN','UNDER_REVIEW'])`
    - `RESOLVED` → `StatusPredicate.IN_LIST(['ACCEPTED','REJECTED'])`
    - `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED` → `StatusPredicate.INDIVIDUAL`
    - anything else → throw `ValidationException` (400/1004)
  - Rejects unknown parameters, duplicate values, non-UUID `accountId`, reversed date ranges, invalid page/size, offset overflow.
  - Normalizes search: trim, collapse whitespace, ≤ 100 chars. Strips `DSP-` prefix (case-insensitive). Escapes `%`/`_`/`\` for ILIKE.
  - Date conversion: `LocalDate → Africa/Johannesburg → UTC` half-open interval.
  - Returns `DisputeQuery`; throws `ValidationException` on any failure.

2.4 PostgreSQL integration tests (Testcontainers, not H2):
  - `status=ACTIVE` returns only OPEN+UNDER_REVIEW disputes; ACCEPTED/REJECTED excluded.
  - `status=RESOLVED` returns only ACCEPTED+REJECTED; OPEN/UNDER_REVIEW excluded.
  - Individual values (`OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`) still work.
  - Unknown `status` value → `400/1004`.
  - `accountId` selector: owned narrows correctly; non-owned → 404; malformed → 400.
  - Reference search: `DSP-5F9868E0`, `dsp-5f9868e0`, `5F9868E0` (no prefix), `5F98` (partial) all match; `DSP-XXXXXXXX` returns empty.
  - Reason filter, date filter (Johannesburg boundary), search across description/merchant/txReference.
  - Sort by `createdAt`, `updatedAt`, `status`; ASC/DESC; UUID tie-breaker.
  - Empty page returns total correctly.
  - Repository SQL assertion: `assigned_agent_id`, `assigned_at`, `internal_resolution_notes`, `actor_id`, `previous_agent_id`, `new_agent_id`, `internal_notes` absent from all SELECT lists.

### Verification
- All tests pass against real PostgreSQL.
- No H2 dependency.
- No prohibited column in any SELECT list or DTO.
- `CustomerAccountSummary` has `displayName` and `maskedNumber`; no `accountNumber`.

---

## Task 3 — Backend service and controller

**Satisfies:** DSH-01, DSH-02, DSH-05, design §§4 and 5

### Subtasks

3.1 Create `DisputeQueryService`:
  - `@Transactional(readOnly = true)`.
  - `getCustomerDisputePage(DisputeQuery)`: calls repository list.
  - `getCustomerDisputeDetail(UUID disputeId, UUID customerId)`: calls repository detail, fetches events, calls mapper, builds detail DTO.

3.2 Create `CustomerDisputeController`:
  - `GET /api/v1/disputes` — `@AuthenticatedActivity`, parser + service.
  - `GET /api/v1/disputes/{id}` — `@AuthenticatedActivity`, UUID path validation.
  - `customerId` from `SessionPrincipal` only.

3.3 Add Spring Security matchers (before `anyRequest().denyAll()`):
  ```java
  .requestMatchers(HttpMethod.GET, "/api/v1/disputes", "/api/v1/disputes/**")
      .hasRole("CUSTOMER")
  ```

3.4 HTTP integration tests:
  - Anonymous → `401/1001`; AGENT → `403/1002`.
  - Owned disputes → `200`; non-owned detail → `404/1003`; malformed UUID → `400/1004`.
  - `accountId` on detail endpoint → `400/1004`.
  - Unknown query param → `400/1004`; non-owned `accountId` → `404/1003`.
  - `status=ACTIVE` → HTTP 200, items contain only OPEN/UNDER_REVIEW.
  - `status=RESOLVED` → HTTP 200, items contain only ACCEPTED/REJECTED.
  - `status=BADVALUE` → `400/1004`.
  - DB failure → `503/3002`.
  - **Serialized body scan:** assert no key named `internalResolutionNotes`, `assignedAgent`, `assignedAt`, `internalNotes`, `actorId`, `previousAgentId`, `newAgentId` anywhere in the JSON.
  - **Account context:** `transaction.account` has `id`, `displayName`, `maskedNumber`; no `accountNumber`.
  - `@AuthenticatedActivity` renews for CUSTOMER; not for anonymous/AGENT/rejected.

### Verification
- Full HTTP suite passes.
- Security matchers correct.
- CI passes; no regressions.

---

## Task 4 — Angular dispute models, API service and routes

**Satisfies:** DSH-07, DSH-08, design §§6.1, 6.5, 6.6

### Subtasks

4.1 Create `dispute-models.ts` (as specified in design §6.5). Note that `DisputeListQuery` has no `accounts` field; that marker is URL-only and stripped before the HTTP call.

4.2 Create `dispute-api.ts` (`list` and `get` methods as in design §6.6).

4.3 Register lazy routes (list and detail).

4.4 Unit-test `DisputeApi` with `HttpTestingController`: correct URLs, envelope handling.

4.5 Extend `account-selector` with `@Input() showAllAccounts = false`. When `true`, prepend an "All your accounts" option. Emits `selectionChange(null)` for the all-accounts choice. Add component tests: with `showAllAccounts=false` (default) no extra option; with `true` first option is "All your accounts"; selecting it emits `null`; selecting an account emits the account id.

### Verification
- Routes navigable; no import errors.
- `DisputeApi` tests pass.
- `account-selector` tests pass; existing consumer behaviour unchanged.
- No `any`.

---

## Task 5 — Angular dispute list component

**Satisfies:** DSH-07, DSH-09, DSH-10 (navigation), design §6.2

### Subtasks

5.1 Create `disputes.ts`, `disputes.html`, `disputes.scss`.

5.2 Implement state model: tab signal (`ALL`/`ACTIVE`/`RESOLVED`), form controls (`search`, `statusDropdown`, `datePreset`, `fromDate`, `toDate`), account mode (`account`|`all`), `switchMap` + generation guard, 300 ms debounce.

5.3 Implement account selection logic: no-params → resolve primary → `history.replace(?accountId=<id>)`; `?accounts=all` → all-accounts mode; `?accountId=<id>` → single-account mode; switching emits push navigation. Not-owned accountId → "Account not available" state.

5.4 Implement effective status resolution before API call (as per design §6.2 table).

5.5 Implement date preset computation (Africa/Johannesburg): `last7`, `last30`, `custom`, `any`.

5.6 Implement Reset filters: clears search, statusDropdown, datePreset, fromDate, toDate, tab; retains account selection; resets page to 1.

5.7 Template:
  - Eyebrow "DISPUTES", `<h1>` "Your disputes", subtitle.
  - Filter bar: search field (placeholder "Case ID or merchant"), Status dropdown, Created date preset; Reset filters button.
  - Tabs (All / Active / Resolved): `role="tablist"` with arrow key navigation and accurate per-tab counts for the active account/search/date scope.
  - `<table>` (desktop): columns Case/merchant, Amount, Status, Created, Details.
  - Mobile cards: same data stacked, "View case" tap target.
  - Status chip component: icon + text label per DSH-09 status chip table.
  - "View case" secondary-button link per row.

5.8 Component tests (`disputes.spec.ts`):
  - Tabs: clicking "Active" sets `status=ACTIVE` in API call; "Resolved" → `RESOLVED`; "All" → no status param.
  - Status dropdown overrides tab when set to individual value.
  - Reset filters clears all except account.
  - `last7` preset sets `fromDate` = today−6 in Johannesburg, `toDate` = today.
  - `accounts=all` URL → all-accounts view (no `accountId` sent to API).
  - Switching "All your accounts" → pushes `?accounts=all`.
  - Switching to a specific account → pushes `?accountId=<id>`.
  - Back/forward restores URL state.
  - Stale response discarded.
  - Row renders merchant (bold), `DSP-…` + reason label, amount, status chip with icon, created date, "View case" link.
  - Status chip shows `inbox` icon for Received, `clock` for Under review, `check-circle-2` for Accepted, `x-circle` for Rejected.
  - `formatDisputeRef('5f9868e0-1272-47ee-b0d2-ef33e4e1d331')` === `'DSP-5F9868E0'`.
  - All five `formatReason` values correct.
  - Empty state shown when `items` is empty.
  - 503 → manual retry; 401 → `clearIdentity()`.
  - Non-owned `accountId` → "Account not available".

### Verification
- All component tests pass.
- No `any`.
- Status chip contrast ≥ 4.5:1 verified in comments.

---

## Task 6 — Angular dispute detail component

**Satisfies:** DSH-08, DSH-09, design §6.4

### Subtasks

6.1 Create `dispute-detail.ts`, `dispute-detail.html`, `dispute-detail.scss`.
  - `displayRef` computed signal.
  - `timelineLabel(entry, index, all)` pure helper.
  - `backLink` from allowlisted `returnTo` query param.

6.2 Template (see design §6.4 layout):
  1. Back link.
  2. Case reference label.
  3. `<h1>` "Dispute details".
  4. Subtitle.
  5. Outcome banner (ACCEPTED: green, with no-refund note; REJECTED: red, no no-refund note).
  6. Two-column card layout.

6.3 Transaction card: "DISPUTED TRANSACTION" label, merchant, signed amount, divider, `<dl>` with Account (`displayName · maskedNumber`), Transaction date/time, Reason, Submitted, "What you told us".

6.4 Timeline card: `<ol>` entries with `timelineLabel()` labels, icons per DSH-09, dates, messages. Locked-record or in-progress note.

6.5 Error states: skeleton (`aria-busy`), 404, 401, 503.

6.6 Component tests (`dispute-detail.spec.ts`):
  - `displayRef` correct.
  - `timelineLabel`: 1st `IN_REVIEW` → "Review began"; 2nd `IN_REVIEW` after `RETURNED_TO_QUEUE` → "Review resumed"; `SUBMITTED` → "Dispute submitted"; `RETURNED_TO_QUEUE` → "Returned to the queue"; `RESOLVED` ACCEPTED → "Dispute accepted"; `RESOLVED` REJECTED → "Dispute rejected".
  - ACCEPTED banner present (green tint), includes no-refund note.
  - REJECTED banner present (red tint), no no-refund note.
  - OPEN → no banner.
  - Account field shows "Main Account · ****1467".
  - Locked-record note for ACCEPTED; in-progress note for OPEN.
  - 404 → "Dispute not found"; 401 → `clearIdentity()`; 503 → retry.
  - `backLink` default `/customer/disputes`; allowlisted `returnTo` respected.

### Verification
- All component tests pass.
- No-refund note contrast ≥ 4.5:1.
- No internal/agent data rendered.

---

## Task 7 — Navigation wiring and account dashboard shortcut

**Satisfies:** DSH-07.10, DSH-10, design §6.7

### Subtasks

7.1 Add "Disputes" link to `/customer/disputes` in the customer header (active on `/customer/disputes/**`).

7.2 Add "View Disputes" shortcut to the account dashboard quick-links panel.

7.3 Remove any existing "coming soon" dispute placeholder.

7.4 Tests: "Disputes" in CUSTOMER nav; "View Disputes" in quick-links; neither in AGENT nav.

### Verification
- Navigation tests pass.

---

## Task 8 — Documentation and UI flows update

**Satisfies:** design integrity

8.1 Update `docs/design/plantuml/customer-ui-flow.puml` to include disputes history and detail routes, and note All / Active / Resolved tabs.

8.2 Update `docs/design/plantuml/frontend-navigation.puml`: change "Dispute history" and "Customer dispute detail" boxes from dashed to solid.

8.3 Update `docs/design/plantuml/customer-history.puml` if sequence steps differ.

8.4 Update `docs/design/api-contract.md` §3 capability matrix: add `ACTIVE` and `RESOLVED` to the `status` filter column for `GET /disputes`. Update §8 with a detail response example including `account.displayName`.

8.5 Update `docs/design/ui-flows.md` §5 ("Customer journey B"): replace the old `My Disputes` / dropdown description with the tab-based UI, date preset, Reset filters, row layout and "All your accounts" selector option, consistent with the spec. Remove the old "My Disputes" label; use "Disputes".

### Verification
- PlantUML parses without error.
- API contract example consistent with implemented DTO.
- `ui-flows.md` §5 no longer contradicts this spec.

---

## Current implementation handoff (2026-10-12)

- Tasks 0–4 were present in the working tree before this continuation; their existing backend/API changes and schema check are retained.
- Tasks 5–8 now have implementation work in the working tree, including customer list/detail screens, navigation, dashboard shortcut and documentation updates.
- Frontend production build passes. Angular test bundle compiles, but Karma could not launch because ChromeHeadless is not installed in this environment. Backend test sources compile with `mvn -q -DskipTests test-compile`; full backend integration tests have not yet been rerun for this continuation.
- Keep the task status open until browser tests, backend tests and a manual responsive review have been completed.

## Definition of done

This feature is complete when:

- [x] Task 0 pre-check passed; schema confirmed before any code was written.
- [-] All tasks 1–8 marked complete with their verification criteria satisfied.
- [x] `mvn verify` passes with no new test failures.
- [x] `ng test --watch=false` passes in the connected Chromium test browser.

**Final verification (2026-10-12):** `mvn verify` passed (205 tests, 0 failures/errors); `ng build` passed; Karma frontend suite passed (108 specs, 0 failures) using the connected Chromium browser because this environment has no local browser binary. No customer-history code changed after these checks.
- [~] No `any` in Angular TypeScript without a justification comment.
- [~] Serialized HTTP response bodies assert absence of `internalResolutionNotes`, `assignedAgent`, `assignedAt`, `internalNotes`, `actorId`, `previousAgentId`, `newAgentId`, `accountNumber`.
- [~] `status=ACTIVE` and `status=RESOLVED` covered in HTTP tests.
- [~] Both endpoints covered in `docs/design/api-contract.md`.
- [~] Customer header shows "Disputes"; dashboard shows "View Disputes".
- [~] All / Active / Resolved tabs with counts, date preset, Reset filters, "All your accounts" option, unsigned list amounts, row layout, mobile cards, status chip icons all render and behave correctly.
- [~] ACCEPTED banner has no-refund note; REJECTED banner does not.
- [~] `ui-flows.md` §5 updated and consistent with this spec.
