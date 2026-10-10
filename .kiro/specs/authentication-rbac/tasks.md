# Authentication and RBAC — Implementation Tasks

**Feature:** `authentication-rbac`  
**Date:** 2026-10-10  
**Status:** Approved task plan. Tasks 1–18 completed and verified on 2026-10-10. Awaiting developer feature acceptance review; see acceptance.md.

## Approved baseline and execution rules

Follow [requirements](requirements.md), the approved [design](design.md) and [spec workflow](../../steering/spec-workflow.md), plus relevant backend, frontend, security, API and testing steering. The latest developer approval supersedes older draft/review labels in those unchanged documents. Before this plan was generated, only AUTH-04.3, AUTH-05.7 and AUTH-08.2/6 were aligned with the approved design clarifications; every other requirement was preserved.

Use the existing modular monolith, Boot 3.5.16/BOM (Security 6.5.11, Session 3.5.7), Java 21, Angular 20, PostgreSQL, Flyway and same-origin `/api/v1`. PBKDF2 parameters, JDBC activity metadata, the four authentication endpoints, code 1010, cookie policy and non-web provisioning are approved. Password benchmarking and runtime framework/failure verification remain execution gates, not new design approvals.

Execute one numbered task at a time in the order below after implementation authorization. Complete a task only after its implementation and required verification succeed. Report files changed, exact commands, exit codes/test counts, relevant observations and remaining issues; append redacted evidence here or in an acceptance report. A blocked command/scenario leaves its task unchecked. Do not count skipped/missing tests as success, hide failures with permissive test-runner options or imply hosted CI ran from local results.

Backend commands below run in `backend/`; frontend commands run in `frontend/`. Use Java 21/Maven 3.9+, Node from `.nvmrc`, Docker and Chrome/Chromium (`CHROME_BIN` when needed). Named tests below are **planned** suites to create in the corresponding tasks, not existing tests. Equivalent focused class names may be used if the evidence records the actual invocation and covers the same cases. No commands below have been executed by this planning phase.

All integration accounts/session fixtures live in isolated pinned PostgreSQL Testcontainers. Never seed accounts on application startup or put credentials in migrations. Outage/fault tests have independent containers/contexts and bounded test-only timeouts; they never stop developer Compose or healthy ordinary-test databases. Keep `V1__create_app_users.sql` and historical foundation specs byte-for-byte unchanged. Preserve meaningful foundation tests while updating their obsolete security expectations when the approved security baseline changes.

No transaction/dispute controllers or production business-route matchers, ADMIN role/dashboard/API, JWTs, new service, framework/runner migration, authentication storage in localStorage/sessionStorage, global UI library, custom response buffering or deployment infrastructure. Test-only authorization fixtures are non-business and never shipped. Defaults remain deny-all outside explicit feature endpoints and three health GET paths.

## Backend foundation and lifecycle

- [x] **1. Add managed dependencies and reconcile the approved password policy references.**

  **Depends on:** None. **Requirements:** AUTH-02, AUTH-NFR-02, AUTH-NFR-04.

  Add Boot-managed `spring-session-jdbc` and test-scoped `spring-security-test` without independent version overrides. Reconcile the directly conflicting BCrypt statements in technology/security steering, the current session decision and account schema documentation to the approved versioned PBKDF2 policy; preserve historical V1/foundation text and unrelated rules. Point current policy to this approved feature design. Broader contract/diagram/runbook alignment remains Task 17.

  **Verify:** `mvn --batch-mode -DskipTests package dependency:tree`. Record actual Security/Session versions and absence of OAuth/resource-server or duplicate-version dependencies. Check the documentation diff contains only the accepted policy/reference changes; V1/history unchanged. This is a dependency/build gate, explicitly not runtime authentication evidence; do not claim skipped tests ran.

- [x] **2. Add feature-owned Flyway schema and migration verification.**

  **Depends on:** 1. **Requirements:** AUTH-01, AUTH-05.5/6, AUTH-06, AUTH-NFR-02/03.

  Confirm the latest migration before assigning versions. Assuming V1 is still latest, add V2 for `auth_version` and canonical-email constraints/normalized uniqueness, V3 for the PostgreSQL schema shipped in Session 3.5.7, and V4 for `auth_session_activity`. Preserve the existing user columns/constraints, session schema keys/indexes, activity foreign-key cascades and activity/account indexes. No audit table, user seeds or unrelated banking tables. Disable framework session schema initialization; retain Flyway and Hibernate validation.

  Preflight noncanonical emails and normalization collisions: stop with a safe actionable migration failure, never silently merge identities. Cover both empty-database installation and V1-only upgrade with isolated fixtures, including rejected legacy-data cases.

  **Verify:** `mvn --batch-mode test -Dtest=AuthenticationMigrationTest,FoundationIntegrationTest`. Assert V1 checksum/history unchanged, restart validates without destructive DDL, required schema/types/constraints/indexes exist, failed preflight does not partially extend accounts, fixture transactions roll back and no users are auto-created. Adapt only foundation schema-fixture assumptions necessary for additive defaults/canonical emails; do not weaken original uniqueness/role checks.

- [x] **3. Implement account mapping, normalization and versioned password encoding.**

  **Depends on:** 2. **Requirements:** AUTH-01.1–3, AUTH-02, AUTH-06, AUTH-NFR-01/02.

  Map `app_users` in `auth`, with a narrow account lookup interface for security and transactional row-lock/guarded mutation support. Keep banking `account` separate. Implement the shared ASCII-edge-whitespace/email lowercase normalizer and supported role validation. Implement exact, well-formed 15–128-Unicode-code-point password handling and the approved `{pbkdf2-sha256-v1}` configuration: HMAC-SHA256, random 16-byte salt, 600,000 iterations, 256-bit key. No insecure fallback or hash/credential entity serialization.

  **Verify:** `mvn --batch-mode test -Dtest=AccountValidationTest,PasswordEncodingTest,AccountPersistenceTest`. Cover 14/15/128/129 code points, multibyte/emoji, spaces, no trim/normalization, passwords differing beyond byte 72, distinct salts, verification failures, normalized uniqueness and hash length/identifier. Confirm `ddl-auto: validate` accepts the real mapping. Benchmark the actual encoder on available assessment hardware using a small warm-up/sample set; record parameters, environment and timings without password/hash output. Review unreasonable cost as a blocker rather than silently lowering iterations or inventing a performance threshold.

- [x] **4. Implement explicit non-web account provisioning and maintenance.**

  **Depends on:** 3. **Requirements:** AUTH-01, AUTH-02, AUTH-06.3–6, AUTH-NFR-01/04.

  Add a small opt-in operational command adapter over the transactional service for create, disable, enable, role replacement and password replacement. Normal web startup does not run it. Use masked interactive console input; refuse password arguments/environment values and missing-console execution. No default/demo accounts or remote provisioning endpoint. Operator access is trusted host access plus externally supplied authorized DB configuration; the assessment does not need a new DB-role-management system or secret-manager integration.

  Increment `auth_version` atomically for accepted status/role/password mutations; maintain stable UUIDs and protect against lost updates. Emit redacted operator/account/action/outcome logs after commit/rollback. Document the non-atomic log/commit crash limitation, secure initial credential delivery and interruption verification. Deletion revocation is tested through trusted isolated DB operations; no delete UI/API is added.

  **Verify:** `mvn --batch-mode test -Dtest=AccountProvisioningTest,ProvisioningCommandTest`. Check duplicate/invalid/missing inputs and concurrent changes, rollback, version increments, both roles, explicit status, normal-startup inactivity, unsupported console/credential inputs and log redaction. Run the real interactive command against a disposable database with a masked console, checking success/failure and maintenance outcomes without echoing secrets. Confirm a portal HTTP request cannot reach provisioning.

