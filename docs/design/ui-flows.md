# UI Flows — Transaction Dispute Portal

**Status:** Customer Accounts alignment v0.6 · **Date:** 2026-10-11
**Design:** Capitec public website as the primary visual reference, not an official Capitec product. **Desktop-first web portal**, responsive at tablet and mobile sizes for both customers and agents. UI follows this document and `.kiro/steering/frontend-standards.md`: simplified authenticated header, existing `DashboardLayout`, shared button variants, local component styles, Lucide icons, accessible and responsive layouts. A mobile mockup shown during discussion is AI-generated and is **not** a source of truth — do not cite it or reproduce its labels, field names, merchant categories, card details, account names or number formats. Account-row content, month grouping and the segmented direction control are application design choices justified against these standards.

## 1. Public website reference and visual audit

**Primary reference:** https://www.capitecbank.co.za (reviewed 2026-10-10 via public page extraction). Use the website's design language rather than designing an unrelated dashboard.

### Confirmed from the reference page structure

- Utility/header links: Personal, Business, search, Sign in, Get the app, Switch to Capitec.
- Product navigation: Transact, Save, Insure, Credit, Connect and Rewards.
- Page introduction: clear `Life Cover` heading, short explanatory copy and multiple CTA links (`Get it on the App`, `How to claim`, `Learn more`).
- Content hierarchy: distinct sections with headings, short paragraphs, benefits and expandable FAQ/accordion patterns.
- Footer: grouped links under headings including Get in touch, Personal, Business, Rewards, Company, Tools, General info, Legal and Privacy; narrow-view extraction includes accordion-chevron controls for groups.
- Icon affordances observable in page extraction include search, right-arrow, chevron and accordion chevron. **Exact icon artwork/library is not verified.**

### Browser DevTools CSS supplied by developer (desktop and mobile)

These values come from the **provided CSS excerpt**, not an independent pixel-perfect audit:

- `body { font-family: "NunitoSans", "Arial", "Helvetica", sans-serif; color: #3a3a3a; }` overrides the generic Bootstrap body font declaration. The shown `@font-face` defines NunitoSans light (`font-weight: 300`) with `font-display: swap`; other weights are not yet confirmed.
- `--theme-font-family: NunitoSans, Arial, Helvetica, sans-serif` and `--theme-text-colour: #3a3a3a`.
- `--theme-text-link-colour: #2f70ef`; `--theme-text-link-hover-colour: #2259d0`.
- `--theme-primary-button-background-colour: #2f70ef`, border `#2f70ef`, text `#ffffff`.
- `--theme-single-button-background-colour: #ffffff`, border `#2f70ef`, text `#2f70ef` (secondary/outline variant).
- `--theme-background-colour: #f7f7f7`; some components explicitly use white surfaces or `#f9f9f9`.
- `.hero-section-space .hero-landing-narrow-content` padding-top: **70px desktop**, **30px at max-width 991px**.
- Framework breakpoint variables: 576px, 768px, 992px, 1200px. They are available tokens, not verified layout transition points for all components.
- The supplied stylesheet declares card borders `#e1e1e1` / `#e3e3e3`, and hover shadow `0 30px 60px 0 rgba(187,187,187,.6)` for one card family; do not apply these universally.

### Additional screenshot and CSS audit (desktop navigation and buttons)

- Screenshot shows a white utility bar with logo, Personal/Business segmented control, search field, Sign in, pill CTAs and a close control when the expanded navigation is open. The expanded navigation uses a left-side vertical category list and right-side grouped content/cards. This is a marketing mega-menu; **do not reproduce it wholesale in the authenticated portal**.
- `.primary-nav` uses `display: flex`, `flex-wrap: nowrap`, `align-items: center`, `justify-content: space-between`, `gap: 20px`, and left/right margins of `15px`, overridden to `40px` at `min-width: 992px`.
- `.button--v2` has `height: 48px`, `min-width: 144px`, `border-radius: 24px`, `padding: 14.5px 24px`, `border: 1.5px solid #2f70ef`, `font-size: 16px`, `font-weight: 600`, and centered inline-flex content.
- `.button--v2--secondary` has white background with `#2f70ef` text/border. `.button--v2--small` sets `height: 30px`, `border-radius: 15px`, `padding: 6.5px 15px`, `font-size: 14px`.
- At `min-width: 992px`, `.primary-nav__cta-button-group .button--v2` has `height: 36px`, `min-width: 116px`, `border-radius: 30px`, `font-size: 16px`, and auto width. At `min-width: 768px`, the nav CTA has `max-width: 180px`.
- Use our own icon library (Lucide is suitable); do not copy proprietary site icons or branding.

