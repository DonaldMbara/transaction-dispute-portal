# API Contract — Transaction Dispute Portal

**Status:** Approved design v0.3 · **Date:** 2026-10-10 · **Base:** `/api/v1` · **Auth:** `Authorization: Bearer <JWT>`

Round-two contract decisions: custom JSON envelope, numeric error codes, 1-based pagination and PostgreSQL-backed collection queries. These are design commitments, not implemented behavior. ETag/If-Match optimistic locking, retained terminal assignment and the MVP authentication policy are approved at final design sign-off. Implementation remains separately gated. Domain rules remain in [business rules](../business-rules.md).

## 1. Responses and headers

All documented application REST operations return JSON envelopes: GET/login/agent mutations use 200; dispute creation and successful replay use 201. No application endpoint uses 204. CORS OPTIONS and operational health/metrics endpoints are outside this application envelope contract. Never send a JSON body with 204. Framework/security errors must be adapted to the same custom envelope; infrastructure-generated failures may have a different body and Angular must handle them safely.

Successful envelope: `{ "status": true, "data": <payload>, "error_code": 0 }`. Error envelope: `{ "status": false, "data": null, "error_code": <nonzero integer>, "error": { "type": <problem URI>, "title": <summary>, "detail": <safe actionable message>, "fieldErrors": <optional array> } }`. Success omits `error`; error fields never echo passwords, tokens, raw input or private notes. The envelope's boolean `status` is distinct from `data.status`, a domain enum.