- [x] **5. Establish stateful security, cookies, JSON failures and CSRF bootstrap.**

  **Depends on:** 2, 3. **Requirements:** AUTH-03.7, AUTH-05.1/7, AUTH-08.3/4, AUTH-09, AUTH-10, AUTH-NFR-01/02.

  Configure Session JDBC with 1,800 seconds, IMMEDIATE flush, ON_SET_ATTRIBUTE save mode and Flyway-only schema. Use cookie-only opaque `SESSION`, host-only `/api/v1`, HttpOnly/Lax, nonpersistent browser lifetime and fixed Secure production configuration. Permit insecure cookies only in explicit local configuration; reject production/local conflicts and unsafe production overrides. Keep same-origin proxy behavior and no CORS/Basic/form/remember-me/generated-user/request-cache behavior.

  Establish exact public health GET matching with no account/session access; remaining paths are default-denied. Add `GET /api/v1/auth/csrf` with the session repository and supported XOR/masked token transport `X-CSRF-TOKEN`; remove the blanket unsafe-API exemption. Add shared numeric envelopes/error writing, correlation/no-store support and a normally ordered outer servlet availability filter. Do not add future business matchers or allow login/logout before their implementation tasks.

  **Verify:** `mvn --batch-mode test -Dtest=SessionConfigurationTest,CsrfBootstrapIntegrationTest,SecurityEnvelopeTest,FoundationIntegrationTest`. Assert real JDBC anonymous token persistence, masked-header validation, foreign-context/missing/stale token 403/1010 precedence, no token/identity leakage, cookie flags/path/domain/lifetime, URL-ID rejection and exact health privacy. Update foundation safe anonymous expectations to 401 and unsafe missing-CSRF expectations to 403. Verify actual filter ordering and production/local profile combinations; no broad allowlist or HTML redirect.

- [x] **6. Enforce current-account validation and eligible-admission idle activity.**

  **Depends on:** 4, 5. **Requirements:** AUTH-04, AUTH-05.7, AUTH-06, AUTH-08.1/5, AUTH-NFR-02/03.

  Add the metadata repository and security account validator: check session principal UUID, matching activation metadata/version, current enabled supported-role account and deadline before authorization. No cached/in-memory fallback. Implement narrow eligible-handler admission through `HandlerInterceptor.preHandle`, with validated request state and a server marker/registry. Capture the framework session-access instant through public `HttpSession` access, and use database time for guarded expiry checks. Renew with monotonic metadata updates at admission, even if later validation/domain handling fails. Login initializes separately; health/bootstrap/logout/unmatched/rejected/background requests never qualify.

  Do not re-load/re-save a second mutable session copy solely to touch activity, call private framework flush/primary-key members or update Spring Session's managed columns directly. Zero-row admission denies before handler execution; store failures map to 503. Do not renew from completion hooks or client keepalive headers.

  **Verify:** `mvn --batch-mode test -Dtest=AccountSessionValidationTest,AuthenticationActivityTest,AuthenticationActivityIntegrationTest`. Use isolated persisted security contexts/metadata before real login exists. Test boundary instants without sleeping 30 minutes, wrong/missing metadata/principal/version, disabled/deleted/unsupported role, aligned framework/activity timestamp, monotonic touches and all exclusions. A synthetic non-business HandlerMethod/interceptor fixture demonstrates renewal precedes later validation/error outcomes; no production test endpoint. Check admission failure prevents handler invocation and record backend/database clock assumptions.

- [x] **7. Implement JSON login and current-user identity.**

  **Depends on:** 3–6. **Requirements:** AUTH-03, AUTH-04.3, AUTH-05.1/7, AUTH-06.1/2, AUTH-08, AUTH-09.4/6, AUTH-NFR-01.

  Add `POST /api/v1/auth/login` and `GET /api/v1/auth/me` with strict DTOs and accepted envelopes. Identity is only UUID/email/current role; mark it eligible for admission activity. Normalize only email; preserve password exactly. Reject extra role/id fields. Make unknown/wrong/disabled credentials generic 401 with comparable dummy hash work, malformed input 400 and store failures 503. No new authenticated metadata on failed login.

  Explicitly invoke the composite identifier-rotation/CSRF strategies, create a credential-free context, save it through `HttpSessionSecurityContextRepository` and let the attribute write IMMEDIATE-flush the pending ID change. Resolve session primary ID through SQL using public session ID, then activate metadata after an enabled/version recheck under account locking. Required persistence precedes the success body/cookie. Best-effort failure cleanup does not assume rollback across separately committed framework transactions.

  **Verify:** `mvn --batch-mode test -Dtest=AuthenticationApiIntegrationTest,LoginSessionLifecycleTest,FoundationIntegrationTest`. Send real bootstrap headers/cookies for both roles, inspect stored context/metadata and minimal response, compare generic failures, assert 400 unknown fields, no credentials/hashes in serialization/logs and no authentication bypass. Verify old pre-login cookie replay fails, new CSRF material works and old token fails. Test actual row activation/version-change race and absent/expired identity 401, with no sticky client assumptions.

- [x] **8. Implement CSRF-protected logout and lifecycle token transitions.**

  **Depends on:** 7. **Requirements:** AUTH-05.4/7, AUTH-07, AUTH-09.3–6, AUTH-10, AUTH-NFR-01.

  Add `POST /api/v1/auth/logout`: validate CSRF, synchronously invalidate/delete the JDBC session, clear context/token and expire `SESSION` with original scope before the 200/null envelope. GET has no logout effect. Repeated logout with fresh anonymous-bootstrap CSRF succeeds identically. Other sessions remain valid. Test/bootstrap lifecycle handles expired authenticated contexts without reviving them, and no activity renewal occurs on logout/bootstrap.

  **Verify:** `mvn --batch-mode test -Dtest=LogoutIntegrationTest,CsrfLifecycleIntegrationTest,LoginSessionLifecycleTest`. Check exact deletion headers, invalid token blocks mutation, same-cookie replay fails, repeat anonymous logout, GET has no mutation, other cookie jar remains authenticated and pre-transition tokens never survive login/logout. Verify token-refresh failure is independent of an already committed transition; database deletion failure is not reported as successful logout. Cross-instance replay/fault evidence is completed in Tasks 11–12.

## Backend security and framework verification

- [x] **9. Verify production default-deny, role machinery and private responses/logs.**

  **Depends on:** 8. **Requirements:** AUTH-01.5, AUTH-08, AUTH-09, AUTH-NFR-01/03.

  Build the approved test-only non-business role fixture around real Spring Security authorization filters/managers, production authority mapping and JDBC session/account validation. Innocuous customer-only/agent-only matcher cases terminate in a test filter, with no business controller or shipped allowlist. Keep full production-chain tests separate: both roles may obtain their own identity; unrelated paths, including future business URIs, remain denied. Verify client-forged roles/identities cannot affect backend authorization.

  Capture representative login/logout/provisioning/filter/API error logs and response headers. Never print raw request bodies, cookies, session IDs, CSRF values or hashes into stored evidence; assert against redacted captures instead. Preserve meaningful HTTP statuses and numeric 1001/1002/1010/3002 errors.

  **Verify:** `mvn --batch-mode test -Dtest=RoleBoundaryTest,AuthenticationAuthorizationIntegrationTest,AuthenticationPrivacyTest,FoundationIntegrationTest`. Assert anonymous safe/CSRF-valid protected denial 401, opposite-role fixture denial 403 before terminal execution, missing-CSRF precedence 403 and no HTML redirect. Verify unknown production requests are denied for both roles, no provisioning path, no-store/correlation headers and secret-free safe diagnostics. Do not claim any future operation-specific permission/ownership test passed.

- [x] **10. Implement configurable session/activity cleanup and verify retention boundaries.**

  **Depends on:** 6, 8. **Requirements:** AUTH-04.5, AUTH-05.5/6, AUTH-NFR-04.

  Retain the managed JDBC expired-session cleanup and add the small idempotent cleanup for authoritative activity deadlines. Default cron `0 * * * * *`; expose documented configuration. Delete expired parent sessions so attributes/activity cascade; do not delete unexpired anonymous CSRF sessions solely for lacking metadata. Do not add a scheduler service or speculate about batching infrastructure.

  **Verify:** `mvn --batch-mode test -Dtest=AuthenticationCleanupTest,AuthenticationMigrationTest`. Advance stored timestamps/invoke cleanup deterministically; assert expired rows disappear, active/anonymous rows remain, cascades are correct, repeat/two-instance cleanup is safe and request-time expiry works before cleanup. Check restart preserves unexpired rows and configuration overrides are used; no real-time cron/30-minute sleeps required.

- [x] **11. Prove multi-instance persistence, revocation and concurrency with real HTTP.**

  **Depends on:** 7–10. **Requirements:** AUTH-03.6, AUTH-04, AUTH-05.2–4, AUTH-06, AUTH-07.2/3/7, AUTH-NFR-03.

  Run two distinct application contexts/ports against one isolated database, using compatible configuration/serialization. Login on A, identity on B, restart a context and restore identity, logout on A and reject replay on B. Verify two independent sessions are revoked on both instances after disable/delete/password/role changes; re-enable never resurrects them. Keep deletion within isolated trusted DB fixtures, respecting future history constraints.

  Coordinate concurrent activity, identifier rotation, login/admin change and logout/in-flight access with deterministic barriers rather than timing guesses. Assert no metadata resurrection, no old-ID restoration or lost version increments, and no stale wrapped-session save can reauthorize a retired context. Distinguish already-authorized-before-commit operations from subsequent requests as approved.

  **Verify:** `mvn --batch-mode test -Dtest=MultiInstanceAuthenticationTest,AuthenticationConcurrencyTest`. Record scenarios and actual assertions, not just a test count. Inspect persistence/replay using private assertions, not published secret values. If pinned framework behavior contradicts the design, leave the task unchecked and report the precise race; do not invent repository hooks, weaken revocation or add buffering without review.

