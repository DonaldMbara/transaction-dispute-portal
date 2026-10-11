# Transaction Dispute Portal — Business Rules

**Status:** Approved design baseline · **Version:** 0.5 · **Owner:** Donald Mbara · **Last reviewed:** 2026-10-11

> Working business-rules document, not a Kiro steering file. Decisions may evolve during specification and implementation. Proposed refinements are explicitly marked **To confirm**. If a rule changes, update the relevant feature spec, tests, API contract, UI behaviour and design diagrams together.

## 1. Purpose and scope

Build a customer-facing transaction dispute experience and an agent investigation workspace. Customers view their transactions, submit disputes, and review current and historical disputes. Agents claim, release, investigate and resolve disputes.

**In scope:** authenticated customer and agent experiences, seeded transaction data, submission validation, dispute lifecycle, status/history views, immutable lifecycle audit events, ownership controls, concurrent agent updates, idempotent creation, and responsive UI.

**Out of scope:** real payment processing, refunds, chargebacks, settlement, banking integrations, evidence uploads, supervisor workflow, notifications, and dispute reopening. Acceptance of a dispute is a decision, **not** proof of a refund.

## 2. Actors and access

| ID | Rule | Verification |
|---|---|---|
| BR-A01 | Roles are `CUSTOMER` and `AGENT`; all protected actions require authentication. | Authorization tests for each endpoint. |
| BR-A02 | Customers see only accounts, transactions and disputes they own. Identity is derived from the authenticated principal, not a client-supplied customer ID. | Cross-customer access tests return 404 for non-owned records. |
| BR-A03 | Customers cannot claim, release or resolve disputes. | Role-based API tests return 403. |
| BR-A04 | Agents can view permitted dispute queues/details but only the assigned agent can release or resolve an `UNDER_REVIEW` dispute. | Assigned vs other agent tests. |
| BR-A05 | Agent internal notes and internal audit metadata are never returned in customer-facing responses. | DTO/serialization and API tests. |

Admin-controlled account provisioning is required; no public registration. This does not add an ADMIN application role or a provisioning endpoint. The explicit non-web masked-console mechanism is documented in docs/operations/account-provisioning.md.

## 3. Transactions

| ID | Rule | Verification |
|---|---|---|
| BR-T01 | Transactions belong to an account; an account belongs to a customer. Each customer has 1–N owned accounts. `balance` is a synthetic, read-only display value set by the fixture command only; it is not derived from transactions and carries no settlement meaning. Each customer has at most one primary account (enforced by a partial unique index `WHERE is_primary`); "primary" means the account shown first and selected by default in the UI. If no primary account exists (data fault), the UI falls back to the first account in list order. MVP account types are `MAIN` and `SAVINGS` only (display names "Main Account" and "Savings Account", derived at the application layer). Type values and display names must not use any external bank trademark. No credit/overdraft accounts and no negative-balance semantics in this phase. | Data integrity and ownership tests. |
| BR-T02 | Transaction states are `PENDING`, `COMPLETED`, `FAILED`. Only completed outgoing (`OUT`) transactions are eligible for dispute. | Eligibility matrix tests. |
| BR-T03 | Transaction records are read-only through this portal; seeded/mock data is used. | No transaction mutation API; security tests. |
| BR-T04 | Monetary values use exact decimal precision (`BigDecimal`/`NUMERIC(19,2)`); seeded currency is ZAR. | Persistence and serialization tests. |
| BR-T05 | Customers can view a paginated transaction list and transaction details, including whether a dispute already exists. | API/UI integration tests. |

## 4. Dispute submission

