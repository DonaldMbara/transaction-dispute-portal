# Customer Dispute History — Requirements

**Status:** Draft — awaiting developer approval
**Feature:** Customer Dispute History
**Prerequisites:** Project Foundation, Authentication & RBAC, Customer Accounts, Customer Transactions, Dispute Submission (all implemented)
**Related approved baseline:** [business rules](../../../docs/business-rules.md), [API contract](../../../docs/design/api-contract.md), [database design](../../../docs/design/database-design.md), [UI flows](../../../docs/design/ui-flows.md)

---

## 1. Purpose and scope

Customers need to track their submitted disputes: see the full list, monitor live status and, when a case is resolved, read the outcome. This feature delivers the customer dispute list (`GET /api/v1/disputes`) and dispute detail (`GET /api/v1/disputes/{id}`) endpoints and the two matching Angular screens — the disputes history list at `/customer/disputes` and the dispute detail page at `/customer/disputes/:id`.

The design reference screens (non-authoritative) show:
- A **disputes list page** with a page eyebrow "DISPUTES", heading "Your disputes", subtitle "A clear view of where things stand, from receipt to resolution.", a search field ("Case ID or merchant"), a Status dropdown, a Created date preset control, an "All your accounts" link, a "Reset filters" action, and a tab group (All / Active / Resolved) that scopes the list by lifecycle stage.
- **Rows** show the merchant name (bold), case reference and reason label below it in muted text, an unsigned amount (`R 0.00`) matching the Figma list, a status chip with an icon, a created date, and a "View case" link. The dispute detail retains the signed amount.
- A **Dispute details** page (desktop and mobile) with: a case reference, a prominent outcome banner (green = Accepted, red-tinted = Rejected), a "Disputed transaction" card on the left, and a "Case timeline" section on the right.
- A locked-record note at the bottom: "This case is resolved. Your submitted details and the decision are available here for your records."
- The mobile layout collapses into a single column; the filter controls and All / Active / Resolved tabs appear inline above the rows.

This feature does **not** implement dispute submission (already exists), agent workflows, resolution actions, reopening, notifications, or refunds.

### In scope

- `GET /api/v1/disputes` — customer-owned, paginated, filterable dispute list with optional `accountId` selector and an `accounts=all` view.
- `GET /api/v1/disputes/{id}` — customer-owned dispute detail with customer-safe curated timeline.
- Angular disputes list at `/customer/disputes` with All / Active / Resolved tabs, status filter, date preset, search, Reset filters, and an "All accounts" option on the account selector.
- Angular dispute detail at `/customer/disputes/:id` with outcome banner, transaction card, curated timeline and locked-record note.
- Backend customer-safe DTO mapping: curated timeline milestones derived from `dispute_events`, never exposing internal notes, agent identities, release reasons or raw events.
- "Disputes" navigation link added to the customer header and a "View Disputes" shortcut added to the account dashboard quick-links panel.

### Out of scope

- Dispute creation (owned by dispute-submission).
- Agent workflows (separate spec).
- Dispute reopening, escalation, notifications, real-money outcomes.
- Full account number in any dispute or transaction context — masked values and display names only.
- Any admin or agent read of the customer-facing dispute history endpoints.

---

## 2. Terms and invariants

