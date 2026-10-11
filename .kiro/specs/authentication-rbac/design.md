# Authentication and RBAC — Design

**Feature:** `authentication-rbac`  
**Date:** 2026-10-10  
**Status:** Revision 2 implementation is present; fresh verification of the amended behavior and final feature acceptance remain pending. Task-by-task entries remain historical baseline evidence; acceptance.md now states the current 8–128 policy and records the limitation of the original lower-bound test evidence.
**Requirements baseline:** requirements.md revision 2 explicitly separates creation and verification and adds approved UX/routing refinements. Original implementation used the 15–128 policy and heading-only root; its evidence must not be reinterpreted as verification of this revision.

## 1. Baseline, boundaries and review decisions

This feature adds authentication to the implemented Project Foundation. It retains Java 21, Spring Boot 3.5.16 and its dependency management, Angular 20 standalone components, PostgreSQL 17, Flyway, one modular-monolith backend, native backend/frontend development with Compose PostgreSQL, and relative `/api/v1` requests. No gateway, identity provider, registration, ADMIN application role, business endpoints, demo accounts or deployment topology is added.

References: [requirements](requirements.md); steering [security](../../steering/security.md), [technology](../../steering/tech.md), [structure](../../steering/structure.md), [backend](../../steering/backend-standards.md), [frontend](../../steering/frontend-standards.md), [API](../../steering/api-contracts.md), [testing](../../steering/testing-standards.md), [workflow](../../steering/spec-workflow.md), [product](../../steering/product.md) and [domain](../../steering/domain-rules.md); canonical [session decision](../../../docs/design/session-architecture-decision.md), [API](../../../docs/design/api-contract.md), [database](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md) and [business rules](../../../docs/business-rules.md).

The foundation currently has stateless deny-all security, API CSRF exemptions, inert login/customer/agent pages and no mapped entities. Its `V1__create_app_users.sql` already creates `app_users`. This feature replaces that security baseline, maps that table and adds migrations. Historical foundation specifications, evidence and V1 stay unchanged. Hibernate validates mapped entities; it does not create schema or require unmapped tables.

| Decision | Current position |
| --- | --- |
| Password encoder | **Accepted:** Spring Security PBKDF2-HMAC-SHA256 with the versioned configuration in §3, subject to benchmarking. It supersedes current BCrypt policy statements once canonical documents are aligned during approved implementation. |
| Strict idle semantics | **Accepted with refinement:** JDBC authentication activity metadata and a 1,800-second authoritative deadline. Proposed activity now records admission to eligible authenticated handlers rather than testing the final response status; see §5.2 and the required AUTH-04 clarification in §11. |
| HTTP contract | **Accepted:** `/auth/csrf`, `/auth/login`, `/auth/me`, `/auth/logout`, numeric CSRF code 1010 and the existing envelope. |
| Cookie / provisioning | **Accepted:** Host-only HttpOnly session cookie and explicit non-web privileged operations. Production Secure/Lax and local HTTP isolation remain unchanged. |
| Concurrent sessions | Multiple independent sessions; logout ends one and account-version changes revoke all previous sessions. No new session cap or device-management UI. |

Only final design approval and the identified activity-requirement clarification remain open. No accepted decision is being resubmitted for approval.

## 2. Architecture and component responsibilities

Browser → Angular same-origin `/api/v1` → Spring Security → authentication application service → PostgreSQL account and session storage. Other backend instances use the same database; no sticky routing is needed. A future reverse proxy handles TLS/routing, not authentication or domain authorization.

| Existing module / proposed component | Responsibility |
| --- | --- |
| `auth` / `AppUser`, account repository | Map existing account columns plus authentication version; normalized email lookup. The banking `account` module remains separate. |
| `auth` / authentication service and controller | Validate login DTOs, authenticate, return minimal identity, coordinate explicit context persistence and lifecycle operations. Never return entities or password hashes. |
| `auth` / operational provisioning service and command adapter | Create/maintain accounts transactionally, increment revocation version and emit redacted operational audit logs. Not reachable through MVC. |
| `security` / security configuration | Explicit endpoint/method authorization, stateful context persistence, CSRF, session fixation strategy and JSON entry/denial handlers. |
| `security` / current-account validation and activity service | On every protected request, validate persisted identity/version/current account; enforce authoritative idle deadline; conditionally record eligible request admission. |
| `security` / session availability boundary | Map uncommitted account/session database failures to 503 through an outer servlet filter; rely on supported immediate JDBC persistence and the framework commit wrapper, not custom buffering. |
| `common` / response and correlation support | Reuse the established envelope convention, safe error writer, numeric codes and `X-Correlation-ID`; create focused support where the foundation has only conventions. |
| Spring Session JDBC | Persist security context and CSRF attributes, replace servlet-local sessions, rotate identifiers and invalidate shared sessions. |
| Angular `core/auth` | In-memory identity/CSRF state, initialization, service, functional interceptors and guards. |
| Angular `features/auth/login` and `layouts/dashboard-layout` | Login form; role-aware header/navigation and sign-out. Preserve `Login`, `DashboardLayout` and `app-dashboard-layout`. |

The existing optional org.projectlombok:lombok dependency is available for routine backend boilerplate; use it selectively for required-argument constructors and loggers, while keeping domain invariants, secret handling and derived constructor initialization explicit. Do not use @Data on entities or secret-bearing types. Add only Boot-managed `spring-session-jdbc` and test-scoped `spring-security-test` to existing dependencies. Do not pin a second Security/Session version, add OAuth resource-server libraries or migrate Angular's test runner. The locally cached Boot 3.5.16 BOM resolves Spring Security **6.5.11** and Spring Session **3.5.7**. Local `javap` inspection confirms the Security session/context APIs and Boot JDBC flush/save properties described in §5.5. Tagged Session source confirms persistence behavior. Reconfirm the dependency tree during implementation; do not use APIs from newer reference documentation.

Repositories stay inside their owning module. Security consumes a narrow authentication-account lookup interface; future banking modules consume stable authenticated identity, not the authentication repository. Controllers remain thin, services own transactions, DTOs are immutable records, and JPA entities never become response/session payloads.

## 3. Accounts, passwords and privileged operations

### 3.1 Identity and validation

Account identity remains a stable UUID, normalized login email, encoded password, exactly one `CUSTOMER` or `AGENT` role, enabled flag and creation instant. Add `auth_version`, a nonnegative BIGINT initialized to zero. Do not add display names, customer banking accounts or unrelated business fields.

Provisioning and login share one email normalizer: remove leading/trailing ASCII whitespace, lowercase with `Locale.ROOT`, then validate email syntax and the existing 254-character bound. Do not remove internal characters or invent provider-specific rules such as dot/plus-address folding. Store only the canonical value. A normalized unique database index prevents concurrent duplicate creation; invalid input and duplicates leave no partial account. Migration preflight must detect existing normalization collisions/noncanonical rows and stop for operator review, not silently merge identities. No default locale-dependent normalization.

Password creation/replacement accepts 8–128 well-formed Unicode code points, inclusive. Login and encoder verification separately accept non-empty, well-formed input up to 128 code points; they do not enforce the creation minimum. Preserve every supplied character, including spaces and Unicode, with no trimming, normalization, composition requirements, truncation or silent transformation. Reject missing/empty/non-string/malformed/oversized login fields with generic 400/1004 under existing CSRF precedence; bounded incorrect inputs reach normal verification and generic 401/1001. Apply the existing bounded credential-body limit without echoing supplied values.

