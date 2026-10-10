# Project foundation — final acceptance review

**Date:** 2026-10-10  
**Review status:** Task 9 review complete; core foundation verified, residual acceptance limits disclosed below.  
**Evidence:** [tasks.md](tasks.md), especially Tasks 1–8.  
**Baseline:** [design.md](design.md) and unchanged [requirements.md](requirements.md).

The developer approved the task plan and its recorded requirements clarifications. This review does not amend requirements, authorize authentication or claim all unmeasured criteria are satisfied. Build/runtime assertions derive from actual recorded local runs; hosted success is developer-confirmed rather than independently inspected by the agent.

## Requirement review

| Criterion | Evidence and result |
|---|---|
| FR-1 backend build/start/connect | Maven clean verify and executable JAR build passed; native Maven spring-boot:run connected to a fresh PostgreSQL database and started. The exact clean install command was not separately executed. |
| FR-2 modular structure | Seven feature packages with documented boundaries; no business classes or cross-module persistence access. |
| FR-3 dependencies/configuration | Boot-managed dependency tree/effective POM verified; Java 21; external datasource variables, Flyway, ddl-auto validate, Open Session in View disabled, health-only exposure. |
| FR-4 migration | Real V1__create_app_users.sql applied on empty DB; repeat checksum validation passed. Types/defaults/nullability/unique/PK/role checks passed with rolled-back test rows; no seeds/entities. |
| FR-5 health | Real anonymous HTTP health/probes 200/UP; isolated DB outage gives aggregate/readiness 503/DOWN with liveness 200/UP. Private DB indicator inspected; public diagnostics hidden under approved clarification. Native health group names are allowed. |
| FR-6 backend tests | Six Surefire tests passed in clean-copy Maven verification, zero failures/errors/skips; real pinned PostgreSQL Testcontainers, isolated outage container/context, no local DB dependency. |
| FR-7 frontend | Strict standalone Angular 20.3, Node 22.23.3, production build, native npm start verified. Local lockfile npm ci and npm ls succeeded without invalid peers. |
| FR-8 routing/layout | Lazy Welcome/Login/Customer/Agent pages, concise filenames, DashboardLayout only under layouts; twelve frontend tests cover sibling navigation, fallback and child rendering. Core/shared responsibilities documented; no executable auth placeholders under approved clarification. |
| FR-9 design tokens | Approved font/color variables compiled; live Chromium verified body typography/background/text/reset. Sass @use modules, component styles scoped; no downloaded font assets or UI library. |
| FR-10 communication | provideHttpClient, relative /api/v1 boundary and /api/** dev proxy unchanged to 8080; forwarding yielded real backend 403. No CORS/base service/no-op interceptor under approved clarification. |
| FR-11 test runner | CLI Karma/Jasmine config/polyfills/spec discovery match scripts. Twelve tests passed; temporary deliberately failing assertion returned 1, restored suite returned 0. Approved correction to requirements' erroneous Vitest default. |
| NFR-1 infrastructure | Pinned PostgreSQL 17.11 image/digest, external credentials, loopback 5432, named volume and health check; clean-volume Compose/startup smoke passed. |
| NFR-2 ignored files/secrets | Tracked-file review confirms no .env or installed/build artifacts; configuration password values are blank templates or external placeholders. No application Docker builds, so .dockerignore remains deferred. |
| NFR-3 documentation/setup | README commands verified with clean source copy and fresh volume, using available dependency caches. Ten-minute first-time setup threshold has not been measured with uncached downloads. |
| NFR-4 compatibility | Effective POM, Java bytecode, npm ls and official version compatibility checked. Exact Angular patches and Node pinned; development-tooling security advisories remain below. |
| NFR-5 CI | SHA-pinned workflow passed actionlint; job commands passed locally from clean copies. Developer reported hosted pipelines passed after scaffold commit b7647f9; no run URL/ID independently inspected. |
| NFR-6 baseline | Health GET allowlist, otherwise 403; no form/basic/generated user, stateless sessions, API-only CSRF exemption. No frontend token state/auth behavior/CORS or signing keys. |

## Edge-case verification limits

Missing Compose credentials fail explicitly; backend startup without external datasource configuration fails, and README explains its URL error. A occupied frontend port produced a clear failure; existing workspace server was identified before restart. Flyway checksum validation on a second run passed. Database outage after startup was verified in a separate context/container.

Initial backend startup against an unavailable DB (EC-2), an intentionally corrupted Flyway migration (EC-4), backend port conflict and wrong Java/Node build runtime rejection (EC-3/EC-5) were not separately exercised. These are residual verification limits, not claimed passing checks. The required runtime versions and troubleshooting are documented. Existing successful builds do not prove those negative paths.

## Remaining issues and disposition

- Six high-severity npm development-tooling entries through Karma/braces remain disclosed; production-only audit reported zero. No incompatible downgrade, override or runner migration was approved or applied. Review remediation when compatible tooling becomes available.
- Ten-minute first-time setup target and negative-path checks above remain unverified. Acceptance of those limits belongs to the developer; this report does not silently waive them.
- Hosted CI success is developer-confirmed; capture a run URL/ID when available for an independently retrievable record.
- Documentation/reference commit 1bdb45d is local because git push lacked GitHub credentials. This review's additional documentation is also local until published. No remote CI success is asserted for those subsequent documentation changes.
- System Maven, compatible Node and Chrome remain local prerequisites; temporary tools were used for this machine's verification. Application Dockerfiles, reverse-proxy deployment, Docker-image CI and business/authentication features remain deferred to owning specifications.

Approved architecture/requirements are unchanged. The missing historical review references were replaced with approved pointers to existing design, component diagram and actual task evidence; no past sign-off was reconstructed. Final source review found no obsolete component filenames, misplaced layouts, application controllers, guards/interceptors, entities or extra business migrations.

Task 9 completes the review and evidence reporting. Core foundation is ready to support the next feature specification with these limits visible; unconditional acceptance of every original criterion is not claimed. Stop here: no next-feature specification, tasks or application code is generated automatically.