| ID | Rule | Verification |
|---|---|---|
| BR-S01 | A customer can create a dispute only for their own completed outgoing (`OUT`) transaction. Incoming credits are not eligible for this dispute flow. | Ownership and eligibility tests. |
| BR-S02 | A transaction may have **at most one dispute ever**, including after acceptance/rejection. Enforce with a database unique constraint on `transaction_id`. | Duplicate requests and database constraint tests. |
| BR-S03 | Submission requires `transactionId`, a supported `reason`, and nonblank `description`; enforce bounded lengths and input validation. | Validation tests. |
| BR-S04 | Supported reasons: `UNRECOGNIZED_TRANSACTION`, `DUPLICATE_CHARGE`, `INCORRECT_AMOUNT`, `GOODS_OR_SERVICES_NOT_RECEIVED`, `OTHER`. | Enum/API/UI consistency tests. |
| BR-S05 | Successful submission creates an `OPEN`, unassigned dispute and a `CREATED` audit event in one database transaction. | Transactional integration test. |
| BR-S06 | Submission supports an `Idempotency-Key` scoped to user and operation: same key+same request replays original response; same key+different request returns 409. A different key cannot bypass BR-S02. | Retry, mismatch and concurrent request tests. |
| BR-S07 | Failed creation must not leave an orphaned dispute, event or misleading completed idempotency record. | Rollback tests. |

## 5. Dispute lifecycle

```text
OPEN (unassigned)
  └─ CLAIM by agent ──> UNDER_REVIEW (assigned)
                          ├─ RELEASE by assigned agent ──> OPEN (unassigned)
                          ├─ ACCEPT by assigned agent ──> ACCEPTED (terminal)
                          └─ REJECT by assigned agent ──> REJECTED (terminal)
```

| ID | Rule | Verification |
|---|---|---|
| BR-L01 | Newly created disputes are `OPEN`, with no assigned agent. | Creation test and DB constraint. |
| BR-L02 | Only an `OPEN`, unassigned dispute can be claimed. Exactly one agent wins concurrent claims. | Concurrent claim integration test. |
| BR-L03 | Claim assigns the agent and moves status to `UNDER_REVIEW`. | State transition and audit test. |
| BR-L04 | Only the assigned agent can release an `UNDER_REVIEW` dispute; a nonblank release reason is required. Release clears assignment and returns status to `OPEN`. | State/authorization/validation tests. |
| BR-L05 | Only the assigned agent can resolve an `UNDER_REVIEW` dispute as `ACCEPTED` or `REJECTED`. | State/authorization tests. |
| BR-L06 | Resolution requires nonblank internal investigation notes and a separate nonblank customer-facing resolution message. | Validation and visibility tests. |
| BR-L07 | `ACCEPTED` and `REJECTED` are terminal. No reopening, further claiming, release or second resolution. | Invalid transition matrix tests. |
| BR-L08 | `RELEASED` is an audit event, not a dispute status. | Status enum and history tests. |
| BR-L09 | Agent mutations require strong ETag/If-Match optimistic locking and atomic conditional state/audit writes. Missing precondition 428; malformed 400; stale/lost write race 412; matching version with invalid business state/assignment 409. | Stale version and racing updates tests. |
| BR-L10 | Accepting a dispute does not initiate or imply a refund, reversal, credit or settlement. | UI copy and API contract review. |

## 6. History, audit and visibility

| ID | Rule | Verification |
|---|---|---|
| BR-H01 | Every creation, claim, release and resolution produces a timestamped immutable audit event identifying the actor and relevant state/assignment changes. | Event assertions for each transition. |
| BR-H02 | State changes and their audit events commit or roll back atomically. | Forced-failure transaction tests. |
| BR-H03 | Customers can see their dispute status, submission details, customer-safe history and final decision/message; they cannot see internal agent notes. | Customer response contract tests. |
| BR-H04 | Agents can see investigation details, internal notes and complete permitted audit history. | Agent response contract tests. |
| BR-H05 | Historical disputes remain accessible after resolution; no deletion or reopening in MVP. | History and terminal-state tests. |
| BR-H06 | Events are append-only through application operations; corrections require an explicit future design, not in-place event editing. | Repository/API review. |

## 7. API and error behaviour