Use clearly named creation and verification validation operations with a shared small well-formed-Unicode/counting helper. A short surrogate-aware scan is justified to prevent malformed text from being silently encoded differently; avoid elaborate regex or alternate Unicode normalization. The frontend mirrors only login admission bounds; the server remains authoritative. Non-web create/replace enforces creation rules, while encoder matches uses only verification bounds, so a change to creation policy cannot block a compatible existing hash.

The 8-character minimum is a developer-approved assessment-specific security trade-off for password-only authentication without MFA, not a preferred production banking standard. Recommend longer passphrases in operator guidance without composition rules; no creation instructions or Unicode terminology belongs on the login screen.

### 3.2 Accepted versioned password encoder — benchmark required

Use Spring Security `Pbkdf2PasswordEncoder` with PBKDF2-HMAC-SHA256, a random 16-byte salt, 600,000 iterations and a 256-bit derived key. Wrap it in an explicitly configured `DelegatingPasswordEncoder` identifier such as `{pbkdf2-sha256-v1}`; that identifier fixes all parameters. Do not use the delegating factory's default encoding algorithm or permit plaintext fallback. The resulting encoded value fits `password_hash VARCHAR(255)`. Changing parameters requires a new identifier, not reinterpretation of existing hashes.

This supports the approved Unicode range without BCrypt's byte limit or additional crypto-provider dependencies. Benchmark verification on assessment hardware and record the cost; expensive hashing is done only for login/provisioning, not authenticated requests. Do not lower the selected work factor silently to pass tests. Equal passwords must receive independent salts. Known unsupported/corrupt hash formats fail closed with a generic login outcome and a safe operational alert; they never select an insecure fallback.

BCrypt remains stated in `tech.md`, `security.md`, the session architecture decision, database design and historical V1's comment. The developer has now approved superseding those current policy statements with this versioned encoder. This design-only refinement leaves those documents unchanged; align them during approved implementation. V1's historical comment remains untouched. If actual accounts already contain BCrypt hashes, inventory formats without exporting hashes and require privileged password replacement or separately approve a bounded compatibility policy; do not assume an empty database just because the repository has no seed accounts. Reducing the password range or silently pre-hashing for BCrypt is not proposed.

Unknown email and disabled-account login paths perform one dummy verification using the same configured work factor, and return the same response as a wrong password. Timing tests check for obvious fast-path enumeration; do not claim mathematically identical timing over a network. Never log credential bodies, encoder output or a password DTO's generated string representation.

The encoder configuration and `{pbkdf2-sha256-v1}` interpretation remain unchanged: 600,000 iterations, random 16-byte salt and 256-bit derived key. Existing hashes require neither migration nor re-encoding. Separating input admission from hashing must retain supported-format rejection, dummy verification and generic failure behavior; a password below the creation minimum can still verify against a compatible existing hash. No new encoder/provider, fallback or Flyway change is authorized.

### 3.3 Operational provisioning and maintenance

Provide an explicit command mode of the existing backend artifact, with web startup disabled and a dedicated opt-in operational profile/entry point. It runs only when a trusted operator invokes it; normal application startup never provisions users. Commands accept a non-secret operation/account reference and supported role/status; passwords are read twice from a masked console. Refuse password-bearing command arguments and environment variables. Require an interactive masked console for this assessment; refuse execution if it is unavailable. A secret-manager adapter or automated credential-delivery integration is outside this feature.

The operator requires trusted authenticated OS/host access and externally supplied database credentials authorized to maintain accounts. Portal CUSTOMER/AGENT sessions cannot invoke this interface or obtain these credentials. For the assessment, an explicitly authorized local operator may use the existing local database owner; do not add a database-role provisioning system. Recommend separate least-privilege operational and migration credentials for a production runbook, without making new infrastructure a feature prerequisite. No ADMIN authority is minted.

The repeatable procedure will document: establish trusted operator access; load restricted database configuration; invoke create/disable/enable/set-role/replace-password; validate inputs; commit atomically; record outcome; securely deliver the initial credential using an approved one-to-one secure channel, preferably a password-manager share. Never print the password, store it in a checked-in command example, or send it through application logs. The actual delivery channel and authorized operator identity are deployment-specific operational prerequisites, not a new portal feature. No email/invitation infrastructure is introduced.

Local setup documentation must provide a discoverable end-to-end path: configure the trusted ignored environment file and PostgreSQL; export DB configuration to native processes (Compose interpolation does not do so); build the existing artifact; run explicit masked-console create for synthetic CUSTOMER and AGENT accounts; and sign in through the existing Angular proxy. Database access credentials are separate from website accounts. Automated tests use isolated databases and create no accounts in the developer database. No demo-fixture mechanism, plaintext account-password environment/argument or normal-startup seed is introduced; AUTH-01.7 stays unchanged. The existing applied migration/volume behavior and safe credential troubleshooting remain documented rather than solved by destructive resets.

Creation uses explicit role and enabled status, a fresh UUID, a hash and version zero. Disabling, re-enabling, role changes and password replacements increment `auth_version` in the same database transaction as the change. Re-enabling therefore cannot revive old sessions. Concurrent administrative changes use row locking/guarded updates; no lost version increments. Validation failures and duplicate identities roll back.

Reuse structured operational logging for audit: timestamp, trusted operator reference established by the controlled host procedure, operation, non-sensitive account UUID when known, outcome and correlation reference. Emit success only after commit and failure after rollback; document restricted access/retention for the captured log. No separate audit table, outbox or external logging platform is required by AUTH-NFR-01. Logs and database commits are not a single atomic transaction: a process crash between them can leave an incomplete outcome record. The proposed assessment solution documents this ordinary operational-log limitation; stronger crash-durable audit is deferred hardening, not claimed by this design. Operators verify the database outcome after an interrupted command and do not blindly repeat it.

Deletion is handled only by trusted operations: deleting a user makes lookup fail and removes their authentication metadata. Do not cascade-delete future banking history; future business foreign keys may require disabling instead of deletion. Deletion/recreation never reuses the former UUID. Test this revocation case without adding public delete functionality.

## 4. Proposed API contracts and security policy

### 4.1 Endpoints

All paths below include the existing `/api/v1` prefix. Requests and responses use JSON and the canonical custom envelope, not RFC 9457 bodies. No authentication endpoint uses 201/204, `Location` or an ETag: none represents domain resource creation/versioning. Existing future dispute creation `Location`, agent ETags/If-Match and pagination remain unchanged.

| Method and path | Authorization | Input / 200 success data | Activity |
| --- | --- | --- | --- |
| `GET /api/v1/auth/csrf` | Anonymous permitted | No body; `{ "csrfToken": "<masked token>", "headerName": "X-CSRF-TOKEN" }` | Never extends authenticated idle time |
| `POST /api/v1/auth/login` | Anonymous permitted, valid CSRF required | `{ "email": "customer@example.test", "password": "<exact supplied password>" }`; returns minimal identity | Establishes fresh authenticated activity |
| `GET /api/v1/auth/me` | Authenticated supported role | No body; minimal current identity | Successful user-driven request extends idle time; no polling |
| `POST /api/v1/auth/logout` | Anonymous or authenticated, valid CSRF required | No body; `null` | Ends current session; never keeps it alive |

Successful login/identity example:

```json
{"status":true,"data":{"id":"2b1f3078-9403-46d0-a8e1-0c8b86cbd8ed","email":"customer@example.test","role":"CUSTOMER"},"error_code":0}
```

Successful logout: `{"status":true,"data":null,"error_code":0}`. Bootstrap uses the same envelope with its tabled data. Examples are illustrative contract values, not provisioned accounts or secrets. Identity excludes enabled status, authentication version, hash, session identifier and unavailable display-name fields. Login accepts only `email` and `password`; reject unknown fields, including role/id, with 400. All failures leave the account unchanged.

Every application response, including filter errors, carries a validated/generated `X-Correlation-ID`. Reuse the canonical bounded correlation format; never use a session identifier as a correlation value. Auth/identity/CSRF responses use `Cache-Control: no-store`; no credential data in URLs or cacheable redirects. Session-setting/deletion headers are handled as described in §6.

### 4.2 Errors and precedence

| HTTP / numeric code | Meaning | Safe response detail |
| --- | --- | --- |
| 400 / 1004 | Malformed JSON, invalid email/password fields, unknown properties | Correct the request; optional field errors contain field names/codes, never rejected values |
| 401 / 1001 | Unknown/wrong/disabled login, or missing/expired/revoked identity | Login: `Unable to sign in with these credentials.` Protected request: `Authentication is required.` |
| 403 / 1002 | Authenticated wrong role or default-denied resource | `You do not have permission to perform this operation.` |
| 403 / **1010** | Missing, invalid, foreign-context or stale CSRF token | `Security verification failed. Refresh the security token and try again.` |
| 503 / 3002 | Account/session store unavailable, including persistence failures | `The service is temporarily unavailable. Please try again.` |
| 500 / 3001 | Unexpected internal failure | Generic message and correlation ID only |

Existing 413/1006, 415/1007, 405/1008 and 406/1009 remain available for applicable request/media/method failures. The developer accepted 1010 for CSRF rejection; retain its distinct meaning and do not reuse another code. No new login rate-limiting infrastructure or account lockout policy is inferred from the existing 429 catalog entry.

Example CSRF rejection:

```json
{"status":false,"data":null,"error_code":1010,"error":{"type":"/problems/csrf-rejected","title":"Security verification failed","detail":"Security verification failed. Refresh the security token and try again."}}
```

Use matching `/problems/unauthenticated`, `/problems/forbidden`, `/problems/validation-failed` and `/problems/service-unavailable` types with the same envelope shape. A missing/invalid CSRF token on an unsafe request takes precedence over the usual unauthenticated 401. If session/token storage cannot be read, return 503 because validity cannot be determined; do not falsely classify an outage as invalid CSRF or credentials. Protected safe reads validate session/account before authorization. A valid-CSRF unsafe request then follows 401/403 authorization rules. No HTML login/error redirect.

Password validation errors follow §3.1: malformed/missing/empty/oversized input is generic 400/1004; any non-empty well-formed bounded password is eligible for verification regardless of the creation minimum and gets generic 401/1001 when incorrect. Exact API paths, payload fields, envelope and CSRF/error precedence are unchanged.

### 4.3 Default-deny authorization

Permit exactly the foundation's three health GET paths and the explicit bootstrap/login/logout method/path pairs. Require authenticated CUSTOMER or AGENT for `GET /auth/me`. Unknown paths and methods are not authenticated allowlists; authenticated unknown API requests are denied with 403, unauthenticated safe requests with 401, and unsafe requests first undergo CSRF validation. The servlet error-dispatch path may render an already established safe envelope without reopening arbitrary `/error` REQUEST access.

Do not add transaction/dispute authorization matchers, permissive route reservations or business controllers in this feature. These paths remain default-denied for both roles. Each future feature adds only its approved method/path rules, role checks and ownership/assignment constraints together with its implemented operations and boundary tests; no broad `/api/**` permit rule.

AUTH-08.2 and AUTH-08.6 still require evidence that server-established CUSTOMER/AGENT authorities produce opposite-role 403 responses. Exercise the production authority mapping and Spring Security `AuthorizationFilter`/role authorization managers through a focused test-only filter-chain fixture with innocuous customer-only and agent-only matcher cases. It uses the real session/account-validation components and JDBC fixtures; a terminal test filter records whether authorization reached it. It adds no MVC controller, business operation or shipped route allowlist. Separately, full production-chain HTTP tests cover `/auth/me` for both roles, anonymous 401 and unmatched-path denial for both. This proves role enforcement machinery, not authorization of future business endpoints. Record this scope/evidence interpretation explicitly in §11 rather than claiming future operation coverage.

Use `ROLE_CUSTOMER` and `ROLE_AGENT` derived exclusively from the validated server account. Do not trust browser roles or client-supplied identity. Future customer queries remain ownership-scoped; agent role does not bypass assignment/terminal-state rules. Retain disabled Basic/form-login/remember-me and disabled request caching, and the exclusion of generated-user auto-configuration. Adopt stateful `IF_REQUIRED` sessions with explicit SecurityContext persistence and a null saved-request cache; no session is created merely to remember a denied request.

## 5. Session lifecycle, activity and revocation

### 5.1 Persisted state

Spring Session JDBC persists servlet sessions, security context and session-bound CSRF attributes. Set the repository/default session interval to **1,800 seconds**, schema initialization to **never**, and use Flyway-only schema creation. Use Boot-managed JDBC configuration with **`spring.session.jdbc.flush-mode=immediate`** and **`spring.session.jdbc.save-mode=on-set-attribute`**. Immediate mode moves attribute/access persistence into the normal request path before response output; the modest extra JDBC writes are acceptable for the assessment. Do not claim a public `HttpSession.flush()` API exists. Login saves context through the supported repository API, which sets a session attribute and therefore flushes with this configuration.

Persist a minimal, versioned, serializable principal containing stable UUID and supported authority; never a JPA entity, password hash or credential object. Authorities deserialized from a session are not trusted until the current account/version check completes. Persist `auth_version` and activity in the metadata described below, not mutable authority caches. Compatible application instances must use the same schema, cookie settings, principal serialization and encoder mapping. Java session serialization requires controlled database access; use a fixed serial form/serialVersionUID and test cross-instance/restart compatibility. An incompatible release needs a reviewed session-invalidation rollout rather than pretending all serialized sessions remain compatible.

### 5.2 Strict idle activity — accepted metadata, refined admission policy

Add `auth_session_activity`, keyed by Spring Session's immutable `PRIMARY_ID`, with account UUID, captured `auth_version` and `last_authenticated_activity_at` in epoch milliseconds. Resolve cookie `SESSION_ID` through `SPRING_SESSION` to this metadata; identifiers never leave the server except the opaque cookie. A missing metadata row for a stored authenticated context fails authentication. Anonymous CSRF sessions intentionally have no authenticated activity row.

The authoritative deadline is `last_authenticated_activity_at + 1_800_000`. Use a single database-time value per check, with expiration at equality. On every store-dependent protected request, verify both Spring Session validity and this deadline before authorization. PostgreSQL time avoids trusting browser clocks and bounds multi-instance skew; backend clocks must still be synchronized for Spring Session's own expiry. Cleanup timing is irrelevant to permission decisions.

