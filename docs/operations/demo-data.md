# Opt-in local demo data

Use only a disposable local or assessment database. Default Compose startup never provisions accounts, fixtures, or disputes. The explicit script starts the local stack, creates two demo-only accounts through the existing masked `account-admin` command, runs `account-fixture` and `transaction-fixture` under the Compose `demo` profile, then uses the authenticated API to create one dispute in each lifecycle status.

## Run

1. Follow the root README local setup to create `.env` with local database credentials. Choose an available `POSTGRES_PORT` if 5432 is occupied.
2. From an interactive terminal at the repository root, run:

   ```sh
   ./scripts/demo-seed.sh
   ```

3. Accept or change the demo emails (`demo.customer@example.test` and `demo.agent@example.test`). At the masked prompts, enter and confirm a demo-only password for each account. The script asks for each password again, without echo, to log in through the application API and create the sample disputes. Passwords are not saved in `.env`, shell history, or files.
4. Open the frontend at `http://localhost:${PORTAL_PORT:-8081}` and sign in as either demo account using the password you entered. The customer has synthetic account and transaction fixtures; the agent queue contains OPEN, UNDER_REVIEW, ACCEPTED and REJECTED examples.

The script requires Docker Compose, `curl`, `jq`, and a terminal that supports `Console.readPassword`. It deliberately does not pipe credentials into `account-admin`; that command refuses non-interactive input. The `demo-cli` Compose service is available only with `--profile demo`, and its commands are never run by backend startup. The script does not delete or reset an existing database; duplicate account emails fail safely. To recreate the sample from scratch, use a new disposable database volume only after independently confirming that no wanted local data will be removed.

Passwords are chosen by the operator and are demo-only. Do not use production credentials or expose the demo endpoint to a public network. The fixture balances are synthetic display values, not bank balances or settlement data.
