# API Contract — Transaction Dispute Portal

**Status:** Customer Accounts alignment v0.7 · **Date:** 2026-10-11 · **Base:** `/api/v1` · **Auth:** opaque HttpOnly cookie / PostgreSQL-backed server-side session

Round-two contract decisions: custom JSON envelope, numeric error codes, 1-based pagination and PostgreSQL-backed collection queries. Business operations remain design commitments; only foundation and approved authentication endpoints are implemented. ETag/If-Match optimistic locking and retained terminal assignment remain approved; the current session authentication architecture supersedes the former token policy. See [session architecture decision](session-architecture-decision.md) for implemented policy and lifecycle contracts. Implementation remains separately gated. Domain rules remain in [business rules](../business-rules.md).

## 1. Responses and headers

All documented application REST operations return JSON envelopes: documented domain GET/agent mutations use 200; dispute creation and successful replay use 201. No application endpoint uses 204. CORS OPTIONS and operational health/metrics endpoints are outside this application envelope contract. Never send a JSON body with 204. Framework/security errors must be adapted to the same custom envelope; infrastructure-generated failures may have a different body and Angular must handle them safely.

Successful envelope: `{ "status": true, "data": <payload>, "error_code": 0 }`. Error envelope: `{ "status": false, "data": null, "error_code": <nonzero integer>, "error": { "type": <problem URI>, "title": <summary>, "detail": <safe actionable message>, "fieldErrors": <optional array> } }`. Success omits `error`; error fields never echo passwords, tokens, raw input or private notes. The envelope's boolean `status` is distinct from `data.status`, a domain enum.