- Customer endpoints under `/api/v1/transactions` and `/api/v1/disputes`; agent operations under `/api/v1/agent/disputes`.
- Use DTOs rather than exposing JPA entities, Bean Validation on inputs, and the selected custom application/json error envelope with stable numeric codes; internal ProblemDetail may be mapped into it. The envelope is not a strict RFC 9457 document.
- Use `401` for missing/invalid authentication; `403` for role denial; `404` for nonexistent or non-owned customer resources; `409` for duplicate/conflicting actions; `400` for malformed requests. Use `422` for owned non-completed transactions, as selected in the API design.
- **Approved:** GET agent detail returns a strong version ETag; claim/release/resolve require If-Match. Use 428 missing, 400 malformed, 412 stale/lost write race and 409 matching-version state/assignment conflict. Preserve the canonical representation/command semantics in the API contract.
- Use the custom `{status,data,error_code}` envelope with boolean request status and numeric code 0 for success; domain status remains inside data. Collections use 1-based pages/default 20/max 100 and `{total,page,size,items}`. All counts and search/filter queries remain authorization scoped.
- Paginate lists and use stable ordering. Do not expose internal notes in shared response DTOs.

## 8. UI business behaviour

### Customer

1. Authenticate and land on the **Account Dashboard** (`/customer`) — goal copy describes accounts as (Main Account, Savings Account); one tile per owned account in primary-first order. Tapping a tile navigates to that account's transactions.
2. The **Transactions** page (`/customer/transactions?accountId=<id>`) shows an account selector defaulting to the primary account. Transaction list supports **All / Money In / Money Out** filters; selecting a row opens details.
3. The dispute-submission feature adds **Raise dispute** for completed outgoing transactions without an existing dispute. Incoming credits and pending/failed transactions explain why they cannot be disputed through this flow.
4. Submission form collects reason and description; show validation, submitting state, and confirmation after success. Prevent accidental repeated clicks; backend remains authoritative.
5. **My Disputes** shows active and resolved disputes, status, transaction context and a customer-safe timeline.
6. For accepted/rejected disputes, show decision and customer-facing explanation. Never claim funds have been returned unless a future integrated system explicitly proves it.

### Agent

1. See **Unassigned**, **My Cases**, and **All Disputes** queues, with status and relevant transaction context.
2. Claim only available open disputes; handle another agent winning a claim gracefully.
3. Assigned agent can release with a required reason, or resolve with decision, internal notes and customer message.
4. Confirm consequential actions; after conflicts, refresh details and preserve unsent form text where practical.
5. Other agents can view permitted cases but cannot modify cases assigned to someone else.

### Design direction