- A **customer dispute** is accessible only when its linked transaction belongs to an account owned by the authenticated customer, enforced via `dispute → transaction → account → customer_id` join at every query.
- **Curated timeline** is a safe projection of `dispute_events` ordered by `dispute_version ASC, created_at ASC`. Internal notes, agent identities and release reasons are never included. Each milestone maps one or more raw event types to a public-facing label and description.
- **Outcome banner** appears only when the dispute is in `ACCEPTED` or `REJECTED` state. It shows the customer-facing resolution message stored in `customer_resolution_message`, not the internal notes.
- **Decision field** in the detail DTO is `null` until terminal; once terminal it is `{outcome: "ACCEPTED"|"REJECTED", message: <customerResolutionMessage>}`.
- **`resolvedAt`** is `null` until terminal.
- **`accountId` selector** follows exactly the same ownership rule as `GET /api/v1/transactions`: a supplied UUID is validated through `dispute → transaction → account → customer_id`; a non-owned or absent UUID returns `404/1003`; a malformed/duplicate UUID returns `400/1004`.
- **`accounts=all` view** — when `?accounts=all` is present in the URL, the list shows disputes across all owned accounts (equivalent to omitting `accountId` at the API level). `accounts=all` and `accountId` are mutually exclusive: supplying both is a client error handled by the Angular component (it strips `accountId` and keeps `accounts=all`). The API itself does not receive both simultaneously; if it did, the parser would return `400/1004` for an unknown parameter combination.
- **Status group filter values**: in addition to the four individual status values and `ALL`, the API accepts two group aliases: `ACTIVE` (expands to `OPEN OR UNDER_REVIEW`) and `RESOLVED` (expands to `ACCEPTED OR REJECTED`). The UI All / Active / Resolved tabs store `ALL`, `ACTIVE` and `RESOLVED` in the UI-only `tab` URL parameter, then resolve the effective API `status` before requesting data. The All, Active and Resolved tabs display counts for the current account, search and date filters. Counts are derived from the same list API using lightweight one-item page requests; the response contract is unchanged.
- **Timeline milestone types** used in customer-facing DTO (never raw event types):
  - `SUBMITTED` — dispute created (maps to `CREATED` event).
  - `IN_REVIEW` — an agent began reviewing (maps to each `CLAIMED` event in order).
  - `RETURNED_TO_QUEUE` — returned to the unassigned queue (maps to each `RELEASED` event); description is generic with no internal reason.
  - `RESOLVED` — final decision (maps to `RESOLVED_ACCEPTED` or `RESOLVED_REJECTED`).
- **Case reference** — the dispute UUID is the stable identifier; the UI renders it in the display format `DSP-<first-8-uppercase-hex-chars>` (e.g. `DSP-5F9868E0`) as a short, human-scannable label. This is a derived display string, not a separate stored column.
- **Status labels** on the list follow `ui-flows.md`: `OPEN` → "Received"; `UNDER_REVIEW` → "Under review"; `ACCEPTED` → "Accepted"; `REJECTED` → "Rejected".
- **Account context** in dispute list and detail responses includes `id`, `displayName` and `maskedNumber`. `displayName` is derived from `account_type` using the same mapping as the accounts feature (`MAIN` → "Main Account", `SAVINGS` → "Savings Account"). The full account number never appears in any dispute response, error, log line or audit entry.
- Monetary amounts are positive decimal strings; direction and sign come from the transaction `direction` field.

---

## 3. Functional requirements

### DSH-01 — Authentication and role boundaries

1. Both dispute endpoints shall require a valid, current server-side session.
2. Both endpoints shall be accessible only to users with the `CUSTOMER` role; an authenticated `AGENT` request shall return `403/1002`.
3. An invalid, expired or revoked session shall return `401/1001`.
4. Both dispute GET handlers shall be marked `@AuthenticatedActivity` so that an authenticated and authorized CUSTOMER request renews strict idle activity at handler admission, consistent with the existing policy applied to transaction handlers.
5. This feature shall not introduce authorization for unimplemented routes or agent endpoints.

### DSH-02 — Ownership isolation

1. The system shall derive the authenticated customer's identity exclusively from the validated server-side security context.
2. The system shall only return disputes whose linked transaction → account → customer chain matches the authenticated customer's UUID.
3. Ownership constraints shall be applied before counting, filtering, searching, sorting and paginating.
4. A dispute that does not exist or does not belong to the authenticated customer shall return `404/1003` — no existence disclosure.
5. Internal agent notes, agent identities (names, IDs, email), internal resolution notes, release reasons and raw audit event records shall never appear in any customer-facing response body, timeline entry, error message or log line. No query may select `internal_resolution_notes`, `assigned_agent_id`, `assigned_at`, `actor_id`, `previous_agent_id`, `new_agent_id` or `internal_notes` from any table; these columns are not read at all by the customer read path.
6. The `customer_resolution_message` from a terminal dispute shall be shown to the customer; the `internal_resolution_notes` shall not.

