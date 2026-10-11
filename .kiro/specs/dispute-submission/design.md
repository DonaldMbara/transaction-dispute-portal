# Customer Dispute Submission — Design

**Status:** Approved for implementation — developer requested task execution on 2026-10-11  
**Feature:** Dispute Submission  
**Requirements:** [requirements.md](requirements.md) (approved 2026-10-11)  
**Approved baselines:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md), [Kiro steering](../../steering/)

## 1. Design goals and boundaries

Implement one customer submission use case in the existing Spring Boot modular monolith and Angular application. Backend checks remain authoritative. Keep customer dispute history and all agent workflow operations outside this feature. Preserve current authentication, CSRF, error envelope, design tokens and transaction ownership boundaries.

The Figma source for the customer submission surface is the `Raise dispute · Desktop` frame (`3:5774`, 1440 × 1013) and `Raise dispute · Mobile` frame (`3:5841`, 390 × 1520) in [Transaction Dispute](https://www.figma.com/design/L9AXnbjX8li5ggpmH2pBnr/Transaction-Dispute?node-id=3-5774). Those frames are the visual authority for implementation. The Figma connector exposed frame names and dimensions, but its screenshot/design-context request was blocked by the Starter-plan call limit, and the browser asks for sign-in. Therefore this written design does not claim a visual inspection of the frame contents; Task 7 requires an implementer with Figma access to inspect and compare the actual desktop/mobile frames before completing the UI. Existing screen-specific behavior and visual tokens below come from the approved `docs/design/ui-flows.md` and steering.

## 2. Component boundaries

### Backend

```text
com.assessment.disputeportal
├── transaction/             # owns BankTransaction; exposes a narrow submission lookup/lock port
├── dispute/
│   ├── controller/          # customer POST adapter
│   ├── model/               # Dispute, DisputeStatus, DisputeReason, creation/event values
│   ├── model/dto/            # immutable submission request/response DTOs
│   ├── mapper/              # persistence/domain/response mappings
│   ├── repository/           # dispute and event persistence adapters
│   └── service/              # transactional submit use case and eligibility/ownership policy
└── idempotency/             # key record, canonical fingerprint and safe replay coordination
```

- `dispute` owns disputes and dispute events. `idempotency` owns successful request replay records and its narrow application contract. `transaction` owns transaction persistence and exposes a small interface to check/lock an owned transaction for submission; `dispute` does not import a transaction JPA entity or repository.
- The submission service obtains the current customer and actor UUID from the validated session principal. No customer or actor ID comes from the request body.
- The controller validates transport input, invokes one submit use case and maps the result to the shared envelope/headers. It does not own transaction boundaries, locking or persistence rules.
- Keep the use case transaction boundary around idempotency coordination, the transaction lock, dispute insert, `CREATED` event insert and successful idempotency record. Only return success after commit.
- Use Flyway as the schema authority and the existing common exception/envelope handling. Do not edit historical migrations.

### Frontend

```text
features/customer/
├── transaction-detail/       # adds eligible-only entry action
└── disputes/
    ├── customer-disputes.routes.ts
    ├── dispute-submission.ts # standalone screen/form
    ├── dispute-submission.html / .scss
    ├── dispute-api.ts         # typed submission request/response
    └── dispute-models.ts
```

- Add the submission screen under the customer feature and lazy-load it from the existing customer route tree. Do not add the future My Disputes navigation link or history/detail views.
- Use a transaction-detail action that opens `/customer/disputes/new?transactionId=<uuid>`. The form resolves the transaction through the existing transaction detail API on direct navigation and displays only the existing customer-safe fields, including masked account context. A missing/non-owned ID uses the established 404 behavior.
- Keep form state local to the screen; use a typed Reactive Form, existing application loading/error patterns, HttpClient/RxJS for the mutation, and existing style tokens/shared controls. Do not add a state library, new design-token system, or raw duplicate global button styling.
- Do not persist description, reason, idempotency keys, or other private form data in local/session storage. Preserve the form in component memory for recoverable validation/network/503 errors while the authenticated screen remains mounted.

## 3. Persistence and domain model

Add one additive Flyway migration after the current transaction migration to create `disputes`, `dispute_events`, and `idempotency_records`, using the canonical database design. It shall include:

- `disputes.transaction_id` as a non-null foreign key with a unique constraint, ensuring one dispute for the transaction lifetime.
- An `OPEN` initial status with no assignee, `version = 0`, and server-generated UTC `created_at`/`updated_at` values.
- Supported-reason and status constraints; description stored in a bounded column and validated as trimmed, nonblank plain text at the application boundary.
- A `CREATED` event at dispute version `0`, actor set to the authenticated customer, and no internal notes or customer decision message.
- A unique `(user_id, operation, idempotency_key)` record with request hash and completed response fields. Persist successful response envelopes only; never persist an in-progress or failed result as success.
- The approved foreign keys and indexes in `docs/design/database-design.md`; retain terminal-related columns and constraints needed by the later agent workflow but do not implement those transitions here.

The feature domain contains `Dispute`, `DisputeStatus`, `DisputeReason` and a creation event value. Creation establishes the first valid lifecycle state only. Do not implement claim/release/resolve methods or expose them through the customer feature.

## 4. Submission sequence and concurrency

```text
Angular form       Security/controller      Submit use case      Transaction port      PostgreSQL
    | POST + CSRF + Idempotency-Key + body         |                     |                 |
    |-------------------------------> validate role/session/CSRF           |                 |
    |                                              | normalize + hash       |                 |
    |                                              | lookup key ----------->| idempotency     |
    |                                              |<-----------------------|                 |
    |                                              | lock owned transaction ---------------->|
    |                                              |<-----------------------------------------|
    |                                              | check COMPLETED + no dispute             |
    |                                              | insert OPEN dispute + CREATED event      |
    |                                              | save completed 201 envelope/idempotency  |
    |                                              | commit                                  |
    |<---------------------- 201 + Location + fresh X-Correlation-ID -----------------------|
```

1. Spring Security and the existing CSRF filter authorize a current `CUSTOMER` session before the controller executes. Mark the handler with `@AuthenticatedActivity` consistent with other admitted customer requests.
2. Reject malformed body, unknown fields, missing/invalid key and invalid reason/description before opening writes. Normalize by trimming only leading/trailing description whitespace; preserve internal whitespace. Fingerprint the fixed `transactionId`, `reason`, normalized `description` fields using the canonical UTF-8 serialization and SHA-256 from `docs/design/api-contract.md`.
3. Look up a completed idempotency record by current user + operation + key. Same hash returns the stored successful envelope and Location with a fresh correlation header; a different hash returns `409/2004`.
4. For a new key, lock the transaction row through the transaction-owned port using an ownership-scoped transaction → account → customer predicate. If absent or not owned, return `404/1003`. If owned but no longer `COMPLETED` or its direction is not `OUT`, return `422/2002`. Then check for an existing dispute and return `409/2003` if found.
5. Persist the `OPEN` dispute, its `CREATED` version-0 event, and the completed 201 response record in the same database transaction. The unique transaction constraint is the final one-dispute guard.
6. If two requests race on the same transaction, the transaction row lock serializes them. Same-key identical requests replay the winner. Different keys return the duplicate response after the winner commits. If an idempotency uniqueness race occurs on different transactions, roll back, read the winning key in a fresh transaction and replay or return `409/2004` based on hash. Do not query in a transaction that PostgreSQL has marked aborted.
7. Apply bounded lock/statement waits and the existing safe `503/3002` response with `Retry-After` when coordination exceeds its limit. Do not use process-local locks or unbounded retry loops. Preserve the original key for a client retry after an ambiguous network/503 outcome.

## 5. HTTP contract

| Method/path | Access | Request | Success |
|---|---|---|---|
| `POST /api/v1/disputes` | CUSTOMER session, CSRF valid | `Idempotency-Key`; `{transactionId,reason,description}` | `201`, standard envelope, `Location`, correlation ID, `Cache-Control: no-store` |

The response data is `{id,transactionId,reason,description,status:"OPEN",createdAt}`. Use shared `ApiEnvelope`, JSON content type and exception mapping. Do not return direct entities, account-number fields, assignee, agent identity, internal notes or internal event data. Error precedence and codes follow the canonical API contract: auth/role/CSRF errors, malformed request `400/1004`, absent/non-owned transaction `404/1003`, ineligible transaction `422/2002`, duplicate `409/2003`, key mismatch `409/2004`, and bounded store/coordination failure `503/3002`.

## 6. Angular route, form and states

- Route: `/customer/disputes/new?transactionId=<uuid>`. Validate the query UUID on the client for usable navigation and always revalidate ownership/eligibility on the server. A direct link loads transaction detail via existing `TransactionApi`; the route does not accept transaction fields or customer identity in query parameters.
- Entry action: the transaction list and detail page show **Raise dispute** only when the transaction's current data says `eligible` and `dispute.id` is null. Only completed outgoing transactions are eligible; incoming transactions explain that only payments leaving the account can be disputed. The form can become stale after entry; backend eligibility and duplicate checks remain authoritative.
- Form sequence: customer-safe transaction summary; reason select with the five API enum values and human-readable labels; required description textarea with count up to 2000; review/confirmation; submit. Use a clearly named review action and submit action. Confirmed wording must say receipt/opening of a case, never refund.
- Match the Figma `Raise dispute` Desktop/Mobile frames for layout and use current global tokens/components. Design direction from approved UI flows: desktop-first responsive; restrained portal header/footer, content density appropriate to financial workflow, white surfaces and `#F7F7F7` background, `#2F70EF` primary actions, shared button treatment, keyboard focus, WCAG AA contrast and mobile touch targets. Do not copy any external bank logo/assets.
- Loading: show an inline or page-level skeleton for direct-link transaction loading; show a pending affordance on submit, disable duplicate submission, and announce status accessibly. Do not add polling or automatic unsafe request replay.
- Validation: inline field errors after interaction/submit and an accessible error summary when submit is attempted. Character count is based on normalized text behavior; server remains authoritative.
- Network/503: keep form content in component memory; retry unchanged payload with the same key. Create a fresh key only after the customer changes a submission after a failed/ambiguous attempt. Do not claim a request failed if response outcome is ambiguous; explain that retrying unchanged is safe.
- Duplicate `409/2003`: explain that a dispute already exists and return the customer to transaction details. Do not link to an unimplemented history/detail route in this feature.
- Other errors: map `400/1004` field errors; `401` uses existing session handling; `403/1010` follows the existing CSRF recovery behavior without automatically replaying POST; `422/2002` explains only completed transactions can be disputed; `503/3002` offers manual safe retry.
- Success: show confirmation with case UUID, status `OPEN` / “Received”, submitted transaction summary, and a link back to the source transaction. My Disputes and dispute detail links wait for customer-dispute-history.

## 7. Privacy and failure behavior

- The transaction port returns only fields needed for ownership, current status and customer-safe confirmation. Masked account data may appear in the UI summary; full account numbers do not enter dispute requests, responses, logs, URLs or audit data.
- The customer description is stored as submitted business data, but it must not be copied into routine logs, error messages or correlation metadata. The creation event records actor and state transition, not an unnecessary duplicate of the description.
- All state/event/idempotency writes roll back together. A unique dispute constraint collision is translated to the documented domain duplicate only after rollback and a safe ownership-scoped lookup; unrelated database integrity failures remain generic availability/validation failures per common handling.
- Cross-customer and missing transaction IDs have indistinguishable customer responses. The customer API never queries or returns agent-private data.

## 8. Test design

- Domain/unit: eligibility for all three statuses and both directions (only COMPLETED + OUT is eligible); accepted reason set; description trimming/length; initial open/unassigned/version-zero invariants; normalized idempotency fingerprints.
- Persistence/integration with PostgreSQL Testcontainers: additive migration; constraints/FKs; customer ownership chain; 404 vs 422; unique transaction dispute; atomic dispute + CREATED + successful idempotency persistence/rollback.
- Concurrency integration: same key/same payload, same key/different payload, different keys/same transaction, same key/different transactions and bounded wait recovery. Assert one dispute and exactly one CREATED event; assert no database exception leaks.
- HTTP/security: session, CUSTOMER/AGENT, CSRF ordering, headers, envelope, Location, safe error codes and serialized privacy assertions.
- Angular: typed request mapping and key reuse; action visibility; direct-route transaction loading; form validation and review; disabled/pending state; field and global errors; preserved text; manual retry; success and duplicate navigation; accessible roles/labels/focus; desktop/mobile comparison against the named Figma frames.
- Avoid implementing downstream history/agent tests here except ensuring the initial event/state is consumable by those future features.

## 9. Requirements traceability

| Design area | Requirements |
|---|---|
| Session/role/CSRF | DSP-SUB-01 |
| Transaction ownership/eligibility | DSP-SUB-02 |
| DTO and field validation | DSP-SUB-03 |
| Persistence, state and event atomicity | DSP-SUB-04, DSP-SUB-05 |
| Idempotency/replay/concurrency | DSP-SUB-05 |
| Figma-aligned customer form and result states | DSP-SUB-06 |
| Privacy/accessibility/availability | §7 and DSP-SUB-01–06 |

No implementation is authorized by this design artifact alone. After design approval, use the approved tasks in `tasks.md`; follow `.kiro/steering/spec-workflow.md` at the implementation gate.
