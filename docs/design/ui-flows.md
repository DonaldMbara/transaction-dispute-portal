# UI Flows — Transaction Dispute Portal

**Status:** Approved design v0.3 · **Date:** 2026-10-10  
**Design:** Capitec public website as the primary visual reference, not an official Capitec product. **Desktop-first web portal**, responsive at tablet and mobile sizes for both customers and agents.

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

- Replace public marketing mega-navigation with a compact desktop header containing project identity, Transactions / My Disputes (customer) or Agent Queue / My Cases (agent), account menu and Sign out.
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

Session policy: PostgreSQL-backed server-side sessions with opaque HttpOnly cookies, Secure and SameSite=Lax in production, and 30-minute server-enforced idle timeout. Reload may restore a valid session; expiry/sign-out leads to login. Clear private cached state and preserve only validated role-appropriate navigation. Login/logout and unsafe requests require CSRF; session restoration and CSRF transport remain pending authentication design. No public registration or bearer-token interceptor; provisioning is admin-controlled. Unsent text is preserved only within the authenticated session where safe.

## 3. Information architecture

```text
/login
/customer/transactions
/customer/transactions/:id
/customer/disputes
/customer/disputes/:id
/agent/disputes?queue=unassigned|mine|all
/agent/disputes/:id
```

Authenticated shell displays role-specific navigation. Angular guards help UX; Spring Security remains authoritative.

## 4. Customer journey A — Find and dispute a transaction

```text
Login → Account / Transactions → All | Money In | Money Out
      → Select transaction → Transaction details
      → [Eligible?]
         No, PENDING/FAILED → explain ineligibility
         No, already disputed → View existing dispute
         Yes → Dispute this transaction
             → Reason select + description
             → Review/confirm → Submit with Idempotency-Key
             → Success → Dispute details (OPEN) → My Disputes
```

### Screen: Transactions
- Header: `Transactions`, masked account identifier; aggregate all owned accounts with masked account context; no account selector in MVP.
- Segmented filters: All / Money In / Money Out. Search merchant/reference; status, from/to dates, min/max ZAR amount, sort field/direction as allowlisted by the API. Advanced filters may be collapsible but keyboard/mobile accessible.
- Rows: merchant, date, signed amount (direction), status where useful; tap/click to details.
- Explicit 1-based pagination (default size 20/max 100), skeleton loading, empty filtered state and network retry.
- No merchant logos required.
- Envelope status is request success, not case status; read payload from data and numeric error_code. Collections render data.items and use data.total. Keep query controls in URL state without storing tokens/private notes; cancellation must prevent stale responses replacing newer searches.

### Screen: Transaction details
- Merchant, amount, direction, date/time, reference, masked account, transaction status.
- Eligible: primary `Dispute this transaction` CTA.
- Already disputed: `View dispute` CTA.
- Pending/failed: disabled dispute CTA or explanatory text, not a silent dead end.
- Do not imply all outgoing payments are necessarily fraudulent.

### Screen: Submit dispute
- Transaction summary remains visible.
- Required reason dropdown (five values from BR-S04).
- Required description textarea; character counter and inline errors.
- Review/confirmation before submit (lightweight; may be one-step confirmation modal).
- Disable submit while pending; maintain same idempotency key on network retry.
- Success shows dispute reference/status and navigation to details.
- `409` duplicate: show existing dispute when safely accessible; `422` ineligible: explain; network failure: preserve form text.

## 5. Customer journey B — Track dispute

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
├── shared/                  # app shell, status badge, error/empty/loading components
├── features/customer/
│   ├── transactions/        # list, filter, detail
│   └── disputes/            # submit form, history, customer detail/timeline
└── features/agent/
    └── disputes/            # queue, agent detail, claim/release/resolve forms
```

Use Angular standalone components, typed reactive forms, signals where appropriate, lazy feature routes, accessible controls, and a single API client per feature. Do not share customer and agent DTOs when their visibility differs.

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
- Dates and ZAR amounts localized to South African locale where supported.

## 10. PlantUML flow references

- [Customer journey](plantuml/customer-ui-flow.puml)
- [Agent journey](plantuml/agent-ui-flow.puml)
- [System context](plantuml/system-context.puml)
- [Dispute lifecycle](plantuml/dispute-state.puml)

## 11. Open UX decisions

MVP uses explicit 1-based pagination, aggregate owned accounts and generic returned-to-queue milestones. Remaining choices: exact wording of resolution disclosures, and whether transaction detail opens as a full page or drawer. Font family and key theme colours are now evidenced by supplied CSS; still verify precise section spacing, mobile navigation collapse and footer presentation; use our own icons with a browser-based visual audit. Validate these against API contracts before implementation.