**Still unverified:** header/footer heights, heading typography, exact mobile menu transition, icon dimensions, and general section spacing beyond the measured hero and nav values. The supplied screenshots are a desktop-width expanded navigation view, not evidence of mobile navigation behaviour.

### Adaptation to an authenticated app

- Replace public marketing mega-navigation with a compact desktop header containing project identity, implemented Accounts / Transactions (customer) or Agent area (agent), and Sign out. Add future navigation items only after their routes are implemented.
- On narrow screens collapse navigation into an accessible menu; keep primary task actions visible.
- Reuse reference-inspired hierarchy, white space, headings, restrained CTAs and grouped information. Do not insert a large marketing hero on every authenticated screen.
- Use a minimal app footer (project name, demo disclaimer and optional help), not the public site's entire corporate footer.
- Agent case management may use a desktop sidebar and dense table, retaining the same design tokens.

## 2. Design tokens (based on supplied Capitec CSS; app mappings are our choice)

| Token | Value | Evidence / proposed use |
|---|---|---|
| `--font-family` | `"Nunito Sans", Arial, Helvetica, sans-serif` | Site's `NunitoSans` font; use a legally sourced local/web font for app |
| `--color-primary` | `#2F70EF` | Primary buttons and links |
| `--color-primary-hover` | `#2259D0` | Verified link hover; button hover needs separate inspection |
| `--color-background` | `#F7F7F7` | Site theme background |
| `--color-surface` | `#FFFFFF` | Cards and form surfaces |
| `--color-text` | `#3A3A3A` | Site body text |
| `--color-border` | `#E1E1E1` | One reference card/table border token; proposed common border |
| `--color-muted` | `#64748B` | **Our proposed** secondary text colour; not measured |
| `--color-danger` | TBD | Select accessible error colour, do not infer from Bootstrap defaults |

Primary button: blue background, blue border, white text. Outline button: white background, blue border and text. Standard button geometry is measured as 48px height / 24px radius / 16px semibold Nunito Sans; desktop header CTA is 36px / 30px radius at >=992px. Provide accessible focus/hover states and mobile touch targets during implementation. Keep hero padding as a reference for marketing-style landing content, **not** as a blanket rule for application pages.

Meet WCAG AA contrast, keyboard access, visible focus, readable error text, and touch target sizing. Status uses text + icon, never colour alone. Use a simple project identity, not official logos.

Session policy: PostgreSQL-backed server-side sessions with opaque HttpOnly cookies, Secure and SameSite=Lax in production, and 30-minute server-enforced idle timeout. Reload may restore a valid session; expiry/sign-out leads to login. Clear private cached state and preserve only validated role-appropriate navigation. Login/logout and unsafe requests require CSRF; startup uses GET /auth/csrf then GET /auth/me; masked X-CSRF-TOKEN is held only in memory. Login rotates identifier/token material; confirmed POST logout clears private UI and expires the cookie. 401 clears identity, while 403/503 do not imply logout; ambiguous sign-out hides protected content and offers explicit retry. No automatic identity polling or unsafe replay. No public registration or bearer-token interceptor; provisioning is admin-controlled. Unsent text is preserved only within the authenticated session where safe.

## 3. Information architecture

```text
/login
/customer                         (account dashboard — stacked account rows)
/customer/transactions            (transaction list with account selector)
/customer/transactions/:id        (transaction detail)
/customer/disputes
/customer/disputes/:id
/agent/disputes?queue=unassigned|mine|all
/agent/disputes/:id
```

Authenticated shell displays role-specific navigation.

