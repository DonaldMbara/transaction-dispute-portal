---
inclusion: auto
name: dispute-domain
description: Dispute eligibility, state transitions, customer ownership, agent assignment, audit history and dispute submission rules.
---
# Dispute domain invariants
- Transaction status: PENDING, COMPLETED, FAILED. Only COMPLETED can be disputed.
- One dispute per transaction for its lifetime. Enforce with database UNIQUE(transaction_id), not just application checks.
- State machine: OPEN (unassigned) -> UNDER_REVIEW (assigned on claim) -> OPEN (release) OR ACCEPTED/REJECTED (terminal resolution). No reopen.
- Retain resolving agent and assigned_at on terminal cases (approved); terminal state/assignment/texts immutable. Never expose agent assignment or identity in customer DTOs, errors or timelines. Mine queue includes retained terminal cases.
- Only assigned agent may release or resolve. Release requires reason. Resolution requires decision, internal notes and customer-facing message.
- Record CREATED, CLAIMED, RELEASED, RESOLVED_ACCEPTED, RESOLVED_REJECTED events append-only with actor, timestamp, transition, assignment changes, and appropriate notes.
- Dispute state and audit event commit in the same database transaction. Never publish an event before commit.
- Use @Version/conditional writes for agent races within the state+audit transaction. Follow the approved API precedence: 428 missing precondition, 400 malformed precondition, 412 stale tag or lost write race, 409 invalid state/assignment with current version. GET detail and successful mutations return strong aggregate-version ETag; action URIs require the parent detail tag. Preserve the representation semantics in the API contract.
- Record disputeVersion on each audit event for causal ordering; customer release history uses a generic RETURNED_TO_QUEUE milestone with no internal reason.
- Store internal notes separately from customer-visible message; redact internal fields from customer DTOs and timeline.
- ACCEPTED is a decision only; no refund, reversal or settlement side effects.
- All dates stored as instants; all monetary values decimal, never double.
