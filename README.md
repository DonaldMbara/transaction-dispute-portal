# Transaction Dispute Portal

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
│   │   └── shared/                      # UI primitives, formatters (ZAR currency), layout
│   └── src/tests/                       # Karma unit tests & Playwright E2E suites
├── docs/                                # Architectural & Domain Specifications
│   ├── business-rules.md                # Dispute lifecycle & domain invariants
│   └── design/                          # API contract, DB schema, session ADR, PlantUML
├── scripts/                             # Operational automation
│   └── demo-seed.sh                     # Interactive demo data provisioner
├── compose.yaml                         # Production-grade multi-service Compose definition
└── README.md                            # Primary project documentation
```

---

## Quick Start with Docker Compose

### Prerequisites
- Docker Engine and Docker Compose (v2.20+ with `up --wait` support).
- Ports `8081` (frontend / nginx proxy) and `5432` (PostgreSQL) available on `127.0.0.1`.

### 1. Launch the Stack
```sh
cp .env.example .env
# Optional: customize DB_USERNAME / DB_PASSWORD in .env
docker compose config --quiet
docker compose up --build -d --wait
```

The stack launches:
- **PostgreSQL 17** (published on `127.0.0.1:5432`)
- **Spring Boot 3.5 Backend** (running on port `8080` internally as a non-root user)
- **Nginx & Angular 20 Frontend** (published on `http://localhost:8081`, reverse-proxying `/api/` to backend)

### 2. Seed Demo Accounts & Synthetic Fixtures
Run the interactive demo script in a terminal:
```sh
./scripts/demo-seed.sh
```
This script provisions:
- Customer account: `demo.customer@example.test`
- Agent account: `demo.agent@example.test`
- Synthetic banking accounts, categorized transactions, and seeded disputes across `OPEN`, `UNDER_REVIEW`, `ACCEPTED`, and `REJECTED` states.

Open [http://localhost:8081](http://localhost:8081) to access the portal.

---

## Role Matrix & Capabilities

| Role | Routes & Operations | Security & Data Visibility |
|---|---|---|
| **CUSTOMER** | • `/customer/accounts`: View owned bank accounts and balances.<br>• `/customer/transactions`: Search & filter transactions by date, amount, account, direction.<br>• `/customer/disputes/new`: Submit dispute for completed outgoing (`OUT`) transactions.<br>• `/customer/disputes`: Track status (`OPEN`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`) and timeline. | Identity derived from server session. Unowned records return 404. Masked account numbers (`****6789`). Internal agent notes and agent identities are strictly withheld. |
| **AGENT** | • `/agent/disputes`: Triage dispute queue across tabs (`Unassigned`, `Mine`, `All`).<br>• `claim` / `release`: Manage case assignments.<br>• `resolve`: Accept or reject dispute with mandatory internal investigation notes and customer-facing message. | Requires AGENT role and active case assignment for resolution. Strong ETag optimistic locking prevents race conditions. |

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
export E2E_CUSTOMER_PASSWORD="YourPasswordHere"
npx playwright install chromium
npm run test:e2e
```

---

## API & OpenAPI Documentation

- **Base URL:** `/api/v1`
- **Response Envelope:** All responses follow a standardized JSON envelope:
  - Success: `{ "status": true, "data": { ... }, "error_code": 0 }`
  - Error: `{ "status": false, "data": null, "error_code": 1004, "error": { "type": "...", "title": "...", "detail": "..." } }`
- **Interactive Swagger UI:** Available in `local` development profile at [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html).

---

## Design Specifications & Reference Documents

- [Candidate Interview Preparation Guide](INTERVIEW_PREP.md) — Comprehensive technical review guide, architecture breakdown, trade-off analyses, and interview Q&A.
- [API Contract Specification](docs/design/api-contract.md) — Endpoint specifications, JSON envelopes, pagination, and error codes.
- [Session Architecture Decision](docs/design/session-architecture-decision.md) — In-depth rationale for Spring Session JDBC, HttpOnly cookies, and CSRF token handling.
- [Business Rules Document](docs/business-rules.md) — Domain invariants, dispute lifecycle transitions, and eligibility criteria.
- [Database Design & Schema](docs/design/database-design.md) — Schema breakdown, indexes, and locking semantics.
- [UI Flows & Design Tokens](docs/design/ui-flows.md) — Responsive UI layouts, design tokens, and user journeys.
- [PlantUML Architecture Diagrams](docs/design/plantuml/README.md) — System context, component architecture, sequence diagrams, and ERDs.
- [Figma Prototype](https://www.figma.com/proto/L9AXnbjX8li5ggpmH2pBnr/Transaction-Dispute?t=AuwTn2Eub8fVwpPY-0&scaling=min-zoom&content-scaling=fixed&page-id=0%3A1&node-id=3-5301) — Visual design and interaction prototype.
