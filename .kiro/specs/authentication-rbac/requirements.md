# Authentication and RBAC — Requirements

**Feature:** `authentication-rbac`  
**Intended repository path:** `.kiro/specs/authentication-rbac/requirements.md`  
**Branch:** `feature/authentication-rbac`  
**Date:** 2026-10-10  
**Status:** Revision 2 implemented; fresh verification and feature review remain pending. The assessment-specific 8-character creation minimum is implemented with separate login admission rules. Original task-by-task entries remain historical evidence for the completed 15–128 baseline. The acceptance summary now states the current 8–128 policy and records that the revised lower-bound case still needs fresh verification.

## 1. Purpose and confirmed decisions

Provide application-managed authentication and backend-enforced role-based authorization for the Transaction Dispute Portal using Spring Security and opaque server-side sessions.

| Decision | Confirmed value |
| --- | --- |
| Account provisioning | Admin-controlled; no public registration |
| Session idle timeout | 30 minutes (1,800 seconds) |
| Session persistence | Spring Session JDBC backed by PostgreSQL |
| Authentication mechanism | Spring Security, server-side sessions and secure cookies |

An **admin** is a trusted operational actor authorized to provision and maintain accounts. This decision does not introduce an application `ADMIN` role, an admin dashboard or a public account-management API. Application users have exactly one supported role: `CUSTOMER` or `AGENT`.

The existing Java 21, Spring Boot, Angular 20, PostgreSQL, Flyway, modular-monolith boundaries and same-origin `/api/v1` boundary remain the baseline. This feature owns the necessary change from the foundation's stateless, API-CSRF-exempt security baseline to stateful, CSRF-protected authentication. Historical foundation requirements and applied migrations must not be rewritten.

## 2. Scope

Included:

- A documented, repeatable, admin-only operational account provisioning and maintenance procedure.
- Login, current-user identity, logout and session expiration.
- JDBC session persistence and validation across backend instances.
- Credential hashing, account-status enforcement, CSRF protection and secure session cookies.
- Customer/agent route boundaries, session-aware root navigation, polished accessible Angular login, reusable native-button presentation, lightweight transient UI notifications and automated verification.

Excluded:

- Public registration, third-party identity providers, JWTs, refresh tokens and remember-me.
- Admin UI, application `ADMIN` role and remote user-management endpoints.
- Self-service password reset/change, email verification, MFA and account invitations.
- Transactions, dispute workflows, record ownership queries, business notifications and deployment infrastructure. Lightweight transient UI notifications within authentication are included; no notification delivery infrastructure or component library is included.

Future dispute specifications must enforce record ownership and agent permissions separately. Possessing the `AGENT` role does not establish unrestricted access to customer records.

## 3. Functional requirements and acceptance criteria

### AUTH-01 — Admin-controlled account provisioning

**User story:** As a trusted admin, I want to provision customer and agent accounts so that access is explicitly authorized.

1. WHEN the documented provisioning procedure is used by an authorized operator, THE SYSTEM SHALL create an account with a stable unique identifier, a unique login email, an encoded password, an explicit enabled/disabled status and exactly one supported role.
2. THE PROCEDURE SHALL define email normalization consistently for provisioning and login; leading/trailing whitespace and case variants of an existing login email SHALL be rejected as duplicates.
3. THE SYSTEM SHALL reject unsupported roles, missing required fields and duplicate identities without partially creating an account.
4. THE PROCEDURE SHALL support disabling/re-enabling an account, changing its role and replacing its password without modifying an applied migration.
5. No public request, customer or agent session SHALL be able to provision accounts or modify another account's credentials, role or status.
6. Documentation SHALL identify the required operational privileges and how initial credentials are delivered securely. Plaintext passwords SHALL NOT appear in command arguments, logs, source control or migration files.
7. No default, demo or automatically seeded account SHALL be introduced. Tests SHALL create isolated fixtures.

