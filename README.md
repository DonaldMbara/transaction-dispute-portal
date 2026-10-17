# Transaction Dispute Portal

[![Foundation CI](https://github.com/DonaldMbara/transaction-dispute-portal/actions/workflows/ci.yml/badge.svg)](https://github.com/DonaldMbara/transaction-dispute-portal/actions/workflows/ci.yml)

A Java 21, Spring Boot 3.5, and Angular 20 application for synthetic transaction dispute management. The portal provides an authenticated self-service experience for banking customers to review accounts and transactions, submit disputes with idempotency guarantees, and track lifecycle status changes. It also provides an operational workspace for dispute agents to triage, claim, release, investigate, and resolve disputes with optimistic concurrency locking.

This system is built as a production-minded modular monolith using synthetic banking data (it does not connect to real financial rails, move money, or issue actual refunds).

---

## Key Features & Architecture Highlights

- **Modular Monolith:** Clean feature-first encapsulation (`auth`, `account`, `transaction`, `dispute`, `idempotency`, `security`) with zero distributed network overhead.
- **Server-Side Session Authentication:** Opaque `HttpOnly`, `SameSite=Lax` cookies backed by Spring Session JDBC in PostgreSQL. Immediate session revocation upon password/role changes via `auth_version` tracking and a strict 30-minute server-side idle timeout.
- **CSRF Defense:** Session-bound masked token material (`X-CSRF-TOKEN`) required on all mutating API calls including login and logout.
- **Optimistic Concurrency Control:** Strong HTTP `ETag` and `If-Match` headers for agent case management (`claim`, `release`, `resolve`) preventing lost updates.
- **Durable Idempotency:** Dispute submissions use an `Idempotency-Key` header with atomic database persistence (`idempotency_records`) of the committed 201 response payload.
- **Strict Data Isolation:** Customer identity is derived exclusively from the authenticated server-side principal; unowned resource requests return `404 Not Found` to prevent ID enumeration.
- **Immutable Lifecycle Audit:** State transitions are committed atomically with versioned audit rows in `dispute_events`.
- **Modern Angular Frontend:** Standalone components, reactive Angular Signals for local state, RxJS streams, functional route guards, and accessible design inspired by Capitec web design tokens (`#2F70EF` primary blue).

---

## Repository Structure

```text
transaction-dispute-portal/
├── backend/                             # Java 21 & Spring Boot 3.5 Modular Monolith
│   ├── src/main/java/com/assessment/disputeportal/
│   │   ├── account/                     # Bank accounts, masking, CLI fixtures
│   │   ├── auth/                        # Session auth, PBKDF2 encoding, CLI admin
│   │   ├── common/                      # Unified JSON API envelopes & exceptions
│   │   ├── dispute/                     # Dispute domain, state machine, audit events
│   │   ├── idempotency/                 # Request deduplication & replay store
│   │   ├── security/                    # Spring Security, CSRF, rate limiter
│   │   └── transaction/                 # Transaction history & eligibility rules
│   └── src/test/java/                   # Integration, concurrency, & Testcontainers suites
├── frontend/                            # Angular 20 Standalone Application
│   ├── src/app/
│   │   ├── core/                        # Session & CSRF auth services, guards, interceptors
│   │   ├── features/                    # Feature slices: auth, customer, agent, welcome
│   │   │   └── **/*.spec.ts             # Colocated Karma unit tests
│   │   └── shared/                      # UI primitives, formatters (ZAR currency), layout
│   └── e2e/                             # Playwright browser journey test suites
├── docs/                                # Architectural & Domain Specifications
│   ├── business-rules.md                # Dispute lifecycle & domain invariants
│   ├── interview/                       # Deep-dive interview reference guides
│   └── design/                          # API contract, DB schema, session ADR, PlantUML
├── .kiro/                               # AI-Driven Specifications & Steering Artifacts
│   ├── specs/                           # Requirements, design documents, & task lists
│   └── steering/                        # Architectural constraints & tech stack guidelines
├── scripts/                             # Operational automation
│   └── demo-seed.sh                     # Interactive demo data provisioner
├── compose.yaml                         # Production-minded multi-service Compose definition
└── README.md                            # Primary project documentation
```

---

## Quick Start with Docker Compose

### Prerequisites
- Docker Engine and Docker Compose (v2.20+ with `up --wait` support).
- Ports `8081` (frontend / nginx proxy) and `5432` (PostgreSQL) available on `127.0.0.1`.
  - *Port Collision Note:* If a local PostgreSQL instance already occupies port `5432` on your host machine, set `POSTGRES_PORT=5433` in `.env` before starting.

### 1. Fresh-Clone Setup & Launch
```sh
git clone https://github.com/DonaldMbara/transaction-dispute-portal.git && cd transaction-dispute-portal
cp .env.example .env
docker compose up --build -d --wait
```

The stack launches:
- **PostgreSQL 17** (published on `127.0.0.1:5432` or custom `${POSTGRES_PORT}`)
- **Spring Boot 3.5 Backend** (running on port `8080` internally as a non-root user)
- **Nginx & Angular 20 Frontend** (published on `http://localhost:8081`, reverse-proxying `/api/` to backend)

### 2. Seed Demo Accounts & Synthetic Fixtures
Run the interactive demo script in your terminal:
```sh
./scripts/demo-seed.sh
```

#### Demo Credentials & Password Guidance:
- **Customer Email:** `demo.customer@example.test` (press Enter to accept default)
- **Agent Email:** `demo.agent@example.test` (press Enter to accept default)
- **Password Policy:** Minimum 8 characters, UTF-8 strings up to 128 characters (e.g. `Password123!`).
- **Prompt Flow:** The CLI prompts you to enter and confirm the password for each account via masked console prompts, generates synthetic accounts and transaction history, and seeds disputes across `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, and `REJECTED` states.

Open [http://localhost:8081](http://localhost:8081) to log in as either role.
OpenAPI / Swagger UI: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html).

---

## Role Matrix & Capabilities

| Role | Routes & Operations | Security & Data Visibility |
|---|---|---|
| **CUSTOMER** | • `/customer/accounts`: View owned bank accounts and balances.<br>• `/customer/transactions`: Search & filter transactions by date, amount, account, direction.<br>• `/customer/disputes/new`: Submit dispute for completed outgoing (`OUT`) transactions.<br>• `/customer/disputes`: Track status (`OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`) and timeline. | Identity derived from server session. Unowned records return 404. Full account number visible on personal dashboard; masked (`****6789`) elsewhere. Internal agent notes and agent identities are strictly withheld. |
| **AGENT** | • `/agent/disputes`: Triage dispute queue across tabs (`Unassigned`, `Mine`, `All`).<br>• `claim` / `release`: Manage case assignments.<br>• `resolve`: Accept or reject dispute with mandatory internal investigation notes and customer-facing message. | Requires AGENT role and active case assignment for resolution. Strong ETag optimistic locking prevents race conditions. Customer account numbers are strictly masked (`****6789`). |

---

## Architectural Decisions & Engineering Trade-offs

### 1. Modular Monolith vs. Distributed Microservices
- **Decision:** Encapsulate domain contexts (`auth`, `account`, `transaction`, `dispute`) as decoupled modules within a single Spring Boot application rather than independent microservices.
- **Trade-off:** Eliminates distributed transaction complexity (e.g., 2PC or Saga patterns), network latency, and service mesh operational overhead. Module interactions occur via clean port interfaces (e.g., `DisputeTransactionPort`), allowing future extraction into standalone microservices if organizational boundaries require it.

### 2. Spring Session JDBC (PostgreSQL) vs. Redis
- **Decision:** Persist HTTP sessions and CSRF tokens in PostgreSQL via `spring-session-jdbc`.
- **Trade-off:** Eliminates dual-write consistency hazards between an external Redis cache and PostgreSQL. Enables atomic transactions where user session state, idempotency records, and business audits commit together. Instant global session revocation across all devices is achieved in $O(1)$ time by incrementing `app_users.auth_version`.

### 3. Optimistic Concurrency Control (`ETag` / `If-Match`) for Case Management
- **Decision:** Agent triage state transitions (`claim`, `release`, `resolve`) require HTTP `If-Match` headers matching the dispute's current `version`.
- **Trade-off:** Avoids long-lived database row locks and deadlocks during agent investigation. If two agents attempt to claim or resolve the same dispute concurrently, the second request fails gracefully with `412 Precondition Failed` without corrupting state.

### 4. Durable Idempotency with Atomic Payload Persistence
- **Decision:** Mutating dispute submissions require an `Idempotency-Key` header stored in `idempotency_records` with a SHA-256 request payload hash.
- **Trade-off:** If a client retries due to network timeout, the backend detects the duplicate key and replays the exact stored `201 Created` JSON envelope without re-executing business logic or creating duplicate disputes. Mismatched payloads return `409 Conflict`.

### 5. Domain Assumptions & Data Isolation
- **Synthetic Rails:** Transaction and dispute records operate on synthetic banking data; financial settlement rails and ACH transfers are out of scope.
- **Eligibility Invariants:** Transactions are eligible for dispute if and only if: `direction == OUT`, `status == COMPLETED`, transaction date $\le$ 90 days, and no existing dispute exists for that transaction.
- **Zero-Trust Principal Derivation:** Customer IDs are never accepted from query parameters or request bodies on customer routes; they are derived exclusively from the authenticated session principal. Unowned resource queries return `404 Not Found` to prevent identifier enumeration.

### 6. Scaling Boundaries & Future Roadmap
- **Transactional Outbox Pattern:** For asynchronous event publishing (e.g. notifying external CRM/email systems on dispute resolution) via Kafka/RabbitMQ without dual-write risks.
- **Read Replicas & Connection Pooling:** Routing read-heavy operations (`GET /transactions`, `GET /agent/disputes`) to PostgreSQL read replicas using Spring's `AbstractRoutingDataSource`.
- **Distributed Observability:** Adding Micrometer / OpenTelemetry tracing across HTTP requests, database transactions, and background workers.

---

## Developer Guide & CLI Tools

### Non-Web Operator Commands
Account provisioning and test fixture generation are explicit CLI operations executed via the `demo-cli` profile:

```sh
# Provision a new account with masked password input:
docker compose --profile demo run --rm --no-deps -it demo-cli account-admin create --email user@example.test --role CUSTOMER --enabled true

# Generate synthetic bank accounts for a customer:
docker compose --profile demo run --rm --no-deps -T demo-cli account-fixture --email user@example.test

# Generate synthetic transaction history for a customer:
docker compose --profile demo run --rm --no-deps -T demo-cli transaction-fixture --email user@example.test

# Manage existing account state:
docker compose --profile demo run --rm --no-deps -it demo-cli account-admin disable --id <UUID> --version <V>
docker compose --profile demo run --rm --no-deps -it demo-cli account-admin replace-password --id <UUID> --version <V>
```

---

## Build, Test & Quality Verification

### Backend Verification (Java 21 + Maven)
Run unit tests, integration tests, Spotless formatting, and isolated PostgreSQL Testcontainers:
```sh
cd backend
mvn --batch-mode clean verify
```

### Frontend Verification (Node 22 + Angular CLI)
Run ESLint, production dependency audit, production build, and Karma unit tests in headless Chrome:
```sh
cd frontend
npm ci
npm run lint
npm audit --omit=dev --audit-level=high
npm run build
npm run test:ci
```

### End-to-End Testing (Playwright)
Run the full browser journey (login, account view, transaction selection, dispute submission, history verification) against a running stack:
```sh
cd frontend
export E2E_BASE_URL="http://localhost:8081"
export E2E_CUSTOMER_EMAIL="demo.customer@example.test"
export E2E_CUSTOMER_PASSWORD="YourDemoPassword"
npx playwright install chromium
npm run test:e2e
```

---

## API & OpenAPI Documentation

- **Base URL:** `/api/v1`
- **Response Envelope:** All responses follow a standardized JSON envelope:
  - Success: `{ "status": true, "data": { ... }, "error_code": 0 }`
  - Error: `{ "status": false, "data": null, "error_code": 1004, "error": { "type": "...", "title": "...", "detail": "..." } }`
- **Interactive Swagger UI:** Available in `local` and `test` profiles at [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) (or redirect alias [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)).
- **OpenAPI v3 JSON Specification:** [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs).
- *Note:* In Docker Compose, Nginx automatically proxies Swagger and OpenAPI endpoints directly to Spring Boot without requiring port 8080 exposure. In `local` profile mode, these endpoints bypass session authentication (`permitAll()`) for friction-free API inspection.

---

## Troubleshooting & Common Issues

### Port 5432 Host Collision (`bind: address already in use`)
If a local PostgreSQL instance is already bound to port 5432 on your host machine:
- **Option A (Remap Published Port - Recommended):** Set `POSTGRES_PORT=5433` in `.env` and run `docker compose up -d`. (Internal container networking remains unaffected).
- **Option B (Stop Host Service):** Run `sudo systemctl stop postgresql` (or `sudo service postgresql stop`) and relaunch.

### Database Authentication Failure on Startup (`FATAL: password authentication failed`)
PostgreSQL initializes database credentials only on the very first startup of an empty data volume. If credentials in `.env` were configured or changed after an initial startup attempt:
```sh
# Purge stale volumes and reinitialize cleanly:
docker compose down --volumes
docker compose up --build -d --wait
./scripts/demo-seed.sh
```

### Complete Environment Reset to Clean Slate
To reset the portal back to a completely clean baseline with fresh seeded demo fixtures:
```sh
docker compose down --volumes && docker compose up --build -d --wait && ./scripts/demo-seed.sh
```

---

## Design Specifications & Reference Documents

- [AI-Driven Specifications (.kiro/)](.kiro/specs/) — Spec-driven engineering artifacts, requirements, system design, and verification tasks.
- [API Contract Specification](docs/design/api-contract.md) — Endpoint specifications, JSON envelopes, pagination, and error codes.
- [Session Architecture Decision](docs/design/session-architecture-decision.md) — In-depth rationale for Spring Session JDBC, HttpOnly cookies, and CSRF token handling.
- [Business Rules Document](docs/business-rules.md) — Domain invariants, dispute lifecycle transitions, and eligibility criteria.
- [Database Design & Schema](docs/design/database-design.md) — Schema breakdown, indexes, and locking semantics.
- [UI Flows & Design Tokens](docs/design/ui-flows.md) — Responsive UI layouts, design tokens, and user journeys.
- [PlantUML Architecture Diagrams](docs/design/plantuml/README.md) — System context, component architecture, sequence diagrams, and ERDs.
- [Figma Prototype](https://www.figma.com/proto/L9AXnbjX8li5ggpmH2pBnr/Transaction-Dispute?t=AuwTn2Eub8fVwpPY-0&scaling=min-zoom&content-scaling=fixed&page-id=0%3A1&node-id=3-5301) — Visual design and interaction prototype.
