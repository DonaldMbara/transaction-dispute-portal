---
inclusion: always
---
# Technology decisions
- Backend: Java 21, supported Spring Boot 3.x release, Spring MVC, Spring Security, Spring Session JDBC backed by PostgreSQL, Spring Data JPA, Bean Validation, Flyway, Maven.
- Data: PostgreSQL; money as BigDecimal/NUMERIC(19,2), currency ZAR, UTC instants.
- Frontend: supported Angular version, standalone components, TypeScript strict mode, SCSS, Angular Router, Reactive Forms, HttpClient, signals for local UI state, RxJS for async streams.
- Tests: JUnit 5, Mockito, Spring Boot integration tests, Testcontainers PostgreSQL, Angular unit/component tests.
- Delivery: one monorepo, Docker Compose, multi-stage Dockerfiles, GitHub Actions CI.
- Authentication: admin-controlled account provisioning with versioned PBKDF2-HMAC-SHA256 (`{pbkdf2-sha256-v1}`, random 16-byte salt, 600,000 iterations, 256-bit derived key), benchmarked on assessment hardware; no public registration. Spring Security and PostgreSQL-backed Spring Session JDBC use opaque session identifiers in HttpOnly cookies, Secure and SameSite=Lax in production, with a 30-minute server-enforced idle timeout. Protect unsafe API requests, including login/logout, with CSRF. No JWTs, refresh tokens, localStorage authentication tokens or bearer-token interceptors. Keep same-origin /api/v1 requests. Implemented contracts are in docs/design/api-contract.md and operational procedures in docs/operations/authentication.md; preserve historical foundation specifications.
- Prefer framework-native solutions. Avoid Kafka, Redis, NgRx, distributed services, generic workflow engines and extra libraries without a documented need.
- Pin and verify compatible dependency versions during scaffolding; do not assume newest means compatible.

- Foundation scope and deployment deferrals are in .kiro/specs/project-foundation/design.md; actual verification and remaining limitations are in .kiro/specs/project-foundation/tasks.md. Docker Compose is local; illustrative AWS deployment is outside MVP and requires approval. Keep one backend and no API Gateway.

- Approved password policy and implementation boundary: [.kiro/specs/authentication-rbac/design.md §3.2](../specs/authentication-rbac/design.md#32-accepted-versioned-password-encoder--benchmark-required). Creation/replacement accepts 8–128 Unicode code points; login verification accepts non-empty well-formed input up to 128 without trimming or truncation. No plaintext fallback; changing parameters requires a new encoder identifier.
