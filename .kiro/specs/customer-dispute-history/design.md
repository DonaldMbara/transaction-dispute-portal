# Customer Dispute History — Design

**Status:** Draft — awaiting developer approval
**Requirements:** [requirements.md](requirements.md)
**Approved baselines:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

---

## 1. Scope and design decisions

This feature adds two read-only customer dispute endpoints and two Angular screens to the existing modular monolith. It reads the `disputes` and `dispute_events` tables owned by the dispute-submission feature without altering their schema. All backend authorization, ownership checks and DTO redaction decisions follow the existing session and module-boundary rules.

| Decision | Design |
|---|---|
| Schema ownership | `dispute-submission` owns `disputes` and `dispute_events`. This feature adds no Flyway migration. |
| Read boundary | The `dispute` module adds a `DisputeQueryService` and customer-facing read repository that join through `dispute → bank_transaction → account → customer_id`. No other module's entity or repository is imported. |
| Timeline projection | `dispute_events` rows are ordered by `dispute_version ASC, created_at ASC`, then mapped to safe `CustomerTimelineEntry` objects. The backend mapper determines `type` and a fixed safe `message`; the frontend derives the human-readable milestone label from `type` and ordinal position. Internal notes, agent IDs and release reasons are never read or copied. |
| Account context | Dispute list and detail DTOs include `account: {id, displayName, maskedNumber}`. `displayName` is derived from `account_type` by the adapter (`MAIN` → "Main Account", `SAVINGS` → "Savings Account"), matching the accounts feature mapping. The full account number is never read or returned. |
| `ACTIVE`/`RESOLVED` status groups | The `status` query parameter accepts two group aliases in addition to the four individual values. `ACTIVE` maps to SQL `d.status IN ('OPEN','UNDER_REVIEW')`; `RESOLVED` maps to `d.status IN ('ACCEPTED','REJECTED')`. The response shape is unchanged; no per-group counts are returned. |
| `accounts=all` view | An Angular-only URL marker (`?accounts=all`) signals "show all owned accounts". When present, no `accountId` is sent to the API. On first entry with no account params, the component selects all accounts and rewrites the URL with `?accounts=all` (history.replace), matching Figma. The API itself never receives `accounts=all`; it is stripped by the component before the HTTP call. |
| Display reference | Frontend derives `DSP-<first-8-uppercase-hex>` from the UUID string. No backend column is added. |
| `accountId` selector | Uses exactly the same ownership join and error semantics as `GET /api/v1/transactions`: non-owned → `404/1003`, malformed → `400/1004`. |
| Idle activity | Both GET handlers are marked `@AuthenticatedActivity`. Angular makes no polling, prefetch or auto-retry calls. |
| No new dependency | No new library, cache, search service, notification bus or gateway is introduced. |

---

## 2. Module boundaries and request flow

```text
Angular Disputes page / Dispute detail
  -> GET /api/v1/disputes[?accountId=&status=&...]     (no accounts=all parameter sent)
  -> GET /api/v1/disputes/{id}
  -> Spring Security / session validation
  -> CUSTOMER route authorization
  -> @AuthenticatedActivity admission
  -> CustomerDisputeController
  -> DisputeQueryParser + DisputeQueryService
  -> CustomerDisputeReadRepository (ownership-scoped PostgreSQL queries)
  -> CustomerDisputeListItemDto / CustomerDisputeDetailDto + ApiEnvelope
```

The `dispute` module already exists (dispute-submission). This feature adds read-path classes alongside the existing submission classes:

```text
dispute/
  controller/CustomerDisputeController          (new — read endpoints only)
  model/dto/CustomerDisputeListItemDto          (new)
         CustomerDisputeDetailDto               (new)
         CustomerTimelineEntry                  (new)
         CustomerDecision                       (new)
         CustomerTransactionSummary             (new — nested in dispute DTOs)
         CustomerAccountSummary                 (new — {id, displayName, maskedNumber})
         DisputeQuery                           (new — parsed query object)
  repository/CustomerDisputeReadRepository      (interface, new)
             JdbcCustomerDisputeReadRepository  (new — ownership-scoped SQL)
  service/DisputeQueryParser                    (new)
          DisputeQueryService                   (new — read-only, uses repository + timeline mapper)
  mapper/CustomerTimelineMapper                 (new — event rows → CustomerTimelineEntry list)
```