The existing explicit non-web masked-console interface remains the operational mechanism; an admin portal is not required. Local setup instructions SHALL make manual creation of synthetic CUSTOMER and AGENT accounts discoverable and reproducible, including required PostgreSQL configuration. Isolated automated-test fixtures SHALL NOT be represented as accounts in the developer's local database. AUTH-01.7 remains unchanged: no demo-fixture mechanism or startup seed is authorized.

### AUTH-02 — Credential storage and validation

**User story:** As an account holder, I want my credentials protected against disclosure.

1. THE SYSTEM SHALL store only salted adaptive one-way password hashes using a maintained Spring Security password encoder; plaintext and reversible password storage SHALL be prohibited.
2. Non-web provisioning and password replacement SHALL accept 8–128 well-formed Unicode code points, inclusive, including spaces and Unicode characters. Malformed Unicode and values outside this range SHALL be rejected. Uppercase, lowercase, numeric and special-character composition rules SHALL NOT be required.
3. Password creation, replacement and verification SHALL preserve supplied input exactly: no trimming, normalization, truncation or silent transformation. Creation rules SHALL be separate from login/verification admission rules; verification SHALL NOT enforce the creation minimum. The chosen encoder SHALL support the entire accepted input range.
4. Equal passwords provisioned for two accounts SHALL produce distinct salted hashes, and each SHALL verify correctly.
5. Identity responses, errors and logs SHALL NOT expose password hashes. The existing PBKDF2-HMAC-SHA256 configuration SHALL remain unchanged: `{pbkdf2-sha256-v1}`, 600,000 iterations, random 16-byte salts and a 256-bit derived key. Existing hashes SHALL remain verifiable without migration, reinterpretation or insecure fallback.

The 8-character creation minimum is an explicitly approved assessment-specific security trade-off for password-only authentication without MFA. It is not a claim that eight characters is the preferred production banking standard. Encourage longer passphrases in provisioning guidance without composition requirements or Unicode terminology in the login UI.

### AUTH-03 — Login

**User story:** As a customer or agent, I want to sign in with my provisioned credentials.

1. GIVEN an enabled account and a valid CSRF token, WHEN correct credentials are submitted, THE SYSTEM SHALL authenticate the account and establish a JDBC-backed session.
2. Login SHALL accept email and password only; a client-supplied role or user identifier SHALL NOT determine the authenticated identity or authority.
3. Bounded but incorrect passwords, unknown emails and disabled accounts SHALL reach normal verification and receive the same generic 401 response without revealing whether an account exists or is disabled. The existing same-work-factor dummy verification protections against obvious timing shortcuts SHALL be preserved; no constant-time network claim is made.
4. Login and password verification SHALL accept non-empty, well-formed password input up to 128 Unicode code points, inclusive, without enforcing the creation minimum and without altering input. Missing, empty, non-string, malformed Unicode or oversized input SHALL receive the documented generic 400/1004 validation response without authentication or account details. Structurally valid but incorrect input SHALL proceed to the generic 401/1001 outcome in AUTH-03.3. Existing CSRF/error precedence remains unchanged.
5. A failed login SHALL NOT create an authenticated session.
6. Successful authentication SHALL rotate the pre-authentication session identifier. Replaying the previous identifier SHALL NOT restore access.
7. No HTTP Basic, generated development user, form-login HTML redirect or remember-me authentication SHALL be enabled.

### AUTH-04 — Session lifetime

**User story:** As an account holder, I want unattended sessions to expire.

1. Authenticated sessions SHALL have a 1,800-second maximum idle interval enforced by the backend.
2. GIVEN no qualifying authenticated request for at least 1,800 seconds, WHEN a protected endpoint is requested, THE SYSTEM SHALL reject the session as expired and return 401 for a CSRF-valid request or a safe read.
3. An authenticated request admitted before expiration to an explicitly eligible user-interaction handler, after required CSRF and backend authorization checks, SHALL record that request’s server session-access instant as activity at admission and restart the idle interval from that instant, even if later validation or domain processing fails. Anonymous, pre-admission rejected, unmatched, health, CSRF-bootstrap and background-polling requests SHALL NOT qualify.
4. Frontend timers, health polling and anonymous CSRF-bootstrap requests SHALL NOT keep an authenticated session alive. Automatic identity polling SHALL NOT be used as a keepalive.
5. Expiration SHALL be enforced even if the expired JDBC row has not yet been physically removed by cleanup.
6. No persistent login cookie or client-side timer SHALL extend server-side validity. Idle expiration SHALL be tested using controlled time or stored timestamps rather than a 30-minute test sleep.

