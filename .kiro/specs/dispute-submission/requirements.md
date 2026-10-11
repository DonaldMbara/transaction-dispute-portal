# Customer Dispute Submission — Requirements

**Status:** Approved requirements — design and task plan requested by developer on 2026-10-11  
**Feature:** Dispute Submission  
**Prerequisites:** Project Foundation, Authentication & RBAC, Customer Accounts, Customer Transactions (implemented)  
**Related baselines:** [Kiro steering](../../steering/), [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

## 1. Purpose

Allow an authenticated customer to submit a dispute about an eligible transaction they own, and give clear feedback about the result. The backend is authoritative for ownership, transaction eligibility, duplicate prevention and creation. This feature introduces dispute submission; customer dispute history and detail are specified separately by the `customer-dispute-history` feature.

## 2. Scope

### In scope

- Customer submission entry point from an owned transaction's detail screen.
- Customer dispute submission form with a supported reason and written explanation.
- `POST /api/v1/disputes` using the established session, CSRF, JSON envelope and error contracts.
- Persisting one dispute for an eligible transaction, with its initial state and creation audit event.
- Idempotent retries and duplicate-dispute handling.
- Customer-safe success, validation, eligibility, conflict and network-failure states.
- Backend, API, security, persistence and Angular tests required by the acceptance criteria.

### Out of scope

- Customer dispute list, search, filtering, history timeline and dispute detail routes; these belong to `customer-dispute-history`.
- Agent queues, claims, releases, investigation and resolution; these belong to `agent-workflow`.
- Refunds, reversals, payment processing, settlement, bank integrations, evidence/attachment uploads, notifications, reopening or deletion.
- Account provisioning, authentication changes, transaction mutation, new application roles, or changes to the approved session and CSRF architecture.

## 3. Terms and rules

- A transaction is eligible for submission only when its status is `COMPLETED`, its direction is `OUT`, and it has no existing dispute.
- A transaction can have at most one dispute for its entire lifetime, regardless of the dispute's later outcome.
- A newly created dispute has status `OPEN` and no assigned agent.
- The caller's customer identity comes only from the validated server-side session. A request body never supplies `customerId`.
- `ACCEPTED` is a dispute decision, not evidence of a refund, reversal or settlement.
- Dispute and transaction response context includes only masked account data; no full account number is returned.

## 4. Functional requirements

### DSP-SUB-01 — Access and request protection

1. **When** a request is made without a valid current session, **the system shall** return the established `401` authentication envelope and perform no dispute operation.
2. **When** an authenticated `AGENT` calls the customer submission endpoint, **the system shall** return the established `403` envelope and perform no dispute operation.
3. **When** a customer submits without a valid CSRF token, **the system shall** reject the request with `403/1010` before creating a dispute or audit event.
4. The endpoint shall derive customer identity from the validated session principal and shall not accept a client-supplied customer identifier as identity or authorization.

### DSP-SUB-02 — Eligible transaction and ownership

1. The system shall provide `POST /api/v1/disputes` to authenticated `CUSTOMER` users.
2. **When** the referenced transaction does not exist or is not owned by the authenticated customer through transaction → account → customer, **the system shall** return the standard `404/1003` response without revealing whether another customer's transaction exists.
3. **When** the referenced transaction is owned but is not `COMPLETED` or its direction is `IN`, **the system shall** return `422` with the established safe eligibility error and create no dispute or event.
4. Only completed `OUT` transactions are eligible; completed incoming credits shall not be disputable through this customer flow.

### DSP-SUB-03 — Submission fields and validation

1. The request body shall contain exactly the supported submission fields: `transactionId`, `reason`, and `description`.
2. `transactionId` shall be a UUID identifying the transaction to dispute.
3. `reason` shall be one of `UNRECOGNIZED_TRANSACTION`, `DUPLICATE_CHARGE`, `INCORRECT_AMOUNT`, `GOODS_OR_SERVICES_NOT_RECEIVED`, or `OTHER`.
4. The system shall trim leading/trailing whitespace from `description`, preserve internal whitespace, and require 1–2000 characters after trimming.
5. **When** the body is malformed, contains unknown fields, has a missing/unsupported value, or violates a text limit, **the system shall** return `400/1004` with safe field-level validation feedback and shall create no dispute, event or completed idempotency record.
6. Text shall be treated as plain text. The customer UI shall render it as text and shall not interpret it as HTML.

### DSP-SUB-04 — Successful creation

1. **When** a valid request identifies an owned, completed transaction with no existing dispute, **the system shall** create a dispute with status `OPEN`, no assigned agent, the submitted reason and description, and server-generated identifier and timestamps.
2. Dispute creation and its `CREATED` audit event shall commit atomically. If either write fails, neither shall persist.
3. The successful response shall use HTTP `201`, the established `{status,data,error_code}` JSON envelope, and a `Location: /api/v1/disputes/{id}` header. The response shall include the new dispute identifier, transaction identifier, reason, description, status, and creation time as defined by the canonical API contract.
4. The response shall include no internal notes, agent identity/assignment, full account number, or data belonging to another customer.

### DSP-SUB-05 — Idempotent retry and duplicate protection

1. Every submission shall require an `Idempotency-Key` header matching the case-sensitive format `[A-Za-z0-9_-]{1,128}`; a UUID is recommended.
2. The key shall be scoped to the authenticated user and submission operation, and the request payload shall be fingerprinted canonically.
3. **When** the same user retries the same key with the same canonical payload, **the system shall** replay the original successful response and `Location` without creating another dispute or event.
4. **When** the same user reuses a key with a different canonical payload, **the system shall** return `409/2004` and shall not mutate the original result.
5. **When** a dispute already exists for the transaction, including when a different idempotency key is used, **the system shall** return the established `409/2003` duplicate-dispute response and shall not create another dispute or event.
6. Concurrent submissions for the same transaction shall result in at most one persisted dispute and one `CREATED` event. The losing request shall receive the documented duplicate or replay outcome; it shall not receive an unhandled database error.
7. A failed attempt shall not leave a completed idempotency result that falsely indicates successful creation. A retry after a transient failure shall be safe.

### DSP-SUB-06 — Customer submission experience

1. An eligible transaction's detail screen shall offer a clearly labelled **Dispute this transaction** action that opens the submission form for that transaction.
2. Transactions with status `PENDING` or `FAILED`, or direction `IN`, shall not expose an active submission action and shall show a concise eligibility explanation.
3. The form shall show a customer-safe transaction summary and collect the required reason and description. It shall identify required fields, show inline validation and a description character count, and retain the user's text when correcting validation errors.
4. The form shall show a customer-safe transaction summary beside the dispute form on desktop and above it on mobile, with a clear submit action and cancel navigation. Submission shall occur directly from this form after required-field validation; the form shall not insert a separate review screen.
5. While a submission is pending, the UI shall prevent duplicate clicks and show a loading state. An unchanged retry after an ambiguous network failure shall reuse the same idempotency key and preserve form values; editing the payload shall use a new key.
6. On success, the UI shall confirm receipt, show the dispute identifier/status available in the response, and provide navigation to the relevant dispute view when that destination is implemented. It shall not claim that money has been refunded.
7. On `409/2003`, the UI shall explain that this transaction already has a dispute and offer a safe route to view it when customer dispute history/detail is available. Before that route exists, it shall show a non-dead-end message without constructing an unimplemented link.
8. On `422`, the UI shall explain the eligibility result. On `400`, it shall show actionable validation feedback. On `401`, it shall use the existing session-clearing behavior. On `403/1010`, it shall use the existing CSRF recovery behavior without blindly replaying the unsafe request. On network/`503` failure, it shall preserve the form text and permit a safe retry.

## 5. Nonfunctional and security requirements

1. The database shall enforce one dispute per transaction with a unique constraint; application checks alone are insufficient.
2. The system shall use existing backend module boundaries, DTOs, custom error envelopes, correlation IDs, no-store response policy and parameterized persistence patterns. Successful same-key replay shall return the original `201` envelope and `Location`, with a new request correlation ID.
3. The system shall keep dispute state, creation audit and successful idempotency outcome transactionally consistent.
4. Logs, errors, responses and audit events shall not contain credentials, session/CSRF material, full account numbers, or customer text beyond the fields explicitly required for business audit.
5. The feature shall use bounded inputs and return generic safe failures for infrastructure errors; it shall not expose stack traces or SQL details.
6. The UI shall use semantic labelled controls, keyboard-operable interactions, visible focus, accessible validation announcements, and responsive layouts consistent with the existing application and `.kiro/steering/frontend-standards.md`.

## 6. Acceptance evidence

The feature is acceptable only when automated evidence demonstrates:

- `401` for missing/expired sessions, `403` for the wrong role, and CSRF rejection before side effects.
- Owner success and cross-customer/nonexistent transaction indistinguishability (`404/1003`).
- `422` for owned pending/failed/incoming transactions; transaction metadata marks only completed outgoing transactions eligible.
- Every reason accepted; blank, missing, unsupported and over-limit submission values rejected with `400/1004`. Description is trimmed to 1–2000 characters while internal whitespace is preserved.
- Exactly one `OPEN` dispute and one `CREATED` event after success; complete rollback when event persistence fails.
- Same-key/same-payload replay, same-key/different-payload `409/2004`, different-key duplicate `409/2003`, and concurrent duplicate submission behavior using PostgreSQL integration tests.
- `201`, `Location`, correlation header, envelope and customer-safe field contract; serialized responses contain no internal fields or full account number.
- Angular form validation, direct submission, pending state, retry key behavior, preserved text on errors, outgoing-only eligibility guidance, duplicate handling, keyboard/accessibility basics and success confirmation.

No test execution is claimed by this requirements document; test commands and results belong in the approved task/acceptance evidence.

## 7. Dependencies and traceability

- Depends on implemented authentication/session/CSRF, customer accounts, and customer transactions, including the owned transaction detail endpoint and eligibility data.
- Uses business rules `BR-A01`–`BR-A02`, `BR-T02`, `BR-T05`, `BR-S01`–`BR-S07`, `BR-L01`, `BR-H01`–`BR-H02`, and customer UI behavior in business-rules §8 and ui-flows §4.
- Aligns with `.kiro/steering/domain-rules.md`, `security.md`, `api-contracts.md`, `backend-standards.md`, `frontend-standards.md`, `testing-standards.md`, `structure.md`, and `spec-workflow.md`.
- Customer dispute collection/detail and history are deferred to `customer-dispute-history`; agent lifecycle operations are deferred to `agent-workflow`.
- No `design.md` or `tasks.md` is created in this phase. The design phase may specify implementation details but shall preserve the approved limits and idempotency behavior.

## 8. Decisions requiring explicit change control

The requirements inherit the approved baselines and do not reopen them: one dispute per transaction for life; only completed outgoing transactions are eligible per the customer-requested 2026-10-11 amendment; accepted is not a refund; customer identity derives from session; creation plus audit is atomic; and idempotent retries use the same key for unchanged payloads.
