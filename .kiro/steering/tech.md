---
inclusion: always
---
# Technology decisions
- Backend: Java 21, supported Spring Boot 3.x release, Spring MVC, Spring Security, OAuth2 Resource Server JWT, Spring Data JPA, Bean Validation, Flyway, Maven.
- Data: PostgreSQL; money as BigDecimal/NUMERIC(19,2), currency ZAR, UTC instants.
- Frontend: supported Angular version, standalone components, TypeScript strict mode, SCSS, Angular Router, Reactive Forms, HttpClient, signals for local UI state, RxJS for async streams.
- Tests: JUnit 5, Mockito, Spring Boot integration tests, Testcontainers PostgreSQL, Angular unit/component tests.
- Delivery: one monorepo, Docker Compose, multi-stage Dockerfiles, GitHub Actions CI.
- Authentication: app-managed demo accounts, BCrypt, RS256 JWT access tokens with 15-minute expiry, in-memory Angular token state and reauthentication on reload, no refresh tokens in MVP. Never commit signing private keys or real credentials.
- Prefer framework-native solutions. Avoid Kafka, Redis, NgRx, distributed services, generic workflow engines and extra libraries without a documented need.
- Pin and verify compatible dependency versions during scaffolding; do not assume newest means compatible.

- Production controls/scaling rationale and designed-versus-verified gaps are in docs/design/review-round-2.md. Docker Compose is local; illustrative AWS deployment is outside MVP and requires approval. Keep one backend and no API Gateway.
