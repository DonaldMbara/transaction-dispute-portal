# Design: Project Foundation & Scaffolding

**Feature:** `project-foundation`  
**Status:** Final consistency review complete — task plan drafted; implementation awaiting approval  
**Created / reviewed:** 2026-10-10  
**Requirements:** [requirements.md](requirements.md)

This is a design document, not generated application code or an implementation task list. The requirements file currently labels itself “awaiting approval”; this review does not assume that it has been approved. The developer has authorized drafting tasks after the naming/consistency corrections. The task plan records remaining requirements wording conflicts; application implementation is not authorized.

## 1. Scope and preserved decisions

Create the build/configuration/test foundation for one Java 21 / Spring Boot 3.x modular monolith, Angular standalone frontend, PostgreSQL, Flyway, Maven and GitHub Actions. Docker Compose runs local PostgreSQL; developers run backend/frontend natively for a simple inner loop. Complete application Dockerfiles and the static-server reverse proxy are deferred to a later deployment specification; no half-defined application services are added to foundation Compose.

Keep approved package boundaries, ownership rules, future RS256 15-minute JWT/in-memory frontend policy and ETag/If-Match design. Foundation implements no login, token state, JWT validation, guards, auth interceptors, business APIs, mapped entities, demo users, API response handling or domain behavior. The only business-schema exception is the app_users DDL explicitly required by FR-4. The custom response envelope and 1-based query conventions remain the contract for later application endpoints; Actuator health uses its native payload as already permitted by the API design.

The architecture stays one Spring Boot deployment unit, with no gateway, microservices, OAuth provider, search service or cloud infrastructure. Production-oriented design is distinct from verified production readiness.

## 2. Foundation artifact structure

```text
transaction-dispute-portal/
  .env.example                         # names + blank secret values
  .gitignore
  compose.yaml                         # PostgreSQL only
  README.md
  .github/workflows/ci.yml
  backend/
    pom.xml
    src/main/java/com/assessment/disputeportal/
      DisputePortalApplication.java
      auth/package-info.java
      security/SecurityConfig.java     # minimal health-access/deny-all baseline
      account/package-info.java
      transaction/package-info.java
      dispute/package-info.java        # future dispute.audit responsibility documented
      idempotency/package-info.java
      common/package-info.java
    src/main/resources/
      application.yml
      application-local.yml
      db/migration/V1__create_app_users.sql
    src/test/java/com/assessment/disputeportal/
      FoundationIntegrationTest.java
    src/test/resources/application-test.yml
  frontend/
    package.json
    package-lock.json
    angular.json
    tsconfig.json
    tsconfig.app.json
    tsconfig.spec.json
    proxy.conf.json
    src/
      main.ts
      index.html
      styles.scss
      styles/_tokens.scss
      styles/_reset.scss
      styles/_typography.scss
      app/
        app.config.ts
        app.routes.ts
        app.ts
        app.html
        app.scss
        app.spec.ts
        core/README.md                  # future auth/interceptor responsibilities only
        shared/README.md                # reusable UI/helpers only
        shared/components/README.md     # no application layouts or UI library
        layouts/dashboard-layout/
          dashboard-layout.ts
          dashboard-layout.html
          dashboard-layout.scss
          dashboard-layout.spec.ts
        features/
          auth/auth.routes.ts
          auth/login/login.ts           # inert Login page; no auth behavior
          auth/login/login.html
          auth/login/login.scss
          auth/login/login.spec.ts
          customer/customer.routes.ts
          customer/customer.ts
          customer/customer.html
          customer/customer.scss
          customer/customer.spec.ts
          agent/agent.routes.ts
          agent/agent.ts
          agent/agent.html
          agent/agent.scss
          agent/agent.spec.ts
          welcome/welcome.ts
          welcome/welcome.html
          welcome/welcome.scss
          welcome/welcome.spec.ts
```

No application Dockerfiles/nginx configuration or Docker-build CI job in foundation. A .dockerignore is introduced with the later Docker build contexts, as NFR-2 permits. Git does not retain empty directories: use short boundary documentation/package-info files, not unused barrel exports, fake auth services or unused abstractions. DashboardLayout is the requested minimal routed layout, not a component library. `_mixins.scss` is optional and added only when an actual responsive component needs it.

## 3. Backend dependencies and configuration

### 3.1 Version and dependency contract