Use `Content-Type: application/json` for both success and error envelopes. This is **not a strict RFC 9457 Problem Details document**; do not label it `application/problem+json`. Spring ProblemDetail may supply internal exception information, but the HTTP exception handler, authentication entry point and access-denied handler map it into this envelope. HTTP status remains authoritative. [Speakeasy response guidance](https://www.speakeasy.com/api-design/responses) supports appropriate HTTP codes, consistent bodies and useful headers; the boolean/numeric envelope is our chosen contract, not a requirement of that article.

| Header | Contract |
|---|---|
| Content-Type | application/json for application envelopes; JSON request bodies required |
| X-Correlation-ID | Every application response, including errors/replay; accept only `[A-Za-z0-9_-]{1,64}` from caller, otherwise generate a UUID; never use as identity |
| Cache-Control | no-store for authentication and customer/agent responses |
| Location | `/api/v1/disputes/{uuid}` on creation and replay; derive from stored successful payload |
| ETag | Required on agent detail and successful mutation responses only; see section 6 |
| Set-Cookie | Opaque HttpOnly session identifier; Secure and SameSite=Lax in production; SESSION, host-only /api/v1, browser-session lifetime; Secure=false only with explicit local profile |
| Retry-After | Seconds on 429 and bounded submission-coordination timeout 503 |

No `X-OpenAPI-Amount`. Use same-origin /api/v1 requests through the Angular development proxy and future reverse proxy; no cross-origin browser API or CORS configuration is required. Unsafe requests, including login/logout, require CSRF validation. Bootstrap returns masked material for X-CSRF-TOKEN; missing/invalid/stale material returns 403/1010 before unsafe mutation.

IDs remain UUIDs; `DSP-001` is an illustrative display reference, not a change to persistence identifiers. Amounts serialize as positive decimal strings with exactly two fractional digits; direction supplies display sign. Instants serialize in UTC ISO 8601 with `Z`.

## 2. Pagination and query semantics

Every collection returns `data: { "total": <matching count>, "page": <requested 1-based page>, "size": <requested size>, "items": [...] }`. Default page 1, size 20; maximum size 100. Page/size must be integers with page >=1 and size 1..100; unsupported parameters, bad enums, unknown sort fields and invalid ranges return 400/1004. A valid page beyond the result set returns 200 with empty items and the matching total. Map to Spring Data `PageRequest.of(page - 1, size, sort)` after validation; guard offset arithmetic against overflow. Reject offsets above the supported signed 32-bit database/JPA offset range. Offset paging is deterministic for a fixed dataset, not a snapshot across concurrent writes.

Apply authorization first and AND all filters, then order and paginate; total uses the identical authorized predicate. Never count records the caller cannot read. Search is trimmed, internal whitespace runs collapsed, case-insensitive literal substring matching; empty search means no search predicate. Max normalized length 100; escape SQL LIKE `%`, `_` and escape characters, parameterize values. Do not search internal notes, agent identities or full account numbers. PostgreSQL is sufficient for assessment data; no search service is introduced.

`fromDate`/`toDate` are ISO calendar dates interpreted in Africa/Johannesburg: inclusive start-of-day, exclusive start of the day after toDate, converted to UTC. Reject inverted ranges and unrepresentable dates. Transactions filter occurredAt; disputes filter createdAt. Amount bounds are inclusive positive magnitude ZAR decimal values (0 allowed as a filter bound), max two fractional digits, within NUMERIC(19,2); reject negative/inverted bounds. `sortDirection=ASC|DESC`, default DESC. Add UUID `id` in the same direction as the unique tie-breaker. All sort fields are allowlisted and mapped to persistence fields, never concatenated SQL.

## 3. Endpoint capability matrix

The listed collection capabilities are in the round-two API/UI design scope; specs must implement/test them together. They are enhancements to queries, not changes to dispute eligibility.

| Method and path | Access | Search | Filters | sortBy (default first) | Pagination / success |
|---|---|---|---|---|---|
| GET /accounts | CUSTOMER, owned | — | — | primary first, created_at ASC, id ASC (fixed; no sort params) | page,size / 200 |
| GET /transactions | CUSTOMER, owned accounts | merchant, reference | accountId (optional UUID); direction=ALL/IN/OUT; status=ALL/PENDING/COMPLETED/FAILED; fromDate,toDate,minAmount,maxAmount | occurredAt, amount, merchant, reference | page,size / 200 |
| GET /transactions/{id} | CUSTOMER, owned | — | — | — | — / 200 |
| POST /disputes | CUSTOMER, owned eligible transaction | — | — | — | — / 201 |
| GET /disputes (later customer feature) | CUSTOMER, owned through transaction → account → customer | dispute UUID (text), submission description, transaction merchant/reference | accountId (optional UUID, forward constraint); status=ALL/OPEN/UNDER_REVIEW/ACCEPTED/REJECTED; reason=ALL or supported reason; fromDate,toDate | createdAt, updatedAt, status | page,size / 200 |
| GET /disputes/{id} | CUSTOMER, owned | — | — | — | — / 200 |
| GET /agent/disputes | AGENT | same safe fields as customer dispute search | queue=unassigned/mine/all; same status,reason,date filters | createdAt, updatedAt, status | page,size / 200 |
| GET /agent/disputes/{id} | AGENT | — | — | — | — / 200 |
| POST /agent/disputes/{id}/claim | AGENT, OPEN/unassigned | — | — | — | — / 200 |
| POST /agent/disputes/{id}/release | Assigned AGENT, UNDER_REVIEW | — | — | — | — / 200 |
| POST /agent/disputes/{id}/resolve | Assigned AGENT, UNDER_REVIEW | — | — | — | — / 200 |

Queue defaults to unassigned: OPEN with null assignee. Mine means assigned to caller, including retained terminal assignments. All means all cases permitted to AGENT in this MVP. Additional filters intersect queue predicates; contradictory filters yield empty results. Transaction description search is omitted because bank_transactions has no description column. No GET detail search. Agent queues have no account filter.

GET /accounts is CUSTOMER-only, ownership-scoped, `Cache-Control: no-store`, and uses the existing envelope/correlation rules. It returns the standard {total,page,size,items} collection. Items: id, accountType, displayName (derived from type at the application layer: `MAIN` → "Main Account", `SAVINGS` → "Savings Account"), `accountNumber` (full value for a well-formed owner account; null for malformed stored data), `maskedNumber` (`****` plus last four digits for a well-formed number; exactly `****` otherwise), balance (NUMERIC(19,2) serialized as a two-decimal string), currency ("ZAR"), primary (boolean). Fixed deterministic order: primary first, then created_at ASC, then id ASC. No sort parameters, search or filters; unknown parameters → 400/1004. MVP account types: `MAIN` and `SAVINGS` only. A database CHECK constraint and a Java enum enforce allowed values; adding a type is an additive migration plus an enum change. Do not use any external bank trademark as a type value, display name or fixture label.

The optional `accountId` query parameter on GET /transactions is a resource selector validated against session-derived ownership through the account → customer join. The later customer GET /disputes must accept the same optional single-value UUID and validate ownership through transaction → account → customer. In both cases, it narrows results to one owned account; it never establishes or extends the caller's identity or authorization. A supplied account ID that is absent or non-owned → 404/1003; malformed or duplicate → 400/1004. Omitting it returns all owned records at the API level. GET /transactions/{id}, POST /disputes, and all agent endpoints are unchanged and accept no accountId; agent queues get no account filter. The later customer Disputes page reuses the shared account selector and defaults to the primary account, falling back to the first item in GET /accounts order if none is primary.

Supported reasons: UNRECOGNIZED_TRANSACTION, DUPLICATE_CHARGE, INCORRECT_AMOUNT, GOODS_OR_SERVICES_NOT_RECEIVED, OTHER.

## 4. Authentication and DTOs

Authentication uses Spring Security with Spring Session JDBC and PostgreSQL. An opaque session identifier is carried by an HttpOnly cookie; production cookies are Secure and SameSite=Lax. Idle sessions expire after 30 minutes, enforced server-side. Login/logout and all unsafe API requests require CSRF protection. Backend CUSTOMER/AGENT authorization, enabled-state validation and ownership checks remain mandatory. Accounts are provisioned under admin control; no public registration.

No JWT, refresh-token or Bearer response/header contract applies. Reload may restore a still-valid session; it does not itself require reauthentication. Angular clears private cached state when the session expires or the user signs out. No localStorage authentication tokens or bearer-token interceptors.

The implemented authentication contracts are:

| Method and full path | Access / request | 200 data |
| --- | --- | --- |
| GET /api/v1/auth/csrf | Public; never an activity keepalive | {csrfToken,headerName:"X-CSRF-TOKEN"} |
| POST /api/v1/auth/login | Public with valid CSRF; JSON email/password only | {id,email,role} |
| GET /api/v1/auth/me | Current valid CUSTOMER or AGENT; no polling | {id,email,role} |
| POST /api/v1/auth/logout | Public with valid CSRF; no body | null |

Each returns the standard status/data/error_code envelope and no-store/correlation headers. Identity never exposes hash, enabled status, auth_version or session identifier. Login preserves exact non-empty, well-formed passwords up to 128 Unicode code points; it does not enforce the provisioning/replacement minimum. Email is ASCII-edge-whitespace trimmed and Locale.ROOT lowercased. Unknown fields, non-string/null/empty/malformed/oversized fields and malformed JSON return 400/1004. Bounded incorrect, unknown and disabled credentials share generic 401/1001; store failures return 503/3002. Missing/invalid/foreign/stale CSRF returns 403/1010 before credential/authorization handling. Wrong role/default denial returns 403/1002; safe/CSRF-valid protected anonymous reads return 401/1001. No HTML redirect, registration, ADMIN role or provisioning HTTP endpoint.

Login rotates the public session ID, invalidates old CSRF material, persists a credential-free context and activates account-version metadata under a current-account lock before success. Angular bootstraps fresh CSRF after transitions. Logout synchronously deletes the current session and expires SESSION with original scope; fresh anonymous-bootstrap repeated logout succeeds. Other sessions remain valid unless an account change increments auth_version. All old sessions fail after disable/delete/role/password changes; re-enable never restores them.

Strict idle activity is renewed at eligible-handler admission after CSRF/authorization, including later validation/handler errors. Only /auth/me currently opts in. Health, bootstrap, logout, unmatched/rejected/background requests never renew it. Equality at 30 minutes expires regardless of cleanup. Default denial applies to all unimplemented business paths; future features add narrow role/ownership matchers with their behavior/tests. Pre-commit store faults return generic 503; faults after commitment are safely logged and may terminate the transport, which is an unconfirmed client outcome. See [operations](../operations/authentication.md).

Account list items (GET /accounts): `{id, accountType, displayName, accountNumber, maskedNumber, balance, currency, primary}`. `balance` is a synthetic read-only display value set by the fixture command only; it is not derived from transactions and has no settlement meaning. A stored number is well-formed iff it matches `^[0-9]{10}$` (exactly ten ASCII digits; leading zeroes allowed), stored as text with no trimming or normalization. `maskedNumber` is `****` plus the last four digits for well-formed values and exactly `****` otherwise. This is the only customer endpoint returning the full number, solely to its owner; malformed `accountNumber` is null, and the raw malformed value is never returned. No other endpoint, error body, log, audit entry, URL or search predicate may contain a full account number.

Transaction list/detail data: `{id,reference,merchant,amount,currency,direction,status,occurredAt,account:{id,maskedNumber},dispute:{id,eligible,ineligibilityReason}}`. In Customer Transactions, `dispute.id` is null and eligibility is status-based only: pending/failed returns `NOT_COMPLETED`; completed IN and OUT remain eligible under current business rules. The later dispute-submission feature adds existing-dispute lookup and may return `ALREADY_DISPUTED`; that staged clarification is recorded in the transaction requirements.

Customer dispute list items: `{id,transaction:{id,merchant,reference,amount,currency,account:{id,maskedNumber}},reason,status,createdAt,updatedAt,resolvedAt}`; resolvedAt nullable. Detail adds description, `decision` (null until terminal, otherwise `{outcome,message}`) and customer-safe `timeline:[{type,message,occurredAt}]`. Timeline follows lifecycle order: SUBMITTED; IN_REVIEW on each claim; RETURNED_TO_QUEUE with generic message on release; RESOLVED on resolution. Never return actor IDs, assignee, internal notes, release reasons, raw events or full account numbers; any account context is masked only.

Agent list items add `assignedAgent:{id,email}|null` and version. Agent detail adds description, assignedAt, internalResolutionNotes, customerResolutionMessage, resolvedAt (nullable until applicable) and ordered permitted `events:[{id,disputeVersion,eventType,actorId,fromStatus,toStatus,previousAgentId,newAgentId,internalNotes,customerMessage,createdAt}]`. fromStatus, assignment IDs and notes/messages may be null as appropriate. Agent mutations return the refreshed agent detail DTO, not a customer DTO. No customer-private account number or credentials.

## 5. Creation, validation and idempotency

POST /disputes request `{transactionId,reason,description}`; UUID and enum required; trimmed description 1..2000 characters. Initial data `{id,transactionId,reason,description,status:"OPEN",createdAt}`. Release body `{reason}` with trimmed reason 1..2000. Resolve body `{decision:"ACCEPT"|"REJECT",internalResolutionNotes,customerResolutionMessage}`; trimmed internal notes 1..4000 and customer message 1..2000. Reject unknown JSON body fields to detect contract mistakes. Header Idempotency-Key required on creation: opaque case-sensitive `[A-Za-z0-9_-]{1,128}`, UUID recommended. Missing/invalid header returns 400/1004. Trim text consistently before validation and canonical fingerprinting; preserve internal whitespace in descriptions/notes.

Scope key by authenticated user and operation. Canonical request uses fixed transactionId/reason/normalized-description fields, UTF-8 deterministic serialization and SHA-256. Same key/same request replays original 201 envelope and Location, with a new request correlation ID. Same key/different request returns 409/2004. Different key/same transaction returns 409/2003 even after terminal resolution. Store only successful completed results atomically with dispute and CREATED event. No process-local coordination. See the database design for race recovery and bounded retries; timeout uses 503/3002 with Retry-After, preserving the same key in the client.

## 6. Approved agent concurrency and terminal assignment

GET `/agent/disputes/{id}` returns a quoted strong ETag `"dispute-<uuid>-v<version>"` (for example `"dispute-5f9868e0-1272-47ee-b0d2-ef33e4e1d331-v3"`), with no `W/` prefix. Version is a nonnegative decimal BIGINT, initialized to 0 and incremented once for each committed claim/release/resolve. Clients retain and send the exact tag; it is opaque to Angular. GET and successful mutation responses return the same agent-detail representation for the same committed version, inside the standard success envelope.

The tag identifies the complete stable agent-detail JSON representation: dispute fields, ordered versioned audit events and immutable referenced transaction/identity fields. Serialize deterministically (fixed fields/null handling/date/decimal formatting and event ordering); no per-request timestamps, random fields or correlation ID in the body. X-Correlation-ID varies only in headers. Transaction and user identity display fields are read-only in the MVP. Current enabled/role checks gate access and do not appear as varying detail fields. Any future change to a referenced display field, representation schema or content encoding must change the validator scheme; do not reuse a strong tag across differing representation bytes. For MVP these responses use one JSON representation without compression/content negotiation variants. Cache-Control is no-store; conditional GET/304 is outside MVP. [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-if-match) defines strong comparison for If-Match.

The approved API defines `/claim`, `/release` and `/resolve` as commands against this parent dispute aggregate: each uses the strong tag obtained from the parent detail GET as its required version precondition. This is an explicit application convention across the detail/action URIs, not an ETag on the request body or a generic cache policy. No expectedVersion body field is accepted. On success return committed refreshed agent detail and its new ETag.

Require exactly one quoted tag in the documented format (optional surrounding HTTP whitespace only; no weak tags, wildcard, lists, negative/overflowing versions or duplicate headers): missing → 428/2007; malformed → 400/1004. A syntactically valid tag for a different dispute is a nonmatching precondition → 412/2006. After authentication/role checks and resource lookup, compare tag to current version: mismatch → 412/2006. With matching version, invalid state/assignment → 409/2005. The write must also be guarded by expected version/state/assignee in the same transaction; a race lost after validation → 412/2006. This makes two simultaneous claims with the same version yield one success and one 412. A fresh-tag claim on an already assigned dispute yields 409. Append audit only for the successful write; commit before 200/new ETag. No blind retry of agent mutations. Lost responses require refetch and inspecting state/audit before further action.

Release clears both assigned_agent_id and assigned_at; resolution retains the resolving agent and assigned_at for accountability and historical mine-queue views (approved). Customer responses, errors and timeline must never expose assignee/actor IDs or identity. Terminal disputes are immutable, including notes and assignment. Both messages mandatory; ACCEPTED/REJECTED terminal; no refund or reopening. No caller-supplied agent identity.

## 7. Stable numeric error catalogue

Do not reuse numeric codes. Names are documentation labels; wire `error_code` is numeric.

| Code | Name | HTTP | UI action |
|---|---|---|---|
| 0 | SUCCESS | 200/201 | Render data |
| 1001 | UNAUTHENTICATED | 401 | Clear private UI state; generic credential failure or authentication required |
| 1002 | FORBIDDEN | 403 | Access denied |
| 1003 | RESOURCE_NOT_FOUND | 404 | Not found; no ownership/existence disclosure |
| 1004 | VALIDATION_FAILED | 400 | Inline field/query/header errors |
| 1005 | RATE_LIMITED | 429 | Honor Retry-After |
| 1006 | REQUEST_TOO_LARGE | 413 | Reduce request |
| 1007 | UNSUPPORTED_MEDIA_TYPE | 415 | Client content-type correction |
| 1008 | METHOD_NOT_ALLOWED | 405 | Client correction; retain Allow header |
| 1009 | NOT_ACCEPTABLE | 406 | Request application/json |
| 1010 | CSRF_REJECTED | 403 | Explicit security-token refresh; no automatic unsafe replay |
| 2002 | TRANSACTION_NOT_ELIGIBLE | 422 | Explain pending/failed transaction |
| 2003 | DISPUTE_ALREADY_EXISTS | 409 | Reload owned transaction and link existing dispute |
| 2004 | IDEMPOTENCY_KEY_REUSED | 409 | A changed submission needs a new key; retry unchanged requests with original key |
| 2005 | DISPUTE_STATE_CONFLICT | 409 | Refresh; preserve unsent notes |
| 2006 | VERSION_STALE | 412 | Refresh; preserve unsent notes |
| 2007 | PRECONDITION_REQUIRED | 428 | Refetch case/version |
| 2008 | ACCOUNT_STATE_CONFLICT | 409 | Authentication account (`app_users`) state/version conflict; not a banking-account code |
| 3001 | INTERNAL_ERROR | 500 | Generic message and correlation ID |
| 3002 | TEMPORARILY_UNAVAILABLE | 503 | Bounded retry; same creation key |

No full account numbers, raw rejected values or internal notes in errors. Problem type URIs use a documented relative namespace such as `/problems/dispute-already-exists`; they are identifiers, not a dependency on an external host. Optional fieldErrors entries `{field,code,message}` use allowlisted field names and generic safe messages.

## 8. Response examples

Customer accounts (GET list) and account-scoped transaction request:
```http
GET /api/v1/accounts?page=1&size=20
```
```json
{"status":true,"data":{"total":1,"page":1,"size":20,"items":[{"id":"a1b2c3d4-0001-0000-0000-000000000001","accountType":"MAIN","displayName":"Main Account","accountNumber":"0123456789","maskedNumber":"****6789","balance":"12500.00","currency":"ZAR","primary":true}]},"error_code":0}
```
```http
GET /api/v1/transactions?accountId=a1b2c3d4-0001-0000-0000-000000000001&direction=OUT&page=1&size=20
```

A supplied malformed or duplicate `accountId` returns `400/1004`; a well-formed UUID for an absent or non-owned account returns `404/1003`. Omitting `accountId` returns transactions across all accounts owned by the authenticated customer. The later customer `GET /api/v1/disputes?accountId=<uuid>` uses the same error behavior and validates transaction → account → customer ownership.

201 Created with Location and a per-request X-Correlation-ID:
```json
{"status":true,"data":{"id":"5f9868e0-1272-47ee-b0d2-ef33e4e1d331","transactionId":"72824030-2eba-47f9-a4b0-654438d809d1","reason":"DUPLICATE_CHARGE","description":"I was charged twice.","status":"OPEN","createdAt":"2026-10-10T08:00:00Z"},"error_code":0}
```
409 Conflict:
```json
{"status":false,"data":null,"error_code":2003,"error":{"type":"/problems/dispute-already-exists","title":"Dispute already exists","detail":"This transaction already has a dispute. Reload its details to view the case."}}
```
400 validation failure:
```json
{"status":false,"data":null,"error_code":1004,"error":{"type":"/problems/validation-failed","title":"Invalid request","detail":"Correct the highlighted fields.","fieldErrors":[{"field":"description","code":"REQUIRED","message":"Enter a description."}]}}
```
200 paginated customer disputes (single matching item):
```json
{"status":true,"data":{"total":1,"page":1,"size":20,"items":[{"id":"5f9868e0-1272-47ee-b0d2-ef33e4e1d331","transaction":{"id":"72824030-2eba-47f9-a4b0-654438d809d1","merchant":"Example Merchant","reference":"TX-2026-0001","amount":"450.00","currency":"ZAR"},"reason":"DUPLICATE_CHARGE","status":"OPEN","createdAt":"2026-10-10T08:00:00Z","updatedAt":"2026-10-10T08:00:00Z","resolvedAt":null}]},"error_code":0}
```
200 empty authorized search results:
```json
{"status":true,"data":{"total":0,"page":1,"size":20,"items":[]},"error_code":0}
```

## 9. Sign-off and required implementation verification

Agent ETag/If-Match, retained terminal assignment and the authentication/architecture decisions are approved. The session architecture and authentication feature contracts are approved and implemented; acceptance evidence is recorded in the feature specification. This sign-off does not authorize tasks or code. Completed incoming transactions remain eligible unless an explicit business-rule change is approved. API/UI collection scope, envelope and pagination are selected by the round-two request; feature specs must verify them, including security-handler errors, empty pages, all filter combinations, authorization-scoped counts, deterministic ordering and concurrent creation replay. Authentication endpoint/payload/CSRF/provisioning details follow section 4 and the operational runbooks. Deployment thresholds and production operational controls remain hardening work.