- [x] **12. Verify isolated database failures, response commitment and recovery.**

  **Depends on:** 9–11. **Requirements:** AUTH-05.7/8, AUTH-06, AUTH-07.6, AUTH-09, AUTH-NFR-01/03.

  Create separate fault/outage suites with their own containers, contexts, bounded timeouts and deterministic fault injection. Exercise account/session reads, access/context writes, pending ID rotation flush, metadata activation/admission, logout deletion and framework pre-commit/finalization saves. No in-memory fallback or cached authorization on failure. Uncommitted failures return safe 503; failed activation does not issue a usable new authenticated cookie/context; ambiguous logout is never reported confirmed.

  Verify outer servlet filter placement and standard framework response wrapper behavior without custom body buffering/private API access. A post-commit fault cannot rewrite HTTP status: validate safe operational reporting and avoid promising retroactive cookie withdrawal. Restore the isolated database and verify actual persisted-state recovery, not survival after data loss. Preserve independent health/readiness/liveness behavior.

  **Verify separately:** `mvn --batch-mode test -Dtest=AuthenticationStoreFailureTest`, then `mvn --batch-mode test -Dtest=AuthenticationDatabaseOutageTest,DatabaseOutageTest`. Check zero skips, each failure stage, 503 versus 401/403 distinctions, unavailable-token storage precedence, no secrets, readiness 503/liveness UP and recovery. Ordinary integration tests never share the stopped database. Report a concrete unsupported boundary as a blocker; do not hide it by logging a fault after an already emitted success.

## Angular integration

- [x] **13. Add typed authentication state/service and safe HTTP handling.**

  **Depends on:** 7–9. **Requirements:** AUTH-07.5/6, AUTH-09.4/7, AUTH-10.4/6, AUTH-11.5/6/9, AUTH-NFR-01/02.

  Implement `core/auth` contracts, explicit signal-backed session states, separate in-memory CSRF state, AuthService and functional interceptors. Use relative `/api/v1`, browser-managed cookie and masked token header only on same-origin unsafe API requests; disable conflicting default cookie-XSRF handling. Prevent circular HttpClient/AuthService injection and recovery recursion. No token/cookie reads, browser auth storage or bearer header.

  Handle bootstrap/identity/login/logout transitions and stale response generations. Any backend auth 401 clears private UI state; 403/503 do not auto-logout. No automatic unsafe replay, including ambiguous credential/logout submissions. Confirmed transition followed by token-refresh failure keeps the confirmed outcome and blocks unsafe requests until explicit retry. Treat non-JSON proxy/network failures safely.

  **Verify:** `npm run test:ci -- --include='src/app/core/auth/**/*.spec.ts'` and `npm run build`. Use HttpTestingController with correctly ordered HttpClient/testing providers; assert path/origin/method header rules, no missing-token dispatch, single-flight bootstrap, numeric/non-JSON failures, all lifecycle distinctions, stale response rejection and no automatic replay. Keep Karma/Jasmine and existing Angular pins/configuration.

- [x] **14. Resolve session startup and protect role routes before content loads.**

  **Depends on:** 13. **Requirements:** AUTH-08.5, AUTH-11.1–5/8, AUTH-NFR-03.

  Integrate the supported Angular initializer with one bootstrap → identity resolution. Handle unavailable initialization so a retry state renders while protected content stays hidden; do not hang startup or infer authentication from cached data. Add functional canMatch and child guards for existing `/customer` and `/agent` routes, plus a concise access-denied page. Role guards return UrlTrees; wrong-role components/data are never rendered/fetched. Keep `DashboardLayout` under `layouts/dashboard-layout`, public welcome/login separate and local role-allowlisted return URLs.

  **Verify:** `npm run test:ci` and `npm run build`. Router/initializer tests cover delayed identity, reload restoration, anonymous/expired navigation, wrong role, retryable 503/network initialization, single-flight guard calls, unauthorized/external return URLs and forged browser roles. Update affected foundation inert-route specs to approved guarded behavior rather than weakening routing assertions; retain component naming/outlet/layout coverage.

- [x] **15. Implement accessible login with exact password validation and role navigation.**

  **Depends on:** 14. **Requirements:** AUTH-02.2/3, AUTH-03, AUTH-09.4, AUTH-11.2/6–9.

  Extend existing `features/auth/login/login.ts`, HTML, SCSS and spec using a typed Reactive Form, labelled fields, keyboard operation, autocomplete, pending/error states and duplicate-submit prevention. Count Unicode code points; never trim password. Use backend identity for customer/agent landing and allowlisted return navigation; finish token transition or offer explicit security-token retry without falsely reporting login failure after success. Generic credential and safe availability/CSRF errors; no registration/reset feature.

  **Verify:** `npm run test:ci -- --include='src/app/features/auth/login/login.spec.ts'` and `npm run build`. Test password boundaries/emoji/spaces, exact request payload, both-role destinations, duplicate-submit lock, generic 401, 400/403/503/non-JSON/network outcomes, keyboard/label/error accessibility and login-success/bootstrap-failure handling. No automatic credential resubmission.

- [x] **16. Add role-aware DashboardLayout navigation and confirmed logout UX.**

  **Depends on:** 15. **Requirements:** AUTH-07, AUTH-09.4/6, AUTH-11.2/4–6/9.

  Extend `layouts/dashboard-layout/dashboard-layout.*`, retaining class `DashboardLayout` and selector `app-dashboard-layout`. Display verified-role links only to existing role landing areas and sign-out. Logout uses current CSRF, prevents duplicates, clears private state/content and navigates only after confirmed success; refresh anonymous CSRF separately. Network/store ambiguity shows an explicit retryable unconfirmed state. A deliberate retry bootstraps first; it never silently replays future business actions. No premature transaction/queue navigation destinations or reusable UI library.

  **Verify:** `npm run test:ci` and `npm run build`. Test customer/agent navigation, confirmed and repeated logout, stale-token recovery, refresh failure after confirmed logout, unavailable/transport failure retaining correct state, stale in-flight data responses, and layout/accessibility/SCSS regressions. Keep global Sass `@use` organization and concise component filenames; no animation-provider addition.

## Documentation, regression and acceptance

- [x] **17. Align canonical contracts, diagrams and operational runbooks.**

  **Depends on:** 12, 16. **Requirements:** AUTH-01.6, AUTH-02.5, AUTH-04/05/08/09/10, AUTH-NFR-01/04.

  Align relevant `.kiro/steering/`, README, canonical API/database/session decision/UI docs and PlantUML with implemented approved behavior. Resolve remaining BCrypt/pending-contract text, document numeric envelopes/1010, cookie/CSRF transport and lifecycle, idle admission semantics/exclusions, additive schema/cleanup, minimal identity, default-deny/future-feature extension and operational-log limitations. Preserve unrelated ETag/If-Match, ownership, business rules and historical approvals/migrations.

  Add reproducible native local/proxy setup, explicit local/production cookie profiles, privileged console provisioning/maintenance and secure credential delivery, benchmark evidence, clock/serialization compatibility, multi-instance/restart expectations and outage/ambiguous-logout troubleshooting. Do not include secrets, demo accounts, invented deployment tasks or an ADMIN UI. Update/render only affected diagrams; no blanket diagram redesign.

  **Verify:** `git diff --check`; targeted reference/policy searches distinguishing historical snapshots and explicit prohibitions from stale active guidance; documented local setup against a disposable database. Render each changed `.puml` with the available PlantUML renderer and Graphviz where required, checking renderer exit/syntax results and inspecting images. Record exact renderer command/version and changed filenames. Missing renderer/Graphviz or inability to perform required workflow checks is a blocker, not a passed render/runbook check.

