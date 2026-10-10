---
inclusion: always
---
# Security invariants
- Backend authorization is authoritative; Angular guards are navigation aids only.
- Derive customer identity from validated JWT, never from user-supplied customerId or accountId.
- Customer reads and writes must be ownership-scoped through account -> customer; use 404 for inaccessible resource IDs to avoid enumeration.
- Agent mutations require AGENT role plus current assignment checks in the transaction.
- Validate all request bodies and constrain free text; use Bean Validation and Angular Reactive Forms.
- JWT signature, issuer, audience and expiry validation must be explicit; signing keys supplied securely at runtime, never committed.
- BCrypt passwords for seeded demo users; development demo credentials documented as non-production only.
- Use least privilege, safe CORS origins, parameterized queries/JPA, no sensitive data or JWT in logs, no tokens in localStorage.
- Use application/json custom error envelopes with numeric codes, including authentication/access-denied errors; map internal ProblemDetail to the API contract. Never return stack traces or internal notes.
- Validate enabled/current-role policy on every protected request using the shared database; common issuer/audience and compatible keys across replicas, controlled rollover. No per-instance random signing keys.
- Design login/API throttling, bounded request sizes, safe correlation IDs and private metrics/health access in hardening specs; production values and operational evidence remain required.
- Synthetic data only. No real customer details, card numbers, bank credentials or proprietary code.
- No broad allow-all security exceptions outside clearly isolated local/dev profile.
