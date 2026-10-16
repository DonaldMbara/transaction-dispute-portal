# AGENTS.md — Transaction Dispute Portal

Java 21 / Spring Boot 3 · PostgreSQL 17 · Angular 20. This is a **take-home assessment for a bank (Software Engineer, Java)** and the only evidence reviewers will use. It will be read line by line and discussed in an interview. Optimize for **clarity, correctness, and consistency**, not volume or cleverness.

Read in this order before touching code: this file → `docs/REFACTOR_PLAN.md` (task list) → `docs/design/api-contract.md` (API truth) → `.kiro/steering/*` (older rules; this file wins on conflict).

---

## 0. Ground rules

1. **Refactoring means structure and clarity only.** Behavior, URLs, JSON shapes, headers, error codes and DB schema stay exactly as they are. If a task needs a contract change, stop and ask.
2. **Small, reviewable steps.** One concern per commit. Moves/renames/reformatting are separate commits from logic changes.
3. **Tests before risk.** If code has no test, add a characterization test first. Never leave the build red.
4. **Never claim something passed without running it.** Report the exact command and result.
5. **No speculative abstractions.** Extract only for real duplication (3+ uses) or a clear responsibility boundary.
6. **Match what exists.** Follow the conventions in this file even where you would prefer another style. Changing a convention is a decision for the owner (see section 7).
7. **Ask first** (do not just do): changes to public API/error codes/schema; auth, session, CSRF code; idempotency or transaction boundaries in `DisputeSubmissionService`; deleting a feature (agent workflow, CLI commands); adding or upgrading dependencies; anything touching SQL ownership predicates.

Commands (run from repo root):

```
cd backend  && mvn --batch-mode clean verify      # needs Docker (Testcontainers)
cd frontend && npm ci && npm run build && npm run test:ci
docker compose up -d --wait                       # PostgreSQL for local dev only
```

---

## 1. Project facts (from the repository inventory)

**Stack:** Maven 3.9, Java 21, Spring Boot 3, Spring Security + Spring Session JDBC, Flyway (V1–V7), PostgreSQL 17, Lombok, Testcontainers. Frontend: Angular 20 (standalone only, signals + RxJS), TypeScript 5.9 strict, Karma/Jasmine, Prettier, Node from `frontend/.nvmrc`.

**Architecture:** modular monolith, feature-first, layers inside each feature. Light read/write split in `dispute` and `transaction` (separate read and write repositories). No message bus, no separate read store.

**Backend layout (keep):**

```
com.assessment.disputeportal
├── <feature>/                account | auth | dispute | transaction
│   ├── controller/           one controller per resource
│   ├── service/              use cases, @Transactional boundaries
│   ├── repository/           *Repository interfaces + Jdbc*/Jpa* implementations
│   ├── mapper/               entity/row -> domain/DTO
│   ├── model/                domain models (records) and enums
│   │   ├── dto/              request/response objects ONLY
│   │   ├── entity/           @Entity classes (target; plan R-14)
│   │   ├── projection/       read rows / query projections (EventProjection, TransactionReadRow)
│   │   └── query/            search/filter criteria objects (*Query)
│   ├── port/                 narrow cross-feature contracts (e.g. DisputeTransactionPort)
│   ├── exception/            rare; prefer common exceptions
│   ├── validation/           (auth) PasswordPolicy, EmailNormalizer
│   └── cli/                  explicit non-web commands and everything only they use (provisioning and fixture services, repositories, ports, exceptions)
├── common/
│   ├── api/                  ApiEnvelope<T>, ApiError, PageResponse<T>, ErrorCode
│   ├── web/                  ApiExceptionHandler, ApiErrorWriter, StoreFailureClassifier
│   └── exception/            semantic exceptions
├── idempotency/              durable request replay (dispute submission)
├── security/                 config (security, session, scheduling), session (validator, principal,
│                             session-activity repository + cleanup), authentication, web (filters, interceptor)
```

**API:** all under `/api/v1`. Envelope: `{status, data, error_code, error{type,title,detail}}`.
Codes in use: 1001 auth (401) · 1002 forbidden (403) · 1003 not found (404) · 1004 invalid request (400) · 1007 unsupported media type · 1009 not acceptable · 1010 CSRF (403) · 2002 ineligible transaction (422) · 2003 duplicate dispute (409) · 2004 idempotency payload mismatch (409) · 2006 stale ETag (412) · 2007 If-Match required (428) · 2008 conflict (409) · 3001 unexpected (500) · 3002 store unavailable (503). 1005 rate limited (429) is reserved. Source of truth: `docs/design/api-contract.md`.

