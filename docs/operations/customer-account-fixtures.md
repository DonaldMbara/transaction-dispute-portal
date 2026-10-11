# Non-web customer account fixtures

This trusted operator command adds the deterministic synthetic MAIN and SAVINGS account rows needed for the customer account dashboard and later transaction fixtures. It is an explicit opt-in operation. It is not an HTTP endpoint, does not create or enable an authentication user, and is never run automatically at application startup.

## Prerequisites

- Use a trusted host and database credentials authorized for account fixture maintenance.
- Build the backend with Java 21 and Maven 3.9 or later. The command requires the V5 migration to be available and uses the configured database through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- The target must already exist as an enabled `CUSTOMER`. The command resolves and normalizes the provided email through the authentication lookup; AGENT, disabled, and unknown users are rejected.
- Do not add account numbers to shell arguments, environment variables, command output, logs, or operational notes.

## Build and invoke

Run from `backend/`:

```bash
mvn --batch-mode -DskipTests package
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-fixture --email customer@example.test
```

Successful output contains only the deterministic MAIN and SAVINGS account UUIDs. Exit status is 0 for verified completion, 1 for a rejected or unavailable operation, and 2 for invalid command syntax. Failure output is generic and does not echo the email or database exception.

## Data and safeguards

Each account UUID is derived from the target customer's UUID and the account type. A ten-digit synthetic number is derived from a versioned SHA-256 namespace, the same customer UUID and account type, plus a deterministic probe counter. The database UNIQUE constraint reserves each value globally. If a candidate is already held by another row, the command advances the counter deterministically; if no value can be safely reserved, the whole operation rolls back. No fixture number is printed.

The MAIN account is primary with a fixed `created_at` of `2025-01-01T00:00:00Z`; SAVINGS is non-primary with `2025-01-02T00:00:00Z`. Balances are deterministic per customer and distinct between account types. The command creates both rows in one transaction with `INSERT ... ON CONFLICT DO NOTHING`, locks and verifies the rows, and commits only when all immutable values match. Re-running it does not duplicate, update, or reseed existing accounts. A mismatch is rejected and rolled back.

## Failure and recovery

For an interrupted operation or a safe mismatch refusal, inspect the rows through trusted database access before retrying. Avoid selecting or printing `account_number`; use account UUID, customer UUID, type, balance, primary flag and timestamp to inspect state:

```sql
SELECT id, customer_id, account_type, balance, is_primary, created_at
FROM accounts
WHERE customer_id = '<customer UUID>'
ORDER BY is_primary DESC, created_at ASC, id ASC;
```

Do not manually edit fixture values to make a retry pass. Correct the underlying cause through the approved schema or fixture implementation, then rerun the command after independently verifying the rows. Ordinary process logs and the database are not crash-atomic; if a connection fails during commit, reconcile database state before retrying.
