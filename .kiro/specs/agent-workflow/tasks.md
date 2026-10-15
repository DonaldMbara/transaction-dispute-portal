# Agent Case Portal — Tasks

**Status:** Proposed for review  
**Requirements:** [requirements.md](requirements.md)  
**Design:** [design.md](design.md)

Tasks are ordered and independently verifiable. Implementation starts only after the requirements and design are approved, following `.kiro/steering/spec-workflow.md`.

## 0. Prerequisite gate

- [x] 0.1 Confirm `customer-dispute-history` has completed implementation and verification, as required by the approved Kiro feature order.
- [x] 0.2 Verify V7 creates the `disputes` and `dispute_events` fields and constraints referenced by the design. Do not edit applied migrations; document any schema gap and seek approval before proposing an additive migration.

**Schema verification note (2026-10-12):** `V7__create_disputes_and_idempotency.sql` provides the required dispute lifecycle columns (`assigned_agent_id`, `assigned_at`, `internal_resolution_notes`, `customer_resolution_message`, `resolved_at`, `version`), allowed lifecycle statuses and consistency check, version non-negativity, transaction uniqueness, and query indexes. `dispute_events` provides `dispute_version`, all five required event types, before/after status and assignee fields, internal/customer text, creation timestamp, unique `(dispute_id, dispute_version)`, and the initial CREATED event constraint. No schema gap was found; no migration edit is needed.

**Acceptance:** both dependency checks are recorded before implementation tasks begin.

## 1. Agent domain lifecycle

- [x] 1.1 Replace the initial-state-only dispute representation with an invariant-preserving lifecycle aggregate while retaining current creation behavior and V7 mapping compatibility.
- [x] 1.2 Add explicit claim, release, and resolve transitions with actor/assignment checks, bounded required text, terminal immutability, and exactly-one version increment.
- [x] 1.3 Add immutable versioned audit event model/factory for CLAIMED, RELEASED, RESOLVED_ACCEPTED, and RESOLVED_REJECTED.
- [x] 1.4 Add domain tests for valid transitions, invalid-state/assignment cases, note boundaries, retained terminal assignment, version changes, and immutable terminal state.

**Prerequisite verification (2026-10-12):** the preceding customer-dispute-history feature is implemented and its DoD is verified: `mvn verify` (205 tests passed), `ng build` passed, and the Karma suite passed (108 specs, 0 failures). The V7 schema review found all required columns, lifecycle constraints and versioned event constraints. Agent lifecycle domain tests pass (5 tests).

**Acceptance:** domain tests prove all state invariants; existing submission tests continue to prove OPEN/version-zero creation.

## 2. Agent persistence and atomic mutations

- [ ] 2.1 Add agent-scoped dispute/event repository contracts and persistence adapters without exposing JPA entities outside the repository package.
- [ ] 2.2 Implement conditional/locked claim, release, and resolve writes guarded by current version, state, and assignee; do not add process-local locks.
- [ ] 2.3 Persist the state change and matching versioned audit event in the same transaction; translate lost writes and invalid current state into the documented conflict types.
- [ ] 2.4 Add PostgreSQL integration tests for claim/release/resolve persistence, audit rollback, version/event ordering, terminal constraints, and two same-tag competing writes (one success, one stale conflict).

**Acceptance:** mutation and audit always commit or roll back together; race tests produce at most one state transition and event.

## 3. Agent queue query and transport

- [ ] 3.1 Add immutable agent list/detail/event DTOs and explicit mappers; verify masked account context and separate customer/agent visibility.
- [ ] 3.2 Implement AGENT-only `GET /api/v1/agent/disputes` with unassigned/mine/all predicates, status/reason/date/search filters, scoped counts, stable allowlisted sorting, and pagination.
- [ ] 3.3 Implement AGENT-only detail GET and strong deterministic ETag representation per API contract.
- [ ] 3.4 Add integration/HTTP tests for query bounds, combined predicates, queue semantics (including terminal Mine), scoped totals, pagination, status sorting, missing case, roles, envelopes, no-store/correlation headers, ETag determinism, and absence of full account numbers.

**Acceptance:** queue totals and rows use identical predicates; My Cases includes retained terminal assignments; customer DTOs remain private-field safe.

## 4. Agent mutation endpoints and concurrency contract

