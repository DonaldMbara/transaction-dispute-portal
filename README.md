# Transaction Dispute Portal

Foundation for a Java 21 / Spring Boot 3.5 modular monolith, Angular 20 standalone frontend and PostgreSQL 17. Tasks 1–9 are complete, with hosted CI success confirmed by the developer. The final acceptance review and its unverified criteria/remaining issues are recorded in .kiro/specs/project-foundation/acceptance.md. Approved authentication/RBAC is implemented with PostgreSQL-backed sessions, non-web provisioning, strict idle activity, CSRF, login/identity/logout and guarded Angular navigation. Feature acceptance evidence is under .kiro/specs/authentication-rbac/; transaction/dispute business functionality remains deferred.

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

The Compose stack starts PostgreSQL, the backend and the nginx-served frontend. Open http://localhost:8081 (override with `PORTAL_PORT`); if host port 5432 is occupied, set `POSTGRES_PORT` to an available port for local database clients. Containers communicate over Compose networking, so changing the published host database port does not change the backend connection.

For synthetic demo accounts and transactions, follow [the opt-in demo data instructions](docs/operations/demo-data.md). The seed script requires an interactive terminal and runs only when explicitly invoked; default Compose startup creates no accounts or fixtures.

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

Open http://localhost:4200. `/` resolves after the verified session check: anonymous users go to `/login`, CUSTOMER users to `/customer`, and AGENT users to `/agent`; unavailable session checks show a retry screen. Unknown routes use the same safe resolution. Customer/agent currently contain only feature placeholders, but their role routes use DashboardLayout and are protected by frontend guards plus backend authorization.

Future HttpClient requests use relative /api/v1 URLs. The development server forwards /api/** to localhost:8080 with the path unchanged. Restart npm start after proxy changes. No direct cross-origin browser API requests or backend CORS configuration are needed. Check Actuator directly on backend port 8080; it is not proxied.

## Health and shutdown

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/actuator/health/liveness
```

Healthy endpoints return HTTP 200/UP. Aggregate health may include native group names; component details remain hidden. Readiness includes DB connectivity; liveness excludes DB. After startup, a DB outage yields aggregate/readiness 503/DOWN while liveness remains UP. Initial connection/migration failures prevent startup. These three exact health GET paths remain public and do not load or touch JDBC sessions. Authentication implements GET /api/v1/auth/csrf, POST /api/v1/auth/login, GET /api/v1/auth/me and POST /api/v1/auth/logout. Unrecognized safe requests return JSON 401/1001; unsafe requests without valid CSRF return 403/1010. Form login, HTTP Basic, remember-me, built-in logout and generated-user configuration are disabled; application JSON login/logout require CSRF.

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

Flyway owns schema; Hibernate validates the auth account mapping. Historical V1 creates app_users as explicitly required, with no seeded users. Additive V2–V4 provide account version/canonical-email constraints and session/activity tables; JDBC sessions now use a 30-minute framework timeout, IMMEDIATE flush and ON_SET_ATTRIBUTE persistence. Strict authenticated activity and current-account validation use PostgreSQL metadata and guarded admission updates. Never edit applied V1 or recreate its table. The rollback-only psql assertions in backend/src/test/resources/app-users-constraints.sql are for an isolated database where V1 is already applied; automated tests run the equivalent assertions without persisting rows.

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

Local actionlint and clean-copy command verification do not establish a successful hosted GitHub run. The developer committed the scaffold and confirmed that hosted pipelines passed. The agent could not independently inspect the private/credential-protected run; no run URL or ID was supplied. Approved architecture references now point to existing canonical documents. Final acceptance remains Task 9.

Full npm audit reports six high-severity development-tooling entries through Karma/braces (GHSA-vfj7-8cjw-p6xm); npm proposes incompatible downgrades. Production-only audit reports zero advisories. No force fix or runner migration has been applied. See the task evidence for actual commands/results; neither production readiness nor a clean full audit is claimed.

Current authentication architecture: Spring Security with Spring Session JDBC and PostgreSQL; opaque HttpOnly session cookie, Secure/SameSite=Lax in production, 30-minute server-enforced idle timeout, CSRF for unsafe requests including login/logout, backend CUSTOMER/AGENT authorization and admin-controlled provisioning with no public registration. No JWT/refresh/localStorage authentication tokens or bearer-token interceptor. Same-origin /api/v1 remains unchanged; valid-session reload restoration is permitted. The approved authentication design and canonical API contract define the implemented endpoint/payload/cookie/CSRF behavior. Historical foundation specifications retain their original baseline; session/CSRF infrastructure is implemented without altering applied migrations; authenticated lifecycle behavior is verified through isolated framework, multi-instance, concurrency and fault suites. See [session architecture decision](docs/design/session-architecture-decision.md) for the supersession boundary.

Account administration is available only through the explicit non-web `account-admin` command. Session/CSRF infrastructure is available; login/identity/logout and Angular integration are implemented. See the [account administration runbook](docs/operations/account-provisioning.md) for trusted database configuration, masked-console commands, concurrency preconditions and interruption verification.

Session boundary: GET `/api/v1/auth/csrf` returns the standard envelope containing a masked `csrfToken` and `headerName: X-CSRF-TOKEN`. Bootstrap persists anonymous CSRF state in PostgreSQL before returning; it does not create authenticated activity metadata or disclose identity. Send that header for unsafe same-origin API requests. POST login/logout require valid CSRF; identity requires a currently valid supported role; all unimplemented paths remain default-denied. Cookies are host-only `SESSION`, HttpOnly, SameSite=Lax, path `/api/v1` and browser-session lifetime. Secure is fixed by configuration (true by default); only the explicit local profile permits false. Production/local profile conflicts and insecure production overrides fail startup. Local development continues using exported `.env` configuration and the Angular proxy; no CORS or bearer transport is introduced. Login accepts a non-empty, well-formed password up to 128 Unicode code points; provisioning/password replacement requires 8–128. Both preserve input exactly.

Application envelopes, including security errors, carry a bounded/generated X-Correlation-ID and no-store. An outer servlet filter maps detected uncommitted account/session-store failures to generic 503/3002 and removes pending session-cookie headers. Committed responses cannot be rewritten; subsequent failures are logged safely and propagated without private causes. No custom response buffering is used. MVC request-handled events are disabled and flash-map lookup skips exactly the health GET paths to prevent framework-driven session access; other paths keep their normal flash-map behavior. Framework fault/outage evidence is recorded in the feature task plan; browser/local verification and final acceptance are recorded in its acceptance report.

Authenticated requests validate the persisted principal, current enabled account, supported role, authentication version and strict 30-minute activity deadline before authorization. Only explicitly marked handlers renew activity, at admission after CSRF and role checks; later validation or handler errors do not undo renewal. Health, CSRF bootstrap, logout, unmatched/denied requests and background polling do not renew authentication. Only GET /api/v1/auth/me currently marks user-driven activity; Angular never polls it.

Activity records use the framework access instant captured once through public HttpSession access and a monotonic guarded PostgreSQL update committed before handler execution. Expiry guards use PostgreSQL time; backend hosts and PostgreSQL must maintain synchronized clocks through operational time synchronization. Clock skew can affect the deadline because framework timestamps originate on backend hosts; this implementation assumes synchronized clocks and does not introduce a separate skew allowance. Activity renewal never reloads/saves a second mutable Spring Session object or writes its managed columns. Invalid authenticated contexts fail with 401; CSRF bootstrap clears invalid contexts and may establish fresh anonymous CSRF material. Store failures fail closed with generic 503.

For the native proxy workflow, cookie profiles, masked-console provisioning, clock/serialization assumptions, cleanup configuration, benchmark and ambiguous logout/storage troubleshooting, use the [authentication runbook](docs/operations/authentication.md). Customer/agent landing pages have role-aware navigation and sign-out; no banking workflow is introduced by authentication. Local verification does not claim a newly published hosted CI run or production deployment.