Use a **Capitec-inspired**, not official, design: reference-inspired blue buttons, white transaction rows, light-grey separators, clear transaction amounts and restrained red accents. Both customer and agent experiences are desktop-first responsive web layouts, using the measured public-web tokens in UI flows (#2F70EF primary buttons, white/#F7F7F7 surfaces). This reconciles the earlier contradictory layout guidance without changing dispute behavior. Do not use official logos or imply this is a live Capitec banking product.

## 9. Data integrity and initial model

Proposed entities: `app_users`, `accounts`, `bank_transactions`, `disputes`, `dispute_events`, `idempotency_records`.

Minimum database invariants:
- Foreign keys preserve user/account/transaction/dispute relationships.
- `UNIQUE(disputes.transaction_id)` enforces one dispute per transaction.
- `UNIQUE(idempotency_records.user_id, operation, idempotency_key)` enforces scoped retry keys.
- Dispute version field supports optimistic locking.
- An `OPEN` dispute is unassigned; `UNDER_REVIEW` has an assigned agent; resolved disputes retain their resolving agent and assigned_at for accountability (approved). Terminal assignment is immutable and hidden from customer responses.
- Persist `internal_resolution_notes` separately from `customer_resolution_message` and never expose the former to customer APIs.
- Index transaction account/date, dispute status/assignee/date, and dispute event dispute/date as required by real queries.

## 10. Planned design artifacts

These design deliverables now form the approved baseline; they do not establish implemented or deployed behavior. Keep them version-controlled under `docs/design/` and reference them from Kiro feature specs.

| Artifact | Suggested path | Purpose |
|---|---|---|
| PlantUML system/context diagram | `docs/design/plantuml/system-context.puml` | Customer, agent, Angular, Spring Boot, PostgreSQL |
| PlantUML component diagram | `docs/design/plantuml/components.puml` | Modules, responsibilities, boundaries |
| PlantUML dispute state diagram | `docs/design/plantuml/dispute-state.puml` | Valid transitions and guards |
| PlantUML sequence: submission | `docs/design/plantuml/submit-dispute.puml` | Validation, idempotency, transaction and audit |
| PlantUML sequence: agent resolution | `docs/design/plantuml/resolve-dispute.puml` | Authorization, version check, audit |
| PlantUML ER diagram | `docs/design/plantuml/data-model.puml` | Tables, keys, relationships |
| Database design document | `docs/design/database-design.md` | Constraints, indexes, migrations and decisions |
| API contract | `docs/design/api-contract.md` | DTOs, endpoints, errors, pagination |
| UI flows and screen specifications | `docs/design/ui-flows.md` | Customer and agent journeys, empty/error/loading states |

**Diagram quality rule:** diagrams must match approved requirements and actual code/schema; update them in the same change as meaningful architectural or workflow changes. Prefer PlantUML source over screenshots as the canonical design artifact.

## 11. Business-rule traceability

Every feature spec should map applicable business rule IDs to acceptance criteria and executable tests. Suggested starting matrix:

| Spec | Rules | Required test coverage |
|---|---|---|
| `authentication-rbac` | BR-A01–A05 | Authentication role machinery/default denial, JDBC sessions and CSRF; ownership isolation belongs to the owning business features |
| `customer-accounts` | BR-A02, BR-T01 | Account ownership scoping; at-most-one primary; masking; balance display; cross-customer account access returns 404; AGENT access returns 403 |
| `customer-transactions` | BR-T01–T05 | Ownership, status filtering, accountId resource selector, read-only access |
| `dispute-submission` | BR-S01–S07, BR-L01, BR-H01–H02 | Eligibility, uniqueness, idempotency, rollback |
| `agent-workflow` | BR-L02–L10, BR-H01–H02 | Claim races, ownership, transitions, stale updates |
| `customer-dispute-history` | BR-H03–H06 | Visibility, history, terminal state persistence |
| `frontend-experience` | UI §8 + relevant rules | Forms, navigation, status, loading/errors, accessibility |

## 12. Open decisions and change control

**Approved at final sign-off (with a customer-requested eligibility amendment on 2026-10-11):** strong ETag/If-Match optimistic locking and terminal-assignee retention; Spring Security with PostgreSQL-backed Spring Session JDBC, opaque HttpOnly cookies (Secure/SameSite=Lax in production), 30-minute server-enforced idle timeout, CSRF including login/logout, current enabled-role validation and admin-controlled provisioning without public registration; one modular Spring Boot deployment with no MVP gateway or microservices. Authentication contracts/provisioning are implemented under the approved authentication-rbac feature; future business feature tasks still require approval. **Selected round-two design:** API text limits, custom envelopes/numeric codes, 1-based pagination, generic claim/release customer milestones and no transaction snapshot while seeded transactions remain read-only. The 2026-10-11 amendment limits customer disputes to completed outgoing (`OUT`) transactions; completed incoming credits are ineligible. Historical foundation specs retain their original approval context and are not rewritten; current session architecture supersedes their future JWT/stateless-auth assumptions. Existing Flyway migrations remain unchanged. No JWTs, refresh tokens, localStorage authentication tokens or bearer-token interceptors are part of the current architecture.

When a rule changes:
1. Update this document and increment its version/date.
2. Record why it changed in the relevant spec/design decision.
3. Update PlantUML diagrams, database/API/UI contracts as applicable.
4. Add or revise tests mapped to its BR ID.
5. Review scope and acceptance criteria before implementation.

**Document precedence:** The assessment brief defines the minimum required behaviour. Approved feature specs refine this working document; if a conflict appears, explicitly reconcile it rather than silently choosing one.