- [x] **18. Run full regression and record requirement-indexed acceptance evidence.**

  **Depends on:** 1–17. **Requirements:** AUTH-01–11, AUTH-NFR-01–04.

  Run the existing CI-equivalent commands: backend `mvn --batch-mode clean verify`; frontend `npm ci`, `npm ls`, `npm run build`, `npm run test:ci`. Keep outage contexts isolated even when their tests are included in the full suite. Retain foundation health, probe, V1/checksum, constraints, naming, layout/proxy and build coverage with approved security/routing expectation changes. No nonexistent lint command, production Docker topology or runner switch.

  Perform browser verification through the existing local Angular proxy and a temporary HTTPS-equivalent test boundary using production cookie settings. Confirm host-only Secure/HttpOnly/Lax/path/browser lifetime, cookie invisibility to JavaScript, no auth browser storage/bearer transport, real CSRF header/token transitions, reload restoration, role navigation/denial, expiry, logout/replay, ambiguity and accessibility. The TLS fixture is test-only, not deployment infrastructure. Use disposable synthetic accounts, not startup seeds. Record results with secrets redacted; server/browser checks complement rather than substitute for each other.

  Produce an acceptance report mapping every requirement to actual command/test/browser evidence, counts/exit codes, prerequisite blockers and remaining risks. Include password cost results, each framework persistence/rotation/concurrency/fault stage, operator-procedure evidence and log privacy. Existing dependency advisories are disclosed without an unapproved force-fix/framework migration. A passing build never substitutes for an unexecuted security scenario. If hosted CI is part of the approved execution, record its real run URL/result after authorized publication; do not auto-commit/push or claim it ran in this task.

  **Completion gate:** All required scenarios executed successfully; missing Docker/browser/TLS/console/provisioning privileges or an unresolved framework race/failure boundary leaves this task unchecked and is reported. Stop for feature acceptance review; do not start transactions/disputes or deploy the application.

## Dependency summary and planning status

Main order: managed baseline → additive schema → accounts/passwords → operational provisioning → stateful security/CSRF → validation/activity → login/identity → logout → role/privacy verification → cleanup → multi-instance/concurrency → isolated store failures → Angular state/HTTP → startup/guards → login → layout/logout → docs/diagrams → full acceptance. Listed dependencies permit independently useful work, but numbered execution remains sequential unless the developer explicitly changes that workflow.

No design decision or requirements conflict remains unresolved after the developer's approval of Section 11. External prerequisites (Docker, compatible runtimes, browser, HTTPS-equivalent verification setup, masked console and authorized operator access) must be checked during their tasks; this plan does not assert they are unavailable or verified. Password benchmarking and pinned-framework race/commit behavior are implementation proof obligations. Any discovered incompatibility is reported before architectural changes, unsupported hooks or new buffering are introduced.

Deferred: transaction/dispute operations and their shipped role/ownership rules; stronger crash-durable account audit; production proxy/deployment and operational hardening; MFA/recovery/invitations/rate-limiting infrastructure. No deferred item is used to weaken the approved authentication baseline. Tasks 1–18 are complete with execution evidence below and in acceptance.md. Stop for feature acceptance review.


## Task 1 verification — 2026-10-10

Added Boot-managed `spring-session-jdbc` and test-scoped `spring-security-test` in `backend/pom.xml`, without version overrides. Updated only current password-policy statements/references in `.kiro/steering/tech.md`, `.kiro/steering/security.md`, `docs/design/session-architecture-decision.md` and `docs/design/database-design.md`: versioned PBKDF2-HMAC-SHA256, `{pbkdf2-sha256-v1}`, random 16-byte salt, 600,000 iterations and 256-bit derived key, subject to the later benchmark. No encoder, authentication behavior, schema/configuration change or Task 2 work was implemented.

- Java: OpenJDK 21.0.12.1. System `mvn` is unavailable; reused temporary Apache Maven 3.9.11 at `/tmp/apache-maven-3.9.11/bin/mvn`.
- First build invocation accidentally ran from repository root: exit 1, no root POM. Corrected to `backend/` immediately. The corrected sandbox invocation exited 1 because the Maven cache under `/home/donnie/.m2` was read-only.
- Re-ran the exact Task 1 goals with approved host/cache access from `backend/`: `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode -DskipTests package dependency:tree`. Exit 0, BUILD SUCCESS, 12.205 seconds; executable Boot JAR packaged. Log: `/tmp/auth-task1-build-verified.log` (temporary local evidence).
- Resolved Security config/web/core/crypto/test: 6.5.11; Session JDBC/core: 3.5.7. Parsed dependency-tree assertions passed: one version per Security/Session group, no OAuth2/SAML2/resource-server dependencies; `spring-security-test` is test-scoped. POM assertions confirm no explicit versions for either new dependency.
- Maven explicitly reports “Tests are skipped.” This is the specified dependency/package gate, not runtime/session/database verification or a passing test-suite claim. Password benchmarking remains Task 3.
- Policy/link/POM preservation assertions: exit 0. SHA-256 comparisons confirm every historical foundation specification/evidence file and V1 are byte-for-byte unchanged from pre-task state. Reviewed documentation diffs preserve unrelated architectural/business rules. `git diff --check`: exit 0.
- Remaining `BCrypt` text in `docs/design/plantuml/login-auth.puml` is the explicitly deferred Task 17 diagram alignment; historical references and the architecture decision's supersession explanation are intentional. Broader pending contract/runbook alignment was not attempted.
- No outstanding Task 1 blocker. Temporary Maven/cache access was required and verification succeeded. Pre-existing requirements/design/task-plan edits were retained; requirements and design were not modified by this implementation task. No commit or push. Tasks 2–18 not started; stop for review.


## Task 2 verification — 2026-10-10

Added `V2__extend_auth_accounts.sql`, `V3__create_jdbc_sessions.sql`, `V4__create_auth_session_activity.sql` and `AuthenticationMigrationTest.java`; updated base `application.yml` with `spring.session.jdbc.initialize-schema: never`. V2 adds only version/canonical-email constraints and normalized uniqueness, with safe operator-review preflight errors; V3 is byte-for-byte the PostgreSQL schema in the managed Session JDBC 3.5.7 JAR; V4 adds activity metadata, constraints/cascades and account/activity-time indexes. No user seeds, audit/business tables, entity, authentication behavior or Task 3 implementation.

- Reused OpenJDK 21 and temporary Maven 3.9.11. An initial command ran from repository root and exited 1 because no root POM exists; corrected to `backend/`.
- Required command from `backend/`, with approved cache/Docker access: `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AuthenticationMigrationTest,FoundationIntegrationTest`. Exit 0, BUILD SUCCESS, 17.272 seconds; 12 tests, zero failures/errors/skips (7 migration cases and 5 unchanged foundation tests). Temporary log: `/tmp/auth-task2-tests-backend.log`.
- Each migration case owns a distinct schema in a pinned PostgreSQL Testcontainer; the foundation suite owns its separate container. No developer Compose database or outage test was used/stopped.
- Verified clean installation of four migrations with no seeded accounts/sessions, exact dependency schema bytes, real column types/nullability/default, required indexes, version/canonical-email/session/activity constraints and both cascade directions. Constraint fixtures use savepoints and final rollback; all fixture rows disappear.
- Verified V1-only upgrade retains the canonical user, sets default version zero, keeps V1 checksum 1011929294 and preserves an unexpired session across a second Flyway startup/migration pass. The second pass applies zero migrations and validates history. This is persistence/schema restart verification, not future authenticated HTTP restart evidence.
- Three noncanonical legacy-email cases and a normalization collision fail before V2 changes, keep original identity counts and leave no version column/session infrastructure/non-V1 history. No identities were silently normalized/merged.
- All five existing foundation integration tests passed without fixture or expectation changes; additive defaults/canonical fixtures already satisfy the new schema. Current deny-all foundation security behavior remains unchanged until its owning Task 5.
- SHA-256 preservation and managed-schema byte comparisons: exit 0. Historical foundation files and V1 are unchanged. `git diff --check`: exit 0. No remaining Task 2 blocker. No commit/push; Tasks 3–18 not started. Stop for review.


## Task 3 verification — 2026-10-10

Files added under `backend/src/main/java/com/assessment/disputeportal/auth/`: `AppUser.java`, `AppUserRepository.java`, `AccountIdentity.java`, `AccountLookup.java`, `JpaAccountLookup.java`, `AccountRole.java`, `EmailNormalizer.java`, `PasswordPolicy.java` and `VersionedPasswordEncoder.java`. Updated that module's `package-info.java`. Added `AccountValidationTest.java`, `PasswordEncodingTest.java` and `AccountPersistenceTest.java` under `backend/src/test/java/com/assessment/disputeportal/auth/`; updated this task status/evidence. No other file was changed by Task 3.

