# Customer Transactions — Acceptance Evidence

**Executed:** 2026-10-11

## Backend

- From `backend/`, `mvn --batch-mode clean verify` — **BUILD SUCCESS**; 157 tests, 0 failures, 0 errors, 0 skipped. PostgreSQL/Testcontainers used PostgreSQL 17. The suite includes V6 migration and constraint checks, scoped transaction API/privacy checks, deterministic fixture checks, activity renewal and isolated database-failure coverage.
- From `backend/`, `mvn --batch-mode -Dtest=CustomerTransactionsIntegrationTest test` — **BUILD SUCCESS**; 6 tests passed before EXPLAIN capture was added. The final full verification includes the additional EXPLAIN test (7 tests in the class).
- `git diff --check` — **passed**.

## PostgreSQL query plans

The integration test ran `EXPLAIN (ANALYZE, BUFFERS)` for ownership-only, account-filtered, combined-filter and searched list-query shapes after loading six deterministic transactions, and captured raw output in `backend/target/surefire-reports/TEST-com.assessment.disputeportal.transaction.service.CustomerTransactionsIntegrationTest.xml`.

- Ownership-only used a hash join and sequential scans; execution time 0.033 ms.
- Account-filtered used a nested loop and sequential scans; execution time 0.019 ms.
- Combined filters used a nested loop and sequential scans; execution time 0.025 ms.
- Search used a nested loop and sequential scans; execution time 0.032 ms.
- The sample is intentionally small (six rows), so these plans are not production-scale performance evidence. No additional indexes were indicated by this run.

## Frontend

- From `frontend/`, `npm run build` — **passed**; optimized Angular bundle generation completed successfully.
- From `frontend/`, `npm run test:ci` — test bundle compilation **passed**, but the Karma browser run could not start because ChromeHeadless has no installed browser binary (`CHROME_BIN` is unset). The command therefore exited with an error; browser assertions were not executed.
- From `frontend/`, `npm run lint` — **unavailable**; `package.json` has no `lint` script and `angular.json` has no lint builder. No lint tooling or dependency was added.

## Privacy, ownership and activity evidence

- The PostgreSQL-backed HTTP tests assert against serialized transaction list/detail responses and confirm that a full synthetic account number is absent. They cover customer-to-customer isolation, foreign account selection, CUSTOMER access, AGENT denial, standard error contracts and safe database-failure output.
- The response mapper exposes only `{id, maskedNumber}` and calls the shared `AccountNumberMask`. Its malformed-number warning includes the account UUID and a fixed reason only. Transaction fixture logs include the account count only. Search SQL predicates reference merchant and reference only; account-number values are not query parameters or URL fields.
- The HTTP integration test verifies activity renewal on admitted list/detail handlers and checks the database-failure response. Existing authentication activity integration tests in the full suite passed as well.

## Remaining verification limits

- Frontend browser tests still need a Chrome/Chromium binary installed to execute; the TypeScript test bundles compiled successfully.
- Frontend lint remains unavailable until the project adds an approved lint script/toolchain.
- EXPLAIN plans used the six-row synthetic test fixture and should not be used to predict production query performance.
