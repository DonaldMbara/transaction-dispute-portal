---
inclusion: auto
name: api-contracts
description: Designing REST endpoints, request and response DTOs, pagination, HTTP status codes, error handling or OpenAPI documentation.
---
# API conventions
- Base path `/api/v1`; use plural nouns and consistent DTO names.
- Customer: GET /accounts (list, CUSTOMER-only, owned), GET /transactions, GET /transactions/{id}, POST /disputes, GET /disputes, GET /disputes/{id} (all customer scoped).
- Agent: GET /agent/disputes?queue=unassigned|mine|all, GET /agent/disputes/{id}, POST /agent/disputes/{id}/claim, /release, /resolve.
- Customer dispute POST includes Idempotency-Key header and body `{transactionId, reason, description}`.
- Release requires reason; resolve requires ACCEPT or REJECT, internalResolutionNotes and customerResolutionMessage.
- Use `docs/design/api-contract.md` as canonical: JSON `{status,data,error_code}` envelopes, numeric codes, HTTP 200/201 success (no application 204), HTTP error statuses, application/json. The custom error envelope is not RFC 9457 Problem Details.
- Do not reuse numeric error codes. Existing 409/2008 `ACCOUNT_STATE_CONFLICT` concerns authentication accounts (`app_users`) and is not a banking-account code; resource not found remains 404/1003 and invalid requests remain 400/1004.
- List endpoints use 1-based page (default 1), size (default 20/max 100), data `{total,page,size,items}`, authorization-scoped counts, parameterized search/filters and allowlisted deterministic sorting with UUID tie-break. Validate ranges and pagination. Detail endpoints have no search.
- Emit X-Correlation-ID, no-store, Location on creation/replay and required strong ETag on agent detail/successful mutations and If-Match on claim/release/resolve. Align every response example and Angular client with the contract.
- Customer DTOs show customer-facing outcome only; never internal agent notes or private audit data.
- OpenAPI spec and example requests should reflect actual code; no undocumented endpoints or speculative fields.

- GET /api/v1/accounts returns {total,page,size,items} with fixed deterministic order (primary first, then created_at ASC, then id ASC); no sort parameters, search or filters. Items include: id, accountType, displayName, accountNumber (full well-formed number for owner; null if malformed), maskedNumber, balance (two-decimal string), currency, primary (boolean). Size default 20/max 100. Unknown parameters → 400/1004.
- GET /api/v1/transactions accepts an optional single-value `accountId` (UUID) query parameter. The later customer GET /api/v1/disputes feature must accept the same parameter and validate ownership through transaction → account → customer. In both cases it is a resource selector validated against the authenticated customer's ownership, never an identity or authorization input. A supplied UUID that does not identify an owned account → 404/1003; malformed or duplicate → 400/1004. Omitting it returns all owned records. GET /transactions/{id}, POST /disputes and all agent endpoints are unchanged; agent queues do not accept an account filter. The later customer Disputes page reuses the shared selector and defaults to the primary account (or the first account in GET /accounts order if no primary exists).
- Browser authentication uses opaque HttpOnly session cookies with same-origin /api/v1 requests; no Authorization Bearer contract. CSRF is required for unsafe requests including login/logout. Canonical contracts: GET /auth/csrf, POST /auth/login, GET /auth/me, POST /auth/logout; SESSION cookie and masked X-CSRF-TOKEN. Use numeric 1001/1002/1010/3002 with 401/403/503 as documented; no future business allowlist before its feature implementation.
