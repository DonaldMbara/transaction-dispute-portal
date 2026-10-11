# PlantUML Diagram Catalogue

**Status:** Customer Accounts alignment v0.8 — 2026-10-11. Diagrams distinguish implemented authentication from approved future business design; no deployed/production-ready claim.

## Architecture and data

- [System context](system-context.puml) — actors and application boundary.
- [Component architecture](components.puml) — Angular + Spring Boot modular monolith.
- [Local deployment](container-deployment.puml) — Docker Compose topology (reverse proxy proposed).
- [Data model](data-model.puml) — approved business tables plus implemented V2–V4 authentication-account/session/activity schema.

## Domain and API workflows

- [Dispute lifecycle](dispute-state.puml) — allowed transitions and terminal states.
- [Login](login-auth.puml) — implemented login/identity/logout, masked CSRF, guarded activity and persistence boundaries.
- [Submit dispute](submit-dispute.puml) — ownership, idempotency, uniqueness, atomic audit.
- [Claim and release](claim-release.puml) — assignment, conflict handling, audit.
- [Resolve dispute](resolve-dispute.puml) — agent ownership, required notes and decision.
- [Customer history](customer-history.puml) — customer-scoped queries and safe timeline.

## UI flows

- [Frontend navigation](frontend-navigation.puml) — Angular routes and responsive shell.
- [Customer journey](customer-ui-flow.puml) — account dashboard → per-account transactions (with account selector); dispute actions belong to the later dispute-submission feature.
- [Agent journey](agent-ui-flow.puml) — queue → claim → investigate → resolve/release.

## Approved decisions and deferred implementation details

1. Strong ETag / If-Match on all agent mutations is approved: HTTP 428 missing, 400 malformed, 412 stale/lost write race, 409 matching-version business conflict. Canonical tag representation and action-URI semantics are in the API contract.
2. Same-origin /api/v1 requests use the foundation Angular development proxy; application container/reverse-proxy deployment is deferred.
3. Completed incoming transactions remain eligible. Terminal resolving-agent/assigned_at retention is approved; customer views omit agent identity.
4. Spring Security / Spring Session JDBC in PostgreSQL, opaque HttpOnly session cookies (Secure/SameSite=Lax in production), 30-minute idle expiry, CSRF including login/logout and admin-controlled provisioning are approved. No public registration. One modular Spring Boot application; no MVP gateway/microservices. Feature-spec approval still precedes tasks/code.
5. Feature order: project-foundation → authentication → **customer-accounts** → customer-transactions → dispute-submission → customer-dispute-history → agent-workflow → integration-hardening. The `customer-accounts` spec owns the `accounts` table (V5 migration), GET /api/v1/accounts, the account dashboard UI, the shared account-selector component, and the account fixture command. The `customer-transactions` spec then adds `bank_transactions` (V6 migration) and the transactions list with accountId selector support. Customer Transactions ends at transaction detail and has no dispute CTA.

## Keeping diagrams useful

- Update diagrams when business rules, API contracts, DB schema or UI flows change.
- Graph-layout diagrams select the built-in Smetana renderer, so Graphviz is not required. Sequence diagrams use participant-spanning notes. Validate with `java -Djava.awt.headless=true -jar <plantuml.jar> -tsvg -o <output-dir> "docs/design/plantuml/*.puml"`; compiler exit and artifacts must both be checked.
- Round-two API envelopes, numeric errors, query semantics and 1-based paging are defined in [API contract](../api-contract.md); current implemented session decisions and operating assumptions in [session architecture decision](../session-architecture-decision.md).
- Use PlantUML sources as canonical; rendered PNG/SVG can be generated in CI later.
- Do not use these diagrams to imply Capitec affiliation. The visual reference applies to styling only.
