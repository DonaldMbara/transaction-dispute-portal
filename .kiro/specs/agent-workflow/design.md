# Agent Case Portal — Design

**Status:** Proposed for review  
**Requirements:** [requirements.md](requirements.md)  
**Canonical references:** [Business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md), and applicable [Kiro steering](../../steering/).

## 1. Design decisions

1. Keep case operations in the existing Spring Boot modular monolith and existing Angular agent feature. No new service, state library, database table, role, or external system is needed.
2. Reuse the existing `disputes` and append-only `dispute_events` tables from V7. Add indexes/migrations only if query profiling and an approved contract change show the existing indexes are insufficient.
3. Use a thin HTTP adapter, a focused use case/service layer, a dispute aggregate with explicit transition methods, repository ports/adapters, explicit request/response DTOs, and append audit events in the same transaction.
4. Keep agent DTOs separate from customer DTOs because the permitted fields differ. Do not return JPA entities.
5. Reuse the current shared presentation primitives and tokens. The Figma screenshots guide spacing and content order; mask account numbers, keep “My Cases” including terminal assignments, and keep claim on the detail page per approved business rules.

## 2. Boundaries and structure

### Backend additions

```text
backend/src/main/java/com/assessment/disputeportal/dispute/
├── controller/       # GET agent collection/detail and POST claim/release/resolve adapters
├── model/            # evolve Dispute into lifecycle-capable aggregate; add immutable event value
├── model/dto/        # agent query, claim/release/resolve request, list/detail/event DTOs
├── mapper/           # explicit agent-safe response projections
├── repository/       # dispute/event persistence contracts and JDBC adapter(s)
└── service/          # query, claim, release, resolve use cases and transaction boundaries
```

The exact class names should match existing module conventions. `DisputeSubmissionService` remains the creation use case; do not turn it into a catch-all service. Reuse the existing domain exception/envelope and `SessionPrincipal`. The repository adapter owns SQL/JPA mapping and conditional writes; services do not use persistence entities. Do not edit applied V1–V7 migrations; first verify the V7 columns/constraints needed by the workflow.

### Frontend additions

```text
frontend/src/app/features/agent/
├── agent.routes.ts             # lazy queue and /:id routes
├── disputes/                   # focused queue and detail feature components
│   ├── agent-dispute-api.ts    # typed HTTP/envelope and ETag handling
│   ├── agent-dispute-models.ts # agent-only DTOs and query types
│   ├── case-queue.*            # filters, tabs, table/cards, pagination
│   └── case-detail.*           # transaction/submission, timeline, release/resolve forms
└── agent.*                     # retain only if it remains a useful area shell
```

Use route-scoped components and a small API service. Keep query/selection state in the URL where specified; keep private unsent decision text only in component memory. Do not place notes, messages, or release reasons in query parameters or browser storage.

## 3. HTTP and DTO contract

Implement the pre-reserved paths exactly:

| Method/path | Access | Behavior |
|---|---|---|
| `GET /api/v1/agent/disputes` | AGENT | Filtered, scoped queue; `queue=unassigned\|mine\|all`; default `unassigned`; page/size, search, status, reason, dates, allowlisted sort |
| `GET /api/v1/agent/disputes/{id}` | AGENT | Agent-safe detail plus strong ETag |
| `POST /api/v1/agent/disputes/{id}/claim` | AGENT | Empty command body; `If-Match`; only OPEN/unassigned |
| `POST /api/v1/agent/disputes/{id}/release` | Assigned AGENT | `{reason}`; `If-Match`; only own UNDER_REVIEW case |
| `POST /api/v1/agent/disputes/{id}/resolve` | Assigned AGENT | `{decision, internalResolutionNotes, customerResolutionMessage}`; `If-Match`; only own UNDER_REVIEW case |

Use the canonical `{status,data,error_code}` envelope and `{total,page,size,items}` collection. Queue responses add `assignedAgent:{id,email}|null` and `version` to the agent list fields. The detail DTO adds customer submission description, masked transaction/account context, assignment and resolution timestamps, internal resolution notes, customer resolution message, version, and ordered permitted audit events. Event fields follow `docs/design/api-contract.md` §4; include internal fields only for AGENT authorization. Event and assignment IDs are allowed in this trusted agent representation but must not be reused in customer DTOs. Internal event notes mean release reasons and terminal resolution notes already represented by the audit contract; there is no separate save-investigation-note operation in this MVP.