### DSH-03 — Dispute list

1. The system shall provide `GET /api/v1/disputes` for the authenticated customer's paginated dispute list.
2. The response shall use the standard success envelope with `data.total`, `data.page`, `data.size` and `data.items`.
3. Default page 1, size 20; maximum size 100.
4. A valid page beyond the result set shall return HTTP 200 with empty `items` and the scoped total.
5. Each list item shall include: `id`, `transaction` (`{id, merchant, reference, amount, currency, direction, account: {id, displayName, maskedNumber}}`), `reason`, `status`, `createdAt`, `updatedAt`, `resolvedAt` (nullable).
6. List items shall not include `description`, `decision` or `timeline` — those fields belong to the detail response only.
7. The list shall default to descending `createdAt` order with UUID `id` as the tie-breaker in the same direction.

### DSH-04 — Dispute list filters and search

1. The list shall accept an optional `accountId` UUID query parameter, validated through `dispute → transaction → account → customer_id` ownership. Non-owned or absent → `404/1003`; malformed or duplicate → `400/1004`. When omitted, all owned accounts' disputes are returned.
2. The list shall support `status` filter with the following accepted values:
   - `ALL` (default) — no status predicate.
   - `ACTIVE` — matches disputes with status `OPEN` or `UNDER_REVIEW`.
   - `RESOLVED` — matches disputes with status `ACCEPTED` or `REJECTED`.
   - `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED` — individual values for exact matching.
   No per-status counts are returned. Invalid values return `400/1004`.
3. The list shall support `reason` filter: `ALL` (default) or any single supported reason value.
4. The list shall support `fromDate`/`toDate` (ISO local date, Africa/Johannesburg boundaries, inclusive `createdAt` range).
5. The list shall support free-text search over the dispute reference (see below), submission `description`, and the linked transaction's `merchant` and `reference` fields. **Reference search:** the search input may optionally be prefixed with `DSP-` (case-insensitive); the prefix is stripped before matching. The remaining text is matched against the start of the dispute `id` UUID string (i.e. `d.id::text ILIKE :escapedPrefix || '%'`), fully parameterized and escaped. For example, searching `DSP-5F9868E0` or `dsp-5f9868e0` or `5F9868E0` all match the dispute with UUID `5f9868e0-…`. Partial references (e.g. `5F98`) also match. The non-prefix fields (`description`, `merchant`, `reference`) remain substring matches (`ILIKE '%…%'`). All search predicates are combined with `OR`.
6. The list shall support `sortBy`: `createdAt` (default), `updatedAt`, `status`; and `sortDirection`: `DESC` (default), `ASC`.
7. Unknown query parameters shall return `400/1004`. All filter inputs shall be validated with the same bounds and error semantics as transactions.
8. The endpoint shall not accept `search` over agent-only fields, full account numbers or internal notes.

### DSH-05 — Dispute detail

1. The system shall provide `GET /api/v1/disputes/{id}` for an owned dispute.
2. The detail response shall include all list-item fields plus: `description`, `decision` (null until terminal; otherwise `{outcome, message}`), and `timeline` (ordered array of customer-safe milestone objects).
3. Each timeline entry shall have: `type` (one of `SUBMITTED`, `IN_REVIEW`, `RETURNED_TO_QUEUE`, `RESOLVED`), `message` (safe plain-language string), and `occurredAt` (UTC ISO 8601 instant). The `type` and `message` are determined by the backend mapper; the frontend independently derives the human-readable milestone label from `type` and position (see DSH-08).
4. Timeline entries shall be ordered ascending by `disputeVersion` then `createdAt`. Repeated `IN_REVIEW`/`RETURNED_TO_QUEUE` entries shall each appear separately to reflect genuine lifecycle cycles.
5. The detail shall include the transaction summary (`merchant`, `direction`, `amount`, `currency`, and account `{id, displayName, maskedNumber}`), dispute `reason`, `status`, `createdAt`, and `description` for the "What you told us" section.
6. The path ID shall be a UUID; a malformed UUID shall return `400/1004`; an absent or non-owned UUID shall return `404/1003`.
7. GET `/api/v1/disputes/{id}` shall not accept an `accountId` query parameter.

