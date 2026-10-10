# Current authentication architecture decision

**Status:** Approved architecture change; authentication feature design pending.  
**Date:** 2026-10-10

Spring Security with Spring Session JDBC and PostgreSQL replaces the previous RS256 JWT/stateless authentication plan. The application remains a Java 21 / Spring Boot modular monolith with Angular standalone frontend, PostgreSQL/Flyway and same-origin /api/v1 requests. No gateway, new service or identity provider is introduced.

## Approved decisions

- Shared PostgreSQL-backed server-side sessions with opaque identifiers in HttpOnly cookies; production cookies are Secure and SameSite=Lax.
- Server-enforced 30-minute idle timeout. A valid session can survive a page reload; frontend state is not the authentication authority.
- CSRF protection for unsafe API requests, including login and logout. SameSite does not replace CSRF validation; a CSRF token is distinct from the inaccessible session identifier.
- Backend CUSTOMER/AGENT authorization and existing current-enabled/role, ownership, assignment and customer-redaction rules remain authoritative.
- Admin-controlled account provisioning, no public registration. This does not imply an ADMIN business role, admin UI or provisioning API.
- No JWTs, refresh tokens, localStorage authentication tokens or bearer-token interceptors. Existing BCrypt password policy remains unchanged.

## Pending authentication design

Endpoint names and methods, JSON payloads/safe user profile, authentication operation statuses, CSRF bootstrap/repository/transport/header/error mapping and filter precedence, session cookie name/path/domain/browser lifetime/local HTTP settings, fixation/logout/concurrent-session/role-change invalidation mechanics, session-restoration UX and polling/activity semantics, JDBC session schema/serialization/migration ownership/indexes/cleanup, and the admin provisioning mechanism remain pending. No endpoint or implementation contract is finalized by this document. The 30-minute policy is an idle timeout, not an absolute token expiry; persistence cleanup timing must not determine request-time expiry enforcement.

## Historical foundation and implementation boundary

.kiro/specs/project-foundation remains an immutable historical approval/evidence snapshot for this change. Its future JWT/stateless/CSRF-exemption assumptions are superseded by this current decision, not rewritten. Existing Flyway V1 is unchanged. Current foundation application code still has the deny-all stateless security baseline and API CSRF exemption; it has no authentication behavior. Updating that baseline, adding session dependencies/configuration and schema, and exercising session/CSRF tests belong to the upcoming authentication feature, not this documentation change. Historical foundation test success is not session authentication verification.

## Documentation verification

Current steering, business/API/database/UI docs, READMEs and diagrams are aligned with this decision. Domain response envelopes, pagination, idempotency, ETag/If-Match, terminal assignment and dispute lifecycle are unchanged. Session infrastructure is shared across instances; no instance-local authentication state or JWT key distribution is assumed. Operational session-store resilience and load testing remain hardening work.

References: [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html) and [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html). These describe framework capabilities; their examples do not select this project's pending implementation contracts.

Validation in this documentation pass: all 13 PlantUML sources rendered successfully to SVG, including the 10 modified diagrams. Historical foundation specs and existing SQL/application/configuration files have no diff. No authentication design, task list, migration or application code was generated. An existing untracked authentication-rbac/requirements.md already describes server-side sessions and was left untouched.
