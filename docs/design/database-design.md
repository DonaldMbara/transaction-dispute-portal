# Database Design — Transaction Dispute Portal

**Status:** Current architecture alignment v0.4 · **Date:** 2026-10-10
**Source:** `docs/business-rules.md` v0.3. The approved design baseline precedes separately approved feature specifications and Flyway implementation.

## 1. Goals and boundaries

PostgreSQL also persists Spring Session JDBC server-side sessions and is the single source of truth for authenticated users, customer accounts, seeded read-only transactions, disputes, immutable lifecycle events, and request idempotency. The application is a Spring Boot modular monolith; no banking ledger, payment reversal, or settlement tables are in scope.

See [PlantUML ER model](plantuml/data-model.puml), [state machine](plantuml/dispute-state.puml), and [API contract](api-contract.md).

## 2. Tables

### `app_users`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `email` | VARCHAR(254) | NOT NULL, UNIQUE; normalized lowercase |
| `password_hash` | VARCHAR(255) | NOT NULL; versioned PBKDF2-HMAC-SHA256 (`{pbkdf2-sha256-v1}`), per approved authentication design |
| `role` | VARCHAR(16) | NOT NULL, CHECK `CUSTOMER` or `AGENT` |
| `auth_version` | BIGINT | NOT NULL DEFAULT 0, nonnegative; increments atomically on accepted status/role/password changes |
| `enabled` | BOOLEAN | NOT NULL DEFAULT TRUE |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Password policy: random 16-byte salt, 600,000 iterations and a 256-bit derived key, benchmarked at approximately 0.57-second encode/verify median on the assessment hardware; rerun for deployment hardware. Provisioning/replacement accepts 8–128 Unicode code points and login verification accepts non-empty well-formed input up to 128; both preserve exact input without trimming or truncation. No plaintext fallback; parameter changes require a new identifier. See [authentication design §3.2](../../.kiro/specs/authentication-rbac/design.md#32-accepted-versioned-password-encoder--benchmark-required). Historical V1 remains unchanged; additive V2–V4 implement the approved account/session changes.

### `accounts`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `customer_id` | UUID | NOT NULL FK → `app_users.id` |
| `account_number` | VARCHAR(32) | NOT NULL, UNIQUE; masked in API |
| `account_type` | VARCHAR(32) | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

A service invariant ensures `customer_id` refers to a CUSTOMER user; the FK alone does not enforce role.

### `bank_transactions`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `account_id` | UUID | NOT NULL FK → `accounts.id` |
| `reference` | VARCHAR(80) | NOT NULL, UNIQUE |
| `merchant` | VARCHAR(160) | NOT NULL |
| `amount` | NUMERIC(19,2) | NOT NULL, CHECK `amount > 0` |
| `direction` | VARCHAR(8) | NOT NULL, CHECK `IN` or `OUT` |
| `currency` | CHAR(3) | NOT NULL DEFAULT 'ZAR' |
| `status` | VARCHAR(16) | NOT NULL, CHECK `PENDING`, `COMPLETED`, `FAILED` |
| `occurred_at` | TIMESTAMPTZ | NOT NULL |

`direction` supports Money In / Money Out filters; amounts are positive magnitudes. Completed incoming and outgoing transactions remain eligible under current business rules; restricting incoming transactions would require an explicit approved rule change.

### `disputes`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `transaction_id` | UUID | NOT NULL FK → `bank_transactions.id`, UNIQUE |
| `reason` | VARCHAR(64) | NOT NULL; supported reason enum |
| `description` | VARCHAR(2000) | NOT NULL; trimmed nonblank via validation |
| `status` | VARCHAR(16) | NOT NULL; `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED` |
| `assigned_agent_id` | UUID | NULL FK → `app_users.id` |
| `assigned_at` | TIMESTAMPTZ | NULL |
| `internal_resolution_notes` | VARCHAR(4000) | NULL |
| `customer_resolution_message` | VARCHAR(2000) | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL |
| `resolved_at` | TIMESTAMPTZ | NULL |
| `version` | BIGINT | NOT NULL DEFAULT 0; JPA `@Version` |

**Approved state constraints (SQL expression details to be verified in feature design):** `OPEN` requires no assigned agent, no assigned_at, no resolved_at; `UNDER_REVIEW` requires agent and assigned_at, no resolved_at; terminal states require agent, resolved_at and both resolution texts. The resolving agent and assigned_at are retained after resolution for accountability (approved); terminal assignment and texts are immutable through application operations. Validate role and lifecycle rules in domain/application code, not only DB constraints.

**Snapshot decision:** no transaction snapshot initially because seeded transactions are read-only; revisit if they become mutable.

### `dispute_events`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `dispute_id` | UUID | NOT NULL FK → `disputes.id` |
| `actor_id` | UUID | NOT NULL FK → `app_users.id` |
| `dispute_version` | BIGINT | NOT NULL; UNIQUE(dispute_id, dispute_version); CREATED uses version 0 |
| `event_type` | VARCHAR(32) | NOT NULL: `CREATED`, `CLAIMED`, `RELEASED`, `RESOLVED_ACCEPTED`, `RESOLVED_REJECTED` |
| `from_status` | VARCHAR(16) | NULL for CREATED |
| `to_status` | VARCHAR(16) | NOT NULL |
| `previous_agent_id` | UUID | NULL FK → `app_users.id` |
| `new_agent_id` | UUID | NULL FK → `app_users.id` |
| `internal_notes` | TEXT | NULL; never in customer DTO |
| `customer_message` | TEXT | NULL; only for resolution |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Order events by dispute_version ASC, never rely on wall-clock timestamps/UUIDs for causal order across replicas. Claim/release/resolve increment dispute.version and append exactly one event with the resulting version; CREATED records 0. Returned-to-queue customer milestones are generic and expose no release reason.

Append-only application policy: no update/delete repository operations or HTTP endpoints. State update and event insertion share a single database transaction. Customer timeline is a **projection**, not a direct serialization of event rows; internal claim/release metadata is hidden.

### `idempotency_records`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL FK → `app_users.id` |
| `operation` | VARCHAR(64) | NOT NULL |
| `idempotency_key` | VARCHAR(128) | NOT NULL |
| `request_hash` | CHAR(64) | NOT NULL; SHA-256 canonical payload |
| `response_status` | INTEGER | NOT NULL for committed records |
| `response_body` | JSONB | NOT NULL for committed records |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| — | — | UNIQUE (`user_id`, `operation`, `idempotency_key`) |

Store only completed successful custom envelopes in response_body, with response_status 201, in the dispute+CREATED-event transaction. Derive replay Location from response_body.data.id; issue a fresh X-Correlation-ID, never replay request-specific headers. Retain successful idempotency records for the assessment lifetime; a future cleanup policy must explicitly limit replay guarantees before deletion.

Submission coordination design (one shared PostgreSQL database, no instance-local mutex):
1. Authenticate CUSTOMER, validate/normalize request and fingerprint it. Read completed key by user+operation+key; compare hash and replay or return 409/2004.
2. For a new key, start a transaction and lock the target bank_transactions row with SELECT FOR UPDATE using the ownership-scoped account/customer join. Missing/non-owned returns 404. Re-read the idempotency key after acquiring the lock, since an identical request may have completed while this caller waited.
3. If still new, verify COMPLETED/no dispute and insert version-0 OPEN dispute, version-0 CREATED event and completed response record. Commit before returning 201. The locked transaction row serializes different-key attempts for the same transaction; the unique dispute FK is the final defense. Locks also prevent future transaction updates racing eligibility while held.
4. Same-key attempts for different transaction IDs lock different rows and can collide at idempotency uniqueness. Roll back the losing transaction (including its dispute/event) and read the committed winner in a fresh transaction; same fingerprint replays, different fingerprint returns 409/2004. Do not query in a transaction aborted by a constraint failure. Classify only the named relevant constraints, not every integrity exception as duplicate.
5. A dispute unique failure similarly rolls back, checks for a committed matching idempotency record first, then returns 409/2003 for an owned already-disputed transaction. Do not overwrite or delete the winning key.
6. Bound lock/statement wait and recovery; proposed initial 5-second lock wait, one post-rollback lookup, no unbounded automatic retries. If coordination cannot complete return 503/3002 with Retry-After: 1; Angular retries the unchanged request with the same key. Feature specs must verify actual timeout settings and no orphan state.

Checks and inserts happen on the primary database; replica-lag reads cannot provide replay coordination. Concurrent different-key/same-transaction requests yield one creation and a duplicate; identical-key/identical-request yields creation plus replay. Test the same-key/different-transaction case as well as the common identical request race.

## 3. Relationships and query paths

- Customer → accounts → transactions → optional dispute → events.
- Agents → assigned disputes; agents and customers → audit actor.
- Customer ownership is checked through the account/customer join **on every customer read and write**.
- A non-owned customer resource returns 404, avoiding existence leaks.

## 4. Proposed indexes

```sql
CREATE INDEX idx_accounts_customer ON accounts(customer_id);
CREATE INDEX idx_transactions_account_time ON bank_transactions(account_id, occurred_at DESC, id DESC);
CREATE INDEX idx_disputes_status_created ON disputes(status, created_at DESC, id DESC);
CREATE INDEX idx_disputes_agent_status_updated ON disputes(assigned_agent_id, status, updated_at DESC, id DESC);
-- UNIQUE(dispute_id, dispute_version) supplies causal history lookup index.
CREATE INDEX idx_disputes_created ON disputes(created_at DESC, id DESC);
CREATE INDEX idx_transactions_time ON bank_transactions(occurred_at DESC, id DESC);
```

Uniqueness constraints automatically create indexes; do not duplicate them. These are starting candidates, not claims of optimized query plans. Transaction account/date handles ownership joins; global time and dispute-created indexes support unfiltered chronological lists. Assignee/status/updated and status/created support agent queues. A reason/date or amount index should be added only if representative EXPLAIN ANALYZE shows a need; arbitrary combinations need not each receive an index.

Case-insensitive literal substring search uses parameterized escaped LIKE over allowed fields and joins. Normal B-tree indexes do not promise acceleration for leading-wildcard searches; bounded assessment datasets can use filtered scans. Verify latency and count queries with realistic owned-account and queue data; consider PostgreSQL trigram indexing only for measured need and a documented extension decision. No Elasticsearch. Counts use the same ownership and filter predicates as items. Use Africa/Johannesburg date ranges converted to UTC; compare NUMERIC amounts without floating point. Sort fields map through an allowlist. No full account number/private-note search.

## 5. Concurrency and transaction boundaries

- `disputes.version` is updated on claim, release, and resolution.
- A competing claim cannot overwrite an existing assignment. Handle optimistic-lock/conditional-update failures as conflict.
- A mutation writes the updated dispute and its audit event atomically; failures roll back both.
- Submission inserts dispute, CREATED event, and committed idempotency response atomically. The unique `transaction_id` is the final duplicate defense.
- Strong ETag/If-Match is approved for all agent mutations: 428 missing, 400 malformed, 412 stale/lost guarded-write race, 409 current-version business conflict. Include expected version, state and required assignee in conditional writes; zero rows or optimistic-lock failure rolls back the whole transaction. Never append audit independently or return success before commit.

## 6. Migration and seed plan

- Applied foundation `V1__create_app_users.sql` is immutable. Remaining business tables belong to later owning feature migrations; do not recreate app_users or renumber the applied V1.
- Flyway V2 adds auth_version and canonical-email constraints/preflight; V3 creates the pinned Session 3.5.7 PostgreSQL schema; V4 creates auth_session_activity. No seeded account or applied-migration rewrite.
- spring_session: CHAR(36) primary_id and unique session_id; epoch-ms creation_time/last_access_time/expiry_time, max_inactive_interval seconds, nullable principal_name; expiry/principal indexes. spring_session_attributes: primary(session_primary_id,attribute_name), BYTEA serialized values, parent FK ON DELETE CASCADE.
- auth_session_activity: primary session_primary_id FK to session parent and account_id UUID FK to app_users, both cascading; nonnegative auth_version and last_authenticated_activity_at BIGINT; account/time indexes. Account/version/deadline checks are authoritative even before cleanup; activity touches never insert or modify framework-managed session columns.
- Minimal serializable SessionPrincipal contains UUID with fixed serialVersionUID; compatible application releases share schema/serialization/cookie configuration. Controlled DB access is required for Java serialized session attributes.
- Framework cleanup SESSION_CLEANUP_CRON and activity cleanup AUTH_ACTIVITY_CLEANUP_CRON default to `0 * * * * *`. Activity cleanup deletes expired authenticated parent sessions, preserving unexpired anonymous CSRF sessions; cascades remove attributes/metadata. Request-time expiry never waits for cleanup.
- Account provisioning is admin-controlled; no public registration. Provisioning is explicitly invoked non-web masked-console maintenance; never automatic development startup accounts. Preserve synthetic read-only banking data; no plaintext credentials or demo accounts in production configuration.
- Avoid destructive Flyway changes; new migration choices require test database verification.

## 7. Business-rule traceability

| Rule | Enforcement |
|---|---|
| BR-A02 | Account/customer join + ownership tests |
| BR-T02, BR-S01 | Transaction status validation |
| BR-S02 | UNIQUE `disputes.transaction_id` |
| BR-S05, BR-H02 | One `@Transactional` write boundary |
| BR-S06 | Scoped idempotency uniqueness + replay |
| BR-L01–L07 | Domain transition methods + CHECKs |
| BR-L09 | `@Version` and HTTP concurrency contract |
| BR-H03–H04 | Separate DTO projections and internal/customer messages |

## 8. Approved baseline and feature-design details

API design fixes description 2000/internal notes 4000/customer message 2000/release reason 2000, decimal-string serialization and assessment-lifetime idempotency retention. Current rules still allow completed IN and OUT. Retained terminal assignee and strong ETag/If-Match are approved; there are no remaining blocking design decisions. Relevant feature specs must verify concurrency and customer-field redaction before implementation. Confirm SQL CHECK expressions, timestamp precision and database timeout values in feature design; terminal texts must be nonblank, and release clears both agent and assigned_at.