- **Customer header links:** Accounts (`/customer`) and Transactions (`/customer/transactions`); Transactions remains active on its detail route. Do not show a My Disputes link until that route exists.
- **Agent header links:** Agent area. Do not show customer transaction navigation to AGENT.

Angular guards help UX; Spring Security remains authoritative.

The later customer Disputes page reuses the shared account selector and defaults to the primary account, falling back to the first item in GET /accounts order if none is primary. Its `accountId` is a resource selector checked through transaction → account → customer.

## 4. Customer journey A — Find and dispute a transaction

```text
Login → Account Dashboard (stacked account rows) → Select account row → Transactions list (accountId in URL)
      → All | Money In | Money Out
      → Select transaction → Transaction details → Dispute this transaction (eligible completed transactions only)

The submission flow lives at `/customer/disputes/new?transactionId=<uuid>`. It reloads transaction details from the API and shows only the merchant, amount, reference and masked account context. The customer chooses a supported reason, enters a required description (maximum 2000 characters), reviews it, then submits with an idempotency key. Loading, validation, safe manual retry, duplicate, unavailable and received/open states are accessible. The form does not promise a refund and does not expose a My Disputes link; history/detail remain deferred.
```

### Screen: Account Dashboard

- Route: `/customer`. Header label: `Accounts`.
- **Goal copy:** describe the customer's accounts as (Main Account, Savings Account). Do not use other type or product labels.
- **Layout:** desktop uses two columns: full-width stacked account rows occupy about two thirds, and an optional quick-links panel occupies about one third. On narrow screens, the panel stacks below the account list. Account rows remain full-width and stacked at all sizes. The account list comes first in DOM order.
- **Account rows:** each row is one accessible, keyboard-operable link to `/customer/transactions?accountId=<id>`, activated by Enter or Space with visible focus. The left side contains a Lucide account icon, `displayName` ("Main Account" or "Savings Account"), an optional "Primary" badge, and a labelled "Account number" with the full value when non-null. The right side contains the "Available balance" label, a visually dominant balance ("R 24 500.00" — ZAR, two decimal places, space as thousands separator), and a chevron. Keep the title-row height fixed so rows align with or without the Primary badge. Do not use letter avatars. Check small text, including labels and badges, against WCAG AA contrast. `displayName` is derived from `accountType`; no extra display-name column exists. MVP types are `MAIN` and `SAVINGS` only.
- **Quick-links slot:** render shortcuts only when their destination route exists and is implemented. When present, use `<nav aria-label="Quick links">`. The primary account's "View transactions" shortcut is present and targets `/customer/transactions?accountId=<id>`. Omit the panel entirely when there are no qualifying shortcuts. Do not render dead links or disabled "coming soon" items. Never include transfer, payment, statement, card or other out-of-scope actions without an approved requirements change.
- **Account number display and masking:** stored values are text and well-formed only when they match `^[0-9]{10}$` (exactly 10 ASCII digits, leading zeros allowed, no trimming or normalization). Only owner-scoped `GET /api/v1/accounts` returns `accountNumber`; malformed values produce null and `maskedNumber: "****"`. Transactions and all future dispute responses expose only `maskedNumber` (`****` plus the last four digits when well-formed; exactly `****` otherwise). The account feature has the sole named length constant `AccountNumberMask.ACCOUNT_NUMBER_LENGTH`, and V5 has one matching CHECK; later changes use one additive migration. `AccountNumberMask` in the account feature is the sole masking implementation. On malformed stored data it logs only account UUID and safe reason. No full number may appear in other endpoints, errors, logs, audit entries, URLs or search predicates. The backend alone validates the number and returns null for malformed stored values; the frontend does not validate it. Dashboard rows and the account selector current-account summary show the full number when non-null. No copy button, reveal toggle or other number interaction.

**Amendment (2026-10-11):** The shared account selector shows the full number when non-null in both its selected summary and every option card, using dashboard formatting. The number remains read-only with no copy/reveal interaction.
- **Loading:** stacked row skeletons while GET /accounts is in flight.
- **Empty state:** no account rows, no selector; display helpful copy (e.g. "No accounts yet. Your accounts will appear here when they are available.").
- **Error states:** 401 relies on the existing session-clearing flow. 503 → "Something went wrong. Please try again." with a manual retry button; no automatic retry. No polling, prefetching or auto-refresh.
- **No dispute CTA, statement download, card management or transfer action in this phase.**