The existing `DisputeService` (submission) and `AgentDisputeService` (future) are not touched. `CustomerDisputeController` is in the `dispute.controller` package alongside any future agent controller. No cross-module entity imports are introduced; the repository uses plain SQL projections.

---

## 3. Persistence — read model

### 3.1 No schema changes

This feature adds no Flyway migration. It reads from the tables already created by the dispute-submission and transaction features:

```sql
disputes       (id, transaction_id, customer_id, reason, description, status,
                customer_resolution_message, resolved_at, version, created_at, updated_at)
  -- columns NOT read by customer path: assigned_agent_id, assigned_at,
  --   internal_resolution_notes

dispute_events (id, dispute_id, dispute_version, event_type, customer_message, created_at)
  -- columns NOT read by customer path: actor_id, from_status, to_status,
  --   previous_agent_id, new_agent_id, internal_notes

bank_transactions (id, account_id, reference, merchant, amount, direction,
                   currency, status, occurred_at)

accounts       (id, customer_id, account_type, account_number, balance,
                currency, is_primary, created_at)
  -- account_number is passed to AccountNumberMask only; never assigned to a DTO field
```

The ownership join for every query: `disputes.transaction_id → bank_transactions.id → bank_transactions.account_id → accounts.id → accounts.customer_id = :authenticatedCustomerId`.

### 3.2 List query

```sql
SELECT
    d.id, d.reason, d.status, d.created_at, d.updated_at, d.resolved_at,
    t.id              AS transaction_id,
    t.merchant        AS transaction_merchant,
    t.reference       AS transaction_reference,
    t.amount          AS transaction_amount,
    t.currency        AS transaction_currency,
    t.direction       AS transaction_direction,
    a.id              AS account_id,
    a.account_type    AS account_type,
    a.account_number  AS account_number
FROM disputes d
JOIN bank_transactions t ON t.id = d.transaction_id
JOIN accounts a          ON a.id = t.account_id
WHERE a.customer_id = :authenticatedCustomerId
  [AND a.id = :accountId]                      -- when accountId supplied
  [AND d.status = :status]                     -- individual value
  [OR  d.status IN ('OPEN','UNDER_REVIEW')]    -- ACTIVE group
  [OR  d.status IN ('ACCEPTED','REJECTED')]    -- RESOLVED group
  [AND d.reason = :reason]
  [AND d.created_at >= :fromUtc]
  [AND d.created_at < :toExclusiveUtc]
  [AND (d.id::text ILIKE :refPattern           -- reference search (starts-with, no leading %)
        OR d.description ILIKE :subPattern
        OR t.merchant ILIKE :subPattern
        OR t.reference ILIKE :subPattern)]
ORDER BY <allowlisted field> <direction>, d.id <direction>
LIMIT :size OFFSET :offset
```

**Status predicate note:** `DisputeQueryParser` translates the `status` parameter into one of three predicate forms applied with the correct `AND` to the rest of the WHERE clause:
- `ACTIVE` → `d.status IN ('OPEN','UNDER_REVIEW')`
- `RESOLVED` → `d.status IN ('ACCEPTED','REJECTED')`
- individual value → `d.status = :status`
- `ALL` or absent → no status predicate

The SQL comment above shows the three mutually exclusive forms; only one applies per request.

**Reference search:** `DisputeQueryParser` strips any leading `DSP-` prefix (case-insensitive) from the raw search input, then escapes the remainder for ILIKE and binds it as `:refPattern = escapedPrefix || '%'` (starts-with match against `d.id::text`). The other three fields use `:subPattern = '%' || escapedInput || '%'` (substring match). When the search input after stripping the prefix is empty, no search predicate is applied.

