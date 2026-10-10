---
inclusion: fileMatch
fileMatchPattern: ["backend/**/*.java", "backend/**/*.sql", "backend/pom.xml", "backend/**/application*.yml", "backend/**/application*.yaml"]
---
# Java / Spring / PostgreSQL standards
- Use constructor injection, small focused classes, descriptive names, immutable DTO records where practical.
- Keep controllers thin; services own @Transactional boundaries; domain methods validate state changes; persistence handles database constraints.
- JPA entities should not leak into REST responses; avoid accidental lazy-loading/N+1 and Open Session in View dependency.
- Validate external 1-based pagination; adapt to zero-based PageRequest internally. Use authorization-scoped totals, parameterized combined filters/search, allowlisted stable sorting and the API contract bounds.
- Flyway is sole schema authority; use `ddl-auto: validate`, not `update` or `create` for normal runs.
- PostgreSQL constraints/indexes enforce uniqueness and referential integrity. Handle unique constraint and optimistic locking failures explicitly.
- Idempotency for POST dispute submission: unique (user, operation, key), canonical request fingerprint, replay same response on identical retry, 409 on changed payload; concurrent requests must be safe.
- Return the custom application/json envelope and stable numeric codes from docs/design/api-contract.md, including security/filter errors. Map internal ProblemDetail into it; never advertise strict RFC 9457 compliance. Keep meaningful HTTP statuses; 422 for non-completed transactions; approved agent preconditions: 428 missing, 400 malformed, 412 stale/lost guarded-write race, 409 matching-version state/assignment conflict.
- Recover concurrent creation retries after rollback in a fresh transaction; distinguish identical-key replay from different-key duplicates. Never coordinate replicas with instance-local locks. Append audit synchronously in the same transaction, then return success after commit.
- Favor explicit mappings, simple SQL and targeted tests over reflection-heavy frameworks or speculative abstraction.
- Log request correlation IDs and operation outcomes without leaking secrets or customer-sensitive fields.
