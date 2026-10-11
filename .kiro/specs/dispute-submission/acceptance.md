# Customer Dispute Submission — Acceptance Evidence

**Status:** Implementation in progress; review evidence is partial.

## Completed implementation

- Additive V7 schema, dispute creation/event models, transaction ownership lock, PostgreSQL idempotency persistence and customer POST endpoint are implemented.
- Transaction responses now include an existing dispute ID and report `ALREADY_DISPUTED`, allowing the customer entry action to disappear after submission.
- Customer transaction detail has a shared primary-button entry action. The lazy submission route loads owned transaction context, presents reason/description, review, pending/error/retry, and success states, and retains only in-memory form/idempotency state.
- API contract, database migration plan, customer flow documentation and PlantUML customer navigation were updated.

## Verification run

| Check | Command | Result |
|---|---|---|
| Frontend production build | `cd frontend && npm run build` | Passed. Existing `transactions.scss` style-budget warning remains. Node 25.8 emits an unsupported odd-version warning for this Angular project. |
| Frontend CI tests | `cd frontend && CHROME_BIN=/usr/bin/brave-browser npm run test:ci` | 80 passed, 0 failed. |
| Backend dispute, migration and response-mapper tests | `cd backend && mvn -B -Dtest=AuthenticationMigrationTest,CustomerDisputeSubmissionIntegrationTest,TransactionResponseMapperTest test` | 12 passed, 0 failed. Includes Testcontainers PostgreSQL. |
| Full backend verification | `cd backend && mvn -B verify` | 160 passed, 0 failures/errors/skips. The V7 migration tests are included. |
| Figma desktop/mobile visual comparison | Frames `3:5774` and `3:5841` | Pending. Figma MCP still returns the Starter-plan call-limit response after reconnect; the browser session requires Figma sign-in. No visual-match claim is made. |

## Remaining acceptance work

- Add coverage for same-key/different-payload, concurrent submissions/races, failed write rollback, request bounds, and the full frontend duplicate/error/accessibility paths.
- Inspect both named Figma frames, compare desktop/mobile renderings, and refine this feature screen to those exact frames.
- Update the dispute submission sequence diagram and capture final acceptance evidence after the preceding checks pass.