Query semantics, bounds, search normalization, status/reason enums, filters, deterministic ordering, date boundaries in `Africa/Johannesburg`, error codes, response headers, and strong ETag byte-representation semantics are inherited from `docs/design/api-contract.md` §§2–3 and 6. Collection authorization is AGENT-only. Do not add `accountId` to agent queues. Counts and totals use the same combined predicates as rows.

Mutations use the exact strong ETag from the parent detail GET in `If-Match`; no `expectedVersion` body field. Missing → `428/2007`, malformed → `400/1004`, stale/wrong-resource/lost guarded write → `412/2006`, matching-version business conflict → `409/2005`; missing/invalid session → `401/1001`, role denial → `403/1002`, CSRF denial → `403/1010`. Return HTTP 200, the updated detail DTO and new ETag after commit. Use `Cache-Control: no-store` and correlation IDs.

## 4. Domain and transactional flow

The current `Dispute` record validates only the initial OPEN/version-zero state. Refactor it into a lifecycle-capable aggregate (or introduce an equally explicit lifecycle model) with invariant-preserving `claim`, `release`, and `resolve` transitions. Preserve creation invariants and V7 persistence compatibility.

Each mutation sequence:

1. Validate session role/CSRF and resolve the dispute for the authenticated agent.
2. Parse the required If-Match syntax and compare the complete representation version.
3. Apply the state and assignee precondition in the write itself (conditional update or lock plus `@Version`); do not rely on an earlier read alone.
4. Change exactly one state/version and append exactly one versioned audit event using the same authenticated actor.
5. Commit both together; only then return updated detail and new ETag.

Use database-transaction rollback for audit append failures. Translate a lost write after successful precondition validation into `412/2006`; a current-tag invalid status/assignment is `409/2005`. Keep terminal assignee/timestamp, notes, decision message, and event history immutable. Do not auto-retry within the service. Avoid process-local locks.

Collection read flow uses one authorization-scoped query/projection with allowlisted sort and a stable UUID tie-break. Apply queue predicate, then status/reason/date/search filters, then total, ordering, and pagination. `unassigned` implies OPEN/null assignee; `mine` uses the session agent ID and includes retained terminal assignments; `all` applies no assignment predicate. The API default is unassigned. UI tabs use explicit queue values and counts. Since the contract returns one total per request, fetch tab totals with the same filters and `page=1,size=1`; do not introduce a grouped-count endpoint.

## 5. Frontend flows and behavior

### Queue `/agent/disputes`