- [ ] 4.1 Add claim/release/resolve controller adapters, typed validated requests, If-Match parser, and standard response mapping.
- [ ] 4.2 Implement documented precondition precedence and success response with refreshed detail/new ETag; reject unknown command fields and caller-supplied actor identity.
- [ ] 4.3 Add HTTP/security tests for 401/1001, 403/1002 role denial, 403/1010 CSRF denial, 428/2007 missing, 400/1004 malformed, 412/2006 stale/wrong-resource/lost race, 409/2005 current-tag state/assignment conflict, validation, response headers, and no effects on rejected requests.
- [ ] 4.4 Verify audit/service failure returns safe 503/3002 and never leaks stack/SQL or a partial state transition.
- [ ] 4.5 Verify eligible interaction handlers renew strict idle activity only after security admission; rejected and background requests do not renew it.

**Acceptance:** every error/status matches the canonical API contract; successful mutations return the committed agent-detail representation and current strong ETag.

## 5. Angular agent queue

- [ ] 5.1 Replace the placeholder agent page with lazy `/agent/disputes` queue route and add `/agent/disputes/:id` route using existing role-aware shell/navigation.
- [ ] 5.2 Add typed agent DTO/query contracts and an API service for list/detail/mutations, including opaque ETag retention and If-Match propagation.
- [ ] 5.3 Implement Unassigned / My Cases / All Disputes tabs, URL-backed filters/search/sort/page state, filter reset, counts, and stale-response cancellation.
- [ ] 5.4 Build desktop case table and responsive mobile cards using shared controls, buttons, status treatments, and design tokens; retain all actions without hover or overflow.
- [ ] 5.5 Add Angular tests for query encoding, history navigation, tab/filter interactions, sorting, pagination, counts, actions by assignment/status, loading/error/empty states, keyboard use, and responsive semantic content.

**Acceptance:** queue is usable at desktop and narrow widths, accurately reflects server queue semantics, and exposes no mutation on stale list data.

## 6. Angular case investigation and resolution

- [ ] 6.1 Render transaction, masked account, customer submission, permitted agent timeline/notes, assignment, and terminal outcome using agent-specific DTOs.
- [ ] 6.2 Implement claim with current ETag and pending/success/error states.
- [ ] 6.3 Implement release reason and confirmation form with client bounds and safe conflict recovery.
- [ ] 6.4 Implement decision, internal notes, and customer message form with independent validation/visibility labels and explicit confirmation.
- [ ] 6.5 Implement 409/412 detail refresh while preserving unsent form text; require manual review after ambiguous outcomes; never auto-replay with a fresh tag.
- [ ] 6.6 Add Angular tests for field validation, field privacy labels, action visibility, ETag lifecycle, conflict handling, duplicate-submit prevention, terminal read-only states, and customer-safe final decision wording.

**Acceptance:** only current assignee can see mutation controls; customer and internal text remain separated; mutations require a fresh user-reviewed state after a conflict.

## 7. Figma alignment and feature verification

- [ ] 7.1 Compare desktop queue, mobile queue, desktop detail, mobile detail, and static state screens against supplied Figma screenshots; record any intentional business/privacy deviations.
- [ ] 7.2 Verify WCAG AA text contrast, keyboard focus, form semantics, reduced motion, loading/error/empty/conflict states, and screen-reader announcements.
- [ ] 7.3 Run backend tests and frontend build/tests; validate Flyway on PostgreSQL and inspect API contracts for customer/agent DTO separation.
- [ ] 7.4 Update `docs/design/ui-flows.md`, `docs/design/api-contract.md`, and relevant README/run instructions only where implementation evidence or approved contract changes require it.

**Acceptance:** automated evidence passes; manual visual review records the screenshot comparison and approved deviations; documentation describes implemented behavior without claiming unverified controls.

## Dependencies

- Task 0 blocks all implementation tasks.
- Task 1 precedes tasks 2–4.
- Task 2 precedes mutation endpoints in task 4.
- Task 3 is read-only and may proceed after task 0 independently of tasks 1–2; queue/detail reads are prerequisites to frontend tasks 5–6.
- Task 5 depends on task 3; task 6 depends on tasks 3 and 4.
- Task 7 depends on tasks 1–6.

No task is marked complete before implementation and its acceptance checks have actually run.