### Screen: Transactions

- Route: `/customer/transactions?accountId=<uuid>`. Header label: `Transactions`.
- **Account selector (amendment, 2026-10-11):** The selector is the single current-account display; do not add a separate "Current account" card or "Select account" label above it. It is a shared, presentational component accepting `accounts` and URL-derived `selectedAccountId`, emitting `selectionChange`. The selected trigger and every option are compact cards with a Lucide account-type icon, display name, Primary badge when applicable, labelled account number, full `accountNumber` when non-null, available balance, and chevron. Use the same account-number and balance formatting as dashboard rows. The custom button/listbox supports Enter/Space, Arrow Up/Down, Home/End, Enter selection, Escape with focus returned, outside-click close and visible focus; at narrow widths retain a native `<select>` fallback. The listbox has `role="listbox"`, choices have `role="option"` and `aria-selected`; no "All accounts" option. Masking applies to transaction and dispute payload context only; Angular does not validate account numbers.
- **URL is the only selection state.** Opening `/customer/transactions` without `accountId`: the feature service loads GET /accounts, selects the primary account (fallback: first in response order if no primary is found), then rewrites the URL with `?accountId=<id>` using `history.replace`. Changing the selector updates `accountId` in the URL, resets pagination to page 1, retains other active filters, and uses push navigation so Back restores the previously selected account. Default-primary resolution alone uses replace; ordinary filter edits replace the current query state.
- **Not-owned accountId in URL (API returns 404/1003):** show an "Account not available" error state with a link that navigates to the default (primary/first) account. Do not redirect silently.
- **Stale-response protection:** cancel in-flight requests when the selector changes; discard any response that arrived after a newer request was issued.
- **Segmented direction control:** All / Money In / Money Out — planned as an accessible radio group or tablist with `aria-selected`. Bound to the `direction` query parameter; reflected on load and back/forward navigation; resets pagination to page 1 on change. Justified as a fast, keyboard-accessible way to apply the existing `direction` filter without extra chrome.
- **Month grouping:** optional presentational grouping of the current page's rows by calendar month in Africa/Johannesburg, shown only when sorted by `occurredAt`. It does not change API pagination, sort or filters; a month heading may repeat on the next page when a month spans pages. Justified as a scan aid for a chronological list, not as a copy of any external screen.
- **List polish amendment (2026-10-11):** Keep only Transaction, Date, Amount and Status columns; the selector already identifies the current account. Merchant is a link with reference below in smaller muted text. Transaction, Date and Amount headers are buttons with `aria-sort`, visible Lucide sort direction icons and descriptive accessible names. Clicking an active column flips direction; a new column uses Date DESC, Amount DESC or Transaction ASC. Reference sorting remains available to the API, without a UI control. Search, Direction and Status share the main control row; date/amount ranges are behind a collapsed Filters panel with an active-filter count, inline inverted-range/over-366-day messages and a Clear filters action that keeps `accountId`. Put rows per page beside the pagination controls. Hide radio indicators visually while preserving the accessible radio group and focus ring. Amounts keep their sign and use red for money out, green for money in, with no direction arrow. Status chips are sentence case and use distinct WCAG AA text/background tints. Month heading rows remain semantic and non-interactive. List and detail timestamps use the same `dd MMM yyyy, HH:mm` format in Africa/Johannesburg; month headings use `MMM yyyy`.
- Search merchant/reference; status, from/to dates, min/max ZAR amount, sort field/direction as allowlisted by the API. On narrow widths, advanced filters sit behind an accessible Filters control.
- Rows: merchant, date, signed amount (direction), status where useful; direction-based icons only, no merchant logos; tap/click to details.
- No dispute CTA, statement, receipt or download action on the list in this phase.
- Explicit 1-based pagination (default size 20/max 100), skeleton loading, empty filtered state and network retry.
- No merchant logos required.
- Envelope status is request success, not case status; read payload from data and numeric error_code. Collections render data.items and use data.total. Keep query controls in URL state without storing tokens/private notes; cancellation must prevent stale responses replacing newer searches.

