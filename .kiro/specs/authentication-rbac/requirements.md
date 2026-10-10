# Authentication and RBAC — Requirements

**Feature:** `authentication-rbac`  
**Intended repository path:** `.kiro/specs/authentication-rbac/requirements.md`  
**Branch:** `feature/authentication-rbac`  
**Date:** 2026-10-10  
**Status:** Final requirements draft for review. The three decisions below are developer-confirmed; the remaining acceptance criteria are the proposed feature baseline. No design, tasks or implementation is authorized by this document.

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
- Customer/agent route boundaries, Angular integration and automated verification.

Excluded:

- Public registration, third-party identity providers, JWTs, refresh tokens and remember-me.
- Admin UI, application `ADMIN` role and remote user-management endpoints.
- Self-service password reset/change, email verification, MFA and account invitations.
- Transactions, dispute workflows, record ownership queries, notifications and deployment infrastructure.

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

The operational interface and credential-delivery mechanism are design decisions; an admin portal is not required.

### AUTH-02 — Credential storage and validation

**User story:** As an account holder, I want my credentials protected against disclosure.

1. THE SYSTEM SHALL store only salted adaptive one-way password hashes using a maintained Spring Security password encoder; plaintext and reversible password storage SHALL be prohibited.
2. Provisioned/replaced passwords SHALL accept 15–128 Unicode code points, including spaces, without mandatory character-class composition rules. Values outside this range SHALL be rejected.
3. Passwords SHALL NOT be trimmed, silently truncated or altered during verification. The chosen encoder SHALL support the entire accepted input range.
4. Equal passwords provisioned for two accounts SHALL produce distinct salted hashes, and each SHALL verify correctly.
5. Identity responses, errors and logs SHALL NOT expose password hashes. Algorithm and work-factor choices SHALL be documented and verified during later design/implementation.

### AUTH-03 — Login

**User story:** As a customer or agent, I want to sign in with my provisioned credentials.

1. GIVEN an enabled account and a valid CSRF token, WHEN correct credentials are submitted, THE SYSTEM SHALL authenticate the account and establish a JDBC-backed session.
2. Login SHALL accept email and password only; a client-supplied role or user identifier SHALL NOT determine the authenticated identity or authority.
3. Wrong passwords, unknown emails and disabled accounts SHALL receive the same generic 401 response without revealing whether an account exists or is disabled.
4. Malformed login input SHALL receive a documented 400 response without authentication or account details.
5. A failed login SHALL NOT create an authenticated session.
6. Successful authentication SHALL rotate the pre-authentication session identifier. Replaying the previous identifier SHALL NOT restore access.
7. No HTTP Basic, generated development user, form-login HTML redirect or remember-me authentication SHALL be enabled.

### AUTH-04 — Session lifetime

**User story:** As an account holder, I want unattended sessions to expire.

1. Authenticated sessions SHALL have a 1,800-second maximum idle interval enforced by the backend.
2. GIVEN no qualifying authenticated request for at least 1,800 seconds, WHEN a protected endpoint is requested, THE SYSTEM SHALL reject the session as expired and return 401 for a CSRF-valid request or a safe read.
3. A successful authenticated request before expiration SHALL update the last-access time and restart the idle interval.
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
7. If the account or session store cannot be read/written, THE SYSTEM SHALL fail closed, return a generic 503 for store-dependent API requests and avoid issuing a usable authenticated session. It SHALL NOT report the outage as invalid credentials or fall back to cached authorization.
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
2. Customer-only operations SHALL reject agents with 403; agent-only operations SHALL reject customers with 403.
3. Anonymous requests to protected safe reads or CSRF-valid protected operations SHALL return 401 without an HTML redirect.
4. Unrecognized API paths SHALL NOT be anonymously allowlisted or implicitly grant access to either role. Authentication/authorization SHALL be enforced before any protected behavior.
5. Altered browser state, forged role fields, disabled Angular guards and manually issued HTTP requests SHALL NOT bypass backend checks.
6. Role checks SHALL be verified at the actual security boundary without adding dispute controllers or business operations solely as test targets.

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

