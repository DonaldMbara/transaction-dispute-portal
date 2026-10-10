# Transaction Dispute Portal

Foundation for a Java 21 / Spring Boot 3.5 modular monolith, Angular 20 standalone frontend and PostgreSQL 17. Tasks 1–7 are verified; Task 8 adds CI and clean setup documentation, with hosted CI execution pending. No authentication or business functionality is implemented.

## Prerequisites

- Java 21 and Maven 3.9+ (verification used Maven 3.9.11).
- Node 22.23.3, pinned in frontend/.nvmrc, and its bundled npm. Select it with your preferred version manager; Node 25 is not supported by this workspace.
- Docker Engine with Compose supporting up --wait, accessible to your user. Backend tests require Docker and fail rather than silently skip when unavailable.
- Chrome/Chromium for Karma; set CHROME_BIN to its executable if automatic discovery fails.

Initial dependency downloads depend on connection speed; the ten-minute setup target has not been measured with an empty network cache.

## Local setup

From repository root:

```bash
cp .env.example .env
# Edit .env: choose your own DB_USERNAME and DB_PASSWORD.
set -a
source .env
set +a
docker compose config --quiet
docker compose up -d --wait
```

The ignored .env uses shell-compatible assignments. Quote special characters and load only your trusted local file. Do not print expanded Compose configuration containing secrets. Compose reads .env for interpolation; it does not export variables to native backend processes. IDE run configurations must receive the same DB_URL, DB_USERNAME, DB_PASSWORD and SPRING_PROFILES_ACTIVE values.

Backend terminal: load the root .env as above, then:

```bash
cd backend
mvn spring-boot:run
```

Frontend terminal, from repository root (no secrets required):

```bash
cd frontend
npm ci
npm start
```

Open http://localhost:4200. Routes /, /login, /customer and /agent show inert pages. Customer/agent use DashboardLayout; login remains separate. Unknown URLs return to welcome. There are no login forms, token storage, guards, business actions or role enforcement yet.

Future HttpClient requests use relative /api/v1 URLs. The development server forwards /api/** to localhost:8080 with the path unchanged. Restart npm start after proxy changes. No direct cross-origin browser API requests or backend CORS configuration are needed. Check Actuator directly on backend port 8080; it is not proxied.

## Health and shutdown

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/actuator/health/liveness
```

Healthy endpoints return HTTP 200/UP. Aggregate health may include native group names; component details remain hidden. Readiness includes DB connectivity; liveness excludes DB. After startup, a DB outage yields aggregate/readiness 503/DOWN while liveness remains UP. Initial connection/migration failures prevent startup. Only these three health GET paths are public; other requests return foundation 403. Form login, HTTP Basic and generated-user configuration are disabled; no JWT behavior exists yet.

Stop native processes with Ctrl-C. From repository root run docker compose down to stop local PostgreSQL; its named volume is preserved. Database initialization credentials apply only to a new volume. Changing .env does not rotate an existing user's password; diagnose and deliberately update credentials rather than deleting data automatically.

## Builds and tests

```bash
# From repository root:
cd backend
mvn --batch-mode clean verify
```

```bash
# In a separate terminal, from repository root:
cd frontend
npm ci
npm ls
npm run build
npm run test:ci
```

Frontend test:ci runs once with ChromeHeadless and returns nonzero on failure. npm test runs in watch mode. The build writes frontend/dist/dispute-portal. Global styles.scss composes token/reset/typography modules with Sass @use; component styles stay scoped. Nunito Sans is a font preference with system fallbacks, not an installed asset.

Backend verification uses separate PostgreSQL Testcontainers, with dynamic datasource properties and no .env or Compose dependency. FoundationIntegrationTest verifies HTTP health/security, Flyway V1, schema constraints and probe membership. To run ordinary checks only: mvn test -Dtest=FoundationIntegrationTest. Full clean verify also runs DatabaseOutageTest, which stops only its own container and uses an isolated context with bounded test-only timeouts. Expected outage connection warnings do not mean normal tests failed.

Flyway owns schema; Hibernate validates mappings (none exist yet). V1 creates only app_users as explicitly required, with no seeded users. Authentication owns the later entity and subsequent migrations. Never edit applied V1 or recreate its table. The rollback-only psql assertions in backend/src/test/resources/app-users-constraints.sql are for an isolated database where V1 is already applied; automated tests run the equivalent assertions without persisting rows.

## Troubleshooting

- Missing DB configuration: check exported root .env values. A datasource URL error stating it must start with jdbc indicates DB_URL was not supplied correctly.
- Connection refused: verify Compose is healthy, DB_URL matches port 5432 and existing-volume credentials match. PostgreSQL binds to 127.0.0.1 only.
- Port conflicts: resolve the existing process before restarting. A temporary frontend override is npm start -- --port 4201; do not silently change defaults 8080/4200/5432.
- Migration failure: inspect the Flyway error, repair the unapplied migration through review and rerun verification; never enable ddl-auto update/create or erase data as a shortcut.
- Docker permission errors: configure legitimate Docker access for your user; do not disable Testcontainers checks.
- Browser unavailable: install Chrome/Chromium or set CHROME_BIN. No sandbox-disabling flags are configured.
- Version errors: verify java -version, mvn -version, node --version and npm ls against the prerequisites above.

## CI and remaining verification

.github/workflows/ci.yml runs on push and pull_request with separate Ubuntu 24.04 backend/frontend jobs, read-only repository permission and pinned official action commits. Java 21 and Node from frontend/.nvmrc are configured; caches use backend/pom.xml and frontend/package-lock.json. Backend uses runner Docker for Testcontainers, with no extra DB service or environment secrets. Frontend discovers Chrome and runs npm ci, build and test:ci. Failed prerequisites/tests fail the job. No undefined lint or Docker-image build is included; application Dockerfiles/reverse proxy and later delivery gates belong to integration-hardening/deployment work.

Local actionlint and clean-copy command verification do not establish a successful hosted GitHub run. Task 8 remains incomplete until the permitted remote CI run and outstanding architecture-reference reconciliation are resolved. The working tree currently has no commits; publishing it requires explicit authorization.

Full npm audit reports six high-severity development-tooling entries through Karma/braces (GHSA-vfj7-8cjw-p6xm); npm proposes incompatible downgrades. Production-only audit reports zero advisories. No force fix or runner migration has been applied. See the task evidence for actual commands/results; neither production readiness nor a clean full audit is claimed.
