# Agent Case Portal — Requirements

**Status:** Proposed for review  
**Feature:** Agent Workflow  
**Scope:** Agent case queue and case investigation, claim, release, and resolution  
**Design references:** [Business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [UI flows](../../../docs/design/ui-flows.md), [database design](../../../docs/design/database-design.md), and the agent queue/detail/static-state screenshots supplied in the request.

## 1. Purpose

Give authenticated dispute agents a safe, auditable workspace to find cases, take ownership, investigate customer submissions, return cases to the shared queue, and make a final decision. The supplied Figma captures guide layout and responsive presentation. Business rules, privacy requirements, and the canonical API contract determine behavior when they differ from a screenshot.

## 2. Actors and scope

- **Agent:** authenticated user with the `AGENT` role. May read permitted agent cases; may claim an open unassigned case; only the currently assigned agent may release or resolve an `UNDER_REVIEW` case.
- **Out of scope:** supervisor workflows, reassignment, SLA/escalation, attachments, customer messaging/notifications, refunds or payment reversals, customer/account administration, bulk decisions, and new roles or identity providers.

The feature builds on the existing dispute tables, initial `OPEN` dispute state, customer submission, session authentication, and role-aware application shell. It does not change customer endpoints or customer eligibility.

## 3. User stories and acceptance criteria

### AGW-01 — Open a permitted case queue

As an agent, I want to see cases according to queue ownership so I can work available cases and review my prior decisions.

1. **When** an authenticated `AGENT` opens the agent workspace, **the system shall** show Unassigned, My Cases, and All Disputes queues with counts scoped to the same search/filter criteria.
2. **When** the Unassigned queue is selected, **the system shall** show only `OPEN` cases with no assignee.
3. **When** My Cases is selected, **the system shall** show cases assigned to the current agent, including terminal cases whose resolving assignment is retained for accountability.
4. **When** All Disputes is selected, **the system shall** show all cases permitted to agent users.
5. **When** a request has no valid current session, an agent endpoint shall return `401/1001`; **when** an authenticated non-agent requests an agent endpoint, it shall return `403/1002`, using the established envelope before exposing case data.
6. The UI shall provide loading, retryable error, and empty states, and shall not turn API failures into an empty queue.
7. Agent queue/detail reads and explicit claim/release/resolve submissions are eligible authenticated interactions for strict-idle activity only after the request has passed current-session, enabled-account, AGENT-role, and (for unsafe methods) CSRF checks. Background refresh, prefetch, polling, and rejected requests shall not renew activity.

### AGW-02 — Find, filter, sort, and page cases

As an agent, I want to locate cases using safe case and transaction fields.

1. The queue shall support bounded case-ID/merchant/reference/description search and the contract’s status, reason, and created-date filters; filters shall intersect the chosen queue. Search shall accept the derived `DSP-<first 8 uppercase UUID hex>` reference prefix as the customer dispute search does, alongside UUID text and the documented submission/transaction text fields. Status values are `ALL`, `ACTIVE`, `RESOLVED`, `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, and `REJECTED`; `ACTIVE` means OPEN or UNDER_REVIEW, and `RESOLVED` means ACCEPTED or REJECTED. Reason values are the supported `DisputeReason` values plus `ALL`. Date bounds are calendar dates in `Africa/Johannesburg`, with inclusive start and exclusive day-after end converted to UTC.
2. Search shall be literal, case-insensitive and trimmed/collapsed as specified by the API contract, with a maximum normalized length of 100 characters. It shall not search internal notes, agent identity, or account numbers.
3. The queue shall support allowlisted `createdAt`, `updatedAt`, and `status` sorting, deterministic UUID tie-break ordering, and 1-based pagination (default page 1, size 20, maximum size 100). The API shall return HTTP 200 with `{status,data,error_code}` and `data:{total,page,size,items}` for successful reads.
4. **When** query state changes, **the UI shall** reset to page 1, preserve other valid filters, reflect state in the URL, and discard stale responses.
5. Counts and page totals shall be computed only over cases visible to the AGENT role and matching the request predicates. The API exposes one total per queue request; the UI derives visible tab counts from scoped requests and never labels a count as a total for a broader scope than its filters.
6. The UI shall expose keyboard-operable sort controls with `aria-sort`, visible focus, and a textual current-sort summary.

### AGW-03 — Review case and transaction context

As an agent, I want enough submitted and transaction context to make a sound decision.

1. Agent detail shall include the dispute reference, dispute status, customer submission reason/description, ZAR amount, merchant, transaction reference/date/direction, masked account context, created/resolved timestamps, permitted assignment data, customer-facing final message when resolved, and the ordered permitted audit events. Persisted internal resolution notes and release reasons may be shown in authorized agent views only. Do not add bank transaction status to the agent detail payload; it is not part of the current agent DTO contract or a separate agent business rule.
2. The agent view shall use a masked account number only (`****` plus the last four digits when valid, otherwise `****`). It shall never expose full account numbers. Queue amounts shall use the positive magnitude (`R 0.00`); detail amounts shall include the transaction-direction sign, as in the canonical UI flow.
3. Internal investigation notes and audit actor/assignment metadata shall be available to authorized AGENT views, but never leak through customer DTOs or errors.
4. **When** a case is missing, **the backend shall** return `404/1003`; the backend shall not expose case details to customer users.
5. An accepted dispute shall not be described as a refund, reversal, or settled payment.

### AGW-04 — Claim an open case

As an agent, I want to claim an available case so the system assigns it to me atomically.

1. **When** an `OPEN`, unassigned case is claimed with the current strong detail ETag, **the system shall** set the current authenticated agent as assignee, set `assigned_at`, change status to `UNDER_REVIEW`, increment the aggregate version once, and append one `CLAIMED` audit event in the same database transaction.
2. Caller-supplied agent IDs shall not be accepted; identity shall come from the validated session principal.
3. **When** two agents claim with the same version, **the system shall** commit one claim and return `412/2006` to the lost-write request, without a second event.
4. A claim against a fresh version that is not claimable shall return `409/2005`.
5. After a successful claim, the UI shall display the refreshed case and ETag; it shall not silently retry a failed claim.

### AGW-05 — Release a case back to the queue

As the assigned agent, I want to release a case when I cannot continue reviewing it.

1. **When** the currently assigned agent releases an `UNDER_REVIEW` case with a valid current ETag and a nonblank reason of 1–2000 trimmed characters, **the system shall** move it to `OPEN`, clear the active assignment according to the canonical API contract, increment the version once, and append one `RELEASED` event atomically.
2. The release reason shall be visible only in agent audit data and shall not appear in customer APIs or timelines.
3. **When** another agent attempts release, the case is terminal, or the case is not under review with a matching current ETag, **the backend shall** return `409/2005`; stale version remains `412/2006`. Non-agents are denied with `403/1002` and invalid CSRF with `403/1010` before any mutation.
4. The UI shall require confirmation and reason, explain that release returns the case to the shared queue, and preserve the reason if a safe conflict recovery is needed.

### AGW-06 — Resolve a case

As the assigned agent, I want to record a final outcome with an internal rationale and a separate customer-safe explanation.

1. **When** the assigned agent resolves an `UNDER_REVIEW` case using a current ETag, **the system shall** require `ACCEPT` or `REJECT`, internal notes (1–4000 trimmed characters), and customer message (1–2000 trimmed characters).
2. **When** accepted, **the system shall** persist terminal `ACCEPTED`; when rejected, terminal `REJECTED`; both set `resolved_at`, retain the resolving agent and assignment timestamp, increment version once, and append the corresponding resolution event atomically.
3. Accept/reject shall not initiate or claim any money movement. Customer-facing text is the only decision explanation returned to customer endpoints.
4. Terminal cases shall be immutable and read-only; no second decision, release, claim, or reopen is allowed.
5. The UI shall distinguish internal notes from customer-visible text, identify the visibility of each field, require confirmation, and prevent duplicate submissions while a mutation is in flight. Inputs are trimmed at their edges, preserve internal whitespace, and reject blank/over-limit values; unknown request fields are rejected by the backend.
6. **When** decision persistence succeeds, **the system shall** return the refreshed detail with a new strong ETag. The UI shall show the decision as read-only and the refreshed audit state.

### AGW-07 — Handle version conflicts and unavailable states

1. Agent list/detail GET and successful claim/release/resolve responses shall use HTTP 200, the standard envelope, `Cache-Control: no-store`, correlation ID, and strong aggregate ETag defined in the API contract. Detail and mutation response bodies shall be identical for the same committed version.
2. Claim/release/resolve shall use the exact parent detail ETag in `If-Match`. Missing precondition returns `428/2007`; malformed returns `400/1004`; stale tag or lost guarded-write race returns `412/2006`; matching-version invalid state/assignment returns `409/2005`.
3. **When** the UI receives `412` or `409`, **it shall** refresh case detail and ETag, explain the conflict, and preserve unsent notes/reason/message when safe. It shall never automatically replay the mutation with a fresh ETag.
4. **When** a response is lost or ambiguous, **the UI shall** require a refetch and audit/status inspection before the agent can decide what to do next.
5. Validation, authorization, conflicts, and infrastructure failures shall not expose SQL, stack traces, secrets, account numbers, or internal notes in non-agent responses.

### AGW-08 — Responsive, accessible agent workspace

1. The desktop queue shall use the application’s shared data table, filter controls, sort controls, status chips, buttons, tokens, and shell conventions wherever available.
2. At narrow widths, queue rows shall become readable case cards with the same key fields and actions; no action may depend on hover or horizontal overflow.
3. Detail shall use transaction/submission context and an action area that remains usable on desktop and stacks into a single column on smaller screens.
4. All controls shall have labels, keyboard support, visible focus, appropriate live announcements, and WCAG AA contrast. Reduced-motion preferences shall be honored.
5. Agent routes shall remain role-guarded for navigation, while backend authorization remains authoritative.

## 4. Domain and API invariants

- State machine: `OPEN` (unassigned) → `UNDER_REVIEW` (assigned) → `OPEN` (released) or terminal `ACCEPTED` / `REJECTED`.
- Only assigned agent can release or resolve; only `OPEN` unassigned cases can be claimed.
- Retain terminal assignee and `assigned_at`; My Cases therefore includes terminal cases.
- One successful lifecycle mutation increments the dispute version exactly once and writes exactly one matching versioned event atomically.
- Full ETag/If-Match and error precedence are fixed by `docs/design/api-contract.md` §6; this feature shall implement, not redefine, that contract.
- Use the existing session/CSRF/authorization model, API envelopes/error codes, synthetic data, date/timezone rules, and shared UI tokens.
- Apply the existing `@AuthenticatedActivity` admission only to the five user-initiated agent handlers (`GET` queue, `GET` detail, and `POST` claim/release/resolve); admission follows validated session and role checks, and unsafe handlers also follow CSRF validation. No background request may qualify.

## 5. Out of scope and screenshot corrections

- Do not display full account numbers from screenshots; show masked values only.
- Use the business vocabulary “Unassigned”, “My Cases”, and “All Disputes”, consistent with the approved UI flow. “My Cases” intentionally includes retained terminal assignments.
- Do not invent an “active-only” queue or alter the meaning of `queue=mine` to match the screenshot label “My active”.
- Do not add per-row claim mutation: the approved flow claims from detail to avoid acting on stale list data.
- Do not add a separate pre-decision note-taking operation; no such endpoint or persistence contract is defined. Show persisted internal event notes and terminal resolution notes where present, and collect mandatory internal notes in the resolution form.
- Do not change existing customer dispute creation/history, transaction data, dispute eligibility, or the no-refund semantics of `ACCEPTED`.

## 6. Dependencies and traceability

Depends on authentication/RBAC and CSRF, dispute submission and its V7 schema, transaction and account read data, the standard API envelope/error mapping, Angular `DashboardLayout`, shared buttons/tokens, and completion/verification of the preceding `customer-dispute-history` feature, per `.kiro/steering/spec-workflow.md` feature order. Trace to BR-A03–A05, BR-L02–L10, BR-H01–H04, `.kiro/steering/domain-rules.md`, `security.md`, `api-contracts.md`, `backend-standards.md`, `frontend-standards.md`, and `testing-standards.md`.

The supplied frames inform the agent queue and detail layout. Business/API and privacy rules above override screenshot-only labels, account-number exposure, or actions that conflict with approved flow. This requirements document does not claim a live Figma connector inspection.