### AUTH-05 — JDBC persistence and availability

**User story:** As an authenticated user, I want my session recognized by any backend instance.

1. Authentication state SHALL be persisted through Spring Session JDBC in PostgreSQL; an in-memory fallback SHALL NOT be used.
2. A session created on backend instance A SHALL authorize a request on instance B when both use the same session database and compatible application configuration, without sticky routing.
3. An unexpired session SHALL remain usable after a backend restart, subject to account validity.
4. Logout on one instance SHALL cause subsequent requests using that session on another instance to fail authentication.
5. JDBC session tables SHALL be created through versioned additive Flyway migrations. Restarting SHALL neither recreate tables destructively nor overwrite existing migrations.
6. Expired session rows SHALL be cleaned up on a documented configurable schedule; correctness SHALL NOT depend on the cleanup timing.
7. If the account or session store cannot be read/written, THE SYSTEM SHALL fail closed and avoid issuing a usable authenticated session. Detected store-dependent request failures before HTTP commitment SHALL return a generic 503; persistence required to activate login, invalidate logout or admit a protected request SHALL complete before success is emitted. Already committed HTTP responses cannot be rewritten; post-commit faults SHALL be reported safely through operational logging. The system SHALL NOT report the outage as invalid credentials or fall back to cached authorization.
8. Tests SHALL demonstrate restored operation after database recovery, without assuming sessions survive a database data loss.

### AUTH-06 — Current-user identity and account changes

**User story:** As a signed-in user, I want the UI to reflect my current authorized identity.

1. An authenticated identity operation SHALL return only the user's stable identifier, email and current supported role, plus a display name only if available and needed.
2. Anonymous, expired, invalidated or unknown sessions SHALL receive 401 on the identity operation.
3. After an admin disables or deletes an account, all its existing sessions SHALL be denied on the next protected request after the change commits, regardless of instance.
4. Re-enabling an account SHALL NOT resurrect previously revoked sessions; a fresh login SHALL be required.
5. After a committed password replacement or role change, all previous sessions SHALL require a fresh login. A stale session SHALL NOT retain or acquire privileges.
6. Requests already authorized before an administrative change commits are outside this revocation guarantee.
7. A corrupt or unsupported account role SHALL never grant access.

### AUTH-07 — Logout

**User story:** As a signed-in user, I want to end my session reliably.

1. Logout SHALL be a state-changing operation requiring a valid CSRF token; GET SHALL NOT log the user out.
2. Successful logout SHALL invalidate the server-side session and expire the session cookie using its original name, path and applicable domain.
3. Replaying the logged-out identifier SHALL fail authentication on all backend instances.
4. Repeated logout with a valid freshly bootstrapped CSRF token SHALL succeed without disclosing whether a session existed.
5. On confirmed logout, Angular SHALL clear identity and protected views and navigate to login.
6. If logout cannot be confirmed because of a transport or store failure, the UI SHALL not claim server-side invalidation; it SHALL show a retryable failure.
7. Logout affects the current session. Other sessions remain valid unless revoked by AUTH-06.

### AUTH-08 — Backend role enforcement

**User story:** As a portal owner, I want authorization enforced independently of the browser.