- Use the existing dashboard layout and role-specific “Agent area” navigation.
- Header/intro follows screenshot hierarchy: “Agent workspace · [agent email]”, “Case queue”, and concise explanatory copy. The current identity contract provides email but no display name; do not invent or infer one.
- Tabs: Unassigned, My Cases, All Disputes. The list endpoint reports a total for one queue at a time, so fetch each tab count with the same search/status/reason/date filters and `page=1,size=1`; combine with the visible queue page request, cancel/discard stale responses together, and keep totals scoped. My Cases includes retained terminal cases.
- Filter panel: case ID or merchant search, status, reason, created date, Reset filters. Assignment is represented by the queue tabs and assignee column; the existing API contract does not define an assignee filter, so do not add one here.
- Desktop table: derived case reference `DSP-<first 8 uppercase UUID hex>` (the API's `id` remains the UUID), merchant, positive amount, reason, status, created date/time, assignee, action. Use the contract's four visible status labels (Received, Under review, Accepted, Rejected). Only `createdAt`, `updatedAt`, and `status` are sortable because those are the API allowlist; show the current sort and direction in the same summary style as customer lists. Agent identity is the email in the existing response/identity contract; no display-name field or agent-directory endpoint exists.
- Action: “Review case” for OPEN/unassigned, navigating to detail where “Claim case” is available; “Investigate” for a case assigned to the current agent and UNDER_REVIEW; “View case” for other-agent assignments and terminal records. Queue actions navigate to detail; they do not mutate state from a potentially stale list row.
- At narrow widths, render case cards with equivalent fields and primary action; preserve pagination and filters, no horizontal table overflow.
- Query state uses URL parameters: queue, search, status, reason, fromDate/toDate, sortBy/sortDirection, page, size. Reset filters clears search/status/reason/dates and resets page while retaining selected queue and sort.

### Case detail `/agent/disputes/:id`

- Back link returns to the case queue preserving allowlisted query parameters.
- Main content: transaction summary (merchant, signed amount, date/time in `Africa/Johannesburg`, reference, account display name and masked number); customer submission (reason, plain-text description and submitted date); permitted internal event history and, when resolved, internal resolution notes; side action rail on desktop, stacked on mobile. The current agent detail contract does not include bank transaction status; do not add a new field just to reproduce the screenshot's status line. Order events by `disputeVersion ASC, createdAt ASC`. The existing contract has no independent pre-decision note-taking endpoint, so do not render an editable investigation-notes composer or invented note entries.
- OPEN: Claim control, current ETag required, confirmation optional for the low-impact ownership action; success reflects assignment/version.
- UNDER_REVIEW assigned to current agent: Release requires a reason and confirmation; Resolve requires ACCEPT/REJECT, internal notes, customer-visible message, and explicit confirmation.
- UNDER_REVIEW assigned to another agent: clear read-only explanation; no claim/release/resolve controls.
- ACCEPTED/REJECTED: terminal read-only case with customer message and retained resolving-agent identity visible only to agents. Explain ACCEPTED is an upheld dispute decision only; no refund/reversal is performed.
- Keep unsent forms in memory through 409/412 refresh; clearly state data will be preserved. Do not retry an unsafe command automatically.
- All release-reason, internal-resolution-note, and customer-message fields are plain text with specified bounds, labelled distinctly by visibility, trimmed at the edges while preserving internal whitespace, and never inserted as HTML. Map the CREATED event actor to “Customer”, the current agent's events to “You”, and other agent actors to “Agent”; no display-name/directory contract exists.

## 6. Security, privacy, accessibility, and resilience

- Backend role and current enabled-account checks authorize all agent endpoints. Current agent identity comes only from the validated session principal.
- Customer-facing controller/query mappers remain separate and redact actor IDs, assignment, internal notes, release reasons, and internal audit fields.
- Mask account number; never reveal synthetic account balance in case detail unless a future approved requirement establishes it as necessary. It is unrelated to the decision and should not be copied from screenshot context.
- No secrets or private notes in logs, URL, local/session storage, error bodies, analytics, or correlation metadata.
- Mark only the five explicit user-interaction handlers (`GET` queue, `GET` detail, `POST` claim, `POST` release, `POST` resolve) with existing `@AuthenticatedActivity` admission. The security chain must validate current session, enabled account and AGENT role first, plus CSRF for POST, before handler admission renews activity. Background refresh, prefetch, polling, unmatched and rejected requests do not qualify. Cover these boundaries in tests.
- Accessible native controls, labelled form fields, error summary/inline errors, focus management for conflict/validation, live save/loading announcement, WCAG AA contrast, no hover-only actions, and `prefers-reduced-motion` support.
- GET loading shows skeleton/spinner, retryable errors expose manual retry, mutations disable duplicate submission and indicate progress. No polling, prefetch, or automatic replay.

## 7. Verification design

- Domain tests for all transitions, invalid transition matrix, note normalization/bounds, actor/assignee conditions, version increment, terminal immutability.
- PostgreSQL integration tests for V7-backed persistence, query scopes/totals/sorts, atomic state plus audit, rollback on append failure, and two-agent same-tag races.
- HTTP/security tests for session role, CSRF, owner/agent visibility separation, DTO privacy, all ETag precedence cases, response envelope/headers, concurrency outcomes, and no assignment IDs in customer payloads.
- Verify only the five marked user-interaction handlers renew strict-idle activity, after authorization and CSRF admission. Verify background refresh, prefetch, polling, unmatched paths and rejected requests do not renew it; do not introduce hidden keepalives.
- Angular API/component tests for query URL state, tab/filter counts, sorting/pagination, queue actions, loading/error/empty states, responsive semantics, detail rendering, visibility-based controls, validation, ETag handling, conflict refresh with form preservation, and no blind mutation retry.
- Visual implementation checks should compare against supplied desktop/mobile screenshots using shared app components and tokens. Any difference required by business/privacy rules is documented here and tested behaviorally.

## 8. Requirement traceability

| Design area | Requirements |
|---|---|
| Queue scope/search/filter/sort/page | AGW-01–02 |
| Agent detail and privacy | AGW-03 |
| Claim/release/resolve transitions and atomic audit | AGW-04–06 |
| ETag conflicts, refresh and failure states | AGW-07 |
| Responsive accessible design | AGW-08 |

This design is proposed. It does not authorize implementation until the feature design is approved under `.kiro/steering/spec-workflow.md`.