**Recommended activity policy:** Count admission to an explicitly eligible authenticated user-interaction handler, independent of its eventual HTTP result. The request must already have passed session/account/version/deadline checks, required CSRF validation and the endpoint's backend role authorization. The activity instant is the framework-recorded session access time for this request, captured from the public `HttpSession.getLastAccessedTime()` after session loading; renewal is committed at handler admission, not response completion. This aligns the framework and authentication idle clocks without re-saving a second stale copy of the session. Login establishes fresh activity separately. Currently only `/auth/me` is an eligible authenticated interaction; startup/reload is a deliberate identity check, not a timer.

| Request outcome/category | Renews authoritative activity? |
| --- | --- |
| Eligible authenticated handler admitted before expiry; later 2xx | Yes, once at admission |
| Same handler later returns validation 400/422, domain conflict 409/412, resource-not-found 404, or handler 5xx | Yes: the authorized interaction occurred; application success is not the activity criterion. Future statuses are illustrations, not feature implementations. |
| Anonymous, expired, revoked, unsupported-role or wrong-role request rejected before admission | No |
| Missing/invalid CSRF rejected before admission | No |
| Unmatched path, wrong method, health, CSRF bootstrap, logout or anonymous login failure | No |
| Background polling/timer endpoint | No; excluded by the server handler policy, regardless of cookie or response status |

Use a narrowly scoped Spring MVC `HandlerInterceptor.preHandle` after the security chain and handler resolution, before controller argument validation/execution. An explicit server-side eligible-handler marker/registry selects interactions. It consumes validated authentication request state established by the security filter; no client flag can establish identity or eligibility. Missing handlers and default-denied paths never reach an eligible admission. Pre-handler protocol failures such as a rejected media type do not count if admission never runs. Later service-layer/domain errors after admission do count; do not implement status-based rollback of activity.

At admission perform a guarded JDBC update using one database-time value for expiry/account checks and monotonic `GREATEST` over the captured request activity instant, requiring an existing metadata row, unexpired deadline and matching current enabled supported-role account/version. If zero rows update, re-evaluate the valid account/session condition and reject with 401 rather than invoking the handler. If the database operation throws, return 503 before handler/output. Never insert metadata from a protected request. A concurrent logout/revocation cannot be undone by a touch. A request admitted just before expiry legitimately starts a new 30-minute interval even if it runs for a long time; no completion callback later revives it.

Eligibility is a server endpoint policy, not proof of a physical human gesture: ordinary HTTP cannot distinguish manual and scripted requests to the same permitted handler. Angular must not poll `/auth/me`; future background endpoints must be marked ineligible and use the same admission mechanism. No heartbeat endpoint or client-controlled keepalive marker is introduced. This meets the stated ban on application-generated polling without pretending to detect arbitrary browser automation.

Spring Session's own interval remains 1,800 seconds; default automatic touches cannot extend the authoritative authentication deadline. Bootstrap accesses may change framework `LAST_ACCESS_TIME`, but never authentication activity. Synchronize backend clocks; configure/read Spring Session normally and do not bypass its expiry. Use the same trusted server-recorded request-access timestamp as Spring Session for metadata renewal, so an independently later timestamp does not create a deadline longer than the framework session. Login also initializes metadata from the current session's recorded access instant, after the context write has persisted it. No second `findById`/`save` of a mutable session copy is performed just to refresh activity: that could overwrite a concurrent identifier rotation. Do not directly update Spring Session's managed columns or introduce a custom session repository. Backend clocks must be synchronized with PostgreSQL for database-time expiry checks; record that operating assumption in the runbook. Tests cover captured access time, concurrent monotonic metadata renewal, framework expiration and old-identifier replay during concurrent login/request activity.

**Requirements impact:** AUTH-04.3 currently says a “successful authenticated request.” Explicitly clarify it to successful authentication/authorization and eligible handler admission, including later validation failures. Until approved, this is a recommended policy change, not an asserted interpretation already authorized by the requirements. The alternative is retaining 2xx-only renewal, which excludes legitimate failed submissions and would require a more involved pre-output status decision. See §11.

### 5.3 Login and fixation

1. Bootstrap materializes an anonymous JDBC session and CSRF token through normal session access/attribute persistence.
2. Login validates CSRF, strict request fields and normalized credentials through Spring Security's authentication manager/provider.
3. On success invoke an explicit `CompositeSessionAuthenticationStrategy` comprising `ChangeSessionIdAuthenticationStrategy` and `CsrfAuthenticationStrategy`. Retire any previous authentication-activity row for this session when signing in again. Failed login does not replace an existing authenticated account.
4. Build a credential-free security context; set it on the current SecurityContext holder and explicitly call `HttpSessionSecurityContextRepository.saveContext`. With IMMEDIATE flush, attribute persistence also saves the pending identifier change. `changeSessionId()` by itself marks the JDBC session changed but does not immediately flush it in 3.5.7; the context-attribute write is the required subsequent flush.
5. Resolve the persisted primary key by a JDBC query using the public `HttpSession.getId()` value and `SPRING_SESSION.SESSION_ID`; do not access Spring Session's private/package-private wrapper or primary-key members. Under an account row lock, verify enabled/version against the credential result and insert the activation metadata with the current session-access instant. Commit before the controller returns identity. A changed account returns the generic login failure; missing metadata/context correlation never grants access.
6. The framework commit wrapper saves the session before emitting its rotated cookie. Angular then bootstraps fresh CSRF material before unsafe actions. Old identifier replay is denied.

Calling `AuthenticationManager` alone does not supply these session strategies/context persistence. Clear credentials from the resulting authentication and temporary buffers as practical. JDBC session writes and the activation transaction are separate supported persistence steps, not an invented shared transaction with framework internals. If any step fails, no success is returned; a partial context lacking metadata is unusable. Verify principal UUID equals metadata account UUID on every protected request. Retired metadata is never restored from an old in-flight request.

Use local failure cleanup without requiring another successful database operation: clear the holder, leave no activation metadata, and attempt session invalidation best-effort. The outer availability handler must suppress an uncommitted login cookie on failure. If metadata activation has already committed and a later pre-commit failure occurs, delete it/invalidate best-effort and withhold the new identifier. Do not promise rollback of an already committed repository transaction or an already delivered HTTP response.

### 5.4 Account changes and concurrent sessions

For every protected request, read the account from PostgreSQL and compare the metadata's captured version with current `app_users.auth_version`. Missing/deleted/disabled/unsupported-role/version-mismatched accounts invalidate the current session and return 401; no cached authorization fallback. Only after this check may the request acquire current supported authorities and emit identity.

Privileged changes increment the version atomically. Every old session on every instance is denied on its next protected request after commit; no global in-memory cache or bulk-delete race is required for correctness. Re-enable increments again. Multiple sessions are allowed; logout deletes one, account changes revoke all. Requests authorized before an administrative commit may finish, as AUTH-06 explicitly permits. Background deletion of revoked rows is housekeeping, not the revocation mechanism.

### 5.5 Logout and persistence failure boundary

Logout is POST and validates CSRF before mutation. Invalidate/delete the current JDBC session, clear security context and CSRF material, and expire the cookie with its original scope. Repeat logout after a fresh anonymous bootstrap succeeds with the same 200/null response. GET never performs logout. No automatic retry of a transport-ambiguous logout: the UI explains that confirmation failed and offers a deliberate retry after bootstrap.

### Verified framework boundary and ordering