- Mapped the existing seven-column `app_users` table with field access, stable UUID/creation instant, string-backed CUSTOMER/AGENT role, enabled state and revocation version. The repository stays package-private within auth; security-facing lookup returns an explicit hash-free internal projection rather than a JPA entity. The locked lookup requires a caller transaction; no provisioning/mutation service or login behavior was added. Banking accounts remain separate.
- Email normalization removes only ASCII edge whitespace, uses Locale.ROOT and validates bounded email syntax, rejecting remaining whitespace/control input without echoing it. Password policy accepts exactly 15–128 well-formed Unicode code points, preserves spaces/combining sequences and rejects unpaired surrogates; no trimming/normalization/composition requirement.
- The actual Spring encoder bean uses the Security 6.5.11 algorithm constructor, SHA256/256-bit output, random 16-byte salts, 600,000 iterations and explicit `{pbkdf2-sha256-v1}` delegation. No plaintext/legacy fallback. Invalid/unknown/malformed encodings fail matching safely. Entity hash fields are Jackson-ignored, its diagnostic string contains UUID only, and lookup projections contain no hash.
- Required command from `backend/` using approved Maven-cache/Docker access: `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AccountValidationTest,PasswordEncodingTest,AccountPersistenceTest`. Initial run exit 1: 27 tests, one failure (the framework email validator accepted Unicode whitespace) and four JPA fixture errors (slice lacked its Validator bean). Fixed explicit whitespace/control rejection and imported validation auto-configuration into the JPA test slice. Re-run exit 0: 27 tests, zero failures/errors/skips, 34.982 seconds. Temporary logs: `/tmp/auth-task3-tests.log`, `/tmp/auth-task3-tests-verified.log`.
- Strengthened the whitespace-password verification to keep trimmed/untrimmed comparison inputs inside the valid length range and disabled slice SQL printing explicitly. Final command: `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AccountValidationTest,PasswordEncodingTest,AccountPersistenceTest,FoundationIntegrationTest`. Exit 0, BUILD SUCCESS, 42.450 seconds; 32 tests, zero failures/errors/skips: 18 validation, 5 encoder (including benchmark), 4 persistence and 5 unchanged foundation tests. Log: `/tmp/auth-task3-final-verification.log`.
- JPA integration uses the pinned isolated PostgreSQL Testcontainer, Flyway V1–V4 and explicitly `ddl-auto: validate`, never create/update or the developer DB. Both roles round-trip, lookup normalization/absence work, canonical/duplicate constraints reject bypasses, and an independent JDBC `FOR UPDATE NOWAIT` observes SQLSTATE 55P03 while the caller transaction holds the repository's row lock. Transactional fixtures roll back; the committed lock-test fixture is explicitly removed and zero rows verified.
- Password evidence includes 14/15/128/129 boundaries, emoji/multibyte/spaces, malformed surrogate rejection, exact preservation, distinct random salts, verification failures and passwords differing beyond byte 72. Independent JCE PBKDF2WithHmacSHA256 derivation using the stored salt, 600,000 iterations and 256 bits confirms parameters; serialized entity/projection and diagnostic assertions confirm hash exclusion without printing hashes.

### Actual encoder benchmark

Environment: OpenJDK 21.0.12.1, OpenJDK 64-Bit Server VM, Linux kernel 6.17.0-20-generic, amd64; Intel Core i7-8750H @ 2.20 GHz, 6 cores/12 logical processors (12 visible to Java). Maven 3.9.11; Boot 3.5.16 / Security 6.5.11. Two warm-up encode/verify pairs, then five measured pairs in the real encoder test, with fresh salts. Wall-clock timings use System.nanoTime; no raw password/hash output.

| Operation | Minimum | Median | Maximum | Samples (milliseconds) |
| --- | --- | --- | --- | --- |
| Encode | 557.778 ms | 573.409 ms | 612.123 ms | 573.409, 612.123, 557.778, 589.941, 572.453 |
| Verify | 551.993 ms | 571.946 ms | 610.010 ms | 551.993, 579.574, 610.010, 571.946, 568.044 |

The approximately 0.57-second median cost is reasonable for this assessment hardware; no parameters were reduced. This small local sample is not a throughput/concurrency or production-capacity claim. Unknown-account dummy verification and authentication rate controls are not implemented in this task.

Preservation checks compare SHA-256 values from before Task 3: all existing migrations V1–V4, historical foundation specification/evidence files and SecurityConfig are unchanged. `git diff --check` and preservation assertions exit 0. Foundation's five health/security/schema tests still pass. No remaining Task 3 blocker; initial validation/test-slice issues are resolved. No provisioning, login/session behavior, subsequent task, commit or push. Tasks 4–18 remain unchecked; stop for review.

## Task 4 verification — 2026-10-10

Implemented only explicit non-web account provisioning and maintenance. Added `AccountProvisioningService.java`, `ProvisioningCommand.java` and `ProvisioningConfiguration.java` in `backend/src/main/java/com/assessment/disputeportal/auth/`; extended `AppUser.java` with guarded mutations; updated the auth `package-info.java` and `DisputePortalApplication.java` with an explicit first-argument `provision` dispatch. Added `AccountProvisioningTest.java` and `ProvisioningCommandTest.java`; added one isolation test to `FoundationIntegrationTest.java` without changing its original five tests. Added `docs/operations/account-provisioning.md`, updated the directly affected README statements/link and this task status/evidence. No requirements/design, migration, dependency, web-security, Angular or Task 5 change.

- The packaged artifact selects an isolated opt-in profile/context with a fixed highest-priority non-web startup property. No startup runner, account seed, public registration, HTTP provisioning endpoint, ADMIN role or delete command. Normal startup has no provisioning service bean.
- Create requires explicit CUSTOMER/AGENT and enabled state, stores a canonical email, fresh UUID, approved salted PBKDF2 hash and version zero. Maintenance requires UUID plus the operator's observed `--expected-version`. Accepted mutations increment once; unchanged status/role is rejected without increment. Explicit password replacement is a revocation operation even if the new password matches the old one.
- Hashing and validation precede the write transaction; target existence/version is revalidated under a PostgreSQL row lock before mutation. The service refuses ambient transactions so hashing cannot inherit a caller's locks and success logging cannot precede an outer commit. TransactionTemplate owns commit/rollback. Safe logging records timestamp/operator/action/UUID/outcome/correlation only, sanitizes host attribution and never logs arguments, passwords, hashes or exception detail. Input/console failures are also logged without credential data.
- `Console.readPassword` reads twice without echo and input buffers are cleared; account passwords are not accepted as operands/environment. Missing console and unsupported/duplicate/missing operands fail closed. Established external DB_PASSWORD remains permitted for database access. JVM String-copy limitations, trusted operator/DB access, restricted logging, secure credential delivery and interrupted/ambiguous commit inspection are documented. Ordinary logs and DB commit are not atomic; a `failed` record never proves an ambiguous commit changed nothing. No stronger audit infrastructure was added.

### Commands and results

All Maven commands ran in `backend/` using OpenJDK 21.0.12.1 and temporary Maven 3.9.11, with approved cache/Docker access. No system Maven is installed.

- `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AccountProvisioningTest,ProvisioningCommandTest`: exit 0; initial 13 tests (8 provisioning, 5 command), zero failures/errors/skips; 21.212 seconds. `/tmp/auth-task4-tests.log`.
- Expanded verification after concurrent-create/HTTP isolation coverage: `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AccountProvisioningTest,ProvisioningCommandTest,FoundationIntegrationTest`: exit 0; 20 tests (9 provisioning, 5 command, 6 foundation), zero failures/errors/skips; 29.159 seconds. After logging refinement the same command again exited 0 with 20 passing tests. Temporary logs `/tmp/auth-task4-final-tests.log` and `/tmp/auth-task4-final-tests-verified.log`.
- `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode -DskipTests package`: exit 0; initial and refreshed executable JAR packaging. Tests explicitly skipped by these packaging invocations, not counted as test evidence. Logs `/tmp/auth-task4-package.log`, `/tmp/auth-task4-package-final.log`.
- Final source/artifact verification: `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test package -Dtest=AccountProvisioningTest,ProvisioningCommandTest,FoundationIntegrationTest`: exit 0; 20 tests, zero failures/errors/skips; 30.972 seconds, executable JAR packaged. Surefire correctly reports that repeated lifecycle execution is skipped because the same configuration already ran, not a second test set. `/tmp/auth-task4-acceptance.log`.
- `python3 /tmp/auth-task4-console.py`: final invocation exit 0. The driver invokes the actual packaged `java -jar ... provision` entry point through a real controlling pseudo-terminal, with generated account secrets held in driver memory and entered only at masked prompts, against a fresh pinned PostgreSQL 17 container. Its evidence contains only safe results, never secret values/hashes. Final log `/tmp/auth-task4-console-acceptance.log`; redacted JSON `/tmp/auth-task4-console-evidence.json`. These `/tmp` files are temporary local evidence, not checked-in credentials or durable audit.
- `git diff --check`: exit 0. SHA-256 comparison with the pre-existing Task 3 preservation manifest: exit 0; historical foundation specifications/evidence, V1–V4 migrations and SecurityConfig byte-for-byte unchanged. Approved authentication requirements/design were not edited. No commit/push.

