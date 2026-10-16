# REFACTOR_PLAN.md — Transaction Dispute Portal

Task list for agents and for you. Rules live in `AGENTS.md`; this file is the work queue. Put it at `docs/REFACTOR_PLAN.md`.

How to use: do tasks **in order**, one task per commit series, tick the box when its acceptance checks pass. Every task also has the standing checks:

> formatter clean · `cd backend && mvn --batch-mode clean verify` green · `cd frontend && npm run build && npm run test:ci` green · API contract, error codes and headers unchanged (unless the task says otherwise).

Source of the findings: the repository inventory (architecture, conventions, scope, frontend/tooling, reviewer score card). **Nothing in the inventory was executed** (no build, no tests), so R-0 comes first.

---

## Phase 0 — Baseline (read-only, no code changes)

- [x] **R-0 Verify the facts.** (A placement audit covered 159 declarations.) Run and record: backend build + tests, frontend build + tests, `docker compose up -d --wait`. Report pass/fail counts, failures and the **compiler warning count**. Also answer, with evidence:
  1. Which classes are `final` **and** carry `@Transactional` / `@Async` / `@Cacheable`? (They cannot be CGLIB-proxied and should fail at startup; if they start, the classes are not really final.)
  2. Lombok version (needed for `@Builder(toBuilder = true)` on records).
  3. `git ls-files .agents .codex .kiro` (git does not track empty directories).
  4. Is `Dispute.release(...)`'s validated reason stored anywhere (event/audit), or discarded?
  5. Contents of `.kiro/steering/*` and where it conflicts with `AGENTS.md`.

**R-0 status.** Baseline: 217 backend tests passed (216 before the entity-scan test; no tests deleted); compiler warning count 0; Lombok 1.18.46; no `final` class that Spring must proxy; `.agents/` and `.codex/` have no tracked paths while `.kiro/` is tracked; the release reason is stored in `DisputeEvent.internalNotes`; no material steering conflict. Current frontend check: `npm ci`, `npm run build`, and `npm run test:ci` passed (117 tests); the host Node is 25.8.0 while `frontend/.nvmrc` specifies 22.23.3. Default Compose startup failed because host port 5432 was occupied; the full stack passed health checks with `POSTGRES_PORT=55432 PORTAL_PORT=18081`.

---

## Phase 1 — Delivery blockers (a reviewer will notice these first)

- [x] **R-1 Docker.** `backend/Dockerfile` (multi-stage JDK 21 → JRE 21, non-root, healthcheck on `/actuator/health/liveness`), `frontend/Dockerfile` (Node build → nginx, `/api` proxied to backend, SPA fallback), `.dockerignore` files, and `compose.yaml` extended to postgres + backend + frontend with health-based `depends_on`. Pin base image versions. Make the host database port configurable (`${POSTGRES_PORT:-5432}:5432`), because 5432 is often already taken on a reviewer's machine, and document it.
  *Accept:* `docker compose up --build` from a clean clone serves the app; backend container runs as non-root.
- [x] **R-2 Demo data (opt-in).** A compose `seed` one-shot service (or documented script) that runs the existing `account-admin`, `account-fixture`, `transaction-fixture` commands under an explicit demo profile. Demo credentials documented as demo-only. Default and production profiles still seed nothing.
  *Accept:* after `docker compose --profile demo up --build` a reviewer can log in as a customer and an agent and see data, including at least one dispute in each status.
- [x] **R-3 README reconciliation.** One authoritative root `README.md` (see `AGENTS.md` §4). Fix contradictions in `backend/README.md` (dispute/idempotency marked "reserved") and `frontend/README.md` (auth/business behavior "absent"); shrink both to short pointers. State assumptions: synthetic `accounts.balance`, CLI provisioning, same-origin deployment. Describe how to build, run and test.
  *Accept:* every command in the README was run and works; no statement contradicts the code.
- [x] **R-4 Hardening quick wins.**
  - Spring Security headers (`X-Content-Type-Options`, frame denial, referrer policy, a reviewed CSP, HSTS when secure).
  - `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase`.
  - Correlation id into **MDC** in `CorrelationIdFilter` (clear in `finally`) and into the log pattern.
  - Request-size cap for JSON bodies (a small filter returning 413 using the existing envelope; note that multipart settings do **not** limit JSON bodies).
  *Accept:* tests for each (headers present, 413 on oversized body, correlation id appears in log output).
