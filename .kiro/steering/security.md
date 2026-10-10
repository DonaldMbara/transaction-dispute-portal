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
- Account provisioning is admin-controlled, with BCrypt password hashing and no public registration. Provisioning mechanism and any development-only account setup remain pending authentication design; do not introduce an ADMIN application role or public provisioning API by inference.
- Use least privilege, same-origin requests without unnecessary CORS, parameterized queries/JPA, no passwords, session identifiers or CSRF secrets in logs; no authentication tokens in localStorage.
- Use application/json custom error envelopes with numeric codes, including authentication/access-denied errors; map internal ProblemDetail to the API contract. Never return stack traces or internal notes.
- Validate current enabled state and CUSTOMER/AGENT authorization on protected requests; preserve ownership/assignment checks. Shared JDBC sessions support multiple backend instances without instance-local authentication state. Role-change/session invalidation mechanics remain pending design.
- CSRF protection covers unsafe API requests including login and logout; do not retain the historical stateless API CSRF exemption. SameSite is an additional control, not a substitute for CSRF validation. CSRF bootstrap/transport and error contracts remain pending.
- No JWTs, refresh tokens, bearer-token interceptors or JavaScript access to session identifiers. Session fixation protection, logout invalidation, cookie scope, local HTTP behavior and session cleanup are design obligations; concrete settings/endpoints belong to authentication design.
- Design login/API throttling, bounded request sizes, safe correlation IDs and private metrics/health access in hardening specs; production values and operational evidence remain required.
- Synthetic data only. No real customer details, card numbers, bank credentials or proprietary code.
- No broad allow-all security exceptions outside clearly isolated local/dev profile.
