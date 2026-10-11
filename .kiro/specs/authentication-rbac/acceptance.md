# Authentication and RBAC — Acceptance evidence

Evidence date: 2026-10-10. Current password-policy summary updated: 2026-10-11. Status: implementation Tasks 1–18 complete; awaiting developer feature acceptance. At evidence capture on 2026-10-10, no commit, push, deployment, hosted CI run or subsequent feature work had been performed.

## Implemented decisions

The existing modular monolith now uses Spring Security and PostgreSQL-backed Spring Session JDBC, credential-free session principals, host-only HttpOnly cookies, production Secure/SameSite=Lax, and strict 30-minute authenticated idle expiry. Eligible-handler admission commits activity independently of later handler outcomes; bootstrap, health, denied requests and background polling cannot extend authentication. Current account state/version and database-derived CUSTOMER/AGENT authorities are checked on every authenticated request.

Versioned PBKDF2-HMAC-SHA256 uses 600,000 iterations, random 16-byte salts and a 256-bit key. Current password policy: provisioning/replacement accepts 8–128 well-formed Unicode code points; login accepts non-empty well-formed input up to 128, preserving input without normalization or trimming. The original Tasks 1–18 record their then-current 15-character creation minimum; that historical evidence is unchanged and does not by itself verify the revised lower-bound case. Administrative maintenance is explicit non-web operation with masked console input, transactional versioning, locking and redacted logs. No ADMIN role, public registration, startup users or business endpoint was added.

Login, identity, logout and CSRF bootstrap use the approved envelope/correlation/no-store conventions. Angular uses same-origin requests, memory-only state, one initialization bootstrap/identity exchange, verified-role guards, accessible login and explicit recovery after ambiguous transitions. Modern concise filenames and DashboardLayout organization are preserved.

## Environment and final commands

Java 21.0.12.1; temporary Maven 3.9.11; Boot 3.5.16, Security 6.5.11, Session 3.5.7; PostgreSQL 17.11 containers; Node 22.23.3, existing Angular 20/Karma/Jasmine; Chrome for Testing 151. System Maven is unavailable and the system Node version is incompatible; the temporary compatible runtimes were used without changing repository conventions.

Commands run from backend or frontend as indicated. Task-specific commands, earlier failed attempts, corrective findings and final exit codes are recorded in [tasks.md](tasks.md).

| Final command | Directory | Exit | Actual result |
| --- | --- | --- | --- |
| `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode clean verify` | backend | 0 | 125 tests, zero failures/errors/skips; packaged application; 3:25 minutes |
| `npm ci` | frontend | 0 | Clean locked dependency installation; 542 packages installed |
| `npm ls` | frontend | 0 | Dependency tree valid |
| `npm run build` | frontend | 0 | Production build succeeds |
| `npm run test:ci` with `CHROME_BIN=/tmp/foundation-browsers/chromium-1234/chrome-linux64/chrome` | frontend | 0 | 35 tests pass |
| `npm audit --json` | frontend | 1 | Six high development-tool advisories disclosed below |
| `npm audit --omit=dev --json` | frontend | 0 | Zero runtime advisories |
| `/tmp/node-v22.23.3-linux-x64/bin/node /tmp/authentication-browser-acceptance.cjs` | disposable fixture | 0 | All 12 browser scenarios pass |
| `python3 /tmp/auth-acceptance-runtime.py` | disposable fixture | 0 | Real masked console provisions both roles; native local proxy and cookie behavior pass |
| PlantUML headless SVG and PNG rendering of four modified sources | repository | 0 each | Four SVG/four PNG outputs generated and visually inspected |
| `git diff --check`; SHA-256 preservation assertions | repository | 0 | Historical foundation documents and V1–V4 match preservation manifest |

The first production-only audit attempt could not resolve the registry inside the sandbox; rerun with authorized network access returned exit 0. No failed or skipped command is counted as passing. Docker/cache/browser access was available with authorized execution. Local tests do not assert hosted CI ran.

## Requirement-indexed evidence

The following mapping includes all acceptance criteria within each requirement group. Tests use real isolated PostgreSQL where persistence or transactional behavior is relevant. The complete suite above reran the backend and frontend evidence after final code corrections.