Preserve Spring Boot 3.5.x and Java 21. Use the current documented 3.5.16 parent as the reviewed maintenance-patch candidate instead of stale 3.5.6; recheck its support/security status and pin the exact approved patch before scaffolding. This is a patch update within the existing stack, not a framework migration. Use Maven 3.9+; the parent manages plugin/dependency versions. Set `java.version=21` and use the parent's compiler release handling; do not separately specify competing source/target/release values.

| Dependency | Scope / purpose |
|---|---|
| spring-boot-starter-web | Spring MVC |
| spring-boot-starter-validation | Bean Validation |
| spring-boot-starter-security | Minimal deny-all/health security baseline |
| spring-boot-starter-data-jpa | JPA infrastructure, no mapped entities yet |
| spring-boot-starter-actuator | Health/readiness/liveness |
| org.postgresql:postgresql | Runtime driver |
| org.flywaydb:flyway-core | Migration engine |
| org.flywaydb:flyway-database-postgresql | PostgreSQL migration support, same BOM-managed version as core |
| spring-boot-starter-test | Test scope; JUnit Jupiter, Mockito, assertions and Spring testing |
| org.testcontainers:postgresql | Test scope; real PostgreSQL |
| org.testcontainers:junit-jupiter | Test scope; container lifecycle integration |

Use `spring-boot-maven-plugin`; use BOM-managed versions rather than adding independent Flyway/Testcontainers/JUnit pins. The reviewed 3.5 dependency table manages Flyway 11.7.2 and Testcontainers 1.21.4; the previous “Boot 3.5 + Flyway 10” assertion was incorrect. This design uses Testcontainers 1.x artifact/package names, not 2.x examples. Do not import a second Testcontainers BOM.

Use `@DynamicPropertySource` only for test datasource injection. `spring-boot-testcontainers` is unnecessary for that approach and is omitted; it would be needed for a deliberately selected `@ServiceConnection` design instead. JWT resource-server dependencies/configuration are added in the authentication feature, not as unused foundation behavior. Existing Spring Security dependency is required by FR-3.

### 3.2 Base configuration

Illustrative contents of future `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
      show-components: never
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
        liveness:
          include: livenessState
```

Actuator readiness includes database connectivity explicitly; the default readiness group does not automatically include DB. Liveness deliberately excludes DB to avoid restarting live processes for an external outage. Healthy aggregate/probes return 200/UP; unreachable DB after successful startup yields 503/DOWN for aggregate/readiness while liveness can remain 200. Initial database/migration failure prevents startup, so no health response can be promised before startup completes.

Public health responses expose aggregate status only. Component diagnostics are verified in integration tests using the health infrastructure; public disclosure of DB component details would require a requirements/security decision (§11). No metrics or additional management endpoints are exposed in foundation.

Future `application-local.yml` contains no credential overrides:

```yaml
# Activate via SPRING_PROFILES_ACTIVE=local.
# Datasource values are required external environment variables from application.yml.
spring:
  jpa:
    show-sql: false
```

Do not give DB_USERNAME/DB_PASSWORD fallback defaults in Spring configuration or Compose. Missing/unusable configuration must fail clearly without logging secrets. No committed credentials, token keys, dummy “changeme” password or datasource URL with embedded credentials.

### 3.3 Minimal security baseline

Use Http403ForbiddenEntryPoint for the unauthenticated foundation deny-all baseline so its HTTP acceptance test has an explicit 403 contract; the later authentication feature owns 401 application-envelope handling. Keep a single SecurityFilterChain that permits only health GETs under `/actuator/health` and its readiness/liveness paths and denies all other requests. Disable form login and HTTP Basic and use stateless session policy. Do not configure a JWT decoder, login endpoint, generated-password login flow or role guards yet. Limit CSRF exclusions to `/api/**` as NFR-6 specifies; retain default CSRF handling elsewhere instead of blanket `csrf.disable()`.

No CORS bean, filter, allowlist or `http.cors()` is needed for the selected same-origin browser path. Security owns the future resource-server boundary, not a prebuilt CORS setup. Future authentication changes require their own feature design and tests; they must not loosen the baseline prematurely.

## 4. Flyway and the explicit foundation schema exception