### Screen: Transaction details

- Back link: returns to `/customer/transactions?accountId=<id>` preserving `accountId` and other allowlisted query parameters (direction, status, fromDate, toDate, page, size, sortBy, sortDirection, search). Further back from there leads to the account dashboard (`/customer`).
- Merchant, amount, direction, date/time (Africa/Johannesburg locale), reference, masked account, transaction status.
- Show the transaction's status and date/time in Africa/Johannesburg and its masked account context. Eligible completed outgoing transactions without a dispute show **Raise dispute**. Incoming credits explain that only payments leaving the account can be disputed. Do not imply all outgoing payments are necessarily fraudulent.

### Screen: Submit dispute

Route: `/customer/disputes/new?transactionId=<uuid>`. Reload transaction details and retain the safe transaction summary including masked account.
- Required reason dropdown (five values from BR-S04).
- Required description textarea, 2000-character counter and inline validation.
- Show the transaction summary beside the form on desktop and above it on mobile. Submit directly from the validated form using the shared primary/secondary buttons; there is no intermediate review screen.
- Disable submit while pending; maintain the same idempotency key for an unchanged retry.
- Show receipt/open confirmation; do not promise a refund. Customer history/detail remain deferred.
- The user supplied desktop and mobile screenshot references for the list expansion and dispute form. The form follows the third screenshot; the transaction list reveals the eligible “Raise dispute” action inline with a rotating chevron and an animated expansion. Merchant links continue to open transaction details. Masked account numbers remain in the summary.
- Success shows dispute reference/status and navigation to details.
- `409` duplicate: show existing dispute when safely accessible; `422` ineligible: explain; network failure: preserve form text.

## 5. Customer journey B — Track dispute

The later customer dispute list reuses the shared account selector, defaults to the primary account (or the first account in GET /accounts order if no primary exists), and updates the `accountId` resource selector when the customer switches accounts. Backend ownership is validated through transaction → account → customer.

```text
My Disputes → Active / Resolved (optional filter)
            → Select dispute → Status + customer-safe timeline
            → If terminal: decision + customer resolution message
```

- History includes active and resolved cases, newest first. Search case ID/submission description/transaction merchant/reference; combine status/reason/date filters and allowed sorting with 1-based pagination. Debounce search, reset page to 1 on filter/sort changes, preserve controls during reload and announce empty filtered results.
- Status labels: OPEN = Received; UNDER_REVIEW = Under review; ACCEPTED = Accepted; REJECTED = Rejected.
- Timeline is a **curated projection**: Submitted, Under review on claim, Returned to queue on release, Decision. Repeated claims/releases remain ordered by disputeVersion; generic release copy contains no internal reason. Do not expose claim/release notes, agent identities, or internal audit event details.
- For ACCEPTED, show: `Your dispute was accepted.` and the agent's customer-facing message. **Never say `Refund processed`**.
- For REJECTED, show: `Your dispute was not accepted.` and the customer-facing explanation.
- No reopening button in MVP.

## 6. Agent journey — Claim, investigate, resolve

```text
Login → Unassigned | My Cases | All Disputes
      → Select dispute → Transaction + customer explanation + audit
      → If OPEN: Claim
      → If assigned to me: Release (reason) OR Resolve
      → Resolve: Accept/Reject + internal notes + customer message
      → Confirm → Save → Status/audit update → Updated queue
```

### Screen: Agent queue
- Tabs: Unassigned / My Cases / All Disputes.
- Columns: case ID, merchant, amount, reason, status, created date, assignee where relevant.
- Search, status/reason/date filters and allowlisted sort controls match the collection API matrix; pagination is required (page 1, size 20 default/max 100).
- Claim may be from details (preferred MVP) to avoid hidden stale state.