Boot 3.5.16 manages Security 6.5.11 / Session 3.5.7. The local BOM and compiled API inspection confirm `JdbcSessionProperties.setFlushMode/setSaveMode`, `CompositeSessionAuthenticationStrategy`, `CsrfAuthenticationStrategy` and `HttpSessionSecurityContextRepository.saveContext`. Tagged Session 3.5.7 source confirms IMMEDIATE flush on session attribute/access changes, deferred dirty identifier rotation, synchronous repository deletion during `HttpSession.invalidate()`, and framework-owned saving before response commit. These are supported mechanisms; `commitSession()` and the wrapped session's primary key are internal and must not be called/reflected into.

Ordering is: correlation/availability servlet filter → Spring Session's repository filter → Spring Security → MVC admission interceptor/controller. Register the outer filter using ordinary Boot servlet filter ordering before `SessionRepositoryFilter.DEFAULT_ORDER`; do not also register it as an in-chain security filter. Current-account validation runs after context loading and before authorization. CSRF still rejects unsafe requests before handler admission; the precise placement must preserve the documented 403/401 precedence.

Use **IMMEDIATE** flushing and explicit lifecycle operations before MVC returns its small JSON result. Logout invalidation invokes JDBC deletion synchronously before the success body. Bootstrap materializes its token before returning. Admission activity writes precede controller execution. Do not wait until `afterCompletion`, wrap every response in `ContentCachingResponseWrapper`, replace the session repository/filter, or add a custom response-commit extension point.

Spring Session already wraps the servlet response and saves before intercepted commit/flush and again in its filter `finally`. The outer filter handles a propagated store failure only while `response.isCommitted()` is false: reset uncommitted body/status and session-cookie headers, restore correlation/no-store headers and write the generic 503 using the underlying response after the inner chain unwinds. MVC exception advice and security handlers also map store exceptions to 503; they must not misclassify them as invalid credentials. No custom response-body buffering is planned. Do not use `sendError` for application envelopes or manually flush a success before lifecycle work completes.

IMMEDIATE flush and ON_SET_ATTRIBUTE avoid normal pending attribute writes at completion, but this is not a proof that all filter-finally paths are inert. Acceptance tests must inject read/write failures at context save, rotation flush, metadata activation, logout deletion and the framework's pre-commit save/finalization path. If an uncommitted finalization failure cannot be mapped correctly with standard ordering, stop and report that concrete limitation; do not introduce speculative buffering to hide it. Source/API inspection proves the mechanisms exist; it does not replace executable fault-injection evidence.

**HTTP limit:** Once a response has physically committed, its status/cookie cannot be withdrawn. A later failure can only be logged safely and the connection terminated as appropriate. AUTH-05.7 must be read as requiring a generic 503 for detected store-dependent request failures before HTTP commitment, with no success emitted before required persistence. This is an explicit feasibility clarification for review, not a promise of post-commit status rewriting. Streaming/asynchronous authenticated responses and session mutations after output are outside this feature; a future feature must design their failure boundary separately.

Account/session reads and writes use bounded connection/query timeouts. No local fallback. Readiness includes DB; liveness remains independent. Match exactly the three approved health GET paths in a security chain with a null SecurityContext repository and no session/account access. All remaining paths use stateful/default-deny security. Health therefore does not load/touch a session or depend on session persistence to report liveness; other health methods/paths remain denied.

## 6. CSRF, cookies and browser boundary

### 6.1 CSRF bootstrap and lifecycle

Use `HttpSessionCsrfTokenRepository`, the default XOR/masked request handler supported by the managed Security version, and `X-CSRF-TOKEN` transport. Bootstrap resolves the request's masked token and returns it in JSON; this materializes the deferred token in its JDBC session. Do not return the raw session identifier or disable CSRF on `/api/**`. Using the framework masked token avoids replacing its request handler with an ad hoc token comparison. Verify actual masked-header resolution in integration tests.

Angular keeps token material only in memory; no second JavaScript-readable cookie is necessary. The custom interceptor sends it only for same-origin unsafe `/api/v1` requests. Disable Angular's default cookie-XSRF mechanism if using this custom transport, to avoid two token sources/headers. Cookie HttpOnly does not block the browser from automatically sending it. Do not add credentialed CORS, token query parameters or bearer headers.

Startup: bootstrap once, then resolve `/auth/me`. Login: use the pre-login token, clear token state on success, bootstrap again. Logout: use the current token, then on confirmed success clear identity/token state and bootstrap an anonymous token for future login. Expiry/401: clear private identity/content and obtain a new anonymous bootstrap token when returning to login. Bootstrap encountering expired authenticated metadata must downgrade/invalidate the expired context before providing anonymous material, without extending authentication.

An invalid/missing/stale token gets 403/1010 before login/logout or other unsafe state changes. A separate cookie jar's token cannot authorize this browser's request. Login/logout clear stored tokens so all pre-transition masked values fail. Concurrent tabs can observe stale material after a transition; recover via explicit bootstrap, not automatic replay of unsafe business requests. If transition bootstrap fails, retain the confirmed identity/logout outcome, block unsafe actions, and offer bootstrap retry. A bootstrap failure must not undo a confirmed logout or claim the user failed to authenticate after login already succeeded.

Token acquisition/refresh presentation follows §8. Normal bootstrap and transition refresh are transparent; ordinary incorrect credentials never reveal token-recovery controls. Only a genuine unresolved token acquisition/refresh requires contextual plain-language "Try again". Confirmed authentication/logout remains confirmed after refresh failure; ambiguity never implies success. Single-flight safe bootstrap is permitted, but credentials and unsafe mutations are never automatically replayed after an ambiguous outcome. This changes presentation, not token binding, masking, header transport, rotation or AUTH-09.

### 6.2 Cookie configuration

| Setting | Production | Explicit local development |
| --- | --- | --- |
| Name / content | `SESSION`, opaque Spring Session identifier only | Same |
| Path | `/api/v1` | Same; Angular proxy retains path |
| Domain | Omitted: host-only | Omitted |
| HttpOnly / SameSite | `true` / `Lax` | Same |
| Secure | Explicitly `true`, independent of request forwarding headers | `false` only in the `local` profile |
| Lifetime | Browser-session cookie, no persistent Max-Age/Expires | Same |
| Tracking | Cookies only; no URL rewriting/session-id parameter authentication | Same |

Logout emits deletion for `SESSION` with `/api/v1`, no Domain and matching security attributes. Session management never trusts URL IDs. A forged `Forwarded` or `X-Forwarded-Proto` cannot turn off Secure: the configured production value is fixed. Accept forwarding metadata only from the future trusted proxy; HTTPS redirects/HSTS and network access restrictions belong to that deployment's verification, not a new deployment deliverable here.

Use the foundation workflow: trusted ignored root `.env` supplies DB configuration and `SPRING_PROFILES_ACTIVE=local`; Compose starts PostgreSQL; native backend loads the exported variables; `npm start` proxies `/api/**` from `http://localhost:4200` to `localhost:8080`. Log in through port 4200, not a separate frontend-to-backend origin. DevTools confirms a host-only HttpOnly/Lax cookie with Secure disabled only locally. Never put application credentials in `.env`.

Default configuration requires Secure cookies. The production profile rejects `local` being active and rejects any insecure-cookie override; production execution must explicitly select that profile. Production deployment review must verify the profile and HTTPS boundary. Choosing the local profile explicitly is an operator action, not a production default; do not claim the application can detect every misconfigured external deployment. No hardcoded DB credentials or broad CORS workaround.

## 7. Additive database design

