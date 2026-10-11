# Non-web customer transaction fixtures

This trusted operator command creates a fixed synthetic transaction set for an enabled CUSTOMER who already has MAIN and SAVINGS accounts. It is explicit opt-in, is never run at startup, and is not an HTTP endpoint. It creates neither authentication users nor banking accounts.

## Invocation

Build the backend jar, configure the existing database environment variables for a disposable local/assessment database, then run:

```sh
cd backend
mvn --batch-mode package
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar transaction-fixture --email customer@example.test
```

The account fixture must have already provisioned both account rows. The command accepts only an existing enabled CUSTOMER email and obtains its account IDs through the account module's `AccountFixturePort`. No account entity or repository crosses the feature boundary. The email is an operator argument, not transaction data; passwords, hashes and session credentials are never arguments.

## Safeguards and deterministic data

The fixture contains twelve stable transactions for each account type. It combines the original baseline rows with a versioned sample history: salary, grocery and fuel purchases, food delivery, subscriptions, mobile data, savings contributions, interest and transfers. Amounts, merchants, directions, statuses and occurrence times are plausible South African examples, but every row is synthetic; it is not copied from customer or bank records. IDs derive from the account UUID and a versioned fixture key; references derive from those IDs. Each account receives a different set; separate customer account IDs yield separate fixture rows. Timestamps are fixed constants, never `now()`.

The added history is additive and uses new versioned fixture keys. Do not edit or remove keys that have already shipped: the service verifies existing rows as immutable and fails safely on a mismatch. To add or correct examples, add a new key and update the expected fixture counts and this inventory.

The service inserts with `ON CONFLICT DO NOTHING`, then locks and verifies each expected row in the same transaction. Re-running the command is idempotent. It never updates existing data to make it match. A mismatch or missing account fails safely and rolls back the command's transaction. Do not manually change fixture rows to make a retry pass; investigate and reconcile persisted state before rerunning.

Use only a disposable local or assessment database. Production deployment does not invoke this command automatically. A commit-time connection loss can leave the outcome uncertain; reconcile rows by stable IDs before retrying. Output reports only the row count and never prints account numbers or credentials.
