# Requirements: Project Foundation & Scaffolding

**Feature:** `project-foundation`  
**Status:** Requirements v2 (awaiting approval)  
**Created:** 2026-10-10  
**Revised:** 2026-10-10  
**Owner:** Development Team

## 1. Purpose and scope

Scaffold a clean, maintainable, production-oriented foundation for the Transaction Dispute Portal full-stack application. This specification covers only project structure, build tooling, local infrastructure, testing foundation, and basic health verification — **not** business features.

### 1.1 In scope

- Java 21 and Spring Boot 3.x modular monolith with Maven
- PostgreSQL database connectivity
- Flyway migration infrastructure
- Angular standalone component application
- Docker Compose for local PostgreSQL
- GitHub Actions CI foundation
- Reproducible local development commands
- Appropriate .gitignore and .dockerignore files
- Health and readiness endpoints
- JUnit 5 and Testcontainers backend testing foundation
- Angular unit testing setup

### 1.2 Out of scope (deferred to later specifications)

- Login or JWT issuance
- Customer or agent business functionality
- Transaction APIs
- Dispute submission or resolution
- Search and filtering functionality
- Audit history business logic
- Production AWS infrastructure
- Demo user seeding (belongs to authentication feature)

### 1.3 Success criteria

Developers can:

1. Clone the repository and follow documented commands to start the application
2. Build and run the Spring Boot backend successfully
3. Build and run the Angular frontend successfully
4. Start PostgreSQL through Docker Compose
5. Verify Spring Boot connects to PostgreSQL
6. Verify Flyway migrations execute successfully
7. Access health check endpoints that report application readiness
8. Run backend and frontend tests
9. See GitHub Actions validate the project
10. Navigate a clear modular structure aligned with approved architecture

## 2. Functional requirements

### FR-1: Backend application foundation

**As a** developer  
**I need** a Spring Boot 3.x application with Maven  
**So that** I can build a Java 21 modular monolith backend

**Acceptance criteria:**

- **GIVEN** the project repository  
  **WHEN** a developer runs `mvn clean install` in the backend directory  
  **THEN** the application builds successfully without errors

- **GIVEN** a running PostgreSQL database  
  **WHEN** a developer starts the Spring Boot application  
  **THEN** the application starts and connects to PostgreSQL without errors

- **GIVEN** the application is running  
  **WHEN** startup completes  
  **THEN** logs confirm successful database connection and Flyway migration execution

### FR-2: Modular monolith package structure

**As a** developer  
**I need** a clear package structure organized by feature  
**So that** I can maintain module boundaries as the application grows

**Acceptance criteria:**

- **GIVEN** the backend source code  
  **WHEN** a developer inspects `src/main/java/com/assessment/disputeportal/`  
  **THEN** they see the following package structure:
  - `auth` (authentication placeholder)
  - `security` (security configuration placeholder)
  - `account` (account domain placeholder)
  - `transaction` (transaction domain placeholder)
  - `dispute` (dispute domain placeholder)
  - `idempotency` (idempotency infrastructure placeholder)
  - `common` (shared technical infrastructure)

- **GIVEN** the module structure  
  **WHEN** reviewing code organization  
  **THEN** no business logic exists yet — only structural placeholders

### FR-3: Spring dependencies and configuration

**As a** developer  
**I need** appropriate Spring Boot dependencies configured  
**So that** the foundation supports REST APIs, security, persistence, and actuator endpoints

**Acceptance criteria:**

- **GIVEN** the Maven POM  
  **WHEN** reviewing dependencies  
  **THEN** it includes:
  - `spring-boot-starter-web` (Spring MVC)
  - `spring-boot-starter-validation` (Bean Validation)
  - `spring-boot-starter-security` (Spring Security)
  - `spring-boot-starter-data-jpa` (Spring Data JPA)
  - `spring-boot-starter-actuator` (health and metrics)
  - `org.postgresql:postgresql` driver
  - `flyway-core` (Flyway engine)
  - `flyway-database-postgresql` (required PostgreSQL dialect support; extracted from `flyway-core` as of Flyway 10, which Spring Boot 3.3+ brings in)

- **GIVEN** the application configuration  
  **WHEN** reviewing `application.yml`  
  **THEN** it configures:
  - Server port (default 8080)
  - PostgreSQL datasource with environment variable placeholders
  - JPA `ddl-auto: validate` (Flyway is schema authority)
  - Flyway migration location
  - Actuator health endpoints