### DSH-06 — Case reference display format

1. The backend shall expose the full UUID `id` in all responses; the frontend alone shall derive the display reference `DSP-<first-8-uppercase-hex-chars-of-UUID>`.
2. The display reference shall be shown at the top of the dispute detail page and as a secondary scan label below the merchant name in each list row.
3. There is no separate stored `reference` column on the disputes table; this is purely a frontend formatting rule.

### DSH-07 — Angular disputes list

1. An authenticated CUSTOMER shall reach the disputes list at `/customer/disputes` through the existing `DashboardLayout`. The page shall show the eyebrow "DISPUTES", heading "Your disputes", and subtitle "A clear view of where things stand, from receipt to resolution."
2. The list shall reuse the shared `account-selector` component. The selector shall accept an optional `showAllAccounts` input (boolean, default `false`); when `true` it prepends an "All your accounts" option. On the disputes page this option is always present. URL is the only selection state.
3. **Account selection and URL state:**
   - On first entry with no account URL params: select all owned accounts and rewrite the URL with `?accounts=all` using `history.replace`, matching the Figma history view. A specific account is shown only when `accountId=<id>` is present.
   - Selecting "All your accounts" from the selector replaces the URL with `?accounts=all` (removing `accountId`) using push navigation.
   - Selecting a specific account from the selector replaces the URL with `?accountId=<id>` (removing `accounts=all`) using push navigation.
   - `accounts=all` and `accountId` in the URL simultaneously: the component treats `accounts=all` as the authority, strips `accountId` from the URL silently, and requests the API without `accountId`.
   - Supplied `accountId` that returns `404/1003` → "Account not available" state with link to view all accounts.
   - Back/forward navigation restores whichever URL state was pushed.
4. **All / Active / Resolved tabs** are the primary status scope control, bound to the UI-only `tab` query parameter (`ALL`, `ACTIVE`, `RESOLVED`). The API accepts one effective `status` value. Tabs reset pagination to page 1 and clear any individual status selection on change.
5. **Filter controls** (all appear in one filter bar above the tabs):
   - Search field: placeholder "Case ID or merchant"; searches reference (DSP- prefix accepted), description, merchant and transaction reference. 300 ms debounce.
   - Status dropdown: mirrors the individual status values (`All statuses`, `Received`, `Under review`, `Accepted`, `Rejected`). The individual selection is encoded as `status` and sent to the API. Selecting an individual status moves the selected tab to its matching group (`OPEN`/`UNDER_REVIEW` → `ACTIVE`; `ACCEPTED`/`REJECTED` → `RESOLVED`) so the tab, dropdown and results remain consistent. `All statuses` clears the individual status and retains the current group tab. The group tab is UI-only and is never sent as a second status parameter.
   - Created date preset: `Any date` (default, no date params), `Last 7 days`, `Last 30 days`, `Custom range` (reveals from/to date pickers). All presets compute `fromDate`/`toDate` in Africa/Johannesburg calendar boundaries and set them in the URL as ISO dates. Changing preset resets pagination to page 1.
   - **Reset filters** button: clears search, status dropdown (back to "All statuses"), date preset (back to "Any date"), and tab (back to "All"), but retains the current account selection (`accountId` or `accounts=all`). Sorting is retained.