A matching COUNT query uses the same predicates without ORDER BY / LIMIT / OFFSET and returns `total`. Both queries run inside a single read-only Spring transaction. Ownership predicate is never omitted.

`account_number` is passed to `AccountNumberMask.mask(rawNumber)` in the adapter to produce `maskedNumber`. `account_type` is mapped to `displayName` in the adapter (`MAIN` → "Main Account", `SAVINGS` → "Savings Account"). Neither raw value is assigned to a DTO field.

### 3.3 Detail query

```sql
SELECT
    d.id, d.reason, d.description, d.status,
    d.customer_resolution_message, d.resolved_at,
    d.version, d.created_at, d.updated_at,
    t.id AS transaction_id, t.merchant, t.reference,
    t.amount, t.currency, t.direction,
    a.id AS account_id, a.account_type, a.account_number
FROM disputes d
JOIN bank_transactions t ON t.id = d.transaction_id
JOIN accounts a          ON a.id = t.account_id
WHERE d.id = :disputeId
  AND a.customer_id = :authenticatedCustomerId
```

Returns exactly one row or triggers `ResourceNotFoundException`. The columns `assigned_agent_id`, `assigned_at` and `internal_resolution_notes` are not in the SELECT list and are never read by this query.

### 3.4 Timeline query

```sql
SELECT dispute_version, event_type, customer_message, created_at
FROM dispute_events
WHERE dispute_id = :disputeId
ORDER BY dispute_version ASC, created_at ASC
```

The columns `internal_notes`, `actor_id`, `previous_agent_id` and `new_agent_id` are not in the SELECT list and are never read.

---

## 4. API design

### 4.1 Authorization

Add two narrow GET matchers before the existing `anyRequest().denyAll()` rule:

```text
GET /api/v1/disputes            -> hasRole('CUSTOMER')  @AuthenticatedActivity
GET /api/v1/disputes/{id}       -> hasRole('CUSTOMER')  @AuthenticatedActivity
```

These do not conflict with future agent matchers (`/api/v1/agent/disputes/**`).

### 4.2 List contract

`GET /api/v1/disputes` — accepted query parameters:

| Parameter | Values / default | Notes |
|---|---|---|
| `page` | integer ≥ 1, default `1` | 1-based |
| `size` | integer 1..100, default `20` | |
| `accountId` | optional UUID | ownership through dispute→tx→account→customer; omit for all owned |
| `status` | `ALL` (default), `ACTIVE`, `RESOLVED`, `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED` | `ACTIVE` = OPEN+UNDER_REVIEW; `RESOLVED` = ACCEPTED+REJECTED |
| `reason` | `ALL` (default), any supported reason | |
| `fromDate`, `toDate` | ISO local date, Africa/Johannesburg | inclusive `createdAt` range |
| `search` | normalized ≤ 100 chars | reference (with optional DSP- prefix), description, merchant, tx reference |
| `sortBy` | `createdAt` (default), `updatedAt`, `status` | allowlisted |
| `sortDirection` | `DESC` (default), `ASC` | applied to `sortBy` and tie-breaker `id` |

Unknown parameters → `400/1004`. Duplicate parameter → `400/1004`. Non-UUID `accountId` → `400/1004`. Non-owned UUID → `404/1003`. `GET /api/v1/disputes/{id}` rejects `accountId` as unknown.

The `accounts=all` marker is never sent to the API; it is an Angular URL convention stripped by the component before the HTTP call.

Example list response:
```json
{
  "status": true,
  "data": {
    "total": 1, "page": 1, "size": 20,
    "items": [{
      "id": "5f9868e0-1272-47ee-b0d2-ef33e4e1d331",
      "transaction": {
        "id": "72824030-2eba-47f9-a4b0-654438d809d1",
        "merchant": "Woolworths Food",
        "reference": "TX-2026-0001",
        "amount": "417.38",
        "currency": "ZAR",
        "direction": "OUT",
        "account": { "id": "a1b2c3d4-...", "displayName": "Main Account", "maskedNumber": "****1234" }
      },
      "reason": "DUPLICATE_CHARGE",
      "status": "ACCEPTED",
      "createdAt": "2026-10-06T07:15:00Z",
      "updatedAt": "2026-10-09T14:00:00Z",
      "resolvedAt": "2026-10-09T14:00:00Z"
    }]
  },
  "error_code": 0
}
```

