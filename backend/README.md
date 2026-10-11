# Backend Guide

The backend is one Spring Boot application organized as a modular monolith. Start with a feature package when changing behavior; keep HTTP, use-case coordination, domain rules, and persistence responsibilities distinct.

## Where things live

```text
src/main/java/com/assessment/disputeportal/
  auth/
    controller/      HTTP endpoints
    model/           AppUser entity, Account model, identity and role
      dto/           HTTP request and response contracts
    mapper/          Persistence entity <-> domain model conversion
    repository/      Account/session data access
    service/         Separate login, logout and account-administration use cases
    validation/      Shared email and password input rules
    cli/             Explicit host-only account administration command
  security/          Authentication provider, password encoder, serialized principal,
                     filter-chain policy, session validation and activity store
  common/            Shared HTTP envelope/advice and store-failure classification
  account/           Reserved for banking-account ownership rules
  transaction/       Reserved for transaction queries and eligibility
  dispute/           Reserved for dispute lifecycle and audit behavior
  idempotency/       Reserved for durable request replay behavior
src/main/resources/
  db/migration/      Flyway-owned schema changes
src/test/java/com/assessment/disputeportal/
  auth/validation/   Email/password policy tests
  auth/repository/   Persistence tests
  auth/service/      Use-case tests
  auth/cli/          Operator-command tests
  security/          Cross-cutting authentication and HTTP security tests
  *IntegrationTest   Application-wide integration tests at the root package
```

`LoginService`, `LogoutService`, and `AccountAdministrationService` are separate use cases in separate files. `AppUser` is a model/entity, not a repository. Request/response DTOs live under `model/dto` because they belong to this feature, but they remain HTTP contracts, not persisted or domain state.

## Dependency direction

- `controller` translates HTTP input/output and delegates to a service. DTOs stay in `model/dto`; services return auth models.
- `service` coordinates one use case per focused class and owns transaction orchestration. It depends on repository contracts, not JPA entities or mappers.
- `model` contains both persistence-facing entities (such as `AppUser`) and domain/value models (such as `Account`, `AccountIdentity`, and `AccountRole`). Never return a JPA entity from a controller.
- `mapper` owns conversion between JPA entities and domain models. `repository` owns data access and invokes the mapper internally. Application services depend on repository contracts and never manipulate entities or mappers.
- `validation` contains shared input policy, not generic utilities. Root `security` owns session activation/inspection/renewal and HTTP security adapters. Database failures flow to common error handling; expected auth rejection is mapped to generic 401 by authentication handling.
- `cli` contains the trusted non-web operator command. `java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin ...` manages accounts; it is not a web route or a running service. The older `provision` command remains an alias.
- Feature modules do not reach into another feature's repository or entity. Share a narrow interface or stable domain identity instead.

Keep new business behavior inside its owning feature. Use singular package names, avoid generic `utils`/`util` buckets and cross-feature repository access, and add layers only when they create a real boundary. Keep separate use cases in separate service classes/files. “Provisioning” means creating or changing portal accounts; the user-facing operator entry point is named `account-admin`.

## Schema and tests

Flyway is the schema authority. Add a new migration; never edit an applied migration. Hibernate validates the mapped auth schema. Backend tests use isolated PostgreSQL Testcontainers and must not depend on the developer's Compose database or `.env` values.

Run from this directory with `mvn --batch-mode clean verify`.