**Roles:** `CUSTOMER` (accounts, transactions, submit/view own disputes), `AGENT` (queue, claim, release, resolve). Dispute lifecycle: `OPEN → UNDER_REVIEW → ACCEPTED | REJECTED` (terminal).

### 1.1 Where does it go? (placement rules, apply to every new and moved class)

| It is... | It goes in |
|---|---|
| JPA `@Entity` / `@MappedSuperclass` | `<feature>/model/entity` |
| Domain record/class with behavior or invariants, enums | `<feature>/model` (root) |
| Request or response body | `<feature>/model/dto` |
| Search/filter/sort criteria object | `<feature>/model/query` |
| Read row or query projection | `<feature>/model/projection` |
| Entity/row/domain → DTO conversion (incl. value helpers such as account-number masking) | `<feature>/mapper` |
| Spring Data or JDBC data access | `<feature>/repository` (`*Repository` interface + `Jdbc*`/`Jpa*` implementation) |
| Use case, business rule, transaction boundary | `<feature>/service` |
| Query-parameter or header parsing for one controller (e.g. `AgentIfMatch`, `*QueryParser`) | `<feature>/controller`, package-private. It is web-binding code, not a service. |
| Contract another feature consumes | `<feature>/port` (own file, not a nested type) |
| Seed/fixture/provisioning code (service, repository, port, exception, command, configuration) | `<feature>/cli` only. Never in `service`/`repository`. |
| Envelope, error, generic page, error codes | `common/api` |
| Global exception handler, error writer, failure classifier | `common/web` |
| Filter, MVC interceptor, session validation, session-activity persistence | `security/web` · `security/session` |
| Cross-cutting infrastructure with no feature owner (idempotency) | its own top-level package, named for what it is, not for one feature |

Rules: no nested public records/interfaces inside controllers, services or repository interfaces (give them a file); no entity in `repository/` or the model root; no non-DTO in `dto/`; no class named after one feature when it serves several; a package never mixes data access, web interception and scheduling. The audited class-by-class map is in `docs/REFACTOR_PLAN.md`, Appendix A (batches B1 to B8).

### Non-negotiable invariants (never weaken, never refactor away)

1. Identity comes from the server session, never from request parameters. Ownership is enforced **in SQL predicates**; a non-owned resource is **404**, not 403.
2. Backend authorization is authoritative; Angular guards are navigation aids. `SecurityConfiguration` lists explicit matchers and ends with `anyRequest().denyAll()`.
3. Opaque HttpOnly JDBC sessions, cookie path `/api/v1`, `Secure` on by default, CSRF on all unsafe requests, **no tokens in localStorage/sessionStorage**, CSRF token only in memory.
4. Session is re-validated per request against `app_users` (`enabled`, `role`, `auth_version`) and idle time is enforced server-side.
5. Dispute state change + audit event (`dispute_events`) are written in the **same transaction**. Writes are version-guarded (`WHERE id=? AND version=?`).
6. `Idempotency-Key` is required on `POST /disputes`; `If-Match` (strong ETag) is required on agent mutations.
7. Customer-facing responses never contain actor IDs, internal notes or release reasons. Full account numbers appear only on the owner-scoped accounts endpoint; masked everywhere else, never in logs or errors.
8. SQL: named parameters only, allowlisted sort columns, `COUNT(*)` uses the same predicate as the data query.
9. Flyway is the sole schema authority (`ddl-auto: validate`). **Never edit an applied migration**; add V8+.
10. Integration tests use isolated Testcontainers PostgreSQL, never the developer compose database.
11. Operator provisioning and fixtures are explicit, non-web commands. No default users in the default or production profile.
12. The browser does not auto-retry unsafe requests.

---

## 2. Backend conventions

### 2.1 Layer responsibilities

| Layer | Does | Must not |
|---|---|---|
| Controller | HTTP only: bind, one service call, map, wrap in `ApiEnvelope`, set status/headers | business rules, repository access, `@Transactional`, SQL |
| Service | use cases, business rules, transaction boundaries, ownership checks | HTTP types (`HttpServletRequest/Response`), building JSON envelopes |
| Repository | data access only (JDBC or Spring Data) | business decisions, calling services |
| Mapper | conversion only | logic, I/O |
| Domain record | invariants and legal state transitions | persistence, Spring |

Cross-feature access goes through a **port** or a service. Never reach into another feature's repository or entity.

### 2.2 Java style: records and Lombok (project standard)