### 4.3 Detail contract

`GET /api/v1/disputes/{id}` — adds `description`, `decision` and `timeline` to the list item shape:

```json
{
  "status": true,
  "data": {
    "id": "5f9868e0-1272-47ee-b0d2-ef33e4e1d331",
    "transaction": {
      "id": "72824030-...", "merchant": "Woolworths Food",
      "reference": "TX-2026-0001", "amount": "417.38",
      "currency": "ZAR", "direction": "OUT",
      "account": { "id": "a1b2c3d4-...", "displayName": "Main Account", "maskedNumber": "****1467" }
    },
    "reason": "DUPLICATE_CHARGE",
    "status": "ACCEPTED",
    "createdAt": "2026-10-06T07:15:00Z",
    "updatedAt": "2026-10-09T14:00:00Z",
    "resolvedAt": "2026-10-09T14:00:00Z",
    "description": "I was charged twice for the same purchase at Woolworths Food.",
    "decision": {
      "outcome": "ACCEPTED",
      "message": "A duplicate charge was confirmed. This case is now resolved."
    },
    "timeline": [
      { "type": "SUBMITTED",          "message": "We received your dispute.",                    "occurredAt": "2026-10-06T07:15:00Z" },
      { "type": "IN_REVIEW",          "message": "Your transaction was being reviewed.",          "occurredAt": "2026-10-07T08:00:00Z" },
      { "type": "RETURNED_TO_QUEUE",  "message": "Your dispute was waiting for review again.",    "occurredAt": "2026-10-07T12:30:00Z" },
      { "type": "IN_REVIEW",          "message": "Review of your dispute restarted.",             "occurredAt": "2026-10-08T07:00:00Z" },
      { "type": "RESOLVED",           "message": "A duplicate charge was confirmed.",             "occurredAt": "2026-10-09T14:00:00Z" }
    ]
  },
  "error_code": 0
}
```

The `RESOLVED` milestone `message` is the `customer_message` stored on the resolution event, not the internal notes. The frontend derives "Review began" / "Review resumed" labels independently from the position of each `IN_REVIEW` entry in the array (see §6.3).

### 4.4 Timeline milestone mapping (backend responsibility)

The backend mapper (`CustomerTimelineMapper`) is responsible for `type` and `message` only. The human-readable display label shown to the customer is entirely the frontend's responsibility.

| Raw `event_type` | Customer `type` | Backend `message` |
|---|---|---|
| `CREATED` | `SUBMITTED` | "We received your dispute." |
| `CLAIMED` (1st) | `IN_REVIEW` | "Your transaction was being reviewed." |
| `CLAIMED` (2nd+) | `IN_REVIEW` | "Review of your dispute restarted." |
| `RELEASED` | `RETURNED_TO_QUEUE` | "Your dispute was waiting for review again." (no release reason ever included) |
| `RESOLVED_ACCEPTED` | `RESOLVED` | `customer_message` from the event row |
| `RESOLVED_REJECTED` | `RESOLVED` | `customer_message` from the event row |

The frontend maps `type` → display label: `SUBMITTED` → "Dispute submitted"; `IN_REVIEW` (1st occurrence in array) → "Review began"; `IN_REVIEW` (2nd+ occurrence) → "Review resumed"; `RETURNED_TO_QUEUE` → "Returned to the queue"; `RESOLVED` + `decision.outcome === 'ACCEPTED'` → "Dispute accepted"; `RESOLVED` + `decision.outcome === 'REJECTED'` → "Dispute rejected".

### 4.5 Error and availability behavior