| Requirement | Executed evidence |
| --- | --- |
| AUTH-01 provisioning | AccountProvisioningTest, ProvisioningCommandTest and real masked interactive command: create both roles; disable/enable/role/password replacement; duplicate, stale, invalid, missing-console and forbidden password transport rejection; non-web/default-inactive behavior; exact version increments, rollback, locks and redacted logs. Six successful console mutations plus rejected cases are recorded in Task 4. |
| AUTH-02 credentials | AccountPersistenceTest, AccountValidationTest, PasswordEncodingTest: actual schema mapping, canonical email, supported roles, well-formed Unicode limits/exact password preservation, versioned format, salt randomness and verify/rejection. Actual encoder benchmark recorded below. |
| AUTH-03 login | AuthenticationApiIntegrationTest and LoginSessionLifecycleTest: both roles, normalized email, strict/null/non-text/extra request fields, generic unknown/wrong/disabled failure, minimal credential-free context, old identifier/token rejection, failed switch preserving existing identity, administrative change before activation. Browser keyboard login and identifier rotation also pass. |
| AUTH-04 lifetime | AccountSessionValidationTest, AuthenticationActivityTest/IntegrationTest: equality/just-before deadline, malformed/missing metadata, guarded admission before later successful/validation/error response, excluded anonymous/health/bootstrap/polling/denied requests, greatest captured timestamp and no resurrection. Browser sets only disposable stored activity beyond deadline and verifies expiry on reload. |
| AUTH-05 persistence/availability | AuthenticationMigrationTest, SessionConfigurationTest, AuthenticationCleanupTest, MultiInstanceAuthenticationTest, AuthenticationConcurrencyTest, AuthenticationStoreFailureTest, AuthenticationDatabaseOutageTest and foundation DatabaseOutageTest: JDBC storage, constraints/cascades, supported persistence APIs, A→B identity, restart, logout propagation, cleanup, races, failure stages and recovery. Outage suites own separate containers/contexts. |
| AUTH-06 identity/revocation | AuthenticationApiIntegrationTest, AccountSessionValidationTest, MultiInstanceAuthenticationTest: minimal identity, absent/expired 401; disabled/deleted/password/role/version changes invalidate all old sessions across instances; re-enable cannot revive them. Fresh authentication uses current role. |
| AUTH-07 logout | LogoutIntegrationTest, CsrfLifecycleIntegrationTest, AuthenticationConcurrencyTest: synchronous invalidation and scoped cookie deletion, current-session-only behavior, repeated fresh-CSRF logout, GET cannot mutate, replay/concurrent admission cannot resurrect, deletion failure returns 503. Browser verifies both confirmed and response-lost logout and explicit retry. |
| AUTH-08 authorization | RoleBoundaryTest, AuthenticationAuthorizationIntegrationTest: real security manager/filter test-only CUSTOMER/AGENT matrix, anonymous 401, opposite-role 403, current identity permitted, forged client role ineffective, unmatched/future business/provisioning paths denied. Angular guards and browser forged-storage/opposite route tests pass. No production business matcher shipped. |
| AUTH-09 CSRF | CsrfBootstrapIntegrationTest, CsrfLifecycleIntegrationTest, LogoutIntegrationTest: masked bootstrap token bound to cookie, actual header acceptance, missing/invalid/stale/foreign context 403/1010 without mutation, login rotation and logout renewal. Browser independently checks all transition headers and token refresh failure recovery without credential replay. |
| AUTH-10 cookies/origin | SessionConfigurationTest and real HTTP/browser assertions: host-only SESSION, /api/v1, HttpOnly/Lax/Secure production, browser lifetime, no JavaScript cookie exposure at matching path, no URL identifier substitute, explicit insecure local-only profile and existing Angular proxy. No CORS or bearer transport. |
| AUTH-11 Angular | 35 frontend tests cover HTTP boundary, memory state, single-flight initialization, stale response suppression, 401 versus 403/503, safe malformed errors, guards/return allowlist, exact password validation, labels/live status, keyboard and duplicate submission, both role layouts and confirmed/unconfirmed transitions. Twelve real browser scenarios complement these tests, including reload, mobile layout, expiry, privacy and refresh failures. |
| AUTH-NFR-01 privacy/observability | AuthenticationPrivacyTest, SecurityEnvelopeTest and fault/lifecycle tests assert generic errors, validated correlation, no-store and no password/hash/session/token logging or response leakage. Known-account audit uses UUID only; operational completion logging has documented crash-consistency limits. Browser artifacts contain no credential/token values. |
| AUTH-NFR-02 compatibility/boundaries | Managed dependency tree, existing foundation regression, real JDBC mapping, runtime framework persistence/commit tests and historical hashes; no JWT/OAuth/resource-server, feature-owned business controller or new application role. Existing framework/test runner and concise component convention remain. |
| AUTH-NFR-03 repeatable verification | Full clean backend verification, clean frontend install/tree/build/tests, deterministic test-owned fixtures, separate outage contexts, actual console/HTTPS/browser execution and renderer evidence. Commands/results are recorded rather than inferred from build success. |
| AUTH-NFR-04 documentation/evidence | Current steering, API/database/UI/architecture/operations docs, README and four rendered diagrams aligned; approved requirements/design and historical foundation specifications/migrations preserved. This report and task-by-task evidence provide review references. |