Flyway remains the sole schema authority. Keep V1 byte-for-byte unchanged. Proposed migration versions assume V1 is still the latest applied version; confirm that before allocating filenames. No migration creates a user or embeds a hash/password.

| Proposed migration | Additions |
| --- | --- |
| `V2__extend_auth_accounts.sql` | Add nonnegative `auth_version BIGINT NOT NULL DEFAULT 0`; normalized email uniqueness/checks after explicit preflight. Retain existing UUID, email, hash, role, enabled and created-at columns/constraints. |
| `V3__create_jdbc_sessions.sql` | Copy the PostgreSQL schema shipped by the resolved Spring Session JDBC dependency, including its required keys/indexes; do not copy a newer release's unrelated schema. |
| `V4__create_auth_session_activity.sql` | Authentication activity metadata and its constraints/indexes only. Operational audit uses redacted logs. |

Spring Session schema shape to verify against the managed artifact:

- `SPRING_SESSION`: `PRIMARY_ID CHAR(36)` primary key; unique `SESSION_ID CHAR(36)`; `CREATION_TIME`, `LAST_ACCESS_TIME`, `EXPIRY_TIME` BIGINT milliseconds; `MAX_INACTIVE_INTERVAL` INTEGER seconds; nullable indexed `PRINCIPAL_NAME VARCHAR(100)`.
- `SPRING_SESSION_ATTRIBUTES`: `SESSION_PRIMARY_ID CHAR(36)` plus `ATTRIBUTE_NAME VARCHAR(200)` composite key; `ATTRIBUTE_BYTES BYTEA`; session foreign key with cascade delete. Preserve the dependency's expiry/principal/session-id indexes and repository query compatibility.
- `auth_session_activity`: `session_primary_id CHAR(36)` primary key/FK to session `PRIMARY_ID` with cascade delete; `account_id UUID` FK to `app_users` with cascade delete; `auth_version BIGINT NOT NULL CHECK >= 0`; `last_authenticated_activity_at BIGINT NOT NULL CHECK >= 0`; index `(account_id)` and an activity-time index for cleanup. Resolve identifiers using the existing unique session-id index; avoid storing duplicate secret cookie values here.

Set `spring.session.jdbc.initialize-schema=never`; default cleanup cron proposal `0 * * * * *`, externally configurable through an authentication cleanup setting. Retain Spring Session's expired-row cleanup and additionally delete sessions whose authoritative activity deadline has elapsed; deleting parent sessions cascades attributes/activity. Use database time for authoritative cleanup; do not delete anonymous bootstrap sessions based on missing metadata. Multiple instances may execute idempotent bounded deletes; no scheduler service is needed. Tune batches only when measured.

JPA maps `app_users` in `auth`; JDBC owns session/activity infrastructure. Map a separate optimistic `@Version` only if needed by the account-update implementation; do not confuse persistence locking with `auth_version`'s revocation semantics. The proposed service uses transactional row locking and a version increment instead. Flyway validates history/checksums and Hibernate validates actual mapped entities. Test constraints and indexes against real PostgreSQL, including metadata cascades. Operational audit log verification is separate from database constraint testing.

## 8. Angular design

Reuse `app.ts`, `app.html`, `app.scss`, `app.spec.ts`, existing `login.ts`/HTML/SCSS/spec and `layouts/dashboard-layout/dashboard-layout.*`. Keep `shared/components` for reusable UI; introduce no component library, renamed `app-shell`, deprecated animation provider or state-management package. Preserve global `styles.scss` with Sass `@use` of tokens/reset/typography and component-local styles.

Proposed additions under `core/auth`: typed contracts, `auth.service.ts`, `session-state.ts`, `csrf-state.ts`, functional auth/role guards, CSRF/error interceptors and their specs. A small `features/auth/access-denied/access-denied.ts` page supplies a public safe denial outcome. Service files may use descriptive service suffixes; components use the approved concise filenames/classes. No `.component.ts` filenames.

Session state is an explicit signal-backed union: initializing, authenticated(identity), anonymous, or unavailable. Availability is not anonymous. Initialization is single-flight: bootstrap, then identity; do not render protected routes/content until resolved. Use an Angular 20-supported app initializer and guards sharing the same initialization promise/observable. A 401 becomes anonymous; a 503/network failure shows a retryable initialization state without rendering protected content or pretending the session was logged out. Initializer completes on handled failure so a retry screen can render; it does not hang the application indefinitely.

Functional `canMatch` role checks run before protected feature matching/loading; `canActivateChild` retains the boundary during child navigation. Anonymous users go to login. Wrong-role users go to access-denied without rendering the protected component or fetching its data. DashboardLayout displays links for the verified role and sign-out. Use existing `/customer` and `/agent` landing routes; transactions/queue detail pages remain future features.

`AuthService` orchestrates bootstrap, identity, login and logout. Separate lightweight session/CSRF state stores prevent circular injection between HttpClient, service and interceptors. Guards return router UrlTrees; do not navigate imperatively during guard evaluation. HttpClient sends relative `/api/v1` URLs with browser-managed same-origin cookies. CSRF interception verifies origin and normalized API path, excludes safe methods, and blocks an unsafe submission without usable token state. An HttpContext marker may identify bootstrap/auth handling to prevent recovery recursion; it is not a backend authorization signal.

The error interceptor recognizes the custom numeric envelope but treats non-JSON proxy and transport errors safely. Any backend authentication 401 clears identity, private component/cached state and pending sensitive UI; login 401 also displays the generic form failure. If the server retained a previously valid session after a failed account-switch attempt, Angular still clears its identity on 401 and may restore it only through a deliberate new identity initialization. A late response from an old authentication generation cannot restore cleared state: increment an in-memory generation on transitions, cancel/ignore superseded requests and gate protected rendering against current identity. Permission/CSRF 403 and 503 do not clear a confirmed identity. Shared recovery/bootstrap is single-flight; no automatic unsafe-request replay.

Login uses a typed Reactive Form with labelled email/password controls, password autocomplete and keyboard submission. Client admission accepts non-empty well-formed password input up to 128 code points, without enforcing creation rules. Preserve exact input, retain email on errors and clear passwords after confirmed success or leaving the screen. Field feedback appears after blur/interaction or attempted submit, with visible invalid borders, associated field messages and accessible error announcements. Incorrect credentials remain a persistent generic inline error, without creation-policy instructions or technical token terminology. An accessible native show/hide button does not submit, preserves the value/focus, and announces its current action. Disable duplicate submissions and show distinct loading, focus, hover and disabled states.

Use the current design tokens and restrained financial-application presentation: clear heading/supporting copy, consistent typography/spacing, readable responsive form card and no horizontal overflow at mobile widths. Centralize shared presentation states and token values through styles.scss with @use; retain feature-specific layout styles inside components. Repeated buttons use a small shared directive on native buttons or equivalent proportionate presentation mechanism, with primary/secondary variants. It owns presentation/native accessibility only, not authentication actions or routing. Shared reusable UI belongs under shared/components; DashboardLayout remains under layouts/dashboard-layout. No component library, UI framework or marketing landing page is introduced.

A lightweight notification service and one presentation outlet may cover suitable transient application events with accessible announcements, bounded display and no duplicate inline/toast error. Credential/field errors and actionable recovery remain inline and persistent. Important failures must never depend on a disappearing notification; avoid success toasts for every navigation. No business-notification delivery or global state framework is added.