- [x] **R-5 Login rate limiting.** Per-IP and per-email limiter on `POST /api/v1/auth/login`, returning `429 / 1005` with `Retry-After`, audit-logged. Decision needed (AGENTS §6.4): in-memory vs database-backed. Document the trade-off in the README.
  *Accept:* tests for threshold, reset, and that valid logins still work below the threshold.
- [x] **R-6 Single source for the idle timeout + exception mapping.** Replace the two `1800000` literals (`JdbcSessionActivityStore.renew`, `AuthenticationCleanup`) with a configured `Duration` property that also drives `spring.session.timeout`. Map the negative-access-time fault to a handled exception (not the 500 fallback).
  *Accept:* changing the property changes both behaviors; existing 21 security tests still pass; new test for the mapping.
- [x] **R-7 Repo hygiene.** Ignore local `.agents/` and `.codex/` folders; keep the `docs/profile/` scratch inventory out of version control; move `AGENTS.md` to the repo root; remove the empty `com.assessment.disputeportal.exception` source directory; remove `Welcome` and its route use only if confirmed unused; check `.gitignore`.
  *Accept:* `git status` clean; routes still redirect correctly (`entryRedirect`).

**R-7 status.** Root `AGENTS.md` is in place; empty source package removed; local inventory is ignored; no tracked or untracked scratch state is included. `Welcome` remains because root and wildcard routes use it and route tests cover its redirects. The Angular suite passed all 117 tests.

---

## Phase 2 — Mechanical quality (low risk, high visibility)

- [x] **R-8 Formatter and warnings.** Add Spotless to the backend build and `spotless:check` to CI. Format everything (separate commit). Fix the broken indentation in `JdbcSessionActivityStore.activate`. Reach **zero compiler warnings**: migrate the five `@SpyBean` uses to `@MockitoSpyBean` (Spring Boot 3.4+), fix the unchecked note in `AuthenticationConcurrencyTest`, and register Mockito as a Java agent in Surefire to remove the JDK self-attach warning. In `Dispute.release` the normalized reason is computed and discarded, while the service strips it again before writing `DisputeEvent.internalNotes`. Single-source it with one `Dispute.normalizeReleaseReason(String)` used by both; stored data and error messages stay identical.
- [x] **R-9 Lombok pass.** Replace manual `LoggerFactory` with `@Slf4j(topic = "...")` (keep the audit logger names exactly). Replace hand-written constructors that only assign fields. Add `@Builder(toBuilder = true)` to `Dispute` and `DisputeEvent` and rewrite `claim` / `release` / `resolve` with `toBuilder()`. Keep records and compact-constructor validation.
  *Accept:* `Dispute` unit tests unchanged and green; no 13-argument constructor calls remain.
- [x] **R-10 `final` audit.** R-0 found no `final` class that Spring must proxy. Nothing to change; the ArchUnit rule in R-20 keeps it that way.
- [x] **R-11 Controller cleanup.**
  - Class-level `@RequestMapping` everywhere; methods use sub-paths only (URLs identical).
  - Merge `DisputeSubmissionController` into `CustomerDisputeController`; merge the two agent controllers if it reads cleanly.
  - `LogoutController` is merged into `AuthController` (the earlier report said package-private, the placement audit says public; the merge makes it moot).
  - One ETag formatter next to `AgentIfMatch` (replaces the two copy-pasted templates).
  - `ApiEnvelope` becomes `ApiEnvelope<T>`; replace the two inline `PageResponse` records and `AccountPageResponse` / `TransactionPageResponse` with one `PageResponse<T>`.
  *Accept:* endpoint table in the API contract unchanged; contract tests green.

---

## Phase 3 — Structure and conventions

- [x] **R-12 Naming.** Dispute `*Dto` → `*Response`; align `Summary` / `Entry` / `Result` names; one canonical suffix scheme (`AGENTS.md` §2.5). IDE-safe renames only.
- [x] **R-13 Placement pass.** DONE: batches B1 to B8, commits `3347be2` to `6788665`, 217 tests green, exit checks pass, CLI commands verified.
- [x] **R-14 Entities to `model/entity`.** `AppUser`, `BankAccountEntity`, `BankTransactionEntity`. Update `@EntityScan` / `@EnableJpaRepositories` (including the CLI configuration classes). Enforce with ArchUnit (R-20) that only `repository` and `mapper` use entities.
- [ ] **R-15 Dead and duplicate models.** Decide for each, with tests: `BankTransaction` (used only by fixtures — merge with `TransactionReadRow` or move to the fixture package); `DisputeSubmissionResult` (wrapper around `JsonNode` — use a typed record); account feature Entity → Domain → DTO three-step (flatten only if it removes code without changing behavior).
- [ ] **R-16 Request validation.** Convert `SubmitDisputeRequest`, `LoginRequest` and the agent command bodies to records with Bean Validation; enable global `fail-on-unknown-properties`; delete the `@JsonAnySetter` traps and manual body checks; add the handler mapping to `400 / 1004`.
  *Accept:* every existing validation test passes **unchanged**, including unknown-field and malformed-body cases; error codes and messages identical.