| Situation | HTTP / code |
|---|---|
| Missing/revoked/expired session | `401 / 1001` |
| Authenticated wrong role (AGENT) | `403 / 1002` |
| Absent or non-owned detail/accountId | `404 / 1003` |
| Invalid path/query input (incl. unknown status value) | `400 / 1004` |
| Store/database failure | `503 / 3002` |

---

## 5. Backend components

```text
dispute/
  controller/
    CustomerDisputeController       GET /api/v1/disputes, GET /api/v1/disputes/{id}
  model/dto/
    CustomerAccountSummary          {id, displayName, maskedNumber}
    CustomerDisputeListItemDto      {id, transaction, reason, status, createdAt, updatedAt, resolvedAt}
    CustomerDisputeDetailDto        extends list item + {description, decision, timeline}
    CustomerDecision                {outcome, message}
    CustomerTimelineEntry           {type, message, occurredAt}
    CustomerTransactionSummary      {id, merchant, reference, amount, currency, direction, account}
    DisputeQuery                    immutable parsed query object (statusPredicate replaces raw status)
  repository/
    CustomerDisputeReadRepository   interface
    JdbcCustomerDisputeReadRepository  native SQL, ownership-scoped; derives displayName and maskedNumber
  service/
    DisputeQueryParser              validates raw params → DisputeQuery; translates ACTIVE/RESOLVED
    DisputeQueryService             @Transactional(readOnly=true); calls repository + timeline mapper
  mapper/
    CustomerTimelineMapper          List<EventRow> → List<CustomerTimelineEntry>
```

`DisputeQuery` carries a `StatusPredicate` sealed type (or enum + optional list) rather than a raw status string, so the repository only has to apply the appropriate SQL fragment. `DisputeQueryParser` is responsible for translating the raw `status` parameter value to the correct predicate.

`CustomerDisputeController` is thin: gets `SessionPrincipal`, delegates to `DisputeQueryParser` + `DisputeQueryService`, returns `ApiEnvelope.success(...)`. No business logic. `DisputeQueryService` is the transaction boundary; it does not call mappers directly — the repository adapter returns already-typed value objects, and `CustomerTimelineMapper` is called from the service after the event query. Neither entity nor Spring Data repository from any other feature module is imported.

---

## 6. Angular design

### 6.1 Routes and components

Lazy feature route registered alongside the existing customer routes:

```text
features/customer/
  disputes/
    disputes.ts|html|scss|spec.ts       # class CustomerDisputes — list + filter + pagination
    dispute-detail/
      dispute-detail.ts|html|scss|spec.ts  # class CustomerDisputeDetail — detail + timeline
    dispute-api.ts                          # typed HttpClient service
    dispute-models.ts                       # wire/query/view types
```

Routes: `/customer/disputes` (list) and `/customer/disputes/:id` (detail).

### 6.2 Dispute list component

**State model:**
- Reactive Form controls: `search`, `statusDropdown` (individual values or "ALL"), `datePreset` (`any` | `last7` | `last30` | `custom`), `fromDate`, `toDate`.
- Signals: `loading`, `error`, `pageData`, `accounts`, `activeTab` (`ALL`|`ACTIVE`|`RESOLVED`), `accountMode` (`account`|`all`).
- URL params: `accountId` (one account), `accounts=all` (all accounts), `tab` (UI-only status group), `status` (effective API status), `search`, `fromDate`, `toDate`, `page`, `sortBy`, `sortDirection`. `accounts=all` and `accountId` are mutually exclusive in the URL; see §1 decision table. `tab` is never sent to the API.
- `switchMap` + generation guard prevents stale responses.
- 300 ms debounce on search.

**Effective status resolution before API call:**
```
if (statusDropdown !== 'ALL') → send status = dropdown value (individual)
if (statusDropdown === 'ALL' && activeTab !== 'ALL') → send status = tab value (ACTIVE | RESOLVED)
if (activeTab === 'ALL' && statusDropdown === 'ALL') → omit status param (= ALL)
```

**Date preset computation** (Africa/Johannesburg):
- `last7`: `toDate` = today, `fromDate` = today − 6 days.
- `last30`: `toDate` = today, `fromDate` = today − 29 days.
- `custom`: uses the from/to date pickers.
- `any`: no date params.