- **GIVEN** the configuration files  
  **WHEN** checking for sensitive data  
  **THEN** no plaintext passwords, signing keys, or real credentials are committed

### FR-4: Database connectivity and Flyway

**As a** developer  
**I need** Flyway migration infrastructure  
**So that** database schema changes are version-controlled and reproducible

**Acceptance criteria:**

- **GIVEN** the backend application  
  **WHEN** Flyway is configured  
  **THEN** migrations are located at `src/main/resources/db/migration/`

- **GIVEN** an empty PostgreSQL database  
  **WHEN** the application starts  
  **THEN** Flyway creates and populates the `flyway_schema_history` metadata table

- **GIVEN** Flyway migration files  
  **WHEN** the application starts for the first time  
  **THEN** an initial migration `V1__create_app_users.sql` creates the `app_users` table — the first real entity required by the authentication feature — as a meaningful first migration rather than an artificial scaffold-only table

- **GIVEN** the application configuration  
  **WHEN** reviewing database connection settings  
  **THEN** connection URL, username, and password use environment variables or externalized configuration

### FR-5: Health and readiness endpoints

**As a** developer or operator  
**I need** health check endpoints  
**So that** I can verify application and database readiness

**Acceptance criteria:**

- **GIVEN** the application is running  
  **WHEN** accessing `/actuator/health`  
  **THEN** it returns HTTP 200 with `{"status":"UP"}` when healthy

- **GIVEN** the application is running  
  **WHEN** accessing `/actuator/health/readiness`  
  **THEN** it returns HTTP 200 when ready to serve traffic

- **GIVEN** the application is running  
  **WHEN** accessing `/actuator/health/liveness`  
  **THEN** it returns HTTP 200 when the application is live

- **GIVEN** the database is unreachable  
  **WHEN** checking health  
  **THEN** the health endpoint reports degraded status with database component DOWN

### FR-6: Backend testing foundation

**As a** developer  
**I need** JUnit 5 and Testcontainers configured  
**So that** I can write unit and integration tests

**Acceptance criteria:**

- **GIVEN** the Maven POM  
  **WHEN** reviewing test dependencies  
  **THEN** it includes:
  - `spring-boot-starter-test` (JUnit 5, Mockito, Spring Test)
  - `org.testcontainers:postgresql` (PostgreSQL Testcontainers module)
  - `org.testcontainers:junit-jupiter` (JUnit 5 integration)

- **GIVEN** the test source directory  
  **WHEN** running `mvn test`  
  **THEN** a Spring application context load test passes

- **GIVEN** Testcontainers is configured  
  **WHEN** running the integration test suite  
  **THEN** at least one `@SpringBootTest` integration test starts a real PostgreSQL container via Testcontainers, connects to it, and verifies that Flyway migrations execute successfully against it

### FR-7: Frontend application foundation

**As a** developer  
**I need** an Angular application with standalone components  
**So that** I can build a modern TypeScript frontend

**Acceptance criteria:**

- **GIVEN** the project repository  
  **WHEN** a developer runs `npm install` and `npm run build` in the frontend directory  
  **THEN** the Angular application builds successfully

- **GIVEN** the frontend application  
  **WHEN** a developer runs `npm start` or `ng serve`  
  **THEN** the application starts and serves at `http://localhost:4200`

- **GIVEN** the Angular configuration  
  **WHEN** reviewing `angular.json` and `tsconfig.json`  
  **THEN** TypeScript strict mode is enabled

### FR-8: Frontend structure and routing

**As a** developer  
**I need** a clear Angular application structure  
**So that** I can organize components by feature

**Acceptance criteria:**

- **GIVEN** the frontend source code  
  **WHEN** a developer inspects `src/app/`  
  **THEN** they see the following structure:
  - `core/` (authentication services, guards, interceptors)
  - `shared/` (reusable UI components, utilities)
  - `features/` with subdirectories:
    - `customer/` (customer feature placeholder)
    - `agent/` (agent feature placeholder)
    - `auth/` (authentication feature placeholder)

- **GIVEN** the application routing  
  **WHEN** starting the application  
  **THEN** Angular Router is configured with lazy-loaded feature routes (placeholder routes only)

- **GIVEN** the application  
  **WHEN** navigating to the root URL  
  **THEN** a simple welcome or placeholder page is displayed

### FR-9: Frontend design token foundation