1. Backend authorization SHALL distinguish authenticated `CUSTOMER` and `AGENT` users using server-established authorities.
2. Customer-only authorization rules SHALL reject agents with 403; agent-only authorization rules SHALL reject customers with 403. This feature SHALL verify that role-enforcement machinery without introducing transaction/dispute operations or their production authorization matchers.
3. Anonymous requests to protected safe reads or CSRF-valid protected operations SHALL return 401 without an HTML redirect.
4. Unrecognized API paths SHALL NOT be anonymously allowlisted or implicitly grant access to either role. Authentication/authorization SHALL be enforced before any protected behavior.
5. Altered browser state, forged role fields, disabled Angular guards and manually issued HTTP requests SHALL NOT bypass backend checks.
6. Role checks SHALL be verified at the real Spring Security authorization-filter boundary using a test-only non-business fixture with the production authority mapping and session/account-validation components. Production HTTP tests SHALL cover this feature’s identity and default-deny behavior. No dispute controllers, business operations or future transaction/dispute authorization matchers SHALL be added solely as test targets; operation-specific authorization evidence belongs to the later owning features.

### AUTH-09 — CSRF protection

**User story:** As a portal user, I want cookie-authenticated actions protected against forged cross-site requests.

1. CSRF protection SHALL apply to unsafe API methods, including login and logout. The foundation's blanket API exemption SHALL be removed within this feature.
2. A documented anonymous bootstrap operation SHALL provide the token material required by Angular without exposing an authenticated identity.
3. Missing, invalid or stale CSRF tokens SHALL produce a generic 403 and SHALL prevent the requested state change, even when the caller is anonymous. This takes precedence over the general 401 rule for protected operations.
4. Angular SHALL obtain usable CSRF token material at startup and after login/logout, and after session expiration when needed.
5. Tokens SHALL be bound to the relevant browser/session context; a token from another independent browser context SHALL fail.
6. Login/logout SHALL invalidate pre-transition token material; tests SHALL verify refreshed tokens work and stale tokens fail.
7. CSRF token material SHALL NOT be placed in URLs or sent to unrelated origins. A CSRF cookie, if selected later, may be JavaScript-readable; the authentication session cookie SHALL always be HttpOnly.

### AUTH-10 — Cookies and origin boundary

**User story:** As an account holder, I want my session identifier protected in transit and from script access.

1. Production session cookies SHALL use `HttpOnly`, `Secure` and `SameSite=Lax`, omit a broad Domain attribute and use a documented path covering the required API operations.
2. The session identifier SHALL be opaque and carried only by cookies; URL-based session identifiers SHALL NOT authenticate requests.
3. Production access SHALL require HTTPS, including when TLS terminates at a trusted reverse proxy. Spoofed forwarding headers SHALL NOT downgrade cookie protection.
4. Angular SHALL use the existing relative `/api/v1` boundary and development proxy. Broad credentialed CORS SHALL NOT be enabled.
5. A reproducible local workflow SHALL be documented. Any HTTP development exception for Secure cookies SHALL be explicit, limited to local development and impossible to activate through production defaults.
6. The session identifier SHALL NOT be readable by JavaScript or stored in localStorage/sessionStorage. Angular SHALL NOT attach bearer tokens.

### AUTH-11 — Angular integration

**User story:** As a customer or agent, I want the portal to navigate according to my verified session.