Use `Content-Type: application/json` for both success and error envelopes. This is **not a strict RFC 9457 Problem Details document**; do not label it `application/problem+json`. Spring ProblemDetail may supply internal exception information, but the HTTP exception handler, authentication entry point and access-denied handler map it into this envelope. HTTP status remains authoritative. [Speakeasy response guidance](https://www.speakeasy.com/api-design/responses) supports appropriate HTTP codes, consistent bodies and useful headers; the boolean/numeric envelope is our chosen contract, not a requirement of that article.

| Header | Contract |
|---|---|
| Content-Type | application/json for application envelopes; JSON request bodies required |
| X-Correlation-ID | Every application response, including errors/replay; accept only `[A-Za-z0-9_-]{1,64}` from caller, otherwise generate a UUID; never use as identity |
| Cache-Control | no-store for authentication and customer/agent responses |
| Location | `/api/v1/disputes/{uuid}` on creation and replay; derive from stored successful payload |
| ETag | Required on agent detail and successful mutation responses only; see section 6 |
| WWW-Authenticate | Bearer challenge for protected-resource 401 |
| Retry-After | Seconds on 429 and bounded submission-coordination timeout 503 |

No `X-OpenAPI-Amount`. Same-origin reverse proxy preferred; if local separate origins are used, allowlist origins/methods/request headers including Authorization, Idempotency-Key and If-Match, and expose ETag, Location, X-Correlation-ID and Retry-After. Never use wildcard credentialed CORS.

IDs remain UUIDs; `DSP-001` is an illustrative display reference, not a change to persistence identifiers. Amounts serialize as positive decimal strings with exactly two fractional digits; direction supplies display sign. Instants serialize in UTC ISO 8601 with `Z`.

## 2. Pagination and query semantics

Every collection returns `data: { "total": <matching count>, "page": <requested 1-based page>, "size": <requested size>, "items": [...] }`. Default page 1, size 20; maximum size 100. Page/size must be integers with page >=1 and size 1..100; unsupported parameters, bad enums, unknown sort fields and invalid ranges return 400/1004. A valid page beyond the result set returns 200 with empty items and the matching total. Map to Spring Data `PageRequest.of(page - 1, size, sort)` after validation; guard offset arithmetic against overflow. Reject offsets above the supported signed 32-bit database/JPA offset range. Offset paging is deterministic for a fixed dataset, not a snapshot across concurrent writes.

Apply authorization first and AND all filters, then order and paginate; total uses the identical authorized predicate. Never count records the caller cannot read. Search is trimmed, internal whitespace runs collapsed, case-insensitive literal substring matching; empty search means no search predicate. Max normalized length 100; escape SQL LIKE `%`, `_` and escape characters, parameterize values. Do not search internal notes, agent identities or full account numbers. PostgreSQL is sufficient for assessment data; no search service is introduced.

`fromDate`/`toDate` are ISO calendar dates interpreted in Africa/Johannesburg: inclusive start-of-day, exclusive start of the day after toDate, converted to UTC. Reject inverted ranges and unrepresentable dates. Transactions filter occurredAt; disputes filter createdAt. Amount bounds are inclusive positive magnitude ZAR decimal values (0 allowed as a filter bound), max two fractional digits, within NUMERIC(19,2); reject negative/inverted bounds. `sortDirection=ASC|DESC`, default DESC. Add UUID `id` in the same direction as the unique tie-breaker. All sort fields are allowlisted and mapped to persistence fields, never concatenated SQL.

## 3. Endpoint capability matrix

The listed collection capabilities are in the round-two API/UI design scope; specs must implement/test them together. They are enhancements to queries, not changes to dispute eligibility.

| Method and path | Access | Search | Filters | sortBy (default first) | Pagination / success |
|---|---|---|---|---|---|
| POST /auth/login | Public | — | — | — | — / 200 |
| GET /auth/me | Authenticated | — | — | — | — / 200 |
| GET /transactions | CUSTOMER, owned accounts | merchant, reference | direction=ALL/IN/OUT; status=ALL/PENDING/COMPLETED/FAILED; fromDate,toDate,minAmount,maxAmount | occurredAt, amount, merchant, reference | page,size / 200 |
| GET /transactions/{id} | CUSTOMER, owned | — | — | — | — / 200 |
| POST /disputes | CUSTOMER, owned eligible transaction | — | — | — | — / 201 |
| GET /disputes | CUSTOMER, owned | dispute UUID (text), submission description, transaction merchant/reference | status=ALL/OPEN/UNDER_REVIEW/ACCEPTED/REJECTED; reason=ALL or supported reason; fromDate,toDate | createdAt, updatedAt, status | page,size / 200 |
| GET /disputes/{id} | CUSTOMER, owned | — | — | — | — / 200 |
| GET /agent/disputes | AGENT | same safe fields as customer dispute search | queue=unassigned/mine/all; same status,reason,date filters | createdAt, updatedAt, status | page,size / 200 |
| GET /agent/disputes/{id} | AGENT | — | — | — | — / 200 |
| POST /agent/disputes/{id}/claim | AGENT, OPEN/unassigned | — | — | — | — / 200 |
| POST /agent/disputes/{id}/release | Assigned AGENT, UNDER_REVIEW | — | — | — | — / 200 |
| POST /agent/disputes/{id}/resolve | Assigned AGENT, UNDER_REVIEW | — | — | — | — / 200 |

Queue defaults to unassigned: OPEN with null assignee. Mine means assigned to caller, including retained terminal assignments. All means all cases permitted to AGENT in this MVP. Additional filters intersect queue predicates; contradictory filters yield empty results. Transaction description search is omitted because bank_transactions has no description column. No GET detail search. No account selector/GET accounts in MVP: transactions aggregate all owned accounts with masked account context; a future account filter requires a separate specification.

Supported reasons: UNRECOGNIZED_TRANSACTION, DUPLICATE_CHARGE, INCORRECT_AMOUNT, GOODS_OR_SERVICES_NOT_RECEIVED, OTHER.

## 4. Authentication and DTOs

POST /auth/login request `{ "email": "customer@example.test", "password": "<password>" }`; success data `{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900, "user": { "id": "<uuid>", "email": "customer@example.test", "role": "CUSTOMER" } }`. Missing user, bad password and disabled user share generic 401/1001. GET /auth/me data is `{id,email,role}`. No hashes.

Approved policy: RS256 JWT with 15-minute (900-second) expiry, no refresh token, no server session. Each protected request validates signature/algorithm, issuer, audience, expiry and loads current enabled-user/role state from PostgreSQL; disabled users receive generic 401. Current role must match the claimed role; mismatch invalidates authentication. Keys and accepted verification keys must be consistent across replicas and support a controlled rollover. No per-instance random startup key. Angular stores token in memory; reload, expiry and sign-out require login. It may retain only a validated role-appropriate return route, never credentials or tokens, and clears sensitive cached data on sign-out.

Transaction list/detail data: `{id,reference,merchant,amount,currency,direction,status,occurredAt,account:{id,maskedNumber},dispute:{id,eligible,ineligibilityReason}}`. `dispute.id` nullable; eligibility false for non-completed/already-disputed, with stable reason NOT_COMPLETED or ALREADY_DISPUTED. Both completed IN and OUT remain eligible under current business rules.

Customer dispute list items: `{id,transaction:{id,merchant,reference,amount,currency},reason,status,createdAt,updatedAt,resolvedAt}`; resolvedAt nullable. Detail adds description, `decision` (null until terminal, otherwise `{outcome,message}`) and customer-safe `timeline:[{type,message,occurredAt}]`. Timeline follows lifecycle order: SUBMITTED; IN_REVIEW on each claim; RETURNED_TO_QUEUE with generic message on release; RESOLVED on resolution. Never return actor IDs, assignee, internal notes, release reasons or raw events.

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
| 1001 | UNAUTHENTICATED | 401 | Clear session; login; generic credentials message for login |
| 1002 | FORBIDDEN | 403 | Access denied |
| 1003 | RESOURCE_NOT_FOUND | 404 | Not found; no ownership/existence disclosure |
| 1004 | VALIDATION_FAILED | 400 | Inline field/query/header errors |
| 1005 | RATE_LIMITED | 429 | Honor Retry-After |
| 1006 | REQUEST_TOO_LARGE | 413 | Reduce request |
| 1007 | UNSUPPORTED_MEDIA_TYPE | 415 | Client content-type correction |
| 1008 | METHOD_NOT_ALLOWED | 405 | Client correction; retain Allow header |
| 1009 | NOT_ACCEPTABLE | 406 | Request application/json |
| 2002 | TRANSACTION_NOT_ELIGIBLE | 422 | Explain pending/failed transaction |
| 2003 | DISPUTE_ALREADY_EXISTS | 409 | Reload owned transaction and link existing dispute |
| 2004 | IDEMPOTENCY_KEY_REUSED | 409 | A changed submission needs a new key; retry unchanged requests with original key |
| 2005 | DISPUTE_STATE_CONFLICT | 409 | Refresh; preserve unsent notes |
| 2006 | VERSION_STALE | 412 | Refresh; preserve unsent notes |
| 2007 | PRECONDITION_REQUIRED | 428 | Refetch case/version |
| 3001 | INTERNAL_ERROR | 500 | Generic message and correlation ID |
| 3002 | TEMPORARILY_UNAVAILABLE | 503 | Bounded retry; same creation key |

No full account numbers, raw rejected values or internal notes in errors. Problem type URIs use a documented relative namespace such as `/problems/dispute-already-exists`; they are identifiers, not a dependency on an external host. Optional fieldErrors entries `{field,code,message}` use allowlisted field names and generic safe messages.

## 8. Response examples

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

Agent ETag/If-Match, retained terminal assignment and the authentication/architecture decisions are approved. No blocking design decision remains before Kiro feature requirements. This sign-off does not authorize tasks or code. Completed incoming transactions remain eligible unless an explicit business-rule change is approved. API/UI collection scope, envelope and pagination are selected by the round-two request; feature specs must verify them, including security-handler errors, empty pages, all filter combinations, authorization-scoped counts, deterministic ordering and concurrent creation replay. Deployment thresholds/key provisioning and production controls remain in the architecture assessment in the review document.
