---
inclusion: always
---
# Security invariants
- Backend authorization is authoritative; Angular guards are navigation aids only.
- Derive customer identity from the authenticated server-side session principal, never from user-supplied customerId or accountId.
- Customer reads and writes must be ownership-scoped through account -> customer; use 404 for inaccessible resource IDs to avoid enumeration.
- Agent mutations require AGENT role plus current assignment checks in the transaction.
- Validate all request bodies and constrain free text; use Bean Validation and Angular Reactive Forms.
- Use Spring Security with Spring Session JDBC and shared PostgreSQL session storage. Session identifiers are opaque and kept in HttpOnly cookies; production cookies are Secure and SameSite=Lax. Enforce 30-minute idle expiry on the server, not a frontend timer.
- Account provisioning is admin-controlled, with versioned PBKDF2-HMAC-SHA256 (`{pbkdf2-sha256-v1}`, random 16-byte salt, 600,000 iterations, 256-bit derived key), benchmarked on assessment hardware; no public registration. Provisioning uses the explicitly invoked non-web masked-console procedure in docs/operations/account-provisioning.md; no automatic account setup; do not introduce an ADMIN application role or public provisioning API by inference.
- Use least privilege, same-origin requests without unnecessary CORS, parameterized queries/JPA, no passwords, session identifiers or CSRF secrets in logs; no authentication tokens in localStorage.
- Use application/json custom error envelopes with numeric codes, including authentication/access-denied errors; map internal ProblemDetail to the API contract. Never return stack traces or internal notes.
- Validate current enabled state and CUSTOMER/AGENT authorization on protected requests; preserve ownership/assignment checks. Shared JDBC sessions support multiple backend instances without instance-local authentication state. Every accepted operational status/role/password change atomically increments auth_version; old sessions fail current-account/version validation, including after re-enabling.
- CSRF protection covers unsafe API requests including login and logout; do not retain the historical stateless API CSRF exemption. SameSite is an additional control, not a substitute for CSRF validation. GET /api/v1/auth/csrf supplies session-bound masked material for X-CSRF-TOKEN; missing/invalid tokens return 403/1010 before unsafe behavior.
- No JWTs, refresh tokens, bearer-token interceptors or JavaScript access to session identifiers. Login rotates the identifier and CSRF material; POST logout deletes the current session. SESSION is host-only /api/v1, browser lifetime; Secure=false requires explicit local profile. Cleanup is configurable, with request-time expiry independent of cleanup.
- Design login/API throttling, bounded request sizes, safe correlation IDs and private metrics/health access in hardening specs; production values and operational evidence remain required.
- Synthetic data only. No real customer details, card numbers, bank credentials or proprietary code.
- No broad allow-all security exceptions outside clearly isolated local/dev profile.

- Password encoding follows the [approved authentication design §3.2](../specs/authentication-rbac/design.md#32-accepted-versioned-password-encoder--benchmark-required): provisioning/replacement accepts 8–128 well-formed Unicode code points; login accepts non-empty well-formed input up to 128 without imposing the creation minimum. Preserve exact input, spaces and supported existing hashes; no trimming, truncation or plaintext fallback. Parameter changes use a new encoder identifier. Historical foundation specifications and V1 remain unchanged.