1. On initial load/reload, Angular SHALL resolve identity from the backend before rendering protected content; it SHALL not infer authentication from cached role data.
2. Successful customer login SHALL navigate to the customer area; successful agent login SHALL navigate to the agent area.
3. Anonymous or expired sessions SHALL be redirected to login when accessing protected frontend routes.
4. A user accessing the wrong role's route SHALL see an access-denied outcome without rendering the protected component or fetching its protected data.
5. A backend authentication 401 SHALL clear frontend identity and protected content. A permission/CSRF 403 or availability 503 SHALL NOT automatically be treated as logout.
6. CSRF recovery SHALL not automatically replay potentially non-idempotent business requests. Login/logout retry behavior SHALL be documented.
7. Login SHALL provide labelled inputs, keyboard operation, submission state and a generic failure message. It SHALL prevent duplicate submissions while one is pending.
8. Any return URL SHALL be restricted to local, role-authorized routes; external or unauthorized destinations SHALL not be followed.
9. API errors SHALL not display stack traces, database information or raw backend diagnostics.

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

Backend integration tests SHALL use isolated real PostgreSQL instances and JDBC sessions, not the developer's database. Frontend tests SHALL verify startup identity resolution, login/logout, role navigation, expired sessions, 403 distinctions and store/transport failures. Browser-level verification SHALL confirm cookie attributes, CSRF header/token exchange and reload behavior under HTTPS-equivalent production configuration. Existing foundation tests and production builds SHALL remain passing.

### AUTH-NFR-04 — Documentation and evidence

Documentation SHALL cover privileged provisioning/maintenance, password policy, idle-time semantics, session cleanup, local cookie configuration, JDBC restart/multi-instance assumptions and troubleshooting. Test evidence SHALL map to the requirement IDs below. A passing build SHALL NOT substitute for an unexecuted security scenario.

## 6. Acceptance verification matrix

| Requirement IDs | Required evidence |
| --- | --- |
| AUTH-01–02 | Provision both roles; duplicate/invalid inputs rejected; privilege boundary checked; salted hashes verified; password range/boundaries tested; no seeds or credential disclosure |
| AUTH-03 | Valid customer/agent login; unknown/wrong/disabled generic 401; malformed 400; forged role ignored/rejected; pre-login identifier replay denied |
| AUTH-04 | 1,800-second configuration; access before expiration extends idle time; access at/after expiration denied even before row cleanup; health/bootstrap do not extend validity |
| AUTH-05 | Real JDBC persistence; same cookie accepted across two instances and restart; cross-instance logout; migration/cleanup checks; outage and recovery fail-closed checks |
| AUTH-06 | Minimal identity response; unknown/expired 401; disable/delete/password/role changes deny all old sessions on the next request; re-enable requires fresh login |
| AUTH-07 | CSRF-valid logout and repeat logout; cookie expiration; identifier replay denied; GET cannot log out; failed logout not reported as success |
| AUTH-08 | Direct anonymous/customer/agent request matrix; frontend bypass attempts; no permission for unmatched API paths |
| AUTH-09 | Missing/invalid/stale/cross-context CSRF 403; no state change; initial bootstrap and login/logout token renewal succeed |
| AUTH-10 | Production cookie/header inspection; session cookie inaccessible to JavaScript; URL session IDs rejected; origin/local/proxy configuration checks |
| AUTH-11 | Reload/login/role/return-URL/expiry navigation; protected content withheld; 403/503 distinguished from 401; accessible form and duplicate-submit behavior |
| AUTH-NFR-01–04 | Log/response privacy, no-store, dependency/boundary review, backend/frontend/build results and reproducible documentation |

## 7. Completion and approval boundary

The feature is accepted only when each applicable criterion has recorded passing evidence, or a specific exception is explicitly approved and recorded by the developer. Previously disclosed foundation limits remain tracked separately; this specification does not waive them.

This document finalizes the requested requirements draft and records the confirmed provisioning, timeout and persistence decisions. It does not select endpoint names, JSON schemas, classes, encoder work factors, session-table definitions or the provisioning interface. Those belong to a separately authorized design phase.

**Stop after requirements review. Do not generate design.md, tasks.md or application code without a subsequent instruction.**

## 8. Reference material

Reference behavior, not an instruction to upgrade the existing dependencies:

- [Spring Security — CSRF and SPA token lifecycle](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Session](https://spring.io/projects/spring-session/)