## Runtime framework and failure findings

Framework-managed immediate context writes and public identifier rotation occur before the login controller returns. Activation separately locks/rechecks account state/version and commits metadata; failure leaves no usable authenticated metadata. Logout deletes the managed session synchronously; cascades remove metadata. Tested interleavings cover concurrent requests, logout during admission, rotation during admission, stale framework copies and account mutation before activation. Revoked metadata cannot be renewed or recreated by stale requests.

Fault tests inject failures at provider/session reads, context insertion, identifier rotation, activity creation/renewal, session access update and logout deletion. Uncommitted failures return generic 503/3002 and suppress pending cookie/body success. A failure in the supported precommit save path also returns 503. A final save failure after headers have physically committed cannot rewrite them: executable HTTP evidence observes 200 headers followed by an incomplete body/transport error, with sanitized logging. Clients treat that outcome as ambiguous and require deliberate confirmation/retry. No custom buffering, private framework hook or false complete-success claim was introduced.

The isolated database outage verifies login/identity/logout/bootstrap fail closed, readiness is unavailable while liveness remains UP, and the same persisted session can recover when the database returns. These tests never pause/stop ordinary integration or developer Compose databases. Backend/PostgreSQL clock synchronization remains operationally required for strict activity semantics.

## Password cost

Measured actual configured encoder on Java 21.0.12.1, Linux amd64, Intel i7-8750H (6 cores/12 logical processors), two warm-up pairs and five measured samples: encode min/median/max **557.778/573.409/612.123 ms**; verify **551.993/571.946/610.010 ms**. Task 3 records test/benchmark commands and output. Parameters were retained; deployment hardware capacity and abuse protection require operational assessment, rather than silently weakening the encoder.

## Browser and visual evidence

A temporary self-signed HTTPS boundary forwarded to the existing Angular development server/proxy and the production-profile packaged backend. Certificate exceptions applied only to this disposable browser context; Chrome sandbox remained enabled. Both accounts were provisioned through the real non-web masked console. The twelve passing scenarios are:

1. Keyboard login with real CSRF exchange and identifier rotation.
2. Production cookie attributes, JavaScript invisibility and no authentication browser storage.
3. Reload restoration without password replay or identity polling.
4. Forged browser role ignored and opposite route denied.
5. Missing, stale and cross-context CSRF cannot perform logout.
6. Pre-login identifier replay cannot obtain identity.
7. Confirmed logout cookie deletion and replay denial.
8. AGENT navigation and readable mobile layout without horizontal overflow.
9. Strict stored deadline expiry on reload, with no revival.
10. Transport-ambiguous logout hides protected content; deliberate retry confirms.
11. Confirmed login survives token-refresh failure; explicit recovery never resubmits credentials.
12. Confirmed logout survives anonymous-token refresh failure and permits explicit recovery.