**As a** developer  
**I need** SCSS design tokens configured  
**So that** I can maintain consistent styling based on approved Capitec-inspired design

**Acceptance criteria:**

- **GIVEN** the frontend styles  
  **WHEN** reviewing `src/styles.scss` or a shared `_tokens.scss` file  
  **THEN** it defines design tokens including:
  - `--font-family: 'Nunito Sans', Arial, Helvetica, sans-serif`
  - `--color-primary: #2F70EF`
  - `--color-primary-hover: #2259D0`
  - `--color-background: #F7F7F7`
  - `--color-surface: #FFFFFF`
  - `--color-text: #3A3A3A`
  - `--color-border: #E1E1E1`

- **GIVEN** the token file  
  **WHEN** reviewing design tokens  
  **THEN** they match the approved Capitec-inspired palette from `docs/design/ui-flows.md`

### FR-10: Frontend API client configuration

**As a** developer  
**I need** HTTP client configuration  
**So that** I can make API requests to the backend

**Acceptance criteria:**

- **GIVEN** the Angular application  
  **WHEN** reviewing dependencies  
  **THEN** `@angular/common/http` is available

- **GIVEN** the API client setup  
  **WHEN** reviewing core services  
  **THEN** a base API service or environment configuration defines the backend base URL

- **GIVEN** the HTTP configuration  
  **WHEN** making API requests  
  **THEN** an HTTP interceptor structure exists (empty initially) for future authentication headers and error handling

### FR-11: Frontend unit testing foundation

**As a** developer  
**I need** Angular testing configured  
**So that** I can write component and service tests

**Acceptance criteria:**

- **GIVEN** the Angular application  
  **WHEN** running `npm test -- --watch=false` (or the Angular CLI equivalent for the selected runner)  
  **THEN** the tests run once and exit with a zero status code

- **GIVEN** the test infrastructure  
  **WHEN** reviewing test configuration  
  **THEN** the test runner is whatever the installed Angular version's CLI scaffolds by default (Vitest for Angular 20+; Karma/Jasmine for older supported versions); no alternative runner is introduced without a documented reason

- **GIVEN** placeholder components  
  **WHEN** running tests  
  **THEN** at least one generated component test passes (e.g. the root `AppComponent` spec)

## 3. Non-functional requirements

### NFR-1: Local infrastructure reproducibility

**As a** developer  
**I need** Docker Compose for local PostgreSQL  
**So that** I can run the application without manual database setup

**Acceptance criteria:**

- **GIVEN** a `compose.yaml` file in the repository root  
  **WHEN** running `docker compose up -d`  
  **THEN** a PostgreSQL container starts with:
  - PostgreSQL 15 or compatible version
  - Database name: `dispute_portal`
  - Port mapping: `5432:5432`
  - Environment variables for username and password

- **GIVEN** the Docker Compose configuration  
  **WHEN** reviewing environment variables  
  **THEN** credentials match those referenced in backend `application.yml`

- **GIVEN** the repository  
  **WHEN** reviewing the root directory  
  **THEN** a `.env.example` file documents required environment variables

- **GIVEN** Docker Compose is running  
  **WHEN** the backend application starts  
  **THEN** it connects to the PostgreSQL container successfully

### NFR-2: Version control and ignored files

**As a** developer  
**I need** appropriate `.gitignore` files  
**So that** build artifacts and secrets are not committed

**Acceptance criteria:**

- **GIVEN** the repository root  
  **WHEN** reviewing `.gitignore`  
  **THEN** it excludes:
  - IDE files (`.idea/`, `.vscode/`, `*.iml`)
  - OS files (`.DS_Store`, `Thumbs.db`)
  - Environment files (`.env`)
  - Docker volumes and local data

- **GIVEN** the backend directory  
  **WHEN** reviewing `.gitignore` or root `.gitignore` backend patterns  
  **THEN** it excludes:
  - Maven build artifacts (`target/`)
  - IDE project files

- **GIVEN** the frontend directory  
  **WHEN** reviewing `.gitignore` or root `.gitignore` frontend patterns  
  **THEN** it excludes:
  - Node modules (`node_modules/`)
  - Angular build output (`dist/`, `.angular/`)

- **GIVEN** the repository root  
  **WHEN** reviewing `.dockerignore` (if Docker builds are defined)  
  **THEN** it excludes unnecessary files from Docker build context

### NFR-3: Build and run documentation

