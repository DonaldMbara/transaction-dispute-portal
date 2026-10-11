# Customer Dispute Submission — Implementation Tasks

**Status:** Approved — implementation requested by developer on 2026-10-11  
**Requirements:** [requirements.md](requirements.md) (approved 2026-10-11)  
**Design:** [design.md](design.md) (draft)  
**Scope:** DSP-SUB-01–DSP-SUB-06. Implement customer dispute submission only. Do not implement customer dispute history/detail or agent workflow.

## Task 1 — Add dispute and idempotency schema and domain types

**Requirements:** DSP-SUB-03–05  
**Depends on:** Implemented Customer Transactions (V6), Authentication & RBAC

- Add one additive Flyway migration after V6 for `disputes`, `dispute_events` and `idempotency_records`, including approved foreign keys, status/reason constraints, `UNIQUE(disputes.transaction_id)`, idempotency uniqueness, indexes and lifecycle columns required by future agent workflow. Do not edit V1–V6.
- Add dispute reason/status and creation/event domain values with explicit invariants for `OPEN`, unassigned, version 0; supported reasons; bounded trimmed description.
- Add PostgreSQL persistence/domain tests for clean migration, constraints, references, version-zero CREATED event representation and uniqueness.
- **Acceptance:** migration applies to a clean database and after V6; exactly one dispute per transaction is enforced by PostgreSQL; no application/service code is introduced outside dispute/idempotency feature boundaries.

## Task 2 — Define the transaction-owned submission lookup/lock port

**Requirements:** DSP-SUB-02, DSP-SUB-04–05  
**Depends on:** Task 1; transaction module

- Define a narrow transaction-module contract that locks and returns only an owned transaction's identifier, current status and customer-safe summary needed for submission/confirmation. Derive ownership from the authenticated customer through transaction → account → customer.
- Implement the port in the transaction module using the existing transaction/account persistence boundary. Do not expose or import `BankTransaction` JPA entities/repositories into the dispute module.
- Add tests for owned, missing, foreign-owned, completed, pending and failed records, including row-lock behavior within the caller's transaction.
- **Acceptance:** dispute use cases can validate ownership/status and serialize competing submissions without taking ownership of transaction persistence or trusting request-supplied customer IDs.

## Task 3 — Implement atomic submit use case and idempotent persistence

**Requirements:** DSP-SUB-02–05  
**Depends on:** Tasks 1–2

- Implement bounded request normalization and canonical fingerprinting exactly as the API contract specifies: trimmed outer description whitespace, preserved internal whitespace, fixed transactionId/reason/description representation, UTF-8 and SHA-256.
- Implement the scoped idempotency lookup/replay and changed-payload `409/2004` behavior. Use PostgreSQL-backed coordination only; no in-process locks.
- Implement the transactional submit use case: user-scoped idempotency check, transaction-owned row lock, 404 for absent/non-owned, 422/2002 for owned non-completed, 409/2003 for existing dispute, and atomic writes of OPEN dispute, version-0 CREATED event and successful 201 response record.
- Implement rollback/recovery in a fresh transaction for same-key races and unique transaction conflicts. Apply the approved bounded lock/statement wait and map timeout to 503/3002 with Retry-After.
- Add unit and Testcontainers integration tests for normalization, status/reason validation, duplicate prevention, event/write rollback, same key/same payload, same key/different payload, different keys/same transaction and same key/different transactions.
- **Acceptance:** every success persists one dispute, one CREATED event and one replay record; all failed/racing paths leave no partial or misleading success state and return the documented domain outcome.

## Task 4 — Expose the customer POST endpoint and security contract

**Requirements:** DSP-SUB-01–05  
**Depends on:** Task 3; shared envelope/error/correlation/security infrastructure

- Add immutable request/response DTOs, focused mappings and thin `POST /api/v1/disputes` controller. Reject unknown fields and enforce the exact API body/header bounds.
- Add only the narrow CUSTOMER POST security matcher before default deny. Preserve current session/CSRF rules; mark the handler with `@AuthenticatedActivity`.
- Return the canonical 201 envelope, `Location`, fresh `X-Correlation-ID`, JSON content type and `Cache-Control: no-store`; replay must preserve body/Location but use the current request correlation ID.
- Add HTTP integration coverage for anonymous, AGENT, invalid/missing CSRF, valid CUSTOMER, malformed/unknown fields, missing/invalid Idempotency-Key, non-owned/missing transaction, non-completed, duplicate, changed-key payload and store failure outcomes.
- Assert serialized responses/errors and logs do not disclose full account numbers, internal agent fields, stack traces, session/CSRF material, SQL or unnecessary customer text.
- **Acceptance:** only the documented CUSTOMER POST route is authorized; statuses/codes/headers match `docs/design/api-contract.md`; all customer-visible bodies pass privacy assertions.