Root and unknown-route resolution awaits the existing single-flight verified identity initialization: anonymous → /login; CUSTOMER → /customer; AGENT → /agent; unresolved → safe loading; unavailable → explicit retry state. Guards return UrlTrees and never expose protected content while resolving. The original heading-only welcome component is no longer the entry destination under this refinement. Retain role guards and allowlisted local return URLs; wrong-role access still goes to access-denied. Backend authorization remains authoritative.

Normal token acquisition/refresh is transparent. An ordinary 401 displays generic incorrect credentials inline; cleared/missing token state alone must not display a technical recovery button. A subsequent deliberate submission may acquire a token before its single credential POST, as today. If token acquisition itself fails, contextual plain-language copy and "Try again" recover token availability; recovery does not resubmit the previous credentials. After confirmed login with failed refresh, preserve confirmed identity, clear password/form, and offer contextual retry of bootstrap followed by role navigation without another login POST. No unbounded retry loop or automatic unsafe replay.

Logout blocks duplicate submissions. Confirmed 200 clears private state and navigates to login even if anonymous token refresh fails; any needed follow-up is plain-language contextual retry. A network/503/incomplete-response transition remains unconfirmed: hide protected views, do not announce successful sign-out, and offer deliberate retry that safely bootstraps before repeating the idempotent logout. An ambiguous login similarly must not claim success or automatically resubmit credentials. Preserve generation checks, 401/403/503 distinctions and all existing session/CSRF protections. No polling or client timer extends session validity.

## 9. Security, concurrency and failure scenarios

| Scenario | Required behavior / trade-off |
| --- | --- |
| Unknown email, wrong password, disabled account | Same generic 401 envelope; dummy hash where needed; no account-existence detail or authenticated-session creation. |
| Existing-session login attempt fails | Existing valid session is not replaced; UI does not infer success for the attempted identity. |
| Missing/stale CSRF while anonymous | 403/1010 before unsafe action; bootstrap/retry deliberately. |
| Expired row still physically present | Request-time deadline check rejects safe or CSRF-valid protected request with 401. Bootstrap can create anonymous material but cannot revive identity. |
| Disable → re-enable with no intervening request | Incremented versions keep all previous sessions revoked; fresh login required. |
| Concurrent admin change/login | Activation checks current enabled/version; a subsequently committed change revokes the captured version. Already authorized operations follow AUTH-06's boundary. |
| Concurrent logout and old in-flight save/activity | Admission touches never insert metadata; parent deletion/cascades and guarded writes prevent revival. Verify repository behavior with real requests, including zero-row updates and stale context saves. |
| Multiple tabs / concurrent browser transitions | Tokens can become stale; generation checks stop old responses restoring UI; bootstrap on explicit retry. Backend remains authoritative. |
| Multi-instance/restart | Shared database and compatible serialization/config recognize the same cookie. Logout/version changes apply across instances; no local fallback. |
| Database outage on read, login save, logout delete or admission write | Bounded fail-closed 503 before commitment; no credential-invalid claim or success before required persistence. No post-commit HTTP rewriting promise. Readiness down, liveness up; recovery rechecks persisted state. |
| Database data loss | Do not promise session survival; reauthentication and operational recovery required. |
| Corrupt account role or session payload | Never grant access; invalidate unsafe context, safe client failure and redacted operational alert. Store unavailable remains distinguishable as 503. |
| Browser XSS | HttpOnly hides the identifier, but XSS can still act as the browser. Retain Angular sanitization and safe templates; CSRF is not an XSS cure. |
| Forwarded-header spoofing | Secure remains fixed in production; trusted proxy configuration must be verified separately. |

Authentication success/failure/logout emit structured timestamped outcome events with correlation ID and non-sensitive account UUID when known. Operational account changes use the same redacted logging convention with operator/operation references; log persistence/retention is documented rather than introducing an audit subsystem. Unknown-account failure logs omit submitted email; never log credential bodies, hashes, cookie headers, session IDs or CSRF material, including exception/debug logging. Representative log capture is acceptance evidence. No claim of constant-time network responses or production readiness is made.

Assessment trade-offs: JDBC shares state without Redis or a new service, at the cost of database dependency and per-request account validation. Revocation version avoids unreliable replica-local caches. Authentication activity metadata is deliberate complexity required by the unusually strict activity requirement. No refresh tokens, absolute session cap, MFA, self-service recovery, distributed rate limiter or account-invitation workflow is added. Future hardening can assess abuse controls and operational audit retention separately.

## 10. Testing strategy and requirement traceability

Use JUnit/Spring Boot tests and the existing pinned PostgreSQL Testcontainers image. Browser-like cookie jars must send actual cookies and bootstrap-produced masked headers through the running HTTP filter chain; `@WithMockUser` and test-only CSRF helpers are insufficient lifecycle evidence. Unit tests cover validation/policy; integration tests cover persistence and races. Fixtures create isolated accounts through the provisioning service with test-only credentials, not migrations or startup seeds.

| Requirement | Planned evidence |
| --- | --- |
| AUTH-01 | Operational command cannot run through HTTP or default startup; both roles provisioned; explicit status; normalized duplicates/races and invalid roles roll back; documented OS/DB privilege boundary; redacted commit/rollback operational logs; no default accounts. |
| AUTH-02 | Creation/replacement 7/8/128/129 and verification empty/1/128/129 code-point boundaries; malformed surrogates rejected; below-creation-minimum verification using compatible existing hashes, emoji/multibyte and space-only accepted-length inputs; no trim/normalization/truncation; passwords differing after byte 72 remain distinct; salts differ; positive/negative verification; stored format/length and work-factor benchmark. |
| AUTH-03 | Actual customer/agent login; bounded passwords below the creation minimum reach verification; malformed/missing/oversized inputs are generic 400, incorrect bounded inputs generic 401; identical unknown/wrong/disabled errors; malformed/unknown-role-field 400; no Basic/form/generated user; failed login leaves no new authenticated metadata; identifier rotation and old-cookie replay. |
| AUTH-04 | Interval equals 1,800; timestamp just before/at/after deadline; eligible identity admission extends; admission-interceptor tests verify validation/domain errors count under the proposed clarification without adding shipped endpoints; pre-admission 401/403, unmatched paths, health and repeated bootstrap do not extend; admission-write failure returns 503 before the handler; expiry despite retained row; monotonic metadata touches, aligned framework access timestamp and concurrent identifier-rotation regression checks; no completion-based renewal. |
| AUTH-05 | Two running application contexts/ports share one isolated PostgreSQL; A-login/B-identity; context restart with persisted state; A-logout/B-replay; Flyway clean and upgrade from V1, checksum validation and schema/index checks; scheduled cleanup; read/write outage and recovery. |
| AUTH-06 | Minimal identity; unknown/expired session; two sessions on two instances revoked by disable/delete/password/role change; re-enable cannot resurrect; corrupt role never grants; commit-boundary race. |
| AUTH-07 | Valid-token logout, exact cookie deletion scope, stale identifier denied, repeat anonymous logout after bootstrap, GET has no mutation, other sessions remain valid; failed deletion/transport not reported successful. |
| AUTH-08 | Production HTTP identity/default-deny matrix plus focused real authorization-filter fixture for opposite-role rejection; no shipped future-operation matchers/test business controllers; forged identity/roles ineffective. |
| AUTH-09 | Bootstrap exposes no identity and no raw session ID; missing/invalid/stale/cross-cookie-jar token 403; valid masked header works; login/logout old tokens fail and refreshed tokens work; precedence over anonymous 401 and blocked side effects. |
| AUTH-10 | Secure/HttpOnly/Lax/host-only/path/session-lifetime assertions, cookie-only tracking, spoofed proxy headers, production/local profile combinations, relative proxy flow and no bearer/storage auth. |
| AUTH-11 | Karma/Jasmine HttpTestingController and router tests: single-flight startup, unresolved/unavailable blocks content, role landings/denial, login-only Unicode boundaries, accessible show/hide and interaction-timed invalid fields, shared button variants/states, nonduplicated notification/inline announcements, root/unknown redirects for anonymous/both roles/unavailable/expiry, desktop/mobile visual verification, contextual recovery absent on ordinary credential failure, duplicate submission, 401 clear, 403/503 retain, token-refresh failure, logout ambiguity, no automatic replay, safe return URL and stale response generation. |
| AUTH-NFR-01 | Response headers and captured auth/filter/provisioning/error logs exclude all secrets; generic errors and correlation; redacted operational outcome logs, transaction rollback and documented log/commit crash limitation. |
| AUTH-NFR-02 | Maven dependency tree matches Boot BOM; only feature-owned additive schema/modules; concise Angular names/DashboardLayout retained; no framework/runner migration. |
| AUTH-NFR-03 | Full backend/frontend regressions; real isolated PostgreSQL/JDBC; browser HTTPS-equivalent cookie/header/reload and local proxy checks with recorded evidence. |
| AUTH-NFR-04 | Discoverable manual local CUSTOMER/AGENT creation, isolated test/developer database distinction, separate password policies and operator/local/recovery documentation; unchanged historical Tasks 1–18 evidence plus fresh refinement requirement-indexed command/output evidence; explicitly mark blocked/unexecuted scenarios. |