**As a** developer  
**I need** clear documentation to build and run the application  
**So that** I can get started quickly

**Acceptance criteria:**

- **GIVEN** the repository root  
  **WHEN** reading `README.md`  
  **THEN** it includes:
  - Project description
  - Prerequisites (Java 21, Node.js, Docker)
  - Local setup instructions (Docker Compose, backend, frontend)
  - Commands to build and run the application
  - Commands to run tests
  - Health check URL

- **GIVEN** the README  
  **WHEN** following setup instructions  
  **THEN** a new developer can start the application within 10 minutes

### NFR-4: Dependency version compatibility

**As a** developer  
**I need** compatible dependency versions  
**So that** the application builds without version conflicts

**Acceptance criteria:**

- **GIVEN** the Maven POM  
  **WHEN** reviewing `spring-boot-starter-parent`  
  **THEN** it uses a supported Spring Boot 3.x release

- **GIVEN** the Angular `package.json`  
  **WHEN** reviewing Angular dependencies  
  **THEN** it uses a supported Angular version

- **GIVEN** the backend dependencies  
  **WHEN** running `mvn dependency:tree`  
  **THEN** no version conflicts are reported

- **GIVEN** the frontend dependencies  
  **WHEN** running `npm list` or `npm install`  
  **THEN** no critical peer dependency warnings appear

### NFR-5: CI/CD foundation with GitHub Actions

**As a** developer  
**I need** GitHub Actions CI configured  
**So that** builds and tests run automatically on push and pull requests

**Acceptance criteria:**

- **GIVEN** the repository  
  **WHEN** reviewing `.github/workflows/`  
  **THEN** a CI workflow file exists (e.g., `ci.yml`)

- **GIVEN** the CI workflow  
  **WHEN** a commit is pushed or a pull request is opened  
  **THEN** the workflow:
  - Checks out the code
  - Sets up Java 21
  - Builds the backend with Maven (`mvn clean verify`)
  - Runs backend unit and integration tests (Testcontainers requires Docker available on the CI runner)
  - Sets up Node.js
  - Installs frontend dependencies with `npm ci`
  - Builds the frontend with `npm run build`
  - Runs frontend tests in CI mode (non-interactive, single run, exits with non-zero status on failure)

- **GIVEN** the CI workflow  
  **WHEN** all steps succeed  
  **THEN** the workflow reports success

- **GIVEN** the CI workflow  
  **WHEN** any step fails  
  **THEN** the workflow reports failure and provides clear logs

### NFR-6: Security baseline

**As a** developer  
**I need** a secure baseline configuration  
**So that** the application is not vulnerable by default

**Acceptance criteria:**

- **GIVEN** the repository  
  **WHEN** reviewing committed files  
  **THEN** no plaintext passwords, API keys, or signing keys are present

- **GIVEN** the backend security configuration  
  **WHEN** Spring Security is configured  
  **THEN** it has a placeholder `SecurityFilterChain` bean that:
  - Permits unauthenticated access to `/actuator/health/**` and `/actuator/health/readiness` and `/actuator/health/liveness` so health checks remain operational
  - Denies all other requests by default (no `permitAll` for application endpoints)
  - Disables CSRF protection only for stateless API paths (the application is JWT-based and has no form-login sessions)
  - Provides a documented placeholder for future JWT resource server configuration

- **GIVEN** the application configuration  
  **WHEN** reviewing CORS settings  
  **THEN** CORS is either not configured yet or uses safe defaults (not wildcard `*` with credentials)

- **GIVEN** the frontend  
  **WHEN** reviewing authentication service placeholder  
  **THEN** it does not store tokens in localStorage (in-memory state placeholder only)

## 4. Edge cases and constraints

### EC-1: Missing or incorrect environment variables

**GIVEN** the backend application starts without required environment variables  
**WHEN** database connection is attempted  
**THEN** the application fails gracefully with a clear error message

### EC-2: Docker Compose not running

**GIVEN** PostgreSQL is not running  
**WHEN** the backend application starts  
**THEN** it fails to connect and logs a clear database connection error

### EC-3: Port conflicts

**GIVEN** port 8080 (backend) or 4200 (frontend) is already in use  
**WHEN** starting the application  
**THEN** a clear error message indicates the port conflict

### EC-4: Flyway migration failure

**GIVEN** a corrupted or invalid migration file  
**WHEN** the application starts  
**THEN** Flyway fails startup with a clear error message

### EC-5: Node.js or Java version mismatch