## Task 5 — Add customer submission API client and lazy route

**Requirements:** DSP-SUB-06  
**Depends on:** Existing customer transaction detail and route tree

- Add typed dispute submission DTOs/API client and the lazy customer route `/customer/disputes/new?transactionId=<uuid>`.
- Resolve transaction context via the existing transaction detail API on direct navigation; validate route input, preserve masked account context and handle missing/non-owned transaction through existing errors.
- Add customer-only **Raise dispute** entry action to transaction details and the transaction list expansion when `eligible` is true and `dispute.id` is null. The server marks only completed `OUT` transactions eligible. Do not add a dashboard/header My Disputes link.
- Add router/API/component tests for eligible action visibility, pending/failed explanation, direct route transaction loading, invalid ID, customer role, and navigation back to the originating transaction.
- **Acceptance:** form deep link and detail entry point work; ineligible/already-disputed states cannot start submission; no future history route is linked prematurely.

## Task 6 — Build the Figma-aligned submission form and result states

**Requirements:** DSP-SUB-03, DSP-SUB-06  
**Depends on:** Task 5; existing global tokens, button styles, layouts and shared form/error/loading patterns

- Use the user-supplied desktop and mobile screenshot captures as the visual reference for the transaction list and the third image’s dispute form. Match their hierarchy, spacing, component geometry, copy and responsive behavior; reuse existing shared/global styles for repeated controls rather than screen-local duplicate button rules. Preserve semantic/accessibility requirements and do not copy external logos/assets.
- Implement the customer-safe transaction summary, five reason choices, plain-text description textarea with 1–2000 normalized-character count, inline validation, accessible error summary and direct submit action. Do not add a separate review/confirmation screen.
- Expand a transaction row with a keyboard-accessible chevron and a smooth inline reveal for the eligible “Raise dispute” action. The expansion must preserve the existing merchant-to-details navigation and move following rows naturally without overlap while opening/closing.
- Implement pending/loading prevention, same-key retry for an unchanged ambiguous submission, new key for a changed payload, form-state retention for validation/network/503 errors, duplicate `409/2003`, CSRF `403/1010`, eligibility `422/2002`, session `401`, and success confirmation.
- Use copy that confirms receipt/open status and never promises a refund. On duplicate, explain the existing case and return to transaction details; do not add a dead link to history/detail.
- Test form rules, outgoing-only eligibility (including API bypass attempts for incoming credits), focus/keyboard flow, labels/announcements, count, error mapping, pending state, key reuse/rotation, retained text, success and duplicate paths. Verify rendered UI at desktop and mobile viewports against the supplied screenshot references and record the viewports in acceptance evidence.
- **Acceptance:** form and row expansion match the supplied screenshots and approved UI rules at desktop and mobile widths; shared application styles are reused and no design drift is introduced on unrelated routes. Figma connector frame comparison is not required for this screenshot-based amendment.

## Task 7 — Update canonical documentation and diagrams

**Requirements:** DSP-SUB-01–06  
**Depends on:** Tasks 1–6

- Update `docs/design/api-contract.md` with the implemented POST request/response, validation, headers, replay, error precedence and codes.
- Update `docs/design/database-design.md` to record the actual additive migration constraints/indexes and idempotency recovery behavior. Update `docs/business-rules.md` only for clarifications needed to keep the approved baseline and implementation aligned.
- Update `docs/design/ui-flows.md` with the implemented entry action, Figma-referenced submission screen, validation/loading/error/success states, and explicit deferral of customer history until its feature.
- Update the existing PlantUML ER model and add/update the submission sequence diagram so diagrams reflect implemented persistence and transaction boundaries. Do not include agent workflow implementation details beyond the schema required for later features.
- **Acceptance:** API, database, user-flow and diagram artifacts describe the same implemented behavior; no speculative endpoint or unapproved scope is documented as implemented.

## Task 8 — Run feature and repository verification; record evidence

**Requirements:** DSP-SUB-01–DSP-SUB-06  
**Depends on:** Tasks 1–7

- Run the dispute unit, PostgreSQL/Testcontainers integration, security/HTTP, and Angular form/router tests; fix all feature regressions.
- Run the required backend and frontend verification commands from the repository guides/CI (backend Maven verify; frontend lint, CI tests and build). Report unavailable commands or environment limitations without claiming success.
- Verify the full backend/frontend test totals, migration validation, Figma desktop/mobile comparison and privacy assertions. Record exact commands, actual counts/results, known warnings and remaining limitations in `acceptance.md`.
- **Acceptance:** all required checks pass or are explicitly identified as blocked with reproducible evidence; implementation stops for feature acceptance review. Do not start customer-dispute-history or agent-workflow.