### Concurrency, rollback and console observations

Real PostgreSQL integration verifies both roles/statuses and encoder matching; duplicate/invalid/missing/stale/no-change operations leave versions unchanged. Two same-version maintenance callers produce exactly one commit and one conflict, version one. Two concurrent canonical duplicate creates produce exactly one account at version zero. A separate service instance commits a role/version change while another is paused in password hashing; the first holds no transaction/row lock, then rejects its stale replacement after hashing, preserving the newer role/version and original hash. An isolated test-only failing UPDATE trigger proves status and version roll back together. Audit appenders observe committed database state only after commit and unchanged state after confirmed rollback, with no active transaction or secret output. Trusted isolated deletion makes the old UUID lookup disappear; recreation uses a different UUID. Actual session revocation request behavior belongs to later tasks, not claimed here.

Normal full application startup leaves account count zero and has no provisioning service. GET/POST attempts at `/api/v1/auth/provision` and `/provision` are denied; all original foundation health/security/schema/probe checks pass. No developer Compose database was used/stopped and no outage suite was mixed into ordinary tests.

The final console run verifies six successful commands: CUSTOMER creation (enabled, version zero), AGENT creation (disabled, version zero), disable → version one, enable → two, role replacement → three, password replacement → four. Stale mutation and normalized duplicate creation return 1; unsupported ADMIN role, forbidden account-password environment input and missing console return 2. Stored final state is checked independently through PostgreSQL using only non-secret columns and hash format/length booleans. Rejected commands do not alter versions. Both password prompts are reached for credential operations; no password/hash echo or web listener is observed. Creation also inherits `SPRING_MAIN_WEB_APPLICATION_TYPE=servlet` deliberately and still remains non-web. Every disposable container is removed afterward.

Two early console-driver attempts returned exit 1 because the driver treated terminal EOF immediately before child termination as a timeout; redacted capture showed the account command had committed and shut down. Fixed the driver's bounded child-exit wait and re-ran successfully on fresh disposable databases. These were verification-driver failures, not claimed passes. Minor editing-script issues (unavailable `python` alias and incorrect README working directory) were corrected with `python3`/repository-root paths; no application/test failure remains.

No remaining Task 4 blocker. Task 4 is complete; Tasks 5–18 remain unchecked and unexecuted. Stop for developer review.


## Task 5 verification — 2026-10-10

Implemented only stateful session/CSRF/security infrastructure. Added `auth/CsrfController.java`, `security/SessionConfiguration.java` and common technical support `ApiEnvelope.java`, `ApiErrorWriter.java`, `ApiExceptionHandler.java`, `AvailabilityFilter.java`, `StoreFailures.java`. Updated `security/SecurityConfig.java`, base/local application YAML, directly affected README statements and this task status/evidence. Added `security/SessionConfigurationTest.java`, `CsrfBootstrapIntegrationTest.java`, `SecurityEnvelopeTest.java` and their isolated `SessionIntegrationSupport.java`; updated only the approved safe/unsafe security expectations in `FoundationIntegrationTest.java`. No approved requirements/design, historical foundation specification, dependency, migration, account provisioning/password code or Angular change. No Task 6 implementation.

The default chain uses IF_REQUIRED sessions, explicit HttpSessionSecurityContextRepository persistence, HttpSessionCsrfTokenRepository, the supported XOR masked-token handler, disabled Basic/form/remember-me/built-in logout/request caching and default-deny authorization. GET `/api/v1/auth/csrf` is the sole new public authentication operation; it materializes/persists the anonymous token and returns only masked token/header name inside the approved success envelope. Login/logout/identity and future business paths remain denied. Store-independent public health GETs use a separate chain with null context persistence and no anonymous-detail/session-management filters. No CORS, bearer transport, raw CSRF response or public account endpoint.

Boot-managed JDBC session configuration is 1,800 seconds, IMMEDIATE flush, ON_SET_ATTRIBUTE save mode, schema initialization never. Cookies are fixed host-only SESSION, /api/v1, HttpOnly/Lax, browser-session lifetime and configured Secure (default true), unaffected by forwarding headers. Only explicit local configuration permits false; production/local conflict and insecure default/production overrides fail startup. Servlet tracking is cookie-only and security URL rewriting is disabled; URL identifiers and query-token substitutes fail. Cookie profile tests use public serializer APIs and the real HTTP integration verifies production-style cookie headers.

Common error writing uses JSON status/data/error_code envelopes with optional structured error, correct 401/1001, 403/1002, 403/1010, 400/1004 firewall rejection and generic 503/3002. The outer servlet filter is registered once before SessionRepositoryFilter.DEFAULT_ORDER; it validates/generates the canonical correlation header and sets no-store. Propagated uncommitted store failures reset pending response body/cookies and restore safe headers before 503; MVC store advice and security handlers use the same convention. A committed response is not rewritten; safe correlation/outcome logging and a sanitized exception avoid exposing private causes. No custom response buffering or replacement session repository/filter.

### Framework findings and corrections

Executable health-cookie tests revealed session reads despite a null security-context repository: Spring Security anonymous WebAuthenticationDetails/SessionManagementFilter, MVC SessionFlashMapManager and MVC request-handled events all call servlet session methods. Disabled anonymous details/session management only on the exact public health chain; used the supported MVC FlashMapManager bean to skip only those health reads and delegate other routes unchanged; set the supported `spring.mvc.publish-request-handled-events=false` property. The final test confirms zero repository finds, no Set-Cookie and byte-for-byte unchanged persisted last_access_time after all three health GETs with a supplied real JDBC cookie. Framework events containing session identifiers are not emitted. This is an implementation correction to satisfy the approved health isolation rule, not a new architecture or custom filter/response mechanism.

Initial compilation rejected an unavailable CsrfConfigurer accessDeniedHandler method; corrected it to the supported shared exception-handling denial handler. Initial test compilation also rejected direct calls on the package-private JDBC session type; tests now use the public Session interface. A unit fixture initially selected ServletContextInitializer by type despite multiple beans; it now selects the named cookie initializer. Required-suite intermediate runs exited 1 (17 tests/two failures, then 17/one, then 18/one) while locating health reads; isolated one-test stack traces confirmed their source and were removed from final test code. None of these attempts is counted as passing verification.

### Final command and evidence

From `backend/`, using OpenJDK 21.0.12.1 and temporary Maven 3.9.11 with approved cache/Docker access:

`/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=SessionConfigurationTest,CsrfBootstrapIntegrationTest,SecurityEnvelopeTest,FoundationIntegrationTest`

Exit 0, BUILD SUCCESS, 20.669 seconds; **19 tests, zero failures/errors/skips**: 3 configuration, 5 CSRF/session integration, 5 envelope/availability and 6 foundation. Final temporary log `/tmp/auth-task5-tests-6.log`. System Maven remains unavailable; the specified goals were executed with the temporary distribution. No package/build success is substituted for tests.

- Real pinned PostgreSQL/Flyway integration verifies anonymous token persistence, 30-minute interval, bound flush/save/schema modes and observable row/attribute writes before any explicit repository save. Persisted raw token differs from response masked token; the masked header passes CSRF and reaches default-deny 401 on unimplemented login. Missing, malformed, foreign-cookie-context and deleted-session/stale tokens return 403/1010 before authorization. No account, authenticated metadata or identity is introduced by bootstrap.
- Real HTTP tests verify only exact health GET paths are public and session-independent; other health paths/methods remain denied. URL identifiers and query parameters cannot substitute for cookies/token headers. Cookie flags/scope/lifetime, correlation validation/generation, no-store, JSON/error structure and absence of redirects/private identifiers are asserted.
- A trusted isolated test-only stored SecurityContext demonstrates authenticated unknown paths still return 403/1002 without principal/authority leakage. This is default-deny/context-loading evidence, not production login, role mapping or account-validation acceptance; those belong to later tasks.
- Isolated repository read and bootstrap-create failure injection return generic 503/3002 without issuing a cookie or echoing private failure detail. Outer-filter tests confirm uncommitted pending body/cookie reset and the documented committed-response limit with sanitized propagation. No ordinary database was stopped; complete lifecycle/finalization/outage scenarios remain the explicitly planned Tasks 11–12.
- All foundation health/probe/schema/provisioning-isolation assertions pass with approved anonymous safe 401 and missing-CSRF unsafe 403 expectations. Historical foundation specifications/evidence and migrations V1–V4 match the existing SHA-256 preservation manifest. `git diff --check` and preservation assertions exit 0. Requirements/design remain unchanged.

