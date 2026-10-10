---
inclusion: always
---
# Repository and architecture
```
transaction-dispute-portal/
  .kiro/steering/        # enduring rules
  .kiro/specs/           # feature requirements, design, tasks
  backend/               # Spring Boot Maven application
  frontend/              # Angular application
  docs/                  # ADRs and architecture diagrams
  compose.yaml
  README.md
  .github/workflows/
```
- One Spring Boot deployable modular monolith. No MVP API Gateway; a reverse proxy/load balancer provides HTTP routing/TLS, not domain authorization. Scale stateless replicas and PostgreSQL before considering extraction.
- Follow module boundaries in .kiro/specs/project-foundation/design.md §§1–2 and interface/transaction relationships in docs/design/plantuml/components.puml; no cross-module repository/entity access or circular dependencies. Audit is a dispute submodule with an append interface joining the caller transaction; common contains technical infrastructure only.
- Backend root package `com.assessment.disputeportal`, organized by feature: `auth`, `security`, `account`, `transaction`, `dispute`, `idempotency`, `common`.
- Within dispute feature use `api`, `application`, `domain`, `persistence` only where they improve clarity. Controllers translate HTTP; application services coordinate transactions; domain enforces invariants; repositories persist.
- Angular organize by `core`, `shared`, `features/customer`, `features/agent`, `features/auth`. Use lazy routes and role guards for UX only.
- No direct entity serialization in REST. Use request/response DTOs; do not expose password hashes or internal audit notes to customers.
- Keep implementation simple and consistent; do not create abstractions before there is a real second use case.