- [ ] **R-17 `ErrorCode` enum.** One enum mirroring `api-contract.md` (code, HTTP status, type slug). `DisputeApiException` and the semantic exceptions carry it; `ApiExceptionHandler` reads from it. Domain records throw semantic exceptions instead of raw `IllegalArgument/StateException`. Add `ErrorCode` ↔ contract consistency test.

---

## Phase 4 — Quality gates and polish

- [ ] **R-18 Coverage gate.** JaCoCo in the backend with a risk-based minimum (e.g. 70% line, higher on `dispute`, `security`); publish the report in CI. Frontend: enforce a coverage threshold in Karma config.
- [ ] **R-19 Frontend tooling.** Add `angular-eslint`, `npm run lint`, and a CI step. Add `npm audit --omit=dev --audit-level=high` as a named CI step (document the six accepted dev-only advisories). Add one narrow Playwright journey: login → view transactions → submit dispute → see it in history.
- [ ] **R-20 ArchUnit guardrails.** Rules: controllers do not depend on repositories; no cross-feature repository/entity access; no cycles between feature packages; `model/dto` contains only DTO types; no `final` on `@Transactional` classes.
- [ ] **R-21 OpenAPI.** springdoc from code (works best after R-11's `ApiEnvelope<T>`); export the spec in CI; keep it consistent with `api-contract.md`; document Swagger UI location in the README.
- [ ] **R-22 Frontend consistency.** One canonical `DisputeStatus` / `DisputeReason` / `TransactionDirection` in `shared/models/` (remove `AgentDisputeStatus`, `AgentReason`, inline `'IN' | 'OUT'`). API services unwrap the envelope consistently. Audit every `@for` for `track`. `OnPush` on the four remaining components. Rename `*-contracts.ts` → `*-models.ts`. Split `Disputes` (231 lines) if it reads cleaner.
- [ ] **R-23 Reconcile steering files.** Fix two stale statements (`api-contracts.md` calls the implemented dispute account filter "later"; `product.md` says "seeded" while ruling out automatic seeds) and align `.kiro/steering/*` with `AGENTS.md` (Dockerfiles, Bean Validation, hardening, CI gates) or remove them.
- [ ] **R-25 Optional, ask first: resolve the `Account` name clash.** `auth` has `Account`, `AccountMapper`, `AccountRepository`, `AccountLookup` (login users) while the `account` feature means bank accounts. Rename the auth side (for example `UserAccount*`) or the bank side (`BankAccount*` is already used for the model). Wide rename; do it in its own commit with the compiler as the guide.
- [ ] **R-24 Optional, only with time and strong tests.** Extract `DisputeIdempotencyGuard` from `DisputeSubmissionService`; split the 317-line `JdbcCustomerDisputeReadRepository` query composition. High risk to concurrency behavior; do not start without owner approval.

---

## Final submission check

- [ ] `docker compose up --build` from a fresh clone works; demo path documented and verified.
- [ ] `mvn clean verify` and `npm run build && npm run test:ci` green on CI (link the run).
- [ ] Zero warnings; formatter and lint clean; no TODOs, dead code, empty tool folders.
- [ ] README commands all executed and accurate; assumptions and trade-offs written down.
- [ ] You can explain, in one or two sentences each: the idempotency design, the session/auth_version design, why JDBC for disputes, why records, and why the agent workflow exists.

- [ ] **R-26 Dispute event queue (transactional outbox, PostgreSQL).** Owner decision: build it. **Blocked until R-1 to R-8 are done** (Docker, README, hardening, formatting). Touches transaction boundaries in `DisputeSubmissionService` and `AgentDisputeCommandService`, so get explicit approval before editing them (`AGENTS.md` §0.7).
  - **Purpose:** reliable, asynchronous, simulated customer notifications for `DISPUTE_SUBMITTED`, `DISPUTE_CLAIMED` (optional) and `DISPUTE_RESOLVED`, with no dual-write problem.
  - **Package:** top-level `outbox/` (cross-cutting, like `idempotency/`): `OutboxEvent` (record), `OutboxRepository` + `JdbcOutboxRepository`, `OutboxRelay` (`@Scheduled`), `NotificationHandler` (interface) + `LoggingNotificationHandler` (simulated), `OutboxProperties` (`@ConfigurationProperties` record: batch size, poll interval, max attempts, backoff base, retention).
  - **Schema (Flyway V8):** `outbox_events(id uuid pk, aggregate_type, aggregate_id uuid, event_type, payload jsonb, status ('PENDING','SENT','FAILED'), attempts int, next_attempt_at timestamptz, created_at, processed_at, last_error)`; partial index on `(next_attempt_at) WHERE status = 'PENDING'`; CHECK on status values.
  - **Write path:** `OutboxRepository.append(...)` is called **inside the same transaction** that writes the dispute and its `dispute_events` row. Rollback means no event.
  - **Payload:** ids, status and event type only. No account numbers, names, notes or release reasons (invariant 7).
  - **Relay:** claim a batch with `SELECT ... WHERE status='PENDING' AND next_attempt_at <= now() ORDER BY created_at LIMIT :n FOR UPDATE SKIP LOCKED`; call the handler; mark `SENT`; on failure increment `attempts`, set `next_attempt_at` with exponential backoff and store `last_error`; after max attempts set `FAILED`. Delivery is **at-least-once**, so handlers dedupe on the event id.
  - **Operations:** Micrometer gauges for pending count and oldest-pending age, a failure counter, a retention job for old `SENT` rows, graceful stop of the relay on shutdown, and a log line carrying the correlation id.
  - **Tests (Testcontainers):** event committed with the dispute and absent after rollback; two relays never process the same row; crash between send and mark leads to a redelivery that the handler tolerates; retry with backoff then `FAILED`; relay ignores rows not yet due; no PII in payloads.
  - **README:** short section on why an outbox (atomicity, no extra infrastructure), at-least-once semantics, polling latency and throughput limits, and the next step at scale (change-data-capture feeding a broker).
  - **Alternative:** Spring Modulith's JDBC event publication registry or db-scheduler instead of the hand-rolled relay. Check current versions and Spring Boot compatibility first. Pick one approach and document why.
  *Accept:* all tests above green; `docker compose up --build` still works; no change to existing API responses or error codes.

---

## Appendix A — Placement map (audited)

Source: placement inventory of all 159 declarations in `backend/src/main/java` (about 56 were misplaced). Paths are relative to `com/assessment/disputeportal/`. Use IDE refactoring (imports and scanning follow), one batch per commit, build green after each. Nested types move with their parent unless a row says "own file".

### B1 — `common`
| Type | From | To |
|---|---|---|
| `ApiEnvelope` + nested `ApiError` | `common/` | `common/api/` (`ApiError` as its own file) |
| `ApiExceptionHandler`, `ApiErrorWriter`, `StoreFailureClassifier` | `common/` | `common/web/` |

### B2 — Entities (update scanning in the same commit)
| Type | From | To |
|---|---|---|
| `AppUser` | `auth/model` | `auth/model/entity` |
| `BankAccountEntity` | `account/repository` | `account/model/entity` |
| `BankTransactionEntity` | `transaction/repository` | `transaction/model/entity` |

Also update: `@EntityScan` / `@EnableJpaRepositories` in `AccountAdminConfiguration`, `AccountFixtureConfiguration`, `TransactionFixtureConfiguration` and the main application. Add an integration test that fails if an entity is not scanned.

### B3 — Mappers
| Type | From | To |
|---|---|---|
| `BankAccountMapper` | `account/repository` | `account/mapper` |
| `AccountNumberMask` | `account/` | `account/mapper` |

### B4 — Query objects and projections
| Type | From | To |
|---|---|---|
| `DisputeQuery`, `AgentDisputeQuery` | `dispute/model/dto` | `dispute/model/query` |
| `StatusPredicate` (+ nested `None`, `Individual`, `InList`) | `dispute/model/dto` | `dispute/model/query` |
| `AgentQueue` (enum) | `dispute/model/dto` | `dispute/model` |
| `EventProjection` | `dispute/model/dto` | `dispute/model/projection` |
| `TransactionQuery` (+ `SortField`, `SortDirection`) | `transaction/model` | `transaction/model/query` |
| `TransactionPage` | `transaction/model` | `transaction/model/projection` |
| `TransactionReadRow` | `transaction/repository` | `transaction/model/projection` |

### B5 — Parsers move to the web layer (package-private)
| Type | From | To |
|---|---|---|
| `AgentIfMatch` | `dispute/service` | `dispute/controller` (also owns ETag formatting) |
| `DisputeQueryParser` (+ `SearchPatterns`), `AgentDisputeQueryParser` | `dispute/service` | `dispute/controller` |
| `TransactionQueryParser` | `transaction/service` | `transaction/controller` |

### B6 — Nested types get their own file
| Nested type | From | To |
|---|---|---|
| `Credentials` | `AccountCredentialsRepository#Credentials` | `auth/model/projection/Credentials` |
| `IdempotencyResult` | `DisputeIdempotencyRepository#IdempotencyResult` | `idempotency/IdempotencyResult` |
| `SubmissionTransaction` | `DisputeTransactionPort#SubmissionTransaction` | `transaction/port/SubmissionTransaction` |
| `Snapshot` | `SessionActivityStore#Snapshot` | `security/session/SessionActivitySnapshot` (do together with B8) |

### B7 — Fixtures and operator provisioning into `cli`
| Type | From | To |
|---|---|---|
| `AccountFixtureService`, `AccountFixtureException` | `account/service` | `account/cli` |
| `AccountFixtureRepository` (+ `FixtureRow`) | `account/repository` | `account/cli` |
| `AccountFixturePort` | `account/port` | `account/cli` (stays public; the transaction fixture uses it) |
| `TransactionFixtureService` (+ `FixtureDefinition`) | `transaction/service` | `transaction/cli` |
| `TransactionFixtureRepository` (+ `FixtureRow`) | `transaction/repository` | `transaction/cli` |
| `AccountAdministrationService` | `auth/service` | `auth/cli` |

Update the `@Import` lists in the three `*Configuration` classes. `AccountAdministrationService` is operator provisioning (it asserts it is never run inside an existing transaction), so it follows the same rule as fixtures. If the owner prefers to keep it in `auth/service`, strike that row and edit `AGENTS.md` §1.1 instead of leaving the two files in conflict.

### B8 — `security/activity` split
| Type | From | To |
|---|---|---|
| `SessionActivityStore`, `JdbcSessionActivityStore` | `security/activity` | `security/session` (rename to `*ActivityRepository` is optional) |
| `AuthenticationCleanup` | `security/activity` | `security/session` |
| `AuthenticationActivityInterceptor`, `AuthenticatedActivity` | `security/activity` | `security/web` |
| `AuthenticationActivityConfiguration`, `SchedulingConfiguration` | `security/activity` | `security/config` |

Delete the empty `security/activity` package afterwards. Combine with R-6 only if the commits stay separate.

### Moves bundled with other tasks (not pure placement)
| Type | Move | Task |
|---|---|---|
| `AccountPageResponse`, `TransactionPageResponse`, `AccountQueryService#AccountPage`, `CustomerDisputeController#DisputePageResponse`, `AgentDisputeController#PageResponse` | replaced by `common/api/PageResponse<T>` (JSON shape unchanged) | R-11 |
| `DisputeApiException` | `dispute/exception` → `common/exception`, `ErrorCode`-based; delete the dispute exception package when empty | R-17 |
| `DisputeSubmissionResult` | typed record in `dispute/model/dto`, or removed | R-15 |

### Non-placement items (already in the right package)
- `CustomerDecision` stays in `dispute/model/dto` (response sub-object).
- Controller merges (`DisputeSubmissionController` + `CustomerDisputeController`, the two agent controllers, `LogoutController` + `CsrfController` into `AuthController`) are readability tasks (R-11), not placement.
- `DisputeIdempotencyRepository` / `JdbcDisputeIdempotencyRepository` are already in the right package; dropping the `Dispute` prefix is a naming task (R-12).
- `AuthController.toResponse` → `auth/mapper` is a method extraction (R-11), not a class move.
- The empty root `exception` directory is hygiene (R-7).
- `BankTransaction` is correctly placed; whether it should exist is R-15.

### Exit check
After B1 to B8: `model/dto` holds only request/response types, no `repository` package holds an entity, mapper or record, no `service` package holds parsing, fixtures or provisioning, `common` root has no classes, and `security/activity` is gone. ArchUnit (R-20) enforces all of it.
