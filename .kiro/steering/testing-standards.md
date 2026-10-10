---
inclusion: auto
name: testing-quality
description: Writing or reviewing unit, integration, security, concurrency, idempotency, Angular component, or end-to-end tests.
---
# Testing and quality gates
- Prefer test-first for domain invariants and regression bugs. Arrange-Act-Assert, deterministic fixtures and no dependence on test execution order.
- Domain tests: eligibility, duplicate, all state transitions, unauthorized agent mutations, terminal states, required notes.
- Integration tests with real PostgreSQL/Testcontainers: ownership isolation, database uniqueness, idempotency replay/key mismatch, concurrent claims, optimistic locking, audit atomicity/rollback.
- Security tests: 401 missing/invalid/expired server-side session, 403 wrong role, 404 customer cross-account access, no internal notes in customer responses.
- Angular tests: form validation, transaction eligibility, status labels, role navigation, API errors, loading and empty states.
- Add contract tests for numeric envelopes, non-200 HTTP errors (including security filters), 1-based paging/bounds, scoped totals, empty results, combined query predicates and sort allowlists; concurrency tests must cover same-key replay, key mismatch, different-key duplicates, two backend service instances and failed audit rollback.
- Agent concurrency acceptance: two same-version claims produce exactly one 200 and one 412; competing release/resolve and resolve/resolve commit at most one change/event. Test missing 428, malformed 400 (weak/wildcard/list/duplicate/overflow tags), stale or wrong-resource tag 412, current-tag invalid state/assignment 409, full rollback after forced audit failure and cross-instance coordination. Verify refreshed ETag/version/event order and stable serialized representation; never claim these runtime tests executed before implementation.
- Test retained resolving-agent/assigned_at in terminal mine queue, terminal mutation rejection, and complete assignee/actor redaction from customer DTOs/errors. Authentication tests must cover 30-minute idle expiry and activity handling, enabled/current-role changes, shared JDBC session recognition across backend instances, valid-session reload restoration, logout invalidation and private-cache clearing. Test production HttpOnly/Secure/SameSite=Lax cookies and CSRF rejection for unsafe requests including login/logout. Exact endpoints, CSRF error codes and provisioning tests await authentication design; no public registration.
- Keep CI reproducible: backend tests, frontend lint/build/tests, Docker build, migration validation.
- Never claim tests passed without executing them. Report exact commands, results and any unverified checks.