**Account selection:**
- On entry with no account URL params → select all accounts → `history.replace(?accounts=all)` to match the Figma history view.
- `?accounts=all` in URL → `accountMode = 'all'`; do not send `accountId` to API.
- `?accountId=<id>` in URL → `accountMode = 'account'`; send `accountId` to API.
- Switching to "All your accounts" → push `?accounts=all` (remove `accountId`).
- Switching to a specific account → push `?accountId=<id>` (remove `accounts=all`).
- Not-owned `accountId` (API 404/1003) → "Account not available" state, link to default.

**Reset filters** clears `search`, `statusDropdown` (→ ALL), `datePreset` (→ any), `fromDate`, `toDate`, `activeTab` (→ ALL), and resets `page` to 1. Account selection (`accountId` or `accounts=all`) is retained.

**Tabs** (All / Active / Resolved): accessible `role="tablist"` with `role="tab"` and `aria-selected`. Active tab has a visible underline in primary blue. Each tab displays its result count for the current account, search and date filters. Fetch counts with one-item list requests for ALL, ACTIVE and RESOLVED scopes; the API response contract remains unchanged.

**List columns** (desktop `<table>`):
- "Case / merchant": merchant name (bold), `DSP-<ref>` and `formatReason(reason)` in muted text below.
- "Amount": unsigned `formatZar` value (`R 0.00`) as shown in the Figma list reference; detail amount remains signed.
- "Status": status chip — icon + label (see DSH-09 status chip table). WCAG AA tints.
- "Created" (sortable header): `dd MMM yyyy` in Africa/Johannesburg.
- "Details": "View case" link (secondary button style).

Column headers "Created" and "Status" are sortable (`aria-sort`, Lucide sort icon).

**Mobile cards:** same data elements stacked; "View case" is the accessible tap target.

**Reason labels:**

| Reason enum | Display label |
|---|---|
| `UNRECOGNIZED_TRANSACTION` | Unrecognised transaction |
| `DUPLICATE_CHARGE` | Duplicate charge |
| `INCORRECT_AMOUNT` | Incorrect amount |
| `GOODS_OR_SERVICES_NOT_RECEIVED` | Goods or services not received |
| `OTHER` | Other |

### 6.3 Account selector extension

The shared `account-selector` component gains one optional input:

```ts
@Input() showAllAccounts = false;
```

When `true`, the component prepends an "All your accounts" option (no `id`, no account number, a generic accounts icon) to the top of the listbox and the `<select>` fallback. Selecting it emits `selectionChange` with `null` as the `accountId` value. The disputes list component interprets `null` as "switch to all-accounts view". All existing consumers pass no value for this input and retain their current behaviour.

### 6.4 Dispute detail component

Loads once on route-ID change. Derives display reference from UUID in the component:
```ts
displayRef = computed(() => {
  const id = this.dispute()?.id ?? '';
  return 'DSP-' + id.replace(/-/g, '').slice(0, 8).toUpperCase();
});
```

The frontend independently derives the milestone display label from `type` and ordinal position:
```ts
timelineLabel(entry: TimelineEntry, index: number, all: TimelineEntry[]): string {
  if (entry.type === 'IN_REVIEW') {
    const inReviewsBefore = all.slice(0, index).filter(e => e.type === 'IN_REVIEW').length;
    return inReviewsBefore === 0 ? 'Review began' : 'Review resumed';
  }
  // other types are fixed
}
```

**Layout (desktop — two-column):**
```
[Outcome banner — full width, only when ACCEPTED/REJECTED]
┌─────────────────────────┬────────────────────────────────┐
│  Transaction card (~40%)│  Timeline card (~60%)          │
│  DISPUTED TRANSACTION   │  Case timeline                 │
│  Merchant name          │  ─ entries ─                   │
│  −R 417.38 (large bold) │  [locked/in-progress note]     │
│  ─────────────────────  │                                │
│  Main Account · ****1467│                                │
│  Transaction date/time  │                                │
│  Reason for dispute     │                                │
│  Submitted              │                                │
│  What you told us       │                                │
│  <description>          │                                │
└─────────────────────────┴────────────────────────────────┘
```