6. **Column-header sorting:** sortable columns are Created (maps to `createdAt`) and Status (maps to `status`). Clicking an active column toggles `sortDirection`. Clicking a new column sets that field with `DESC` as default. The results heading displays a sort summary (for example, “Newest first ↓”) in addition to sortable Created and Status column headers; default sort is `createdAt DESC`.
7. **Rows:** each row is a single keyboard-operable link to `/customer/disputes/:id`. Row content:
   - Left cell: merchant name (bold), case reference (`DSP-…`) and reason label in muted text below.
   - Amount cell: unsigned ZAR amount (`R 0.00`) as shown in the Figma list reference. The detail page retains the direction sign.
   - Status cell: status chip with a Lucide icon + text label (see DSH-09 icon table). WCAG AA tints: Received (neutral), Under review (blue tint), Accepted (green tint), Rejected (red tint).
   - Created date cell: `dd MMM yyyy` in Africa/Johannesburg.
   - Details cell: a visible "View case" link (secondary button style) pointing to the detail page. This is the accessible link target for the row; the entire row is also clickable.
   - On narrow widths (mobile) rows become stacked cards; each card maintains the same data elements, accessible tap target, and the "View case" link.
8. An empty result set shall display a helpful "No disputes yet" message and a link to the transactions list.
9. `401` relies on the existing session-clearing flow; `503` shows a manual retry without automatic retry, polling or prefetch.
10. The "Disputes" navigation link shall appear in the customer header alongside Accounts and Transactions.

### DSH-08 — Angular dispute detail

1. The detail page at `/customer/disputes/:id` shall show: a "Back to disputes" link, the case reference (`DSP-…`), the page heading "Dispute details" and the subtitle "Your case, its progress and the final decision — all in one place."
2. When the dispute is terminal (`ACCEPTED` or `REJECTED`), a **prominent outcome banner** shall appear directly below the subtitle, occupying the full content width. For `ACCEPTED` it shall use a green background tint; for `REJECTED` a red/amber tint. It shall show the status chip, a bolded outcome headline ("Your dispute has been accepted" / "Your dispute was not accepted"), the customer-facing resolution message, and the `resolvedAt` date/time in Africa/Johannesburg on the right. The banner shall also include a short plain-language note that acceptance means the dispute was upheld but does not by itself move money (e.g. "This decision applies to your dispute only and does not automatically result in a payment or refund."). This note shall appear for ACCEPTED disputes only and shall be visually subordinate to the resolution message.
3. Below the banner (or subtitle if no banner), the layout shall split into a **left transaction card** and a **right timeline card** on desktop (roughly 40/60 split); on mobile both stack vertically with the outcome banner first, then the transaction card, then the timeline.
4. The **transaction card** shall show: label "DISPUTED TRANSACTION", merchant name (bold, large), signed amount (very large, bold, in ZAR format — sign from direction), a horizontal rule, then labelled fields in a `<dl>`: "Account" (`displayName · maskedNumber`, e.g. "Main Account · ****1467"), "Transaction date and time", "Reason for dispute", "Submitted" (dispute `createdAt`), and a "What you told us" section with the full `description`.
5. The **timeline card** shall show: heading "Case timeline", subtitle "The key updates to your dispute.", then an ordered vertical list of milestone entries. Each entry has a Lucide icon (see DSH-09), the milestone label (bold), the date/time in Africa/Johannesburg below the label, and a one-line plain-language message below that. The frontend derives the human-readable label from the entry's `type` and its ordinal position among all `IN_REVIEW` entries in the list: the first `IN_REVIEW` shows "Review began"; each subsequent `IN_REVIEW` shows "Review resumed". All other type-to-label mappings are fixed (see DSH-09).
6. For resolved disputes, a **locked-record note** shall appear at the bottom of the timeline card: a Lucide lock icon and the text "This case is resolved. Your submitted details and the decision are available here for your records."
7. For `OPEN` or `UNDER_REVIEW` disputes, an **in-progress note** shall appear at the bottom of the timeline card: a Lucide info icon and the text "Your case is being reviewed. We will update the timeline as your dispute progresses."
8. The detail component shall load once per route-ID change; it shall not poll or auto-refresh.
9. `401` relies on the existing session-clearing flow; `404` shows a "Dispute not found" message with a back link; `503` shows a manual retry.

### DSH-09 — Timeline milestone icons, labels and status chip icons

**Timeline milestone icons and labels** (frontend mapping from API `type`):