1. On initial load/reload, Angular SHALL resolve identity from the backend before rendering protected content; it SHALL not infer authentication from cached role data. Opening `/` SHALL resolve anonymous users to `/login`, verified CUSTOMER users to `/customer` and verified AGENT users to `/agent`. Initialization SHALL show a safe loading state; backend unavailability SHALL show an explicit unavailable/retry state. Unknown routes SHALL use the same verified-state entry resolution without exposing protected content. Backend authorization remains authoritative.
2. Successful customer login SHALL navigate to the customer area; successful agent login SHALL navigate to the agent area.
3. Anonymous or expired sessions SHALL be redirected to login when accessing protected frontend routes.
4. A user accessing the wrong role's route SHALL see an access-denied outcome without rendering the protected component or fetching its protected data.
5. A backend authentication 401 SHALL clear frontend identity and protected content. A permission/CSRF 403 or availability 503 SHALL NOT automatically be treated as logout.
6. Normal CSRF bootstrap and refresh SHALL be transparent. Ordinary incorrect credentials SHALL NOT expose technical security-recovery controls. Recovery controls SHALL appear only when token acquisition/refresh genuinely requires user action, using plain-language messages and a contextual "Try again" action. Confirmed login/logout SHALL remain confirmed if subsequent token refresh fails; ambiguous transitions SHALL NOT be represented as successful. Credentials and unsafe mutations SHALL NEVER be replayed automatically after an ambiguous failure. Deliberate login/logout recovery behavior SHALL be documented without weakening AUTH-09.
7. Login SHALL provide a polished responsive interface consistent with the existing design system, labelled inputs, keyboard operation, clear loading/disabled/focus/failure states and duplicate-submission prevention. An accessible show/hide password control SHALL preserve input and focus without submitting the form. Invalid fields SHALL have visible borders and associated field-level messages after interaction or submission, not prematurely. Generic incorrect-credentials errors SHALL remain persistent inline. No permanent creation-policy instructions SHALL appear on login; email SHALL be preserved on failure and passwords SHALL not be exposed.
8. Any return URL SHALL be restricted to local, role-authorized routes; external or unauthorized destinations SHALL not be followed.
9. API errors SHALL not display stack traces, database information or raw backend diagnostics.
10. Appropriate transient application events SHALL use a lightweight reusable notification mechanism with accessible announcements. The same error SHALL NOT be duplicated in a toast and inline notification. Field/credential errors and required recovery actions SHALL remain persistent inline; transient notifications SHALL NOT be the sole source of actionable failure information.
11. Repeated native-button presentation SHALL be reusable through a small shared directive or similarly proportionate mechanism, preserving native semantics, keyboard behavior and consistent primary/secondary, focus, disabled and loading states. Component-specific behavior SHALL remain within its feature/layout; no UI component library is authorized.

## 4. Security and role boundaries

| Operation | Anonymous | CUSTOMER | AGENT | Trusted operational admin |
| --- | --- | --- | --- | --- |
| Approved health GET endpoints | Allowed | Allowed | Allowed | Not an application role |
| CSRF bootstrap and login | Allowed, with required CSRF on login | Same controls | Same controls | Not an application role |
| Current identity | 401 | Own identity | Own identity | Not an application role |
| Logout | CSRF-protected, idempotent | Current session | Current session | Not an application role |
| Customer area | Denied | Allowed | 403 | No implied portal access |
| Agent area | Denied | 403 | Allowed | No implied portal access |
| Account provisioning/maintenance | No public API | Denied | Denied | Authorized operational procedure only |
| Dispute/transaction operations | Deferred | Deferred | Deferred | Deferred |

Anonymous health access remains limited to the foundation's approved endpoints and hides diagnostic details. Safe requests SHALL NOT perform business state changes. API roles SHALL not authorize operational database administration.

## 5. Non-functional requirements

### AUTH-NFR-01 — Privacy and observability

Authentication success/failure, logout and operational account changes SHALL be auditable using timestamp, outcome and a non-sensitive account reference when known. Passwords/hashes, session identifiers, CSRF tokens, cookies and request bodies containing credentials SHALL NOT be logged. Responses containing identity, credentials-related outcomes or CSRF material SHALL use `Cache-Control: no-store`. Tests SHALL inspect representative responses and captured logs for disclosure.

### AUTH-NFR-02 — Compatibility and boundaries

Dependencies SHALL remain compatible with the foundation's pinned runtime and Spring Boot-managed dependency baseline. Authentication/session responsibilities SHALL stay within the existing modular-monolith boundaries. No unrelated business schema, framework migration or deployment infrastructure SHALL be introduced.

### AUTH-NFR-03 — Repeatable verification