Desktop CUSTOMER and mobile AGENT screenshots were visually inspected. Four changed PlantUML diagrams rendered successfully with Java headless mode and explicit Smetana for non-sequence diagrams; optional Graphviz is absent. Initial X11/diagram-label rendering issues were corrected and the final SVG/PNG rendering passed.

## Files and review scope

Backend changes comprise Boot-managed dependencies/configuration, feature-owned account/password/provisioning classes, session/login/identity/logout/cleanup classes, common envelope/availability support, security validation/activity configuration, V2–V4 additive migrations from earlier completed tasks, and focused authentication/security integration tests. V1 and historical foundation specifications remain unchanged; V2–V4 were not modified during remaining-task execution.

Frontend changes comprise core authentication contracts/state/service/interceptors/guards/tests, app initializer/root/routing, concise Login and DashboardLayout files, and access-denied/session-unavailable pages. Current README, seven directly relevant steering documents, business/API/database/UI/session architecture documentation, operational runbooks and four PlantUML sources were aligned. Task evidence/checklists and this acceptance report complete the specification artifacts. Existing requirements edits are the previously approved planning clarifications, not new implementation scope changes.

## Remaining risks and disposition

No required implementation or verification blocker remains. Six high advisories affect the existing frontend development tooling (Angular build/Karma/Jasmine dependency chain, including braces/chokidar); the production-only audit returned zero. No force-fix, framework downgrade or runner migration was applied. Dependency remediation needs separate review.

Deferred as approved: transaction/dispute feature authorization/ownership/endpoints, production reverse proxy/deployment, deployment password-cost tuning, abuse/rate-limit hardening, stronger crash-durable operational audit, MFA/recovery/invitations. None is claimed as implemented or used to weaken current authentication requirements. Session serialization compatibility and synchronized clocks are documented operational prerequisites.

Disposable browser/native runtime processes and database were removed after verification; generated fixture credentials and TLS keys were deleted. No developer database was changed or stopped. The feature is ready for developer acceptance review.

## Workspace file manifest

This is the complete current changed/untracked feature file inventory, including approved planning artifacts and Tasks 1–6 carried into this execution. It does not claim every file was newly changed during Tasks 7–18.