| Kind | Style |
|---|---|
| Immutable DTOs, query/filter objects, value objects, domain aggregates | **Java records.** Do not convert them to Lombok classes. |
| Records with 6+ components or transitions that copy most fields (`Dispute`, `DisputeEvent`) | Add `@Builder(toBuilder = true)` (Lombok 1.18.20+; confirm the project version). Validation stays in the compact constructor. Transitions use `toBuilder()` instead of long positional constructor calls. |
| Spring beans (services, repositories, controllers, filters, mappers) | `@RequiredArgsConstructor` with `private final` fields. A hand-written constructor only when it does real initialization; add a one-line comment saying why. |
| Logging | `@Slf4j`. Named audit loggers: `@Slf4j(topic = "authentication.audit")`. No manual `LoggerFactory` calls. |
| JPA entities | `@Getter @NoArgsConstructor(access = PROTECTED)`; `@Setter` only on fields that really change. Never `@Data`. `equals/hashCode` on id only; never include lazy relations in `toString`. |
| Utility classes | private constructor via `@NoArgsConstructor(access = PRIVATE)` |

Never use field injection. Never hand-write getters/setters/loggers that Lombok can generate.

`final`: fine on plain beans, records and filters. **Never on a class Spring must proxy** (`@Transactional`, `@Async`, `@Cacheable`, `@Validated`, method security). CGLIB cannot subclass a final class.

### 2.3 Persistence

- **Why JDBC exists here:** dispute, transaction and session-activity queries need CTEs, `FOR UPDATE`, `SET LOCAL`, dynamic filters and window functions. Keep them JDBC (`NamedParameterJdbcTemplate`). Spring Data JPA is for simple CRUD (`AppUser`, `BankAccountEntity`). Do not rewrite JDBC to JPA.
- Convention: `*Repository` interface + `Jdbc*` / `Jpa*` implementation; services depend on the interface. Do not add an adapter on top of an adapter. Do not add an interface for a class with no consumer outside its feature.
- Do not return API DTOs from **new** repositories; return a read projection. (Existing dispute reads map straight to DTOs. Do not churn them unless a task says so.)
- Multi-line SQL uses Java text blocks. No repeated column lists via string concatenation; one constant per query. Import types; no inline fully-qualified names.
- Entities: `LAZY` by default, `@Enumerated(STRING)`, pagination always (1-based `page`/`size`, default 20, max 100).
- Seed or demo data never lives in production repositories.

### 2.4 Transactions

- Reads: `@Transactional(readOnly = true)` on the service method or class. Writes: `@Transactional` on the service method.
- `DisputeSubmissionService` uses a programmatic `TransactionTemplate` (`REQUIRES_NEW`) on purpose (lock/statement timeouts, idempotency race recovery). Do not simplify it without asking.
- `JdbcSessionActivityStore` uses `REQUIRES_NEW` on purpose. Keep it.
- No remote calls inside a transaction. Keep transactions short.

### 2.5 Request validation and DTOs

- Request bodies are records with Bean Validation (`@NotNull`, `@NotBlank`, `@Size`) and `@Valid` at the controller. Unknown JSON fields are rejected globally (`spring.jackson.deserialization.fail-on-unknown-properties=true`), **not** with per-class `@JsonAnySetter` traps.
- `MethodArgumentNotValidException` and unreadable-body errors map to the existing `400 / 1004` envelope. Error codes and message shape must not change.
- Limits (2000, 4000, …) are defined once in a constants holder and used by DTO `@Size`, the domain record and the service. No repeated literals.
- Business-rule validation lives in the service or the domain record, not the controller.
- Query-parameter parsing: prefer a typed record bound with `@ModelAttribute` + `@Valid` and allowlist enums. Hand-written `*QueryParser` classes are tolerated where they exist; do not add new ones.
- DTO naming: `<Action><Thing>Request`, `<Thing>Response`, list rows `<Thing>ListItemResponse`. Target: replace the `*Dto` suffix in `dispute` with `*Response`. No `Summary`/`Entry`/`Result` mix for the same role.
- Page wrapper: one generic `PageResponse<T>` in `common`; no inline per-controller page records.
- `model/dto` holds DTOs only. Enums, projections and query objects (`AgentQueue`, `StatusPredicate`, `EventProjection`, `*Query`) do not belong there; they go to `model/`, `model/query` or `model/projection`. Header and query-parameter parsers (`AgentIfMatch`, `*QueryParser`) belong in the `controller` package, not `service`.

### 2.6 Exceptions and error codes