### Screen: Agent dispute details
- Top: case ID, status, assignee and clear action area; version retained by client, with optional display.
- Main: transaction context, customer reason/description, internal event timeline.
- `OPEN`: Claim available to agents. My Cases includes terminal cases still assigned to me; filters intersect queue membership. Version display is optional technical context; ETag handling is approved: retain the exact strong detail tag and send If-Match on claim/release/resolve; store the new response tag after success.
- `UNDER_REVIEW` assigned to me: Release and Resolve enabled.
- Assigned to another agent: read-only.
- `ACCEPTED` / `REJECTED`: terminal, read-only.
- Resolve form: decision radio, mandatory internal notes, mandatory customer message, confirmation.
- Release form: mandatory internal reason, confirmation.
- If 412 stale, refetch detail/tag and preserve safe unsent text; if 409, show that current business state/assignment prevents the action and refresh. Missing 428 requires refetch; malformed 400 is a client contract error. Never automatically resend an agent mutation with a replaced tag. Terminal cases remain read-only with retained resolving agent visible only in agent views.

## 7. Component map

```text
app/
├── core/auth/               # auth service, interceptor, role guard
├── shared/
│   ├── components/
│   │   └── account-selector/  # shared presentational: inputs accounts/selectedAccountId, output selectionChange
│   └── ...                    # status badge, error/empty/loading, other reusables
├── features/customer/
│   ├── accounts/            # account dashboard (stacked rows), account service (owns GET /accounts)
│   ├── transactions/        # list (uses account-selector), filter, detail
│   └── disputes/            # submit form, history, customer detail/timeline
└── features/agent/
    └── disputes/            # queue, agent detail, claim/release/resolve forms
```

Use Angular standalone components, typed reactive forms, signals where appropriate, lazy feature routes, accessible controls, and a single API client per feature. Do not share customer and agent DTOs when their visibility differs. Account data fetching belongs in the accounts feature service, not in the shared `account-selector` component.

## 8. UI state checklist

| State | Customer | Agent |
|---|---|---|
| Loading | Skeleton list/detail, disabled submit | Skeleton queue/case |
| Empty | No transactions/disputes with helpful copy | No cases in queue |
| Invalid input | Inline reason/description errors | Inline notes/message errors |
| Network failure | Retry without losing form/key | Retry, preserve notes |
| 401 | Session expires → login | Session expires → login |
| 403 | Access denied | Access denied |
| 404 | Not found / no existence leak | Case not found |
| 409 | Duplicate/key conflict | Claim/state conflict → refresh |
| 422 | Non-completed transaction → explain | N/A |
| 428 | N/A | Refetch version |
| 429 / 503 | Honor Retry-After; keep unchanged creation key | Safe retry/refetch |
| 412 | N/A | Stale version → refresh |
| Success | OPEN confirmation | Updated status, toast and audit |
| Terminal | Read-only decision and message | Read-only resolved case |

## 9. Accessibility and responsive behaviour

- Customer: desktop-first content width with transaction table/list and optional adjacent details panel; tablet and mobile collapse to a single column with full-page details. Comfortable touch targets and no horizontal page overflow.
- Agent: desktop queue table and investigation workspace, tablet compressed columns, mobile stacked case cards; no essential action hidden behind hover.
- Labels associated with inputs; focus moved to first invalid field; success announced via accessible status region.
- Confirmation dialogs trap focus and restore it when closed.
- Times render in `Africa/Johannesburg`. ZAR amounts use the deterministic format `R 12 500.00` (ordinary spaces for thousands, period decimal separator, exactly two decimals); wire amounts remain decimal strings. This display format is an application choice and does not depend on locale-specific currency formatting.

## 10. PlantUML flow references

- [Customer journey](plantuml/customer-ui-flow.puml)
- [Agent journey](plantuml/agent-ui-flow.puml)
- [System context](plantuml/system-context.puml)
- [Dispute lifecycle](plantuml/dispute-state.puml)

## 11. Open UX decisions

MVP uses explicit 1-based pagination and generic returned-to-queue milestones. The account dashboard uses stacked account rows with an optional implemented-route quick-links panel; the transactions list uses the shared account-selector component; the URL is the only account-selection state. Remaining choices: exact wording of resolution disclosures, and whether transaction detail opens as a full page or drawer. Font family and key theme colours are now evidenced by supplied CSS; still verify precise section spacing, mobile navigation collapse and footer presentation; use our own icons with a browser-based visual audit. Validate these against API contracts before implementation.