Outage tests own their container/context, use bounded test-only datasource timeouts, and run separately from ordinary integration fixtures. Never stop developer Compose PostgreSQL or a shared healthy test database. Exercise lifecycle writes and framework pre-commit/finalization failures deliberately, not just initial connection failure; restore the isolated store and demonstrate recovery. Multi-instance tests must use distinct backend contexts/ports rather than two clients to one singleton service.

Retain foundation health, readiness/liveness, V1 checksum/schema and constraints coverage. Historical foundation specs are not rewritten, but implementation tests asserting the former anonymous blanket 403 must be updated to the new approved 401/CSRF semantics. This is the requirements' explicit replacement of the baseline, not a hidden scope change or a separate obsolete security profile. Rollback-only foundation account constraint fixtures must use canonical emails and allow the additive default version column; maintain their original constraints and no-seed assertions.

Keep Angular's `@angular/build:karma` builder with installed Jasmine/Karma, `zone.js/testing`, current `app.spec.ts` naming and `npm run test:ci` (`ng test --watch=false --browsers=ChromeHeadless`). Configure HttpClient then its testing provider in frontend tests. No Vitest/Jest switch, nonexistent lint script or deprecated animation provider. Existing CI commands remain `mvn --batch-mode clean verify`, `npm ci`, `npm run build`, `npm run test:ci`; additional focused test invocations are recorded during implementation, not represented as having run now.

Browser acceptance uses an actual browser over the existing local proxy and a temporary HTTPS-equivalent test boundary using production cookie configuration. It verifies reload restoration, header/token transitions, cookie invisibility to JavaScript and absence of browser authentication storage. TLS/browser setup is a verification fixture or documented manual procedure, not a production reverse-proxy deployment task. Lack of Docker, browser, TLS fixture or operator channel is reported as an external prerequisite; a build alone does not close these checks.

## 11. Revision boundary, documentation deliverables and review status

The developer approved the refinement direction and assessment-specific 8–128 creation policy. The refinement implementation is separate from the original Tasks 1–18 and requires fresh verification before final feature acceptance. Task-by-task entries remain historical implementation records for the prior 15–128 baseline and UX. The acceptance summary now states the current 8–128 policy, while the historical test results do not by themselves verify the revised lower bound, entry routing or presentation.

Previously approved AUTH-04.3 eligible-handler admission, AUTH-05.7 response-commit boundary and AUTH-08.2/6 test-only role scope clarifications are already reflected in requirements and remain unchanged. Strict idle metadata, current-account/version revocation, generic store failures, physical commitment limitations and operational audit crash-consistency limits are retained. PBKDF2 parameters/identifier, session/CSRF/cookie configuration, endpoints/envelope, backend role authority and modular-monolith boundaries are not reopened.

Amended requirements: AUTH-02.2/3/5 and related policy note; AUTH-03.3/4; AUTH-11.1/6/7 plus new .10/.11; AUTH-NFR-03/04. Local provisioning expectations supplement the AUTH-01 section without altering AUTH-01.7. Scope now distinguishes lightweight transient UI notifications from deferred business notifications. AUTH-09 remains verbatim; presentation is clarified in §§6.1 and 8.

After specification review and separately authorized planning/implementation, align current README, operator/local runbooks, security/frontend steering and canonical API/UI documents with the split password policy, root routes and recovery presentation. Correct stale inert-page/absent-guards and subsequent-login descriptions. Update affected diagrams only in that later authorized phase. Do not change historical Foundation specifications, applied migrations or rewrite original acceptance results.

Current refinement implementation includes split password admission, root/unknown routing, accessible login controls, shared native-button presentation and lightweight notifications. Fresh full backend/browser verification, current-document alignment and final acceptance review remain required. Password-cost capacity and development-tool advisories remain existing deferred risks, with no parameter change or framework migration.

**Review status:** Refinement implementation is underway. No unresolved architectural decision is required, but the assessment password trade-off and fresh verification obligations remain explicit. Refinement is not complete; final acceptance stays withheld.

## 12. Framework/security references

These references support design choices; implementation must use the foundation's resolved versions, not upgrade to the reference site's latest release.

- [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html): maintained encoders, algorithm identifiers and work-factor benchmarking.
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): BCrypt input limit and PBKDF2-HMAC-SHA256 iteration guidance.
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): deferred/masked tokens and authentication/logout token lifecycle.
- [Spring Session JDBC configuration](https://docs.spring.io/spring-session/reference/configuration/jdbc.html) and [3.5.7 repository source](https://github.com/spring-projects/spring-session/blob/3.5.7/spring-session-jdbc/src/main/java/org/springframework/session/jdbc/JdbcIndexedSessionRepository.java): persistence schema, save/cleanup behavior and supported customization boundary.

- [Session 3.5.7 repository filter](https://github.com/spring-projects/spring-session/blob/3.5.7/spring-session-core/src/main/java/org/springframework/session/web/http/SessionRepositoryFilter.java) and [commit wrapper](https://github.com/spring-projects/spring-session/blob/3.5.7/spring-session-core/src/main/java/org/springframework/session/web/http/OnCommittedResponseWrapper.java): framework response/session lifecycle; no public manual flush hook.
- [Security 6.5.11 context repository](https://github.com/spring-projects/spring-security/blob/6.5.11/web/src/main/java/org/springframework/security/web/context/HttpSessionSecurityContextRepository.java) and [identifier strategy](https://github.com/spring-projects/spring-security/blob/6.5.11/web/src/main/java/org/springframework/security/web/authentication/session/ChangeSessionIdAuthenticationStrategy.java): explicit context persistence and servlet identifier rotation.