No remaining Task 5 blocker. Strict authenticated idle activity/current-account validation, real login/identity/logout, cross-instance lifecycle and full fault/browser evidence are not implemented or claimed by Task 5. Task 5 is complete; Tasks 6–18 remain unchecked/unexecuted. No commit or push. Stop for developer review.


## Task 6 execution evidence — 2026-10-10

Implemented current-account/session validation and explicitly marked handler admission only. Added auth `SessionPrincipal`, `SessionActivityStore`, `JdbcSessionActivityStore`; security `ValidatedSession`, `AccountSessionValidator`, `AccountSessionValidationFilter`, `AuthenticatedActivity`, `AuthenticationActivityInterceptor`, `AuthenticationActivityConfiguration`. Updated `SecurityConfig` to validate after CSRF and before authorization. The filter is registered only inside the security chain. Added `AccountSessionValidationTest`, `AuthenticationActivityTest`, `AuthenticationActivityIntegrationTest`; updated the existing unsupported-principal security fixture to expect 401. Updated README, account-provisioning runbook and this task evidence. No production fixture endpoint, login, provisioning change, migration or subsequent task was implemented.

Verification from `backend/`, using Java 21, Boot 3.5.16 / Security 6.5.11 / Session 3.5.7 and real isolated PostgreSQL 17.11 through Testcontainers:

- `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AccountSessionValidationTest,AuthenticationActivityTest,AuthenticationActivityIntegrationTest`: exit 0, BUILD SUCCESS, 16 tests, zero failures/errors/skips, 15.540 seconds. Log `/tmp/auth-task6-tests.log`.
- After adding rotation, re-enable and inspection-failure coverage, `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AccountSessionValidationTest,AuthenticationActivityTest,AuthenticationActivityIntegrationTest,SessionConfigurationTest,CsrfBootstrapIntegrationTest,SecurityEnvelopeTest,FoundationIntegrationTest`: exit 0, BUILD SUCCESS, 36 tests, zero failures/errors/skips, 25.473 seconds. Task 6 contributes 17 tests (5 validator, 3 interceptor, 9 real PostgreSQL/HTTP); the remaining 19 are foundation/session regressions. Log `/tmp/auth-task6-final-tests.log`.
- `git diff --check` and SHA-256 preservation assertions: exit 0; historical foundation specifications/evidence and V1–V4 unchanged.

Evidence covers exact deadline equality versus one millisecond before, malformed principal and missing/mismatched metadata/version, disabled/deleted/re-enabled/unsupported-role accounts, database-derived authorities, renewal before later 2xx/4xx/5xx or argument-validation outcomes, framework/activity timestamp alignment, excluded health/bootstrap/logout/polling/unmatched/denied requests and ignored client activity headers. Parallel updates retain the greatest captured timestamp; revoked/deleted metadata cannot be renewed or resurrected. Public framework identifier rotation rejects the old identifier and accepts the new one without directly modifying managed session columns. Zero-row guards and isolated store read/write failure injection prevent handler execution, with 401 or generic 503 respectively. No ordinary database outage was introduced. Full login concurrency, cross-instance lifecycle and outage scenarios remain Tasks 11–12.

Database time governs guarded expiry; the captured framework access instant originates on the backend. Operationally synchronize backend and PostgreSQL clocks; no unapproved skew allowance is introduced. Admission commits in a separate transaction before handler invocation and is not rolled back by later handler errors. No completion-hook renewal, private framework extension, second mutable session copy or custom buffering is used.

No remaining Task 6 blocker. Tasks 7–18 remain unchecked and unexecuted. No commit or push. Stop for developer review.

## Task 7 verification — 2026-10-10

Added auth AuthenticationProvider, SessionActivation and AuthenticationController; updated SecurityConfig and ApiExceptionHandler. Added AuthenticationApiIntegrationTest and LoginSessionLifecycleTest. Credential-free public principal/context is saved through framework APIs; account-row-locked activation rechecks enabled/role/version before metadata commit. Hashing occurs before that lock. Required command `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=AuthenticationApiIntegrationTest,LoginSessionLifecycleTest,FoundationIntegrationTest` from backend: final exit 0, 13 tests, zero failures/errors/skips, 37.030 seconds (`/tmp/auth-task7-final.log`). Both roles, normalized email, strict fields, unknown/wrong/disabled generic failures, expired identity, failed account switch preserving the former session, identifier/token rotation and an administrative change immediately before activation are verified with real PostgreSQL/HTTP. Initial invocation at repository root exited 1 (no POM); corrected working directory. Initial successful run had 12 tests before race coverage. No requirements/migrations/history changed. No blocker. User authorized all remaining tasks; continue to Task 8, without commit/push.

## Task 8 verification — 2026-10-10

Added LogoutController, updated exact POST allowlists and expired-context handling for CSRF-validated lifecycle requests; added LogoutIntegrationTest/CsrfLifecycleIntegrationTest. Required command `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode test -Dtest=LogoutIntegrationTest,CsrfLifecycleIntegrationTest,LoginSessionLifecycleTest`: exit 0, 12 tests, zero failures/errors/skips (`/tmp/auth-task8-final.log`). Real HTTP verifies synchronous deletion and exact cookie expiry, no GET mutation, invalid/old/foreign tokens, repeated anonymous logout, independent session survival, expired bootstrap downgrade and failure-to-delete returning 503 while the former session remains valid. Initial run passed 11 tests before deletion-fault coverage. No blocker; no commit/push.

## Task 9 verification — 2026-10-10

Added RoleBoundaryTest (real authorization chain ending in a test-only terminal filter), AuthenticationAuthorizationIntegrationTest and AuthenticationPrivacyTest. Updated the bootstrap fixture's obsolete unimplemented-login expectation to 400/1004 for a missing JSON body; production allowlists remain feature-only/default-denied. Required Maven command with `-Dtest=RoleBoundaryTest,AuthenticationAuthorizationIntegrationTest,AuthenticationPrivacyTest,FoundationIntegrationTest`: exit 0, 15 tests, zero failures/errors/skips (`/tmp/auth-task9.log`). Both role directions reject before terminal behavior, forged headers have no authority, anonymous safe/CSRF-valid requests receive 401 and missing CSRF receives 403/1010. Production business/provisioning/unknown paths stay denied; captured login/failure/logout audit and responses exclude actual fixture passwords/hashes/cookies/tokens, with no-store/correlation/no redirects. Task 4 already verifies provisioning logs. Root-directory command attempt failed before corrected backend invocation. No blocker.

## Task 10 verification — 2026-10-10

Added AuthenticationCleanup and configurable managed-session/activity cron settings (default once per minute); disabled scheduling only in deterministic test configuration. Added AuthenticationCleanupTest. Required Maven command `-Dtest=AuthenticationCleanupTest,AuthenticationMigrationTest`: exit 0, 8 tests, no failures/errors/skips, 19.666 seconds (`/tmp/auth-task10-final.log`). Two concurrent cleanup callers and repeated execution preserve active/anonymous sessions, delete expired parent/attribute/activity rows, and a newly reconstructed framework repository restores active attributes. Request-time expiration remains covered by Task 6; full application restart follows in Task 11. No migration change or blocker.

## Task 11 verification — 2026-10-10

Added MultiInstanceSupport, MultiInstanceAuthenticationTest, AuthenticationConcurrencyTest and isolated OperationalTestConfiguration. Required Maven command `-Dtest=MultiInstanceAuthenticationTest,AuthenticationConcurrencyTest`: final exit 0, 6 tests, no failures/errors/skips (`/tmp/auth-task11-verified.log`). Distinct ports/contexts share actual JDBC sessions: A login/B identity, B restart restoration, A logout/B replay denial; two independent sessions revoked across both instances after actual operational disable/re-enable, role/password replacements and trusted deletion. Deterministic admission barriers race with logout and identifier rotation; stale framework saves cannot recreate authentication metadata or restore permission. Concurrent admissions succeed monotonically. A second-instance account mutation before row-locked activation rejects login. Initial 5-case suite passed; expanded fixture initially failed because direct field reads on a CGLIB proxy saw uninitialized proxy fields. Corrected to public fixture methods; no production change. No unsupported framework hook or blocker.

## Task 12 verification — 2026-10-10

