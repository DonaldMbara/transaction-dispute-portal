# Customer Accounts — implementation acceptance evidence

**Date:** 2026-10-11
**Review status:** Implementation tasks 1–7 completed; verification results and environment limits recorded below.
**Scope:** ACC-01 through ACC-09; customer accounts and required backend/data/fixture dependencies only.
**Navigation status:** Dashboard account tiles and the primary-account quick link target the implemented `/customer/transactions?accountId=<id>` route.

## Verification commands and results

| Directory | Command | Result |
|---|---|---|
| `backend/` | `mvn --batch-mode clean verify` | Passed: 143 tests, zero failures/errors/skips; PostgreSQL/Testcontainers ran successfully. |
| `backend/` | `mvn --batch-mode -Dtest=CustomerAccountsIntegrationTest test` | Passed: 8 account HTTP/fixture/schema tests, zero failures/errors/skips; confirms the final collision-stability behavior. |
| `frontend/` | `npm run lint` | Unavailable: `package.json` has no `lint` script and the frontend has no linter dependency or Angular lint target. No dependency was added. |
| `frontend/` | `npm run test:ci` | Angular specs compiled. Karma started after local-port permission was granted, but execution stopped because ChromeHeadless has no installed browser binary (`CHROME_BIN` unset). Firefox is installed, but this project has no Firefox Karma launcher. |
| `frontend/` | `npm run build` | Passed. Angular build completed successfully. Node v25.8.0 emitted its odd-numbered release warning; project engines specify Node 22.12–22.x. |

The initial backend run caught and led to correction of test setup assumptions (JDBC `Timestamp` binding and migration count expectations after V5). The full clean verification and the focused account suite then passed.

## Implemented evidence

- V5 creates `accounts` with `VARCHAR(32) NOT NULL UNIQUE`, a single exact ten-ASCII-digit CHECK, `balance NUMERIC(19,2) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, ownership FK, type CHECK, customer index, and partial unique-primary index.
- Account mask and response-mapper unit tests cover the specified valid/malformed cases and null-safe owner response behavior. PostgreSQL integration tests exercise V5 constraints, multiple-customer fixture uniqueness, stable IDs/timestamps, idempotency, collisions, safe mismatch handling, owner-only HTTP serialization, role/session boundaries, pagination, and failure envelopes.
- The owner-scoped API exposes full account numbers only through `GET /api/v1/accounts`; errors contain safe messages. The accounts UI displays a full number only for non-null backend values. The selector presents full number in its selected summary and masked numbers in options.
- No transaction or dispute endpoint/page has been implemented in this feature. Future transaction/dispute tests must assert serialized responses contain only `maskedNumber` and no full account number when those features are implemented. Search, URL, and log paths in the account feature do not use or emit full account numbers; malformed-value logging includes account UUID and a safe reason only.
- Fixture usage and safeguards are documented in [backend/README.md](../../../backend/README.md) and [customer-account-fixtures.md](../../../docs/operations/customer-account-fixtures.md).
