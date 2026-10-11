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
- One Spring Boot deployable modular monolith. No MVP API Gateway; a reverse proxy/load balancer provides HTTP routing/TLS, not domain authorization. Scale backend instances sharing PostgreSQL-backed JDBC sessions and PostgreSQL before considering extraction; authentication state is shared server-side, not stateless JWT state.
- Follow module boundaries in .kiro/specs/project-foundation/design.md §§1–2 and interface/transaction relationships in docs/design/plantuml/components.puml; no cross-module repository/entity access or circular dependencies. Audit is a dispute submodule with an append interface joining the caller transaction; common contains technical infrastructure only.
- Backend root package is `com.assessment.disputeportal`. Keep business code feature-first: `auth`, `account`, `transaction`, `dispute`, and `idempotency`. Keep genuinely cross-cutting infrastructure at the root in `security` and `common`.
- The `account` feature owns the `accounts` table (V5 migration), the `BankAccount` domain model, the GET /api/v1/accounts endpoint, the `AccountFixturePort`, and the account fixture command. The transaction fixture obtains IDs only through that narrow public contract, without importing the account entity or Spring Data repository; stable ID derivation remains inside `account` and is not duplicated across modules. The account module does not own authentication accounts (`app_users`); those remain in `auth`.
- Within an implemented feature, use clear responsibility packages such as `controller`, `model`, `mapper`, `repository`, and `service`. Put feature-specific transport contracts in `model/dto`. Add focused packages such as `validation` or `cli` only when they name a real responsibility. Do not create empty scaffolding.
- `controller` owns HTTP endpoints and translates between the HTTP contract and a use case. `model/dto` holds request/response wire types; DTOs are not domain models, even though both are data-shaped classes.
- `model` holds feature data models, including JPA entities such as `AppUser`, domain aggregates such as `Account`, and value types such as `AccountRole`. Keep session/framework principals in root `security`, not in the account model.
- `service` coordinates one application use case per focused class and transaction boundaries. Login, logout and account administration remain separate classes. Services use repository interfaces and models; they do not manipulate JPA entities or call mappers directly.
- `mapper` owns focused conversions between persistence entities and domain models. `repository` owns repository contracts, Spring Data repositories and persistence adapters; only adapters use the mappers. Keep persistence entities inside their feature and never expose them to controllers.
- Put input rules in a focused `validation` package when shared; put trusted non-web operator entry points in `cli`. Explain the purpose of each command in the backend guide.
- Do not create a generic `utils`/`util` package. Put a helper beside its owner or give it a specific responsibility/package name.
- Root `security` owns shared filter-chain policy, Spring Security adapters and session enforcement. Account lookup/credential data remains owned by `auth` through narrow repository contracts.
- Feature-specific expected failures stay with their feature. `common` handles truly cross-cutting HTTP concerns; it maps store/database failures to the shared unavailable response, while authentication rejections retain their auth-specific status and response.
- Test packages should mirror the production feature/responsibility layout. Keep cross-module/application integration tests at the root test package, and shared test fixtures in an explicit test-support location or package with clear visibility.
- Angular organize by `core`, `shared`, `features/customer`, `features/agent`, `features/auth`. Use lazy routes and future role guards for UX only. Application layouts belong under layouts/dashboard-layout; shared/components contains reusable UI only.
- Customer routes: `/customer` is the account dashboard (tiles); `/customer/transactions` is the transaction list with account selector; `/customer/transactions/:id` is transaction detail. There is no `/customer/accounts/:accountId/transactions` path route. The account selector is a shared presentational component at `shared/components/account-selector`; it takes `accounts` and `selectedAccountId` as inputs and emits a `selectionChange` output. Account data fetching belongs in the feature service, not in the shared component.
- No direct entity serialization in REST. Use request/response DTOs; do not expose password hashes or internal audit notes to customers.
- Keep implementation simple and consistent; do not create abstractions before there is a real second use case.