- One `@RestControllerAdvice` (`ApiExceptionHandler`). Filter-layer errors go through `ApiErrorWriter` using the same envelope.
- Define every code once in an **`ErrorCode` enum** (numeric code, HTTP status, problem `type` slug) that mirrors `api-contract.md`. Exceptions carry an `ErrorCode`; the handler reads status and code from it. Do not create a new exception class per scenario.
- Semantic exceptions: `InvalidRequestException`, `ResourceNotFoundException`, `BusinessConflictException`, `InvalidOperationException`, `DomainInvariantException`, plus `DisputeApiException` (dispute business codes). Add a class only if callers must catch it specifically.
- Domain records throw the semantic exceptions, not raw `IllegalArgumentException` / `IllegalStateException`, so rule violations never surface as 500.
- Infrastructure or framework faults are not domain invariants: do not throw `DomainInvariantException` for bad framework input.
- Never swallow exceptions; never return null or a magic value to signal failure; no stack traces in responses.

### 2.7 Controllers

- `@RestController` + **class-level `@RequestMapping("/api/v1/<resource>")`**; methods use only the sub-path (`@PostMapping("/login")`). Never repeat the prefix per method.
- One controller per resource: `POST /disputes` and `GET /disputes` belong in one `CustomerDisputeController`; agent endpoints in one agent controller.
- Return typed `ApiEnvelope<T>`, never a raw or `Object`-typed envelope.
- Use `@AuthenticationPrincipal SessionPrincipal`; do not call `SecurityContextHolder` in controllers.
- No mapping helpers inside controllers; use a mapper.
- Shared strings that are part of the API contract (ETag format, header names) have one definition, with the formatter next to the parser.
- Controllers are `public`; apply the `final` rule from 2.2.

### 2.8 Security and sessions

- The custom session-activity layer (`JdbcSessionActivityStore`, filter, interceptor, cleanup) is **deliberate and tested** (21 test files). Keep it. Do not extend it. Refactor only:
  - one `Duration` configuration property drives Spring Session timeout **and** the SQL/cleanup deadline (no `1800000` literals);
  - map framework-input faults to a handled exception instead of a 500;
  - fix formatting; document the design and its coupling to `spring_session` in the README.
- Passwords: PBKDF2 via `VersionedPasswordEncoder`; keep the dummy-hash timing mitigation.
- Outstanding hardening (see plan): security headers, graceful shutdown, MDC correlation id, request-size cap, login rate limiting.

### 2.9 Logging, configuration, constants

- Correlation id: set in `CorrelationIdFilter` **and put in SLF4J MDC** (clear in `finally`); include it in the log pattern. Structured JSON logging is a plus.
- Configuration via `@ConfigurationProperties` records, environment variables with safe defaults; no secrets committed. Profiles: default and `local` (cookie `Secure=false` only).
- No magic numbers or strings: constants live next to their owner, one definition each.
- Money is `BigDecimal` with currency; time is `Instant` / `OffsetDateTime` (UTC).

### 2.10 Formatting and static checks (zero tolerance)

- Backend: Spotless (google-java-format or the IntelliJ style via `.editorconfig`), enforced in CI with `spotless:check`. Mixed or broken indentation must never be committed.
- Zero compiler/IDE warnings. No unused imports, variables, parameters or computed-but-discarded values.
- Run the formatter before every commit; reformatting existing files is its own commit.
- No commented-out code, no `TODO` left behind, no `System.out`.

### 2.11 Testing

- Unit tests (JUnit 5, AssertJ, Mockito) for services, mappers, domain records, parsers. No Spring context.
- Slice tests: `@WebMvcTest` for controllers + security, repository tests against Testcontainers PostgreSQL.
- Keep the security, concurrency and privacy test depth. Never delete a test to make a refactor pass.
- Names describe behavior: `shouldReturn404_whenTransactionBelongsToAnotherCustomer`.
- Add ArchUnit rules (plan R-20): controllers do not depend on repositories; no cross-feature repository/entity access; no cycles between features; `model/dto` contains only DTOs.
- Coverage: JaCoCo with a risk-based gate (plan R-18).

---

## 3. Frontend conventions (Angular 20 — match the repo)