Mobile: banner → transaction card → timeline, full-width stacked.

**Outcome banner** (`ACCEPTED`):
- Background: `#ECFDF3` (green tint); text `#067647`
- Status chip: `check-circle-2` icon + "Accepted"
- Headline: "Your dispute has been accepted" (bold)
- Body: `decision.message`
- Resolved date/time right-aligned
- No-refund note (subordinate): "This decision applies to your dispute only and does not automatically result in a payment or refund."

**Outcome banner** (`REJECTED`):
- Background: `#FFF5F5` (red tint); text `#76241C`
- Status chip: `x-circle` icon + "Rejected"
- Headline: "Your dispute was not accepted" (bold)
- Body: `decision.message`
- Resolved date/time right-aligned
- No no-refund note

**Transaction card "Account" field:** `dispute.transaction.account.displayName + ' · ' + dispute.transaction.account.maskedNumber`, e.g. "Main Account · ****1467".

**Timeline entries:** icon in a circle — vertical connector line — next icon. Bold label from `timelineLabel()`, date/time, message text.

**Locked-record note** (resolved): `#EFF6FF` bg, Lucide `lock` icon.
**In-progress note** (open/under-review): `#EFF6FF` bg, Lucide `info` icon.

### 6.5 Angular models

```ts
export interface DisputeAccountSummary {
  id: string;
  displayName: string;   // "Main Account" | "Savings Account"
  maskedNumber: string;
}
export interface DisputeTransactionSummary {
  id: string; merchant: string; reference: string;
  amount: string; currency: string; direction: 'IN' | 'OUT'; occurredAt: string;
  account: DisputeAccountSummary;
}
export type DisputeReason = 'UNRECOGNIZED_TRANSACTION' | 'DUPLICATE_CHARGE' |
  'INCORRECT_AMOUNT' | 'GOODS_OR_SERVICES_NOT_RECEIVED' | 'OTHER';
export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'ACCEPTED' | 'REJECTED';
export type TimelineType = 'SUBMITTED' | 'IN_REVIEW' | 'RETURNED_TO_QUEUE' | 'RESOLVED';
export interface TimelineEntry { type: TimelineType; message: string; occurredAt: string; }
export interface DisputeDecision { outcome: 'ACCEPTED' | 'REJECTED'; message: string; }
export interface DisputeListItem {
  id: string; transaction: DisputeTransactionSummary; reason: DisputeReason;
  status: DisputeStatus; createdAt: string; updatedAt: string; resolvedAt: string | null;
}
export interface DisputeDetail extends DisputeListItem {
  description: string; decision: DisputeDecision | null; timeline: TimelineEntry[];
}
export interface DisputePage { total: number; page: number; size: number; items: DisputeListItem[]; }
export interface DisputeListQuery {
  page?: number; size?: number; accountId?: string;
  status?: string; reason?: string;
  fromDate?: string; toDate?: string; search?: string;
  sortBy?: string; sortDirection?: string;
  // Note: 'accounts=all' is a URL-only marker; it is never sent as a query param to the API.
}
```

### 6.6 `dispute-api.ts`

```ts
@Injectable({ providedIn: 'root' })
export class DisputeApi {
  list(query: DisputeListQuery): Observable<ApiResponse<DisputePage>>
  get(id: string): Observable<ApiResponse<DisputeDetail>>
}
```

Uses relative `/api/v1/disputes` URLs. No CSRF header on GET.

### 6.7 Navigation updates

- Customer header: "Disputes" link to `/customer/disputes` alongside Accounts and Transactions.
- Account dashboard quick-links panel: "View Disputes" shortcut.
- Both changes happen in this feature's task.

### 6.8 Accessibility and responsive behavior