Backend integration tests SHALL use isolated real PostgreSQL instances and JDBC sessions, not the developer's database. Frontend tests SHALL verify startup identity resolution, login/logout, role navigation, expired sessions, 403 distinctions and store/transport failures. Browser-level verification SHALL confirm cookie attributes, CSRF header/token exchange and reload behavior under HTTPS-equivalent production configuration. Refinement verification SHALL additionally cover separate creation (7/8/128/129) and login (empty/1/128/129) boundaries, malformed Unicode, exact preservation and existing-hash compatibility; accessible field/visibility behavior; safe recovery; root/unknown-route routing; reusable controls/notifications; and real desktop/mobile browser behavior. Existing foundation tests and production builds SHALL remain passing. Historical Tasks 1–18 results are evidence for their original baseline, not proof that refinement criteria pass.

### AUTH-NFR-04 — Documentation and evidence

Documentation SHALL cover privileged provisioning/maintenance, password policy, idle-time semantics, session cleanup, local cookie configuration, JDBC restart/multi-instance assumptions and troubleshooting. Test evidence SHALL map to the requirement IDs below. A passing build SHALL NOT substitute for an unexecuted security scenario. Current refinement documentation SHALL distinguish creation from verification policy, include discoverable manual local account setup, and identify implemented versus proposed behavior. Historical acceptance evidence SHALL remain intact; fresh refinement evidence is required before final feature acceptance.

## 6. Acceptance verification matrix

| Requirement IDs | Required evidence |
| --- | --- |
| AUTH-01–02 | Provision both roles; duplicate/invalid inputs rejected; privilege boundary checked; salted hashes verified; 8–128 creation/replacement boundaries and exact preservation tested; reproducible manual local setup; no seeds or credential disclosure |
| AUTH-03 | Valid customer/agent login; unknown/wrong/disabled generic 401; non-empty bounded login independent of creation minimum, malformed 400; existing hashes compatible; forged role ignored/rejected; pre-login identifier replay denied |
| AUTH-04 | 1,800-second configuration; access before expiration extends idle time; access at/after expiration denied even before row cleanup; health/bootstrap do not extend validity |
| AUTH-05 | Real JDBC persistence; same cookie accepted across two instances and restart; cross-instance logout; migration/cleanup checks; outage and recovery fail-closed checks |
| AUTH-06 | Minimal identity response; unknown/expired 401; disable/delete/password/role changes deny all old sessions on the next request; re-enable requires fresh login |
| AUTH-07 | CSRF-valid logout and repeat logout; cookie expiration; identifier replay denied; GET cannot log out; failed logout not reported as success |
| AUTH-08 | Direct anonymous/customer/agent request matrix; frontend bypass attempts; no permission for unmatched API paths |
| AUTH-09 | Missing/invalid/stale/cross-context CSRF 403; no state change; initial bootstrap and login/logout token renewal succeed |
| AUTH-10 | Production cookie/header inspection; session cookie inaccessible to JavaScript; URL session IDs rejected; origin/local/proxy configuration checks |
| AUTH-11 | Reload/login/role/return-URL/expiry navigation; protected content withheld; 403/503 distinguished from 401; accessible form/visibility/field states, reusable buttons/notifications, root/unknown-route resolution, contextual recovery and duplicate-submit behavior |
| AUTH-NFR-01–04 | Log/response privacy, no-store, dependency/boundary review, backend/frontend/build results and reproducible documentation |

## 7. Completion and approval boundary

The feature is accepted only when each applicable criterion has recorded passing evidence, or a specific exception is explicitly approved and recorded by the developer. Previously disclosed foundation limits remain tracked separately; this specification does not waive them.

This document finalizes the requested requirements draft and records the confirmed provisioning, timeout and persistence decisions. It does not select endpoint names, JSON schemas, classes, encoder work factors, session-table definitions or the provisioning interface. Those belong to a separately authorized design phase.

**Stop after requirements review. Do not generate design.md, tasks.md or application code without a subsequent instruction.**

## 8. Reference material

Reference behavior, not an instruction to upgrade the existing dependencies:

- [Spring Security — CSRF and SPA token lifecycle](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Session](https://spring.io/projects/spring-session/)