**Decision: preserve the existing FR-4 criterion without changing requirements.** Foundation V1 is `V1__create_app_users.sql`, creating app_users exactly as specified by FR-4 and its acceptance checklist. The former comment-only foundation migration is not part of this design. It cannot satisfy a requirement for a real app_users table. No requirements approval is inferred from the file's status; this choice aligns design with the written criterion so approval does not conceal a scope change.

This is a narrow, explicit foundation exception to feature-owned business DDL. Use the canonical app_users schema in [database design](../../../docs/design/database-design.md#2-tables): UUID id PK; normalized unique nonnull email VARCHAR(254); password_hash VARCHAR(255); role VARCHAR(16) constrained to CUSTOMER/AGENT; enabled BOOLEAN default TRUE; created_at TIMESTAMPTZ. Required fields are NOT NULL. Normalization/role validation in application behavior belongs to authentication; do not introduce an entity, repository, password encoder, user service, seeded users or login endpoint here. Other business tables are introduced in their owning feature migrations.

Flyway applies V1 to an empty PostgreSQL database, tracks its checksum/success in flyway_schema_history and creates the table. Authentication introduces AppUser against that schema and uses a later numbered migration only for any needed schema change; it must not create app_users a second time or modify applied V1. Do not enable baseline-on-migrate or confuse a versioned V1 with Flyway's baseline command. The high-level database migration plan is refined by this feature's explicit V1 exception; reconcile later versions at the owning feature design.

With **no mapped entities**, `ddl-auto: validate` has no entity tables/columns to inspect and does not itself require app_users. V1 creates app_users because FR-4 requires it, not to appease Hibernate. Startup still requires working datasource/Flyway configuration. Once authentication maps AppUser, validate must detect mismatched mapped schema. Keep validate in base/tests; no create/update or H2 fallback.

Deferring app_users entirely remains an optional requirements change, not the selected design. It would need explicit approval to amend FR-4 and its checklist before this decision is reversed.

## 5. Backend test design and discovery

Use one `FoundationIntegrationTest` with `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers` and `@ActiveProfiles("test")`. A static `@Container PostgreSQLContainer<?>` uses the same PostgreSQL 17 major as Compose; its actual patch/digest is pinned at implementation. Container credentials are ephemeral defaults generated/provided by the test library, not committed application credentials.

`@DynamicPropertySource` registers spring.datasource.url/username/password from the container getters before application context creation; these override the base DB placeholders. Tests need neither .env nor local Compose, and never fall back to a developer database. Avoid combining dynamic properties with service connections or hardcoded test datasource settings. An application-test.yml may repeat `ddl-auto: validate` for clarity but contains no datasource URL/credentials.

Class names ending in `Test` or `Tests` are found by Maven Surefire; `FoundationIntegrationTest` runs with both `mvn test` and `mvn clean verify`. Do not rename to `*IT` without configuring Failsafe. No silent disabledWithoutDocker skip: unavailable Docker fails with actionable prerequisite guidance.

Assertions must verify:
- Context startup and a live real PostgreSQL connection.
- Flyway reports successful version 1 named create_app_users, verifies app_users exists, and reports no pending/failed migrations; query version-specific metadata or MigrationInfo, not a brittle total-count assertion that fails when later migrations arrive.
- HTTP GET aggregate health/readiness/liveness returns 200 and native UP without redirect/login/component details.
- Application paths are denied by baseline security and no auth endpoint exists.
- Database connectivity is included in readiness membership and excluded from liveness. After startup, an isolated outage smoke check verifies 503 readiness/aggregate and live liveness; keep it separate from normal context tests to avoid poisoning shared state.

A plain contextLoads test does not establish HTTP endpoint accessibility. Use real HTTP requests on the random port for that acceptance criterion. No foundation tests promise domain ownership, JWT behavior, agent concurrency or business-table validation yet.

### 5.1 Executable acceptance-test example (document only)

The following is the proposed `FoundationIntegrationTest.java` body, not an implementation file. It uses only §3.1 dependencies, runs under Surefire, and performs HTTP GETs with no token, cookies or credentials. No separate spring-security-test dependency is needed. Foundation's deny-all chain disables form/basic login and explicitly uses an Http403ForbiddenEntryPoint, so denied anonymous application requests return 403 rather than redirects or a framework-dependent default. Authentication later replaces that entry point with its approved 401 envelope handling.

```java
package com.assessment.disputeportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class FoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsPublicAndOtherPathsAreDenied() {
        for (String path : new String[]{"/actuator/health",
                "/actuator/health/readiness", "/actuator/health/liveness"}) {
            ResponseEntity<JsonNode> response = http.getForEntity(path, JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
            assertThat(response.getBody().has("components")).isFalse();
            assertThat(response.getBody().has("details")).isFalse();
        }
        for (String path : new String[]{"/api/v1/transactions", "/unrelated",
                "/actuator/env"}) {
            ResponseEntity<String> response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getHeaders().getLocation()).isNull();
        }
    }

    @Test
    void requiredFoundationMigrationCreatesUsersWithoutMappedEntities() {
        Integer successfulV1 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '1' AND script = 'V1__create_app_users.sql' "
                        + "AND success = TRUE", Integer.class);
        assertThat(successfulV1).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.app_users')::text", String.class))
                .isEqualTo("app_users");
    }
}
```

Run after implementation with `mvn test -Dtest=FoundationIntegrationTest` or `mvn --batch-mode clean verify`. The example has not been compiled/executed in this design-only pass. It verifies public health and security interception even for unmapped paths; a 404/redirect is not accepted as evidence of deny-all. Additional outage/migration-shape assertions remain in §5. The migration check selects V1 rather than counting all future migrations.

## 6. Angular setup, structure and routing

Retain Angular **20.3.x** standalone/routing/SCSS/strict setup. Resolve and pin compatible CLI/core/build patch versions together before scaffolding, with package-lock.json committed; use the supported-version table again at that time. Node **22.x >=22.12.0** is the selected compatible line; CI and local README use the same line, not a generic unrestricted “Node LTS”. Angular 20 has a limited remaining support window; if support ends before implementation, propose the major-version change explicitly rather than upgrading silently.

Generate with the pinned local Angular CLI (`npm exec`/equivalent), directory frontend, standalone components, routing, SCSS, strict TypeScript, no SSR. Do not rely on an unpinned globally installed `ng`. Use concise filenames consistently: app.ts/app.html/app.scss/app.spec.ts, login.ts/login.html/login.scss/login.spec.ts, dashboard-layout.ts/dashboard-layout.html/dashboard-layout.scss/dashboard-layout.spec.ts, customer.ts and agent.ts. Classes are App, Login, DashboardLayout, Customer and Agent. Imports omit the .ts extension and never add a .component suffix. Keep the Angular 20.3 CLI component schematic type empty (type: "") and prefix app so later generated components follow the same convention. The artifact tree is proposed, not proof of generated files.

Root `/` displays Welcome via an exact full-match route; `/login` lazily loads Login (inert page, no form/token behavior). `/customer` and `/agent` lazily load DashboardLayout, with their respective lazy feature route arrays as children. Root App hosts RouterOutlet; DashboardLayout hosts a child RouterOutlet. Unknown paths redirect to `/` only as a foundation fallback, not a fake authentication redirect. No guards or role-derived navigation are introduced.

**Layout placement:** `layouts/dashboard-layout/` owns the application layout. Its class is **DashboardLayout**, selector **app-dashboard-layout**. `shared/components/` is reserved for reusable UI elements; no application layout belongs there. The foundation DashboardLayout only wraps routed placeholders and has component-scoped styles; no sidebar, authenticated header, role-aware shell or premature UI library is introduced. This structure change is authorized by the current naming request.

Illustrative `layouts/dashboard-layout/dashboard-layout.ts`:

```typescript
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './dashboard-layout.html',
  styleUrl: './dashboard-layout.scss',
})
export class DashboardLayout {}
```

Its dashboard-layout.html contains `<main><router-outlet /></main>`. main.ts bootstraps App imported from `./app/app`, using appConfig from `./app/app.config`; App uses `./app.html` and `./app.scss`. app.spec.ts imports App from `./app`; dashboard-layout.spec.ts imports DashboardLayout from `./dashboard-layout`; login.spec.ts imports Login from `./login`. All standalone tests use TestBed imports rather than declarations.

Illustrative app.routes.ts (paths/imports match §2):

```typescript
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () =>
      import('./features/welcome/welcome').then(m => m.Welcome) },
  { path: '', loadChildren: () =>
      import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES) },
  { path: 'customer', loadComponent: () =>
      import('./layouts/dashboard-layout/dashboard-layout').then(m => m.DashboardLayout),
    loadChildren: () => import('./features/customer/customer.routes')
      .then(m => m.CUSTOMER_ROUTES) },
  { path: 'agent', loadComponent: () =>
      import('./layouts/dashboard-layout/dashboard-layout').then(m => m.DashboardLayout),
    loadChildren: () => import('./features/agent/agent.routes')
      .then(m => m.AGENT_ROUTES) },
  { path: '**', redirectTo: '' },
];
```

AUTH_ROUTES contains `{path: 'login', loadComponent: () => import('./login/login').then(m => m.Login)}`. CUSTOMER_ROUTES and AGENT_ROUTES each contain one empty full-match child loading Customer from `./customer` and Agent from `./agent` respectively. Those route arrays have no wildcard redirects that consume sibling routes. Feature classes are inert display placeholders. Future `/customer/transactions` and `/agent/disputes` routes are added by their owning features.

Use provideRouter(routes) and provideHttpClient() in app.config.ts. No empty registered HTTP interceptor, auth provider, generic BaseApiService or unused barrels. App and DashboardLayout tests provideRouter with test routes; verify welcome/login/customer/agent routing and child outlet rendering without simulating authentication.

No Angular animation providers/configuration are included. The legacy animation providers are deprecated from 20.2; CSS interactions or supported native APIs can be considered only when a later component needs them. Remove leftover animation claims/dependencies that are not part of the selected CLI setup; no animations test/provider is needed for inert placeholders.

## 7. Browser communication and SCSS

### 7.1 Relative API paths and development proxy

Future application requests use relative **`/api/v1`** paths in all environments. Reserve that literal prefix in core boundary documentation; foundation has no business client or request yet. Do not add an absolute localhost backend URL to environments or duplicate environment files solely for API URLs.

Future `frontend/proxy.conf.json`:

```json
{
  "/api/**": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

Use the recursive match for nested API routes with the modern Angular dev server. Configure serve.options.proxyConfig to proxy.conf.json; `npm start` runs `ng serve` through the local CLI, so startup consistently activates the proxy. Forward `/api/v1/...` unchanged; do not strip `/api`. No changeOrigin/logLevel setting is added without a demonstrated need. Restart the dev server after proxy changes.

The browser talks to localhost:4200, whose development server forwards to localhost:8080. It makes no direct cross-origin browser request, so backend CORS configuration is unnecessary. Future static-server reverse proxy preserves the same `/api` prefix. `/actuator` is not a browser application API and is checked directly on backend port 8080; do not proxy or expose management paths just to exercise the frontend proxy.

Because foundation has no API endpoint, a proxy smoke check may receive a denied/not-found response; check forwarding/reachability rather than inventing a business endpoint or claiming a successful transaction request. Test actual frontend/API integration in its owning feature.

### 7.2 Styles

styles.scss is the sole global entry registered in angular.json. It composes these modules:

```scss
@use 'styles/tokens';
@use 'styles/reset';
@use 'styles/typography';
```

- `_tokens.scss`: emits :root CSS custom properties for the approved font-family, primary #2F70EF, hover #2259D0, background #F7F7F7, surface #FFFFFF, text #3A3A3A and border #E1E1E1. Do not claim proposed secondary/error colors or unverified font weights are measured brand tokens; choose/test them in the UI feature.
- `_reset.scss`: minimal box-sizing and body margin reset plus sensible form-font inheritance. Keep native focus, semantics, list markers and link affordances; avoid indiscriminate universal removal of all margins/padding.
- `_typography.scss`: font fallback/body defaults and readable link/heading conventions. Do not remove link underlines globally without another accessible cue. A font-family declaration does not install Nunito Sans; fallback works until a licensed font is deliberately added.
- `_mixins.scss`: optional future responsive helpers. Each component that needs a mixin imports it with @use; adding it globally does not export Sass symbols into component stylesheets. No unused mixin library is scaffolded.

Use @use exclusively for Sass modules, no deprecated @import. Component-specific layout/appearance remains in its component SCSS. No button system, navigation library or role-aware UI is built at foundation.

## 8. Local PostgreSQL and environment loading

Foundation supports **one documented local workflow**, native backend/frontend plus containerized PostgreSQL. Future compose.yaml:

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: dispute_portal
      POSTGRES_USER: ${DB_USERNAME:?Set DB_USERNAME in .env}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Set DB_PASSWORD in .env}
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d \"$$POSTGRES_DB\""]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  postgres_data:
```

Pin image patch/digest consistently with integration tests at scaffolding. Compose automatically reads project-root .env for interpolation; it does **not** export values into a native Maven/Spring process. Spring Boot does not automatically load arbitrary .env files. PostgreSQL initialization credentials apply to a new volume; changing .env does not rotate users/passwords in an existing database. Document that diagnostic distinction; do not prescribe automatic volume deletion.

Future .env.example (non-secret URL and variable names only):

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/dispute_portal
DB_USERNAME=
DB_PASSWORD=
SPRING_PROFILES_ACTIVE=local
```

Developer copies it to an ignored .env and fills local username/password. It uses shell-compatible KEY=value assignments; quote values with spaces/shell metacharacters. Load only the developer's trusted local file, without printing it. No committed usable credential sample. IDE run configurations use these environment values too; no IDE-specific automatic dotenv integration is assumed.

README setup from repository root:

```bash
cp .env.example .env
# Edit .env: choose local DB_USERNAME and DB_PASSWORD before continuing.
set -a
source .env
set +a
docker compose config --quiet
docker compose up -d --wait
```

Backend terminal loads the same root .env before entering backend, then runs `mvn spring-boot:run`. Frontend terminal enters frontend and runs `npm ci` then `npm start`; it does not need secrets. Health URLs are localhost:8080/actuator/health, /readiness and /liveness. The browser opens localhost:4200. Do not suggest export commands containing hardcoded passwords or use full `docker compose config` output that expands secrets. Compose down preserves the named volume by default.

Root .gitignore excludes .env/local environment files (retain .env.example), backend/target, frontend/node_modules/dist/.angular and local IDE/OS files. No secrets enter frontend configuration/build bundles. Application Dockerfiles, nginx runtime, container health gating and Docker image CI belong in deployment/integration-hardening; that later scope is tracked, not silently removed from the overall delivery strategy.

## 9. Angular tests and CI command contract

For the retained Angular 20 line, select **CLI-default Karma/Jasmine**, not experimental Vitest. Use the selected Angular 20.3 CLI-generated @angular/build:karma builder/dependencies, Angular TestBed and its configured zone-testing setup. Preserve the generated compatible configuration; no independent runner/library migration. Calling bare vitest run would bypass the selected Angular CLI build/testing setup and is removed.

Future package.json script subset:

```json
{
  "scripts": {
    "start": "ng serve",
    "build": "ng build",
    "test": "ng test",
    "test:ci": "ng test --watch=false --browsers=ChromeHeadless"
  }
}
```

The selected Angular CLI configuration must match these scripts; this is a minimal angular.json excerpt, not an independently generated workspace:

```json
{
  "version": 1,
  "projects": {
    "dispute-portal": {
      "projectType": "application",
      "root": "",
      "sourceRoot": "src",
      "prefix": "app",
      "schematics": {
        "@schematics/angular:component": {
          "type": "",
          "style": "scss",
          "standalone": true
        }
      },
      "architect": {
        "build": {
          "builder": "@angular/build:application",
          "options": {
            "browser": "src/main.ts",
            "outputPath": "dist/dispute-portal",
            "index": "src/index.html",
            "tsConfig": "tsconfig.app.json",
            "polyfills": ["zone.js"],
            "styles": ["src/styles.scss"]
          }
        },
        "serve": {
          "builder": "@angular/build:dev-server",
          "options": {
            "buildTarget": "dispute-portal:build",
            "proxyConfig": "proxy.conf.json"
          }
        },
        "test": {
          "builder": "@angular/build:karma",
          "options": {
            "tsConfig": "tsconfig.spec.json",
            "polyfills": ["zone.js", "zone.js/testing"],
            "styles": ["src/styles.scss"]
          }
        }
      }
    }
  }
}
```

Preserve generated production/development configurations, budgets and asset settings when implementing this excerpt. The chosen workspace is zone-based consistently in bootstrap/build/test; do not mix a zoneless scaffold with zone-dependent Karma tests. Use CLI-generated tsconfig.app.json and tsconfig.spec.json, with spec include `src/**/*.spec.ts` and Jasmine types so app.spec.ts, login.spec.ts and dashboard-layout.spec.ts are discovered. Install the same pinned @angular/build/CLI/core patch family and generated Karma/Jasmine/zone.js dependencies; no Vitest import/config/script. The browser override is consumed by the Karma builder; it is not a raw test-runner command. Tests with RouterOutlet use provideRouter([]) or real test routes as needed.

`npm test -- --watch=false` satisfies FR-11's one-run command with an available browser; CI uses test:ci. Local headless test users need Chrome/Chromium and CHROME_BIN if not discoverable. GitHub's Ubuntu hosted runner browser availability must be verified; choose a documented Chrome path or set CHROME_BIN after discovery when necessary. No sandbox-disabling browser flags unless required and justified. Lockfile must include the CLI-generated karma-chrome-launcher/Jasmine/build dependencies.

| Step | Directory | Command / environment |
|---|---|---|
| Backend test/build | backend | mvn --batch-mode clean verify, Java 21, Docker available |
| Backend test only | backend | mvn test (Surefire discovers FoundationIntegrationTest) |
| Frontend install | frontend | npm ci (committed package-lock.json) |
| Frontend build | frontend | npm run build |
| Frontend watch tests | frontend | npm test |
| Frontend CI tests | frontend | npm run test:ci (ChromeHeadless available) |
| Backend dependency check | backend | mvn dependency:tree; inspect effective-pom/BOM versions |
| Frontend dependency check | frontend | npm ls; ng version via local CLI |

CI has separate backend/frontend jobs on Ubuntu hosted runners, checkout, Java 21 and Node 22.x (>=22.12) setup. Maven/npm caches use their real paths/lockfile. Backend job uses runner Docker for Testcontainers, never a second CI PostgreSQL service or .env secrets. Frontend job installs, builds, then invokes test:ci. Verify failures propagate; do not append success-forcing shell constructs. No Docker-image build or lint job until its build context/linter exists; eventual delivery steering gates apply when those artifacts are introduced. Pin supported action versions/commit SHAs when creating the workflow. No workflow execution is claimed by this design-only review.

## 10. Verification and traceability

| Requirement | Design coverage / limitation |
|---|---|
| FR-1–3 | Java/Maven/packages/dependencies, external datasource, secure baseline (§2–3) |
| FR-4 | V1__create_app_users.sql creates the explicitly required table; no entity/auth behavior; written requirements preserved (§4–5) |
| FR-5 | Native health/probes with explicit DB readiness; public details criterion needs clarification |
| FR-6 | Executable FoundationIntegrationTest example with real PostgreSQL, V1 and unauthenticated health/403 checks; runs under Surefire (§5.1) |
| FR-7–9 | App/Login concise filenames; inert customer/agent routes inside DashboardLayout and separate login route; shared/components reserved for reusable UI; Sass @use (§2, §6–7) |
| FR-10 | Relative /api/v1 + HttpClient/provider + proxy; empty interceptor criterion deliberately deferred — amendment needed |
| FR-11 | Karma/Jasmine, @angular/build:karma, Jasmine spec config and ChromeHeadless script agree (§9); erroneous requirements runner label still needs correction |
| NFR-1–5 | PostgreSQL-only Compose, ignored secrets, reproducible setup, compatibility and CI (§8–9) |
| NFR-6 | Deny-all baseline, health only, no CORS/auth behavior; service-placeholder wording needs clarification |

Approved ownership/lifecycle/concurrency/API/authentication business decisions stay unchanged for later features. Foundation does not implement those features merely because enduring steering describes them. The existing [deployment diagram](../../../docs/design/plantuml/container-deployment.puml) is the eventual application-container topology, not the foundation local workflow; local PostgreSQL-only setup is already the foundation requirement. The approved [API contract](../../../docs/design/api-contract.md) and [database design](../../../docs/design/database-design.md) remain authoritative for feature behavior; their global migration plan must be reconciled with feature-owned numbering when specifications are approved. The structure steering's missing review document reference is flagged in §11.

Document checks passed: three YAML and three JSON examples parse, relative links resolve, and naming/layout plus runner/script invariants agree across trees/examples. Superseded filename, layout-location and previous-migration references are absent. CLI 20.3 Karma/component schemas were inspected to verify the selected options. These checks cover documents, not compiled behavior. The accompanying tasks.md records implementation work and acceptance checks; no task has been executed. This pass checks document examples and primary-source consistency; it does not execute the documented Java acceptance test. No Maven/Angular scaffold/build, migration, container, CI or browser integration test has been executed; no source files/configuration files have been created. Implementation acceptance requires those actual runs.

## 11. Requirements amendments and approval status

requirements.md is unchanged. The developer has authorized proceeding to task planning using this corrected foundation design. requirements.md remains unchanged; these wording discrepancies are explicitly carried into the task approval gate:

| Item | Current wording / defect | Proposed clarification for approval |
|---|---|---|
| FR-4 | Explicit V1__create_app_users.sql requirement | Resolved by retaining its table creation in foundation as the narrow schema exception; no requirements edit or implicit approval. Complete deferral would still need an explicit future amendment |
| FR-11 | Says Vitest is default for Angular 20+ | Correct to selected CLI/version default; Angular 20 uses Karma/Jasmine, Vitest is experimental in v20; later versions have different defaults |
| FR-8 / NFR-6 frontend | Names auth services/guards/interceptors and an in-memory auth-service placeholder | Reserve directory responsibility only; no executable auth service/guard/token placeholder in foundation |
| FR-10 | Requires an empty interceptor structure | Reserve interception boundary in documentation; no registered or executable no-op interceptor until authentication/error handling feature |
| FR-5 DB outage | Expects database component DOWN visible on health endpoint | Require aggregate/readiness 503/DOWN with public status only; verify DB component privately in tests, liveness remains independent |
| NFR-4 / existing patch pins | Supported Boot/Angular required, old design pins stale Boot patch and vague Node LTS | Recheck/pin compatible patches before scaffolding; retain Boot 3.5.x/Angular 20.3.x/Node 22 compatible floor unless support requires an explicitly reviewed change |

**Steering/document follow-ups:** delivery/test steering describes eventual multi-stage Dockerfiles/Docker builds, not a requirement to create incomplete builds in foundation. Mark deployment-task ownership at the next approved spec. `.kiro/steering/structure.md` references `docs/design/review-round-2.md`, which is absent from the current checkout; restore that approved document or approve updating the reference to existing canonical design before relying on its module matrix. Do not invent or recreate historical approval evidence. These are documented alignment items; no requirements/steering files were silently rewritten.

Naming/layout corrections are applied consistently. The migration mismatch is resolved by honoring FR-4 rather than changing requirements. Remaining requirement clarifications are disclosed above. Local proxy, credentials and runner choices are now internally consistent at design level. Detailed image/dependency pinning and actual build/container/HTTP/CI verification remain implementation acceptance work. The accompanying tasks.md follows this design without expanding foundation scope. Stop after task planning; approval of the task plan and its disclosed requirements clarifications precedes application implementation.

## 12. Primary-source checks

- [Angular 20.3 component schematic](https://raw.githubusercontent.com/angular/angular-cli/20.3.x/packages/schematics/angular/component/schema.json): concise component naming and explicit selector/type options.
- [Angular 20.3 Karma builder schema](https://raw.githubusercontent.com/angular/angular-cli/20.3.x/packages/angular/build/src/builders/karma/schema.json): tsConfig, polyfills, styles, watch and browser options used by the test contract.

- [Angular v20 test-runner guidance](https://v20.angular.dev/guide/testing/unit-tests): Karma default, Vitest experimental; v20 runner selection retained.
- [Angular compatibility](https://angular.dev/reference/versions): Node 22.12+ compatible with Angular 20.3; exact versions rechecked before scaffolding.
- [Angular animation provider API](https://angular.dev/api/platform-browser/animations/async/provideAnimationsAsync): legacy provider deprecated from 20.2; no provider required here.
- [Angular dev-server proxy guidance](https://angular.dev/tools/cli/serve): configure proxy through serve; recursive /api/** matching preserves nested paths.
- [Spring Boot 3.5 managed dependencies](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html): parent/BOM controls Flyway and Testcontainers versions.
- [Boot 3.5.6 dependency source](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.5.6/spring-boot-project/spring-boot-dependencies/build.gradle): even the previous patch managed Flyway 11.7, not the previously claimed Flyway 10.
- [Spring Testcontainers integration](https://docs.spring.io/spring-boot/3.5/reference/testing/testcontainers.html): dynamic properties are a supported alternative to service connections.
- [Spring Actuator health groups](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html): database readiness membership must be explicit; do not couple external DB outage to liveness.