- `.kiro/specs/authentication-rbac/acceptance.md`
- `.kiro/specs/authentication-rbac/design.md`
- `.kiro/specs/authentication-rbac/requirements.md`
- `.kiro/specs/authentication-rbac/tasks.md`
- `.kiro/steering/api-contracts.md`
- `.kiro/steering/backend-standards.md`
- `.kiro/steering/frontend-standards.md`
- `.kiro/steering/product.md`
- `.kiro/steering/security.md`
- `.kiro/steering/tech.md`
- `.kiro/steering/testing-standards.md`
- `README.md`
- `backend/pom.xml`
- `backend/src/main/java/com/assessment/disputeportal/DisputePortalApplication.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AccountIdentity.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AccountLookup.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AccountProvisioningService.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AccountRole.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AppUser.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AppUserRepository.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AuthenticationCleanup.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AuthenticationController.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/AuthenticationProvider.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/CsrfController.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/EmailNormalizer.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/JdbcSessionActivityStore.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/JpaAccountLookup.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/LogoutController.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/PasswordPolicy.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/ProvisioningCommand.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/ProvisioningConfiguration.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/SessionActivation.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/SessionActivityStore.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/SessionPrincipal.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/VersionedPasswordEncoder.java`
- `backend/src/main/java/com/assessment/disputeportal/auth/package-info.java`
- `backend/src/main/java/com/assessment/disputeportal/common/ApiEnvelope.java`
- `backend/src/main/java/com/assessment/disputeportal/common/ApiErrorWriter.java`
- `backend/src/main/java/com/assessment/disputeportal/common/ApiExceptionHandler.java`
- `backend/src/main/java/com/assessment/disputeportal/common/AvailabilityFilter.java`
- `backend/src/main/java/com/assessment/disputeportal/common/StoreFailures.java`
- `backend/src/main/java/com/assessment/disputeportal/security/AccountSessionValidationFilter.java`
- `backend/src/main/java/com/assessment/disputeportal/security/AccountSessionValidator.java`
- `backend/src/main/java/com/assessment/disputeportal/security/AuthenticatedActivity.java`
- `backend/src/main/java/com/assessment/disputeportal/security/AuthenticationActivityConfiguration.java`
- `backend/src/main/java/com/assessment/disputeportal/security/AuthenticationActivityInterceptor.java`
- `backend/src/main/java/com/assessment/disputeportal/security/SecurityConfig.java`
- `backend/src/main/java/com/assessment/disputeportal/security/SessionConfiguration.java`
- `backend/src/main/java/com/assessment/disputeportal/security/ValidatedSession.java`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V2__extend_auth_accounts.sql`
- `backend/src/main/resources/db/migration/V3__create_jdbc_sessions.sql`
- `backend/src/main/resources/db/migration/V4__create_auth_session_activity.sql`
- `backend/src/test/java/com/assessment/disputeportal/AuthenticationMigrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/FoundationIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/auth/AccountPersistenceTest.java`
- `backend/src/test/java/com/assessment/disputeportal/auth/AccountProvisioningTest.java`
- `backend/src/test/java/com/assessment/disputeportal/auth/AccountValidationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/auth/OperationalTestConfiguration.java`
- `backend/src/test/java/com/assessment/disputeportal/auth/PasswordEncodingTest.java`
- `backend/src/test/java/com/assessment/disputeportal/auth/ProvisioningCommandTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AccountSessionValidationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationActivityIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationActivityTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationApiIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationAuthorizationIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationCleanupTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationConcurrencyTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationDatabaseOutageTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationPrivacyTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/AuthenticationStoreFailureTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/CsrfBootstrapIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/CsrfLifecycleIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/LoginSessionLifecycleTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/LogoutIntegrationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/MultiInstanceAuthenticationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/MultiInstanceSupport.java`
- `backend/src/test/java/com/assessment/disputeportal/security/RoleBoundaryTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/SecurityEnvelopeTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/SessionConfigurationTest.java`
- `backend/src/test/java/com/assessment/disputeportal/security/SessionIntegrationSupport.java`
- `backend/src/test/resources/application-test.yml`
- `docs/business-rules.md`
- `docs/design/api-contract.md`
- `docs/design/database-design.md`
- `docs/design/plantuml/README.md`
- `docs/design/plantuml/components.puml`
- `docs/design/plantuml/data-model.puml`
- `docs/design/plantuml/frontend-navigation.puml`
- `docs/design/plantuml/login-auth.puml`
- `docs/design/session-architecture-decision.md`
- `docs/design/ui-flows.md`
- `docs/operations/account-provisioning.md`
- `docs/operations/authentication.md`
- `frontend/src/app/app.config.ts`
- `frontend/src/app/app.html`
- `frontend/src/app/app.routes.spec.ts`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/app.ts`
- `frontend/src/app/core/README.md`
- `frontend/src/app/core/auth/app-initialization.spec.ts`
- `frontend/src/app/core/auth/auth-contracts.ts`
- `frontend/src/app/core/auth/auth-guards.spec.ts`
- `frontend/src/app/core/auth/auth-guards.ts`
- `frontend/src/app/core/auth/auth-interceptors.ts`
- `frontend/src/app/core/auth/auth-service.spec.ts`
- `frontend/src/app/core/auth/auth-service.ts`
- `frontend/src/app/core/auth/session-state.ts`
- `frontend/src/app/features/auth/access-denied/access-denied.ts`
- `frontend/src/app/features/auth/login/login.html`
- `frontend/src/app/features/auth/login/login.scss`
- `frontend/src/app/features/auth/login/login.spec.ts`
- `frontend/src/app/features/auth/login/login.ts`
- `frontend/src/app/features/auth/session-unavailable/session-unavailable.ts`
- `frontend/src/app/layouts/dashboard-layout/dashboard-layout.html`
- `frontend/src/app/layouts/dashboard-layout/dashboard-layout.scss`
- `frontend/src/app/layouts/dashboard-layout/dashboard-layout.spec.ts`
- `frontend/src/app/layouts/dashboard-layout/dashboard-layout.ts`