**GIVEN** the developer uses an unsupported Java or Node.js version  
**WHEN** building the application  
**THEN** a clear error message indicates the required version

## 5. Out of scope (explicit exclusions)

The following are explicitly **not** part of this specification:

- Login endpoints or JWT token generation
- Customer or agent user interfaces beyond placeholder routing
- Transaction API endpoints
- Dispute submission or resolution endpoints
- Search and filtering implementation
- Audit event tracking (beyond table structure if included in baseline migration)
- Seeded demo users or data (deferred to authentication feature)
- Production deployment configuration
- Monitoring or observability beyond basic health checks
- Rate limiting or throttling
- API documentation (OpenAPI/Swagger) generation

## 6. Dependencies and assumptions

### Dependencies

- **None** (this is the first feature specification)

### Assumptions

- Developers have Java 21, Node.js (LTS), Docker, and Maven installed locally
- PostgreSQL 15 or compatible version is used
- The repository is hosted on GitHub with Actions enabled
- Internet access is available for downloading dependencies

## 7. Testing strategy

### Backend testing

- **Context load test:** Verify Spring application context loads successfully
- **Flyway integration test:** A `@SpringBootTest` using a real Testcontainers PostgreSQL instance verifies that Flyway migrations execute without errors and the schema is created as expected
- **Health endpoint test:** Verify actuator health endpoints return expected HTTP 200 and UP status

### Frontend testing

- **Build test:** Verify Angular application builds without errors
- **Component test:** Verify the root `AppComponent` spec passes using the Angular-version-default test runner
- **CI mode test:** Verify `npm run test:ci` (or equivalent) exits cleanly in non-interactive mode

### Integration testing

- **Integration smoke test:** Verify the backend connects to PostgreSQL, Flyway migrations run, and the health endpoint returns UP — confirming the backend, database, and (by proxy of a successful build) the frontend can be started together from the documented commands

### CI testing

- **GitHub Actions test:** Verify CI workflow runs successfully on a sample commit

## 8. Traceability to business rules and architecture

| Requirement | Architecture Document | Rationale |
|---|---|---|
| FR-2 Modular monolith | `docs/business-rules.md` §10 | Package structure supports future feature modules |
| FR-4 Flyway | `docs/design/database-design.md` §6 | Version-controlled schema migrations |
| FR-5 Health endpoints | `docs/design/api-contract.md` §1 | Operational readiness verification |
| FR-9 Design tokens | `docs/design/ui-flows.md` §2 | Capitec-inspired visual foundation |
| NFR-1 Docker Compose | `docs/business-rules.md` §11 | Reproducible local development |
| NFR-5 GitHub Actions | `docs/business-rules.md` §11 | CI validation |
| NFR-6 Security baseline | Security steering rules | No committed secrets, safe defaults |

## 9. Acceptance checklist

Before marking this specification complete, verify:

- [ ] Backend builds successfully with `mvn clean verify`
- [ ] Backend starts and connects to PostgreSQL
- [ ] Flyway `flyway_schema_history` table is created on first startup
- [ ] `V1__create_app_users.sql` migration executes without errors
- [ ] Health endpoints return HTTP 200 when healthy; database DOWN reflected when unreachable
- [ ] At least one Testcontainers PostgreSQL integration test passes with `mvn test`
- [ ] Spring Security permits `/actuator/health/**` without authentication; all other paths denied by default
- [ ] Frontend builds successfully with `npm run build`
- [ ] Frontend starts with `npm start`
- [ ] Frontend tests pass in CI mode (non-interactive, exits on completion)
- [ ] Docker Compose starts PostgreSQL successfully with `docker compose up -d`
- [ ] GitHub Actions CI workflow runs `mvn clean verify` and frontend tests in CI mode, both passing
- [ ] README provides clear setup instructions including all prerequisite versions
- [ ] No secrets or credentials are committed to the repository
- [ ] `flyway-database-postgresql` dependency is present alongside `flyway-core`
- [ ] Package structure matches approved modular monolith architecture

## 10. Sign-off

This requirements document defines the scope of the project foundation scaffolding. It does not authorize design or implementation.

**Next steps:**
1. Developer reviews and approves these requirements
2. Create `design.md` for technical design and component specifications
3. Create `tasks.md` for implementation task breakdown
4. Implement tasks with approval at each phase boundary

---

**Status:** Awaiting developer approval before proceeding to design phase.