- **Standalone only**, lazy routes via `loadComponent`. No NgModules.
- **File and class naming (current Angular style, already used here):** kebab-case files with no `.component` suffix and no `Component` suffix on classes (`disputes.ts` → `Disputes`, `case-queue.ts` → `CaseQueue`). Services `*-api.ts` / `auth-service.ts`. Types in `*-models.ts` (retire `*-contracts.ts`). Specs co-located (`*.spec.ts`).
- **Layout:** `core/` (auth, http: interceptors, guards, singletons) · `shared/` (components, models, utils; no feature imports) · `features/<role>/<area>/` · `layouts/`. Features never import from other features; shared code goes to `shared/`.
- **State:** signals for local state, RxJS for HTTP (`takeUntilDestroyed`, `switchMap` for cancellable flows). No NgRx. **URL query params are the single source of truth** for filters, sort, tab and page. Keep the **generation-counter** pattern to drop stale responses.
- **HTTP:** API calls only in `*-api` services, never `HttpClient` inside components. Interceptors act only when `isOwnApi(url)`. API services unwrap `Envelope<T>` and return the data (Option A). Only agent commands return `HttpResponse` (for ETag). Keep `csrfInterceptor` + `withNoXsrfProtection()`.
- **Types:** one canonical `DisputeStatus`, `DisputeReason`, `TransactionDirection` in `shared/models/`; import them everywhere. No duplicated unions. No `any` (keep zero).
- **Components:** `OnPush` everywhere; `@for` always has `track <stable id>`; control flow syntax (`@if`, `@for`); no function calls in templates; split components over ~200 lines (`Disputes`, 231, is the first candidate).
- **Forms:** typed reactive forms; keep idempotency-key rotation logic exactly as is.
- **Quality:** add ESLint (`angular-eslint`) and run `npm run lint` in CI; keep Prettier; add `npm audit --omit=dev --audit-level=high` to CI.
- **UX:** loading, empty and error states on every list; accessible (labels, keyboard, focus); remove the unused `Welcome` component.
- **Never** store tokens or user data in browser storage.

---

## 4. Assessment delivery checklist (a missing item is a failed submission)

- [ ] `backend/Dockerfile` (multi-stage Temurin 21 JDK build → JRE runtime, non-root, healthcheck, pinned versions) and `frontend/Dockerfile` (Node build → nginx; `/api` proxied to backend). `.dockerignore` for both.
- [ ] `compose.yaml` runs **PostgreSQL + backend + frontend**: `docker compose up --build` works from a clean clone.
- [ ] **Demo data without weakening invariant 11:** an opt-in, clearly labelled path (a one-shot compose `seed` service or a documented script that runs the existing `account-admin`, `account-fixture` and `transaction-fixture` commands). Demo credentials documented in the README as demo-only. Never seeded in the default/production profile.
- [ ] One authoritative root `README.md`: overview, architecture summary, prerequisites, build / run / test (Docker and local), demo credentials, API docs location, security design, design decisions and trade-offs, assumptions (e.g. `accounts.balance` is a synthetic fixture value), what was deferred and why. `backend/README.md` and `frontend/README.md` must not contradict it.
- [ ] CI: build, tests, lint, formatter check, coverage, dependency audit, Docker build.
- [ ] OpenAPI (springdoc) generated from code and consistent with `api-contract.md`.
- [ ] Clean repo: `.gitignore`, no empty tool directories (`.agents/`, `.codex/`), no secrets, no stray files, meaningful commits.

---

## 5. Decisions already made (do not undo)

- Feature-first modular monolith; JDBC for complex reads and writes; JPA for simple CRUD.
- Records for immutable data; Lombok for boilerplate.
- Database-backed sessions with server-side idle enforcement and `auth_version` invalidation.
- Strict, narrow security matchers with default deny.
- Idempotency and ETag/If-Match as designed.
- Same-origin deployment (no CORS), reverse-proxied `/api`.
- Angular without NgRx; URL as state; Reactive Forms.

## 6. Open decisions (owner must confirm before the agent acts)

1. **Agent/back-office workflow** (~18 Java + 5 TS files): recommended **keep**. Without it, disputes never leave `OPEN` and the "history" has nothing to show.
2. **Lombok vs records for DTOs:** current standard is records + Lombok for boilerplate (section 2.2). Changing this means converting ~35 records.
3. **Steering files in `.kiro/steering/`:** keep and reconcile with this file, or fold them in and delete.
4. **Login rate limiting:** in-memory (simple, single instance) or database-backed (multi-instance safe, more code).

---

## 7. Working method for agents

1. **Inventory first** (read-only), then wait for approval on moves larger than ~10 files.
2. Work through `docs/REFACTOR_PLAN.md` in order. One task = one commit series. Record what moved and why.
3. After each task: formatter, `mvn clean verify`, `npm run build && npm run test:ci`, and confirm the API contract and error codes are unchanged.
4. Definition of done for any change:
   - [ ] behavior unchanged, tests green
   - [ ] no new duplication, no god classes (services > ~300 lines or > ~7 dependencies need a reason)
   - [ ] names reveal intent, classes sit where section 1 says
   - [ ] zero warnings, formatted
   - [ ] README / contract updated if behavior-adjacent docs changed