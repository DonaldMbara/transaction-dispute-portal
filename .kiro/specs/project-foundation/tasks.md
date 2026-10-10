# Implementation Plan: Project Foundation & Scaffolding

**Status:** Approved — Tasks 1–9 complete; final review limits recorded in acceptance.md  
**Design:** [design.md](design.md)  
**Requirements:** [requirements.md](requirements.md)

The developer authorized task planning after the final consistency corrections. This plan preserves the modular monolith and foundation scope. Checked tasks have recorded verification evidence. The developer approved implementation one numbered task at a time, with review after each task.

## Approval gate and requirements traceability

Requirements remain unchanged. Task approval must explicitly accept the design's recorded clarifications: Angular 20 Karma/Jasmine rather than the incorrect Vitest-default wording (FR-11); documented future auth/interceptor boundaries instead of executable placeholders (FR-8, FR-10, NFR-6); relative `/api/v1` plus development proxy instead of an unnecessary base service/environment URL (FR-10); and public aggregate health with private DB diagnostics (FR-5). These are disclosed acceptance differences, not silently amended requirements.

FR-4 is satisfied by a real `V1__create_app_users.sql` migration, not a comment-only baseline. Creating that table is the existing narrow foundation schema exception; no mapped entity, authentication behavior or seed data accompanies it. Concise component names and `layouts/dashboard-layout/` apply throughout.

## Ordered tasks

- [x] 1. Confirm compatible versions and scaffold the backend build.
  - Pin Java 21, supported Spring Boot 3.5.x maintenance patch and Maven tooling; preserve BOM-managed Flyway/Testcontainers dependencies from design §3.
  - Create the application entry point and documented feature package boundaries; no business classes or unused JWT dependencies.
  - Verify the effective POM/dependency tree, compiler target and build plugin compatibility. Record chosen versions and support checks.
  - Dependencies: task-plan approval, including the clarification gate above.
  - Requirements: FR-1–3, NFR-4.

- [x] 2. Configure local PostgreSQL and externalized backend settings.
  - Add PostgreSQL-only Compose, loopback port binding, named volume and health check; align its pinned PostgreSQL 17 image with integration tests.
  - Add `.env.example` with blank secrets, ignored local environment files and base/local/test configuration as designed. Keep native process environment loading explicit.
  - Configure Flyway and Hibernate validation, disable Open Session in View and expose health only. Configure DB-aware readiness and DB-independent liveness with public details suppressed.
  - Verify `docker compose config --quiet`, healthy startup and native backend connectivity; check missing configuration fails without printing secrets. Preserve existing volumes.
  - Dependencies: 1.
  - Requirements: FR-3–5, NFR-1–2, EC-1–2.

- [x] 3. Add the meaningful foundation migration.
  - Create `V1__create_app_users.sql` using the canonical schema and constraints in design §4/database design; no other business tables, entity mappings or seeded users.
  - Verify fresh-database migration success, required columns/constraints and Flyway history; rerun migration to verify no pending changes/checksum failures.
  - Keep applied V1 immutable. Document authentication's ownership of the later entity and subsequent schema changes.
  - Dependencies: 2.
  - Requirements: FR-4, FR-6, EC-4.

- [x] 4. Implement and test the minimal security/health baseline.
  - Permit unauthenticated GETs only for aggregate health/readiness/liveness; deny unrelated paths, disable form/basic login, use stateless sessions and the explicit foundation 403 entry point.
  - Limit CSRF exclusions to `/api/**`; add no CORS, JWT decoder or auth endpoint.
  - Implement `FoundationIntegrationTest` with real PostgreSQL, dynamic datasource properties and Surefire discovery, including the executable HTTP acceptance example in design §5.1.
  - Assert healthy endpoints return 200/UP without credentials or component details; unrelated application/management paths return 403 without redirects. Verify V1 and schema constraints against the test container.
  - Separately verify a post-startup DB outage produces aggregate/readiness 503/DOWN while liveness stays UP, with isolated state and private DB diagnostics. Verify readiness/liveness group membership.
  - Run `mvn test -Dtest=FoundationIntegrationTest` and `mvn --batch-mode clean verify`; unavailable Docker must fail rather than silently skip.
  - Dependencies: 3.
  - Requirements: FR-5–6, NFR-6, EC-4.

- [x] 5. Scaffold the Angular 20 frontend and compatible test configuration.
  - Pin compatible Angular 20.3.x CLI/core/build and Node 22 (>=22.12); commit the generated lockfile, strict TypeScript configuration and zone-based Karma/Jasmine setup.
  - Use `app.ts`, `app.html`, `app.scss`, `app.spec.ts` and concise names for all components. Configure component schematic `type: ""`, standalone components and SCSS.
  - Align `@angular/build:karma`, spec tsconfig/Jasmine types, zone testing polyfills and `test:ci` (`ng test --watch=false --browsers=ChromeHeadless`). Preserve generated build configurations/assets/budgets.
  - Run `npm ci`, `npm ls`, `npm run build` and `npm run test:ci` with Chrome/Chromium available; confirm specs are discovered and failures return a nonzero status.
  - Dependencies: approval gate; can proceed independently of 1–4.
  - Requirements: FR-7, FR-11, NFR-4, EC-5.

- [x] 6. Add inert feature routing and dashboard layout.
  - Create `layouts/dashboard-layout/dashboard-layout.ts`, `.html`, `.scss`, `.spec.ts`; class `DashboardLayout`, selector `app-dashboard-layout`, with a child router outlet.
  - Add concise Welcome/Login/Customer/Agent placeholder files and lazy routes as design §6 specifies. Keep login separate from customer/agent dashboard routes.
  - Reserve `shared/components/` for reusable UI; document future core/auth responsibilities without services, guards, interceptors or token state. Add no animation provider or UI component library.
  - Test root/welcome/login/customer/agent navigation, fallback behavior and layout child rendering through Angular Router tests.
  - Dependencies: 5.
  - Requirements: FR-8, FR-11, NFR-6.

- [x] 7. Add relative API communication and global SCSS organization.
  - Configure `provideHttpClient()` and the `/api/**` development proxy to `localhost:8080`, preserving the prefix; document future relative `/api/v1` requests.
  - Add global `styles.scss` with `@use` for `_tokens.scss`, `_reset.scss`, `_typography.scss`; keep component styles local. Add mixins only if actually used.
  - Verify builds/tests and that a request through port 4200 reaches the backend deny-all response. Do not invent a business endpoint to demonstrate forwarding.
  - Check approved token values, accessible native focus/link affordances and absence of deprecated Sass imports or absolute browser API URLs.
  - Dependencies: 4, 6.
  - Requirements: FR-9–10 (recorded clarification), FR-11.

- [x] 8. Add foundation CI and reproducible setup documentation.
  - Add separate backend/frontend GitHub Actions jobs with Java 21, compatible Node, dependency caches, Docker for Testcontainers and ChromeHeadless for Angular tests.
  - Run backend `mvn --batch-mode clean verify`; frontend `npm ci`, `npm run build`, `npm run test:ci`. No extra PostgreSQL service, secrets, image build or undefined lint command.
  - Document trusted local `.env` export, Compose/native startup, tests, health URLs, prerequisite versions, port/configuration errors and existing-volume credential behavior. Defer application Dockerfiles/reverse-proxy deployment explicitly.
  - Resolve the missing architecture-review reference by locating the approved document or obtaining approval to point steering at an existing canonical document; do not reconstruct historical sign-off.
  - Verify documented commands from a clean setup, run CI on an authorized push/PR when available and record any CI execution limitation. Report actual results only.
  - Dependencies: 4, 7.
  - Requirements: NFR-2–5, EC-1–5.

- [x] 9. Review foundation acceptance and stop for the next feature approval.
  - Check every applicable requirements acceptance item against recorded build/test/health/migration/CI evidence, with approved clarifications visible.
  - Confirm consistent concise filenames/layout location, no credentials, no authentication/business implementation and no unexpected scope additions.
  - Report changed files, commands/results and remaining limitations. Do not begin authentication or other features automatically.
  - Dependencies: 8.
  - Requirements: complete foundation acceptance checklist.

## Deferred work

Authentication, JWT behavior, guards/interceptors, business entities/APIs, seed users, ownership/concurrency tests, application Dockerfiles, production reverse proxy, Docker-image CI and later feature quality gates remain with their owning specifications. No architecture redesign is proposed.

## Task 1 verification — 2026-10-10

Created backend/pom.xml, the application entry point, seven package-info.java boundary files and backend/.gitignore (target exclusion). Requirements and architecture are unchanged.

- Runtime: OpenJDK 21.0.12.1; temporary Apache Maven 3.9.11 under /tmp because system mvn is unavailable. No system installation was made.
- Pinned Spring Boot 3.5.16, confirmed by its official system-requirements page as compatible with Java 21. Exact support/security eligibility must continue to be monitored; no future production-readiness claim is made.
- Executed `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode -Dmaven.repo.local=/tmp/foundation-m2 clean verify dependency:tree help:effective-pom -Doutput=/tmp/foundation-effective-pom.xml` in backend: BUILD SUCCESS, exit 0. Sandbox DNS failed initially; approved network execution succeeded.
- Effective POM: compiler release 21, compiler plugin 3.14.1, Surefire 3.5.6, Boot plugin 3.5.16. Dependency tree resolves matching Flyway 11.7.2 and Testcontainers 1.21.4 modules without independent overrides.
- `javap -verbose` confirms application bytecode major version 65 (Java 21). Executable Spring Boot JAR built successfully.
- Maven reports “No tests to run.” Tests are introduced in Task 4; this is build verification, not runtime/database/health test success.
- System Maven remains a local prerequisite for the documented bare mvn commands. Tasks 2–9 remain unchecked; no application startup, database or frontend work attempted.

## Task 2 verification — 2026-10-10

Added compose.yaml, .env.example, root .gitignore, README.md and backend base/local/test YAML settings. PostgreSQL is pinned to 17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3 (verified server 17.11); Task 4 must use this same image digest. No requirements or architecture changes.

- `docker compose config --quiet` without credentials: expected exit 1 with explicit missing DB_USERNAME/DB_PASSWORD errors. With a private temporary environment file: exit 0.
- `docker compose up -d --wait`: exit 0; PostgreSQL healthy. Read-only psql SELECT confirms dispute_portal and PostgreSQL 17.11.
- Temporary Maven 3.9.11: `mvn --offline --batch-mode -Dmaven.repo.local=/tmp/foundation-m2 clean verify`: BUILD SUCCESS. No tests exist yet.
- Native `java -Djava.awt.headless=true -jar target/dispute-portal-0.0.1-SNAPSHOT.jar` with exported validation environment: successful Hikari PostgreSQL connection and application startup on 8080. Only the health management endpoint is exposed. No migration files yet, so Flyway warns as expected; V1 belongs to Task 3.
- Same JAR without DB environment: exit 1; datasource initialization reports URL must start with jdbc. README explicitly identifies required DB variables; no secret values were printed in reported evidence.
- Framework-default generated-user security remains temporarily until Task 4; public health access is not claimed. No authentication code was introduced.
- Stopped validation backend and ran Compose down successfully; named volume preserved. It contains temporary validation credentials from /tmp/foundation-task2.env (mode 0600), not a committed .env. Reuse that temporary environment for subsequent local verification or deliberately configure the existing database; copying new credentials does not rotate the preserved volume.
- Docker required approved host access; verification succeeded. Tasks 3–9 remain unchecked. Outage testing was not run or mixed with ordinary verification.

## Task 3 verification — 2026-10-10

Added backend/src/main/resources/db/migration/V1__create_app_users.sql and backend/src/test/resources/app-users-constraints.sql (executable psql assertions, transactional rollback). The canonical six-column schema includes named email uniqueness and role constraints. No entity, authentication behavior, other business table or seed data was added. Email normalization/password encoding remain authentication responsibilities as approved.

- `mvn --offline --batch-mode -Dmaven.repo.local=/tmp/foundation-m2 clean verify` using temporary Maven 3.9.11 in backend: BUILD SUCCESS. An initial invocation from repository root failed because there is no root POM; corrected working directory succeeded. No JUnit tests exist yet.
- Compose up with the existing private temporary environment: PostgreSQL healthy. Created a new isolated database foundation_task3_validation; the existing dispute_portal database was not modified by V1 verification.
- Ran the packaged backend twice with DB_URL pointing to the isolated database and `--spring.main.web-application-type=none`: both exited 0. First run applied V1; second validated one migration and reported no migration necessary. No web security assertions are claimed.
- Executed psql with ON_ERROR_STOP against app-users-constraints.sql: exit 0. Verified all six types/lengths/nullability, enabled default, successful V1 history/checksum, valid CUSTOMER/AGENT roles, and rejection of duplicate email, duplicate primary key, invalid role and null email. Test inserts rolled back; persisted user count is zero.
- V1 history checksum is 1011929294, success true. Preserve this applied migration; authentication adds the later entity and uses subsequent versions for schema changes. The global database document's illustrative core-tables migration sequence must be reconciled by later owning specs as already recorded in the foundation design.
- Stopped Compose with down, preserving named volume and isolated verification database. No outage test executed. Tasks 4–9 remain unchecked.

## Task 4 verification — 2026-10-10

Added SecurityConfig, FoundationIntegrationTest, DatabaseOutageTest and the JDBC-compatible rollback-only schema assertion resource. Updated the application entry point to exclude generated-user auto-configuration (no authentication behavior), security boundary documentation and README. Requirements and approved architecture remain unchanged.

- Security permits only GET aggregate health/readiness/liveness; other paths/methods are denied. Explicit anonymous 403, stateless policy, disabled form/basic login and request cache, API-only CSRF exclusions; no JWT decoder, auth endpoint or CORS configuration.
- Ordinary tests use their own pinned PostgreSQL Testcontainer and dynamic datasource properties, never the local volume. Verified real HTTP 200/UP health without credentials/details, unrelated application/management/login paths 403 without redirects, denied mutations, readiness/liveness membership, V1/checksum/no pending migrations and rollback-only schema constraints.
- DatabaseOutageTest uses a separate container, application context and bounded connection timeouts. Stops only its container after startup, privately verifies DB DOWN, verifies public aggregate/readiness 503/DOWN and liveness 200/UP, then dirties its context. No ordinary test state or local database was affected.
- `/tmp/apache-maven-3.9.11/bin/mvn --batch-mode -f backend/pom.xml -Dmaven.repo.local=/tmp/foundation-m2 test -Dtest=FoundationIntegrationTest`: BUILD SUCCESS, 5 tests, zero failures/errors/skips.
- Same Maven invocation with `clean verify`: BUILD SUCCESS, 6 tests (5 ordinary + 1 isolated outage), zero failures/errors/skips; executable JAR built.
- Initial offline run required an uncached Surefire JUnit provider; approved network/Docker execution resolved it. Initial digest image parsing failed; explicit DockerImageName compatibility for the official postgres image corrected it without changing the pinned image or dependency versions. Final checks succeeded.
- System Maven remains unavailable; temporary Maven 3.9.11 was used. Docker unavailable environments fail tests rather than silently skip. Testcontainers cleaned up its containers. Tasks 5–9 remain unchecked; stop for review.

## Task 5 verification — 2026-10-10

Generated frontend with Angular CLI 20.3.39: standalone/strict/routing/SCSS, explicitly zone-based, no Git initialization. Retained generated production/development configurations, assets and budgets. Root files are app.ts/app.html/app.scss/app.spec.ts; App hosts only RouterOutlet. Feature pages/layout/proxy/style partials remain Tasks 6–7.

- Exact Angular runtime/compiler pins 20.3.33 and CLI/build pins 20.3.39 (latest available patches in their respective 20.3 release lines at review); generated Karma/Jasmine, TypeScript 5.9 and zone.js dependencies are locked in package-lock.json. Component schematic sets type empty, standalone and SCSS. Jasmine spec discovery and zone testing match @angular/build:karma.
- System Node 25.8.0 is unsupported for this chosen Angular version. Downloaded Node 22.23.3 into /tmp and checked its archive SHA256 against official release metadata; .nvmrc pins that version and package engines require Node 22 >=22.12.0. npm 10 bundled with Node 22 was used. No system runtime replacement.
- Generated CLI scaffold successfully. Initial npm invocation from repository root failed (no package.json); its empty root lockfile was removed. Installation was rerun correctly in frontend. Sandbox registry lookup was unavailable; approved network execution succeeded.
- `npm install`, then required `npm ci`: succeeded. `npm ls`: exit 0, compatible dependency tree without invalid peers. `npm run build`: exit 0, production output dist/dispute-portal.
- `npm run test:ci` with CHROME_BIN pointing to temporary Google Chrome for Testing 151.0.7922.34: exit 0, one discovered App router-outlet test passed. Browser downloaded into /tmp using existing Playwright tooling; no Playwright dependency added to frontend, no sandbox-disabling flags.
- Temporarily added a deliberate failed assertion: test:ci returned exit 1. Restored app.spec.ts immediately and reran: exit 0, one test passed. No deliberately failing spec remains.
- `npm audit`: exit 1, six high-severity development-tooling dependency entries arising from the braces stack-exhaustion advisory GHSA-vfj7-8cjw-p6xm through the Karma chain. npm proposes incompatible downgrades; no force fix or alternative runner introduced. `npm audit --omit=dev`: exit 0, zero production dependency advisories. These tooling advisories remain a disclosed issue for review; required install/dependency/build/test checks passed, but no clean full audit is claimed.
- Chrome/Chromium and compatible Node remain machine prerequisites outside these temporary verification downloads. Task 6 has not started; stop for review.