- Tabs: `role="tablist"` / `role="tab"` / `aria-selected`; keyboard: Left/Right arrow, Home/End.
- Timeline entries: `<ol>` / `<li>`; connector lines `aria-hidden`.
- Outcome banner: outcome reflected in page `<title>` and a `role="status"` region.
- Status chips: icon + text; never colour alone. All text ≥ 4.5:1 WCAG AA.
- Mobile cards: adequate touch targets (≥ 44×44 px); no essential action hidden behind hover.
- Loading: `aria-busy="true"` skeleton; error/not-found: `role="alert"`.

---

## 7. Testing strategy

| Requirements | Evidence |
|---|---|
| DSH-01, DSH-02 | HTTP: `401/1001` anonymous, `403/1002` AGENT, owned list/detail, cross-customer `404/1003`; JSON body scan asserts no agent/internal fields; asserts `account.displayName` + `account.maskedNumber` |
| DSH-03, DSH-04 | PostgreSQL: `status=ACTIVE` returns OPEN+UNDER_REVIEW only; `status=RESOLVED` returns ACCEPTED+REJECTED only; individual status values; `accountId` narrowing/non-owned/malformed; search with DSP-prefix variants; sort; date boundaries (Johannesburg); empty pages |
| DSH-04.2 | Parser unit tests: ACTIVE → IN predicate; RESOLVED → IN predicate; individual → equality; ALL → no predicate; unknown value → 400/1004 |
| DSH-05 | Timeline: CREATED→SUBMITTED, CLAIMED x1→first message, CLAIMED x2→first+restart, RELEASED→RETURNED_TO_QUEUE no reason, RESOLVED_ACCEPTED/REJECTED→RESOLVED with customer_message, ordering |
| DSH-02.5/DSH-11.4 | Repository test: SELECT lists contain only customer-safe columns; prohibited columns absent |
| DSH-06 | Unit: `formatDisputeRef` derives correct string |
| DSH-07 | Angular: tabs send ALL/ACTIVE/RESOLVED; status dropdown + tab interaction; date preset computes fromDate/toDate in Johannesburg; Reset filters clears all except account; `accounts=all` view; switching all↔one account; back/forward; stale responses discarded; row columns; mobile cards; 401/503 states |
| DSH-08 | Angular: detail outcome banner; no-refund note; account "displayName · maskedNumber"; timeline labels; locked/in-progress notes; 404/401/503 |
| DSH-09 | Angular: status chip icons (inbox=Received, clock=Under review, check=Accepted, x=Rejected); timeline icons |
| DSH-10 | Angular: "Disputes" nav link; "View Disputes" shortcut |
| DSH-11 | DB failure → `503/3002`; no full account number |

---

## 8. Risks and resolved decisions

- **Schema pre-check:** `dispute_events.dispute_version` and event types assumed to exist; Task 0 gate verifies before any code is written.
- **`ACTIVE`/`RESOLVED` groups:** implemented entirely in `DisputeQueryParser` and SQL; no response shape change. The list UI derives All/Active/Resolved counts with parallel one-item list requests using the current account, search and date filters.
- **`accounts=all` as URL-only marker:** the API never receives it; it is stripped by the Angular component. This keeps the backend contract clean and avoids an unknown-parameter 400.
- **Tab + dropdown interaction:** the UI-only `tab` query parameter preserves the selected group while `status` represents the individual dropdown value sent to the API. Choosing an individual value moves the tab to its matching group; choosing All statuses clears the individual filter and preserves the current group. A sort summary sits above the table to match the Figma reference; sorting actions remain on the Created and Status headers. List amounts omit direction signs to match the reference; detail amounts remain signed.
- **CLAIMED message variation:** backend mapper sets message text; frontend sets display label independently.
- **Account displayName:** derived from `account_type` in JDBC adapter; no extra column.
- **`account-selector` extension:** additive; `showAllAccounts = false` default preserves all existing consumers.
- **Least-privilege queries:** all three SQL SELECT lists are enumerated in §3; prohibited columns documented and never appear.

No unresolved blocker prevents tasking once this design is approved.