Added isolated AuthenticationStoreFailureTest and AuthenticationDatabaseOutageTest; bounded JDBC connection/socket/statement/query timeouts in base configuration. Tightened login scalar/null-body parsing without changing the approved fields. Separate required Maven commands: `-Dtest=AuthenticationStoreFailureTest` exit 0, 7 tests, zero failures/errors/skips, 30.773 seconds (`/tmp/auth-task12-faults-final.log`); `-Dtest=AuthenticationDatabaseOutageTest,DatabaseOutageTest` exit 0, 4 tests, zero failures/errors/skips, 33.247 seconds (`/tmp/auth-task12-outage-final.log`). Tests use own contexts/containers, not developer Compose.

Public repository spying and actual PostgreSQL triggers exercise account/session reads, access/context writes, pending rotation flush, metadata activation/admission and logout deletion; required pre-output faults return generic 503 rather than credential failure, with old-ID denial/no usable authentication activation. Framework pre-commit save failure is mapped through the normally ordered outer filter with no cookie issued. A post-commit filter-finally save fault has already emitted 200 headers and terminates the chunked body; the client receives a transport failure and safe operational logs record committed=true. Tests assert this physical boundary rather than reporting confirmed client success or promising retroactive status replacement. No private framework API calls, response buffering or repository replacement.

Pausing the isolated PostgreSQL process causes identity/login/logout/bootstrap 503, readiness 503 and session-independent liveness UP; unpause restores the same persisted session, followed by verified logout/replay denial. Initial post-commit test incorrectly expected a complete response; corrected to assert headers followed by IOException. Initial stop/start recovery fixture failed to restore datasource connectivity; pause/unpause preserves its connection boundary and passed. Both attempts remain failures in local evidence, not counted as passes. No unresolved framework boundary or blocker.

## Task 13 verification — 2026-10-10

Added typed auth contracts, separate signal session/CSRF stores, AuthService and functional same-origin CSRF/safe-response interceptors, plus auth-service.spec.ts. No browser storage, cookie reads, bearer transport or interceptor/service injection cycle. Required focused `npm run test:ci -- --include='src/app/core/auth/**/*.spec.ts'`: final exit 0, 10 Karma/Jasmine tests (`/tmp/auth-task13-tests-final.log`); `npm run build`: exit 0 (`/tmp/auth-task13-build.log`), Node 22.23.3 and Chrome 151 temporary prerequisites. Tests verify header boundary/safe methods, missing-token blocking, single-flight startup, exact password payload, 401 clear versus 403/503 retention, CSRF rejection without replay, stale success/401 generation rejection, confirmed login/logout with failed refresh and deliberate ambiguous-logout retry. No blocker.

## Task 14 verification — 2026-10-10

Integrated provideAppInitializer, custom HttpClient interceptors with default XSRF disabled, canMatch/child role guards, exact local role return-URL allowlist and concise access-denied/session-unavailable pages. Root App hides unresolved/unavailable/ended-session protected content. Updated foundation root/router tests to established guarded behavior; added actual application-initializer and route-boundary suites. `npm run test:ci`: final exit 0, 29 tests (`/tmp/auth-task14-tests-final.log`); `npm run build`: exit 0 (`/tmp/auth-task14-build.log`). Initial delayed-route assertion confused absent DOM undefined with null; corrected without weakening absence check. Tests cover delayed identity, restored role, anonymous/wrong-role denial before content, unavailable startup completion/retry screen and unsafe return routes. No blocker or layout/naming change.

## Task 15 verification — 2026-10-10

Extended concise Login TS/HTML/SCSS/spec with typed Reactive Forms, canonical-email validation, exact well-formed Unicode password bounds, labels/autocomplete/focus, keyboard form submission, duplicate locking and fixed safe errors. Confirmed login with failed token refresh offers explicit refresh without credential replay; return navigation uses the role allowlist and passwords clear after success/destroy. Required focused login test command: exit 0, 5 tests (`/tmp/auth-task15-tests.log`); production build exit 0 (`/tmp/auth-task15-build.log`). Tests cover both roles, exact spaces/emoji/15–128 boundaries, malformed surrogate rejection, errors including non-JSON/transport, labels/status region and transition refresh failure. No blocker.

## Task 16 verification — 2026-10-10

Extended DashboardLayout TS/HTML/SCSS/spec with verified-role landing links, sign-out, duplicate lock, confirmed-only navigation, hidden content during unconfirmed logout and explicit retry. Added a non-secret session notice so confirmed sign-out with failed anonymous bootstrap remains signed out and offers token refresh on login. `npm run test:ci`: exit 0, 35 tests (`/tmp/auth-task16-tests.log`); `npm run build`: exit 0 (`/tmp/auth-task16-build.log`). Existing signal/service/router tests and new layout tests cover confirmation/ambiguity, role links, stale responses, token rejection/recovery, refresh failure and outlet removal. Global Sass @use and concise naming preserved; no premature UI library or business route. No blocker.

## Task 17 verification — 2026-10-10

Aligned current security/technology/backend/frontend/API/testing/product steering, README/core README, business/API/database/UI/session decision docs and diagram README. Added docs/operations/authentication.md with native proxy/cookie/CSRF/idle/cleanup/clock/serialization/recovery/benchmark procedures; retained the secure account-provisioning runbook. Unrelated domain/ETag/ownership and historical foundation specs/migrations are unchanged.

Changed exactly login-auth.puml, data-model.puml, frontend-navigation.puml and components.puml. PlantUML 1.2025.10, Java 21, headless mode: `java -Djava.awt.headless=true -jar /tmp/transaction-dispute-plantuml.jar -failfast2 -tsvg -o /tmp/authentication-rbac-diagrams <four changed source paths>` and the same command with `-tpng`: exit 0 each, four SVG/four PNG images generated and visually inspected. Smetana layout is explicitly selected for non-sequence diagrams; these require no Graphviz. System dot is unavailable, and `-version` reports exit 16 for that absent optional renderer. Initial non-headless invocation failed on X11; initial diagram rendering rejected an accidental literal newline inside the layout label. Corrected to escaped PlantUML newline; successful full renders, not checkonly's misleading zero status, are the evidence. Outputs/logs under /tmp/authentication-rbac-diagrams and /tmp/auth-task17-render-{svg,png}.log.

`mvn --batch-mode -DskipTests package`: exit 0, tests explicitly skipped only for packaging. `python3 /tmp/auth-acceptance-runtime.py`: final exit 0, real masked-console creation of both synthetic roles, native local backend readiness, existing Angular localhost:4200→8080 proxy login/identity and explicit local cookie policy; redacted /tmp/auth-task17-native-evidence.json. Initial driver incorrectly passed Docker's POSTGRES_PASSWORD into the operational command, which safely refused password environment input; separated environments and reran on a fresh disposable database. No app policy changed. Runtime retained only for Task 18 browser verification and will be removed afterward. `git diff --check` and SHA-256 preservation checks exit 0. Targeted policy search leaves only explicit prohibitions/history/deferred business work, no pending active authentication contract. No blocker.

## Task 18 verification — 2026-10-10

Final backend `mvn --batch-mode clean verify`: exit 0, 125 tests, zero failures/errors/skips, 3:25 minutes (/tmp/auth-task18-backend.log). Frontend Node 22: `npm ci`, `npm ls`, `npm run build`, `npm run test:ci` all exit 0; 35 tests pass. Logs /tmp/auth-task18-{npm-ci,npm-ls,build,frontend-tests}.log. Final strict JSON DTO/audit/error handling corrections are included in this full regression. No skipped verification.

Actual production-profile HTTPS browser acceptance through the existing Angular proxy: `/tmp/node-v22.23.3-linux-x64/bin/node /tmp/authentication-browser-acceptance.cjs`, exit 0, all 12 scenarios pass. Redacted /tmp/auth-task18-browser-evidence.json and inspected desktop/mobile screenshots record cookie privacy/scope, CSRF, rotation, reload, roles, expiry, logout/replay/ambiguity and explicit refresh recovery. Chrome sandbox enabled; self-signed test certificate exception applies only to the fixture. No secrets published.

`npm audit --json`: exit 1, six high development-tool advisories; no unapproved dependency migration. `npm audit --omit=dev --json`: final exit 0, zero runtime advisories after retrying sandbox DNS failure with authorized network access. Historical hashes and `git diff --check`: exit 0. Disposable runtime/database and private fixture credentials/TLS keys cleaned up. Requirement-indexed evidence, actual password benchmark, command counts, files and physical response-commit limits are recorded in [acceptance.md](acceptance.md). No blocking issue remains. All tasks complete; stop for feature acceptance review, without commit/push/deployment or subsequent business feature.