## Task 6 verification — 2026-10-10

Added concise Welcome/Login/Customer/Agent TS/HTML/SCSS/spec files, lazy auth/customer/agent route arrays, layouts/dashboard-layout files with class DashboardLayout and selector app-dashboard-layout, routing integration specs, and core/shared/shared-components boundary README files. Updated app.routes.ts and setup documentation. No authentication forms, token state, services, guards, interceptors, navigation library or animation provider.

- `npm --prefix frontend run build` with temporary Node 22.23.3: exit 0, production build with lazy feature/page/layout chunks.
- `npm run test:ci` with the same Node and temporary Chromium CHROME_BIN: exit 0, 12 tests passed. Initial sandbox attempt could not bind Karma port 9876 (EPERM); approved host execution succeeded. No sandbox-disabling browser flags.
- Verified welcome/login are separate from dashboard layout; customer/agent render through its child outlet, each page has one main landmark, unknown paths redirect to welcome and sequential sibling navigation is not consumed by the lazy auth route. Standalone page/layout specs verify inert headings and outlet structure.
- All component filenames are concise; application layout exists only under layouts/dashboard-layout. shared/components contains boundary documentation only. Tasks 7–9 remain unchecked. Existing development-tooling audit advisories remain as recorded in Task 5; no dependency changes made. Stop for review.

## Task 7 verification — 2026-10-10

Updated app.config.ts with provideHttpClient, angular.json serve options, core boundary documentation and READMEs. Added proxy.conf.json (/api/** to localhost:8080 unchanged) and global _tokens/_reset/_typography modules composed by styles.scss with @use. Component styles stay local; no unused mixins, API service, interceptor, business endpoint, UI library or CORS configuration added.

- `npm --prefix frontend run build` under temporary Node 22.23.3: exit 0. `npm run test:ci` with temporary Chromium: exit 0, 12 tests passed.
- Compiled CSS verified approved font/colors, sole global entry and absence of Sass @import. A first assertion assumed minified token spacing; corrected whitespace-tolerant checks passed. Live Chromium computed-style check confirmed body font fallback, text rgb(58,58,58), background rgb(247,247,247), margin zero and border-box sizing. Native focus/list/link affordances are preserved in source; link underline is explicit. No font assets downloaded.
- Existing workspace Angular server occupied 4200 with old proxy configuration. Confirmed its cwd before restarting it with the updated configuration; default port remains 4200.
- Started validation backend against the existing isolated database and PostgreSQL Compose. GET /api/v1/transactions directly on 8080 and through 4200 both returned 403 with backend security headers and no CORS allow-origin header. No endpoint invented. Successful responses are not silently accepted by the smoke assertion.
- Direct aggregate health returned 200/UP without details/components; native Actuator also includes health group names. An initial exact-body smoke assertion failed on that native groups field; status/privacy assertions passed without changing application configuration. /actuator remains outside proxy rules.
- Stopped validation backend and Compose, preserving database volume. The replacement frontend dev server remains running on 4200 because it was already running before this task. API forwarding requires the backend to be started separately. Existing development-tooling audit advisories remain unchanged. Tasks 8–9 unchecked; stop for review.

## Task 8 progress and verification — 2026-10-10 (not complete)

Added .github/workflows/ci.yml with independent Ubuntu 24.04 backend/frontend jobs, read-only permissions, timeouts and official checkout/setup-java/setup-node release commit pins verified by git ls-remote. Java 21/Maven and Node from .nvmrc use dependency caches at real POM/lockfile paths. Chrome is discovered before tests; no sandbox bypass, extra database service, application image build, undefined lint or secret inputs. Updated README with complete native/Compose/frontend/test/health/shutdown/troubleshooting instructions and deployment deferrals. Corrected Compose healthcheck quote escaping so pg_isready receives unquoted variable values rather than literal quote characters.

- actionlint 1.7.12 on .github/workflows/ci.yml: exit 0, no findings. Temporary validator only, not a repository/application dependency.
- Copied source into /tmp/foundation-task8-clean excluding target/node_modules/dist/.angular. Created new mode-0600 ignored local environment with fresh temporary credentials; no existing volume/data modified.
- Backend clean-copy `mvn --batch-mode -Dmaven.repo.local=/tmp/foundation-m2 clean verify` with temporary Maven 3.9.11: exit 0, six tests passed, zero failures/errors/skips (ordinary and isolated outage containers).
- Frontend clean-copy `npm ci`, `npm run build`, `npm run test:ci` with temporary Node 22.23.3 and Chromium CHROME_BIN: exit 0; twelve tests passed. Development-tooling audit warnings remain disclosed.
- Documented environment export and `docker compose config --quiet`, `docker compose up -d --wait`: succeeded with a fresh independent named volume. Native `mvn spring-boot:run` connected and applied V1 to that empty database.
- `npm start -- --port 4201` (documented temporary override because existing user/workspace server occupies 4200) served the clean copy. Direct aggregate/readiness/liveness returned 200/UP; clean-copy API proxy returned backend 403. Clean frontend returned 200. Stopped only clean-copy processes/Compose stack, preserved its volume and original 4200 frontend.
- Hosted GitHub CI has NOT run. The repository has an origin but no local commits yet; no commit/push was authorized by this step, and gh CLI is unavailable. Local validation is not substituted for the required hosted execution.
- Missing docs/design/review-round-2.md was not found in checkout or named attachments. Prepared /tmp/foundation-task8-reference-proposal.patch for review: point structure steering to existing foundation design/component diagram and tech steering to design/current verification evidence. It changes references only, not architectural rules, and does not recreate historical sign-off. Steering remains unchanged pending the explicit approval required by this task.
- Task 8 remains unchecked until reference reconciliation approval and hosted CI verification succeed. Task 9 has not started. Required next approval: reference-only patch and a commit/push of the reviewed foundation to trigger CI, followed by actual run results.

### Task 8 completion update — 2026-10-10

Developer approved the reference-only patch and publishing the foundation, then committed the scaffold (local commit b7647f9) and reported that the pipelines passed. Hosted success is developer-confirmed, not independently observed by this agent: GitHub page access failed and command-line remote access requires unavailable credentials. The local workflow/clean-copy verification evidence above remains valid.

Applied the approved reference-only changes to structure.md and tech.md, replacing the absent review document with existing canonical foundation design, component diagram and current task evidence. No architectural rule or approved requirement changed; no historical sign-off was recreated. Task 8 is complete based on local verification plus the developer's hosted-CI confirmation. Task 9 has not started.

## Task 9 final review — 2026-10-10

Added acceptance.md covering every functional/nonfunctional criterion, approved clarifications and explicit residual negative-path/setup-timing limits. Updated README to point to the report. No application code, requirement or architectural change. Task 9 is a review/evidence task; its completion is not a silent waiver of unverified original criteria.

- Reviewed current files against approved design/requirements and preceding actual validation evidence; no broad test rerun because executable code/configuration is unchanged since the successful Task 8 clean-copy checks.
- Executed tracked-file/configuration/naming/layout/scope/runner/reference assertions: passed. An initial scanner used a multiline whitespace expression that misread the blank .env.example value; corrected line-by-line check passed. No secret values printed.
- Executed actionlint on the current workflow: exit 0. Requirements diff against scaffold baseline is empty. Confirmed retained clean-copy evidence: Maven six tests passed, frontend twelve tests passed and production build passed. Hosted CI remains developer-confirmed, not independently inspected.
- Remaining advisories, unmeasured first-time setup timing, unexercised negative paths and local documentation publication limits are explicitly recorded in acceptance.md. No next feature started. Stop for developer review/next-feature approval.