| Milestone type | Lucide icon | Fixed label | Notes |
|---|---|---|---|
| `SUBMITTED` | `mail` | "Dispute submitted" | |
| `IN_REVIEW` | `search` | "Review began" (1st) / "Review resumed" (2nd+) | Label varies by ordinal position; icon is the same |
| `RETURNED_TO_QUEUE` | `rotate-ccw` | "Returned to the queue" | |
| `RESOLVED` (ACCEPTED) | `check-circle-2` | "Dispute accepted" | |
| `RESOLVED` (REJECTED) | `x-circle` | "Dispute rejected" | |

Icons shall be inside a filled circle for visual separation. Completed milestones may use a connecting vertical line between entries as shown in the design reference.

**Status chip icons** (used in the list row and outcome banner):

| Status | Lucide icon | Label |
|---|---|---|
| `OPEN` (Received) | `inbox` | "Received" |
| `UNDER_REVIEW` | `clock` | "Under review" |
| `ACCEPTED` | `check-circle-2` | "Accepted" |
| `REJECTED` | `x-circle` | "Rejected" |

All chips display icon + text; never colour alone.

### DSH-10 — Account dashboard quick-links

1. When this feature's route is implemented, the account dashboard quick-links panel shall include a "View Disputes" shortcut targeting `/customer/disputes`.
2. The shortcut shall only appear after this feature is implemented — not as a "coming soon" dead link.
3. This change is owned by this feature's task plan and shall be delivered alongside the disputes list route.

### DSH-11 — Reliability, security and maintainability

1. All responses shall use the existing `{status, data, error_code}` envelope, `Cache-Control: no-store`, `X-Correlation-ID`, and meaningful HTTP statuses.
2. The feature shall not add CORS configuration. GET requests do not require CSRF.
3. Database failures before commitment shall return `503/3002`; they shall never become empty results or misleading not-found responses.
4. No full account number, internal note, agent ID or agent email shall appear in any customer-facing response, error, log line or audit entry produced by this feature. No customer read query shall select `internal_resolution_notes`, `assigned_agent_id`, `assigned_at`, `actor_id`, `previous_agent_id`, `new_agent_id` or `internal_notes`.
5. Tests shall use the existing PostgreSQL Testcontainers approach; no H2 substitution.
6. Implementation shall follow the approved specification workflow: requirements → design → tasks → implementation.

---

## 4. API contract acceptance

| Endpoint | Required access | Required behaviour |
|---|---|---|
| `GET /api/v1/disputes` | CUSTOMER session | Ownership-scoped, filterable (incl. ACTIVE/RESOLVED groups), sortable, paginated dispute list |
| `GET /api/v1/disputes/{id}` | CUSTOMER session and ownership | Customer-safe dispute detail with curated timeline |

No mutation endpoint, agent endpoint or submission endpoint is introduced. The existing `POST /api/v1/disputes` (dispute-submission) is unchanged.

---

## 5. Acceptance evidence

Before this feature is marked complete, the following evidence must exist:

1. **Schema pre-check:** confirmation that the dispute-submission migrations include `dispute_events.dispute_version` and the event types `CREATED`, `CLAIMED`, `RELEASED`, `RESOLVED_ACCEPTED`, `RESOLVED_REJECTED`. If any are absent, implementation stops and reports the gap rather than adding a migration inside this feature.
2. **Unit/service tests:** ownership predicate; curated timeline mapping for each milestone type (single claim, multiple claim-release cycles, resolution); redaction — assert the SQL projection selects only the customer-safe columns listed in DSH-02.5 and DSH-11.4; display-reference formatting.
3. **PostgreSQL integration tests:** V1–V7+ Flyway history unaffected; list scoped counts across two customers; detail 404 for non-owned UUID; `accountId` selector narrowing; `status=ACTIVE` returns OPEN and UNDER_REVIEW only; `status=RESOLVED` returns ACCEPTED and REJECTED only; individual status values still work; combined filter/search/date/reason/status predicates; reference search with `DSP-` prefix, lowercase prefix, partial reference and no-match case; empty results on valid filtered request.
4. **HTTP tests:** success envelope/headers, fixed ordering, pagination, unknown-parameter rejection, `accountId` ownership rules (same as transaction tests), `401/1001` unauthenticated, `403/1002` AGENT, `404/1003` non-owned detail, `503/3002` database failure; serialized HTTP response bodies assert no internal notes, no agent identity, no full account number; assert `account` object contains `displayName` and `maskedNumber`.
5. **Timeline contract tests:** assert each milestone type maps correctly; `RETURNED_TO_QUEUE` contains no release reason; `RESOLVED` contains the customer message, not the internal notes; ordering is by `disputeVersion ASC, createdAt ASC`.
6. **Angular component tests:** tabs send correct `status` values (`ALL`, `ACTIVE`, `RESOLVED`); date preset computes correct fromDate/toDate in Johannesburg; Reset filters clears search/date/status-dropdown/tab but retains account selection; `accounts=all` URL state shows all-accounts view; switching between all-accounts and a specific account updates URL correctly; back/forward restores URL state; stale responses discarded; list rows render merchant, DSP reference + reason label, amount, status chip with icon + label, created date, "View case" link; mobile stacked cards render the same elements; account field in detail shows "displayName · maskedNumber"; ACCEPTED banner has no-refund note; REJECTED banner does not; timeline labels correct; locked/in-progress notes correct; 404/401/503 states tested.
7. **Navigation test:** "Disputes" link appears in the customer header and "View Disputes" appears in the account dashboard quick-links panel.

---

## 6. Dependencies and future work

- Depends on: implemented session authentication, CUSTOMER/AGENT role checks, dispute-submission feature (dispute and event tables, `dispute_events.dispute_version` column, event types listed in §5.1), `DashboardLayout`, shared `account-selector` (extended with optional `showAllAccounts` input), and correlation/envelope infrastructure.
- The dispute-submission feature owns `disputes` and `dispute_events` tables. This feature reads them; it does not alter their schema.
- The shared `account-selector` component is extended by this feature with an optional `showAllAccounts` input. The existing behaviour (no "All accounts" option) is preserved for transactions and other consumers.
- Agent workflow (separate spec) owns `GET /agent/disputes` and mutation endpoints; this feature must not conflict with agent DTOs or route guards.
- Future: dispute search improvements, cursor pagination, real-time status push, reopening — all separate specs.

---

## 7. Traceability

| Requirement area | Approved source |
|---|---|
| Customer ownership through dispute → transaction → account | BR-A02, BR-H03, `api-contract.md` §3 |
| Timeline curation, agent-field redaction | BR-H03, BR-H05, dispute-domain steering |
| Curated milestone types, generic returned-to-queue copy | `api-contract.md` §4, `business-rules.md` §6 |
| `accountId` resource selector (same rule as transactions) | `api-contract.md` §3 |
| `accounts=all` view (all-accounts disputes) | This feature; consistent with omitting accountId at the API level |
| `ACTIVE`/`RESOLVED` status group aliases | This feature; no schema or response-shape change |
| Pagination, envelope, error codes, search/filter semantics | `api-contract.md` §§1–3, 7 |
| Status labels, outcome banner wording, locked-record note | `ui-flows.md` §5, design reference screens (non-authoritative) |
| All/Active/Resolved tabs, date preset, Reset filters, row layout | design reference screens (non-authoritative); justified on UX merits |
| Case reference display format `DSP-…` | design reference (non-authoritative), `ui-flows.md` §5 |
| Lucide icon choices (timeline + status chips) | `ui-flows.md` §1, design reference (non-authoritative) |
| Account context includes displayName and maskedNumber | `api-contract.md` §4, customer-accounts ACC-04 |
| ACCEPTED = upheld, no refund | BR-L10, `business-rules.md` §5 |
| WCAG AA contrast, accessible controls | `frontend-standards.md`, `ui-flows.md` §9 |
| Module boundaries, DTO/persistence practices | `backend-standards.md`, `structure.md` |
