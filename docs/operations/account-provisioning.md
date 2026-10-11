# Non-web account administration

Use trusted authenticated host access and database credentials explicitly authorized for account maintenance. Portal CUSTOMER/AGENT sessions cannot invoke this command. It creates neither an ADMIN application role nor an HTTP endpoint, public registration, default account or startup seed. Normal backend startup does not load the account-administration service. The backend artifact selects a separate non-web Spring context only when its first argument is `account-admin`; it starts no HTTP listener. The older `provision` token remains a compatibility alias.

Build from `backend/` with Java 21 and Maven 3.9+: `mvn --batch-mode -DskipTests package`. Use the resulting `target/dispute-portal-0.0.1-SNAPSHOT.jar` (check the actual artifact filename before invoking it). Supply the established `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` through restricted, externally managed database configuration. For local development, load the trusted ignored root `.env` using the README procedure; Compose interpolation alone does not export these variables. These are database access credentials, not account passwords. An explicitly authorized assessment operator may use the local database owner. Production should separate least-privilege operational and migration credentials; database-role management is outside this assessment.

Run from a real terminal. Pipes, redirected input and execution without a Java console are refused, even for commands that do not change passwords. Account passwords are entered twice using `Console.readPassword`, which disables echo. They are never accepted as command operands or environment input. Any environment key containing `PASSWORD` except the established `DB_PASSWORD` is rejected; remove such variables from the operational process environment. Do not put account credentials in shell history, `.env`, JVM options, checked-in files, command examples or logs. Newly provisioned or replaced passwords must contain 8–128 well-formed Unicode code points; spaces and combining characters are preserved exactly, with no trimming or normalization. The 8-character minimum is an assessment-specific password-only trade-off; use a longer passphrase when possible. Login accepts a non-empty, well-formed password up to 128 code points so compatible existing hashes remain usable. Input character buffers are cleared after execution; JVM/framework String copies cannot be reliably erased, so do not claim zero retention in process memory or enable heap/core dumps for secret-bearing operational runs.

Examples below contain only non-secret account references. Choose the account's role and enabled status explicitly; there are only CUSTOMER and AGENT roles.

```bash
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin create \
  --email customer@example.test --role CUSTOMER --enabled true
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin create \
  --email agent@example.test --role AGENT --enabled false
```

Successful execution prints only the committed account UUID and `auth_version`; creation starts at zero with a fresh UUID. Securely deliver the initial credential through an approved one-to-one secure channel, preferably a password-manager share. The actual authorized operator and delivery channel are deployment prerequisites; no email/invitation infrastructure is added.

Before maintenance, inspect the current account through trusted database access. Never select or print `password_hash`:

```sql
SELECT id, email, role, enabled, auth_version FROM app_users WHERE id = '<account UUID>';
```

Use the observed version as a required precondition, replacing the illustrative UUID/version below:

```bash
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin disable \
  --id 00000000-0000-0000-0000-000000000001 --expected-version 0
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin enable \
  --id 00000000-0000-0000-0000-000000000001 --expected-version 1
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin set-role \
  --id 00000000-0000-0000-0000-000000000001 --expected-version 2 --role AGENT
java -jar target/dispute-portal-0.0.1-SNAPSHOT.jar account-admin replace-password \
  --id 00000000-0000-0000-0000-000000000001 --expected-version 3
```

Every accepted status, role or password replacement increments the version once in the same transaction as the account change. UUID/email/creation time remain stable. Already-enabled/already-disabled and unchanged-role requests are rejected as no change, with no increment. A password replacement is an explicit revocation operation and increments even if the supplied password happens to match the previous one. Re-enabling cannot restore old authentication versions; current-account session validation rejects older versions, including after re-enabling. Login and logout are available after provisioning; the portal login form uses the created email and password. Automated tests use isolated Testcontainers PostgreSQL fixtures and never add accounts to this local database.

PBKDF2 runs before the write transaction and row lock. Inside the transaction, the service locks and re-reads the target, checks its observed version and applies the mutation. A concurrent account change invalidates the precondition, including a change committed while password hashing was running. Rejected, missing, stale, duplicate or invalid requests do not increment versions. Never retry automatically with a newly fetched version; inspect and reconcile the concurrent change first. UUIDs are not reused after trusted deletion/recreation. This interface deliberately has no delete command; future business foreign keys may require disabling instead of deletion.

Exit status is 0 for committed success, 1 for a rejected command after initialization and 2 for invalid invocation/missing console or unavailable initialization. Generic errors do not echo operands, credentials, hashes or database exceptions. Structured `account.provisioning.audit` outcomes include UTC timestamp, bounded host-user reference, action, non-sensitive UUID, outcome and correlation UUID. The operator reference is attribution under trusted host access, not an application authorization credential. Retain captured operational logs with restricted access and your deployment's retention policy. Do not enable Hibernate bind logging, credential-bearing configuration diagnostics or environment dumps.

Success audit is emitted after commit; transaction failure audit follows rollback when rollback can be confirmed. A generic `failed` record is not proof that an interrupted or ambiguous commit changed nothing. Input/console failures are logged before any account transaction. The database and ordinary process log are not one atomic durable operation. A crash after commit but before logging/output can leave an incomplete outcome record; a failed connection during commit can also leave the operator uncertain about the outcome. After interruption or any ambiguous failure, inspect the database via independent trusted access and reconcile UUID/state/version before repeating. For an interrupted create, inspect the canonical email to locate its UUID. Stronger crash-durable audit/outbox is deferred hardening; it is not claimed here. Password values remain secret during interruption verification.
