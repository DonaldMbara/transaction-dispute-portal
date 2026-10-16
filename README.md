# Transaction Dispute Portal

A Java 21, Spring Boot and Angular application for synthetic transaction disputes. Customers can review their accounts and transactions, submit disputes, and follow status changes. Agents can work an assigned queue, claim or release cases, and resolve disputes. This project uses synthetic data only and does not connect to a bank, move money, or issue refunds.

## Stack and prerequisites

- Docker Engine and Docker Compose with `up --wait` support (recommended for running the full app).
- Java 21 and Maven 3.9+ for local backend development and verification.
- Node 22.23.3 from [`frontend/.nvmrc`](frontend/.nvmrc) for local frontend development and verification.
- Chrome or Chromium for the Angular Karma tests.

## Run the application with Compose

```sh
cp .env.example .env
# Edit .env and set DB_USERNAME and DB_PASSWORD for your local database.
docker compose config --quiet
docker compose up --build -d --wait
```

Open [http://localhost:8081](http://localhost:8081). The stack runs PostgreSQL, the Spring Boot backend, and an nginx frontend that serves the Angular app and proxies `/api/` to the backend. The backend runs as a non-root user. No application accounts or fixtures are created during normal startup.

PostgreSQL is published on `127.0.0.1:5432`; the frontend is published on `127.0.0.1:8081`. If either port is busy, set `POSTGRES_PORT` or `PORTAL_PORT` in `.env` to an available port. The backend connects to PostgreSQL through the Compose network, independent of the published host port. `.env` is ignored by Git; keep real credentials out of source files and command history.

For a browser demo with sample accounts, transactions, and disputes, follow the [opt-in demo data instructions](docs/operations/demo-data.md). The script requires a terminal and creates demo-only accounts only when explicitly invoked.

## Build and test

Run from the repository root:

```sh
cd backend
mvn --batch-mode clean verify
```

The backend suite uses isolated PostgreSQL Testcontainers and does not use the Compose database or `.env`. `verify` also checks Java formatting with Spotless.

In another terminal:

```sh
cd frontend
npm ci
npm run build
npm run lint
npm audit --omit=dev --audit-level=high
npm run test:ci
```

The frontend test command runs Karma/Jasmine in ChromeHeadless. Set `CHROME_BIN` if Chrome is installed at a nonstandard path. GitHub Actions runs backend verification and frontend install, build, lint, production audit, unit tests, and one seeded Playwright journey on pushes and pull requests; it publishes coverage reports but does not publish images or deploy the application.

CI also runs `npm run test:e2e` against a disposable Compose stack. It provisions an E2E-only customer using the existing demo CLI and fixtures, then verifies login, transaction selection, dispute submission, and dispute history. Locally, set `E2E_BASE_URL`, `E2E_CUSTOMER_EMAIL`, and `E2E_CUSTOMER_PASSWORD` after provisioning a customer and fixtures with the documented demo path, then run `npx playwright install chromium` and `npm run test:e2e`.

The full development dependency tree currently has six high-severity advisories confined to test/build tooling: `@angular/build`, `braces`, `chokidar`, `karma`, `karma-jasmine`, and `karma-jasmine-html-reporter`. They are accepted only as development dependencies; CI rejects high-severity findings in production dependencies.

## API and access

The API is same-origin under `/api/v1`; the full request, response, header, pagination, and error-code contract is in [`docs/design/api-contract.md`](docs/design/api-contract.md).

| Role | Main operations |
| --- | --- |
| CUSTOMER | Read owned accounts and transactions; submit and view owned disputes. |
| AGENT | View the dispute queue and detail; claim, release, and resolve disputes with the required strong ETag. |

Login, CSRF bootstrap, identity, and logout are also under `/api/v1/auth`. Authorization and ownership checks are enforced by the backend. Unimplemented routes are denied. Customer responses do not expose internal notes, release reasons, or actor identifiers.

The code-generated OpenAPI document and Swagger UI are enabled only with the `local` profile. With Compose, open [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html); the UI and `/v3/api-docs` are proxied through nginx. They are disabled in the default profile. CI validates the documented route/method and request contract against the API and publishes `openapi.json` as an artifact.

## Architecture and important choices

- The backend is a feature-first modular monolith. Controllers handle HTTP, services own use cases and transaction boundaries, repositories perform data access, and feature-to-feature access uses narrow ports.
- Flyway owns the database schema. Applied migrations are immutable; Hibernate validates entity mappings. Account and transaction reads, dispute lifecycle operations, idempotency, and session activity use JDBC where they need ownership predicates, locking, or purpose-built SQL. JPA is used for straightforward account CRUD.
- Dispute state changes and their audit events commit in the same transaction. Submission uses a durable idempotency key; agent commands use a versioned strong ETag to prevent stale writes.
- Records represent immutable requests, responses, projections, and domain values. The `Dispute` aggregate owns legal state transitions and validation.
- Authentication uses opaque HttpOnly PostgreSQL-backed sessions, CSRF protection for unsafe requests, server-side account/role/version checks, and a 30-minute idle timeout. Cookies are Secure by default; the explicit `local` profile disables Secure for local HTTP development only. There are no bearer tokens or browser-storage auth tokens.

### Login rate limiting

Login failures use a bounded Caffeine cache in each backend process. The limiter tracks client IP, normalized email, and the IP/email pair separately, then applies an increasing temporary delay; successful login clears the pair counter. Throttle responses use the same generic authentication wording for known and unknown emails, return `429 / 1005` with `Retry-After`, and emit an `authentication.audit` event and a Micrometer counter. Passwords and full email addresses are never written to throttle logs.

Compose runs one backend instance, so this limiter is per instance. With N backend instances, an attacker can get roughly N times the allowance. Production should enforce limits at the WAF or gateway, or replace this interface with shared storage such as Redis. The Compose backend trusts `X-Real-IP` only when the direct peer is the fixed nginx address on its private Docker network; it ignores forwarded client addresses from other peers. Deployments with another proxy must set `LOGIN_RATE_LIMIT_TRUSTED_PROXY` to that proxy's exact address.
- Operator account provisioning and fixture creation are explicit non-web CLI commands. They do not run at application startup and are not exposed as portal endpoints. See the [account provisioning](docs/operations/account-provisioning.md), [account fixture](docs/operations/customer-account-fixtures.md), and [transaction fixture](docs/operations/customer-transaction-fixtures.md) runbooks.

The displayed `accounts.balance` is a synthetic fixture value. It is not calculated from transactions and does not represent a real or settled bank balance. Transaction and dispute data are synthetic and read-only apart from the documented dispute workflow.

## Design references

- [API contract](docs/design/api-contract.md)
- [Session architecture decision](docs/design/session-architecture-decision.md)
- [Authentication operations](docs/operations/authentication.md)

`backend/README.md` and `frontend/README.md` point back to this file so there is one authoritative setup and behavior guide.
