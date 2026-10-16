#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ ! -t 0 ]]; then
  echo "Run this script from an interactive terminal; account-admin requires a masked console." >&2
  exit 2
fi
if [[ ! -f .env ]]; then
  echo "Copy .env.example to .env and set local database credentials first." >&2
  exit 2
fi
if ! command -v curl >/dev/null || ! command -v jq >/dev/null; then
  echo "Install curl and jq to seed demo data." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

customer_email="${DEMO_CUSTOMER_EMAIL:-demo.customer@example.test}"
agent_email="${DEMO_AGENT_EMAIL:-demo.agent@example.test}"
base_url="http://localhost:${PORTAL_PORT:-8081}"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

echo "This creates demo-only accounts and synthetic fixtures in the configured local database."
echo "Use a disposable database. Demo passwords are entered only at the masked prompts."
read -r -p "Customer email [$customer_email]: " answer
customer_email="${answer:-$customer_email}"
read -r -p "Agent email [$agent_email]: " answer
agent_email="${answer:-$agent_email}"

docker compose up --build -d --wait

demo_cli() {
  docker compose --profile demo run --rm --no-deps -T demo-cli "$@"
}

demo_admin() {
  docker compose --profile demo run --rm --no-deps -it demo-cli account-admin "$@"
}

echo "Create the customer account. Enter the demo password twice at the masked prompts."
demo_admin create --email "$customer_email" --role CUSTOMER --enabled true
echo "Create the agent account. Enter the demo password twice at the masked prompts."
demo_admin create --email "$agent_email" --role AGENT --enabled true

demo_cli account-fixture --email "$customer_email"
demo_cli transaction-fixture --email "$customer_email"

read -r -s -p "Customer demo password (hidden): " customer_password
printf '\n'
read -r -s -p "Agent demo password (hidden): " agent_password
printf '\n'

bootstrap_csrf() {
  local jar="$1"
  curl --fail --silent --show-error --cookie "$jar" --cookie-jar "$jar" \
    "$base_url/api/v1/auth/csrf" | jq -er '.data.csrfToken'
}

login() {
  local email="$1" password="$2" jar="$3" token payload
  token="$(bootstrap_csrf "$jar")"
  payload="$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
  curl --fail --silent --show-error --cookie "$jar" --cookie-jar "$jar" \
    -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $token" \
    --data-binary "$payload" "$base_url/api/v1/auth/login" | jq -e '.data.id' >/dev/null
  bootstrap_csrf "$jar"
}

customer_jar="$temporary_dir/customer.cookies"
agent_jar="$temporary_dir/agent.cookies"
customer_csrf="$(login "$customer_email" "$customer_password" "$customer_jar")"
agent_csrf="$(login "$agent_email" "$agent_password" "$agent_jar")"
unset customer_password agent_password

transactions="$(curl --fail --silent --show-error --cookie "$customer_jar" \
  "$base_url/api/v1/transactions?size=100" \
  | jq -er '[.data.items[] | select(.direction == "OUT" and .status == "COMPLETED" and .dispute.id == null) | .id]')"
mapfile -t transaction_ids < <(jq -er '.[]' <<<"$transactions")
if (( ${#transaction_ids[@]} < 4 )); then
  echo "The customer fixture did not provide four eligible transactions." >&2
  exit 1
fi

declare -a dispute_ids=()
for index in 0 1 2 3; do
  payload="$(jq -cn --arg transactionId "${transaction_ids[$index]}" \
    --arg description "Demo dispute $((index + 1)): please review this synthetic transaction." \
    '{transactionId:$transactionId,reason:"DUPLICATE_CHARGE",description:$description}')"
  response="$(curl --fail --silent --show-error --cookie "$customer_jar" \
    --cookie-jar "$customer_jar" -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $customer_csrf" -H "Idempotency-Key: demo-dispute-$((index + 1))" \
    --data-binary "$payload" "$base_url/api/v1/disputes")"
  dispute_ids+=("$(jq -er '.data.id' <<<"$response")")
done

get_etag() {
  local dispute_id="$1" headers="$temporary_dir/headers" body="$temporary_dir/body"
  curl --fail --silent --show-error --cookie "$agent_jar" -D "$headers" -o "$body" \
    "$base_url/api/v1/agent/disputes/$dispute_id"
  grep -i '^ETag:' "$headers" | tail -n 1 | cut -d' ' -f2- | tr -d '\r'
}

agent_command() {
  local dispute_id="$1" path="$2" etag="$3" payload="$4"
  local headers="$temporary_dir/headers" body="$temporary_dir/body"
  curl --fail --silent --show-error --cookie "$agent_jar" --cookie-jar "$agent_jar" \
    -D "$headers" -o "$body" -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $agent_csrf" -H "If-Match: $etag" \
    --data-binary "$payload" "$base_url/api/v1/agent/disputes/$dispute_id/$path"
  jq -e '.data.id' "$body" >/dev/null
  grep -i '^ETag:' "$headers" | tail -n 1 | cut -d' ' -f2- | tr -d '\r'
}

etag="$(get_etag "${dispute_ids[1]}")"
agent_command "${dispute_ids[1]}" claim "$etag" '{}'

for index in 2 3; do
  etag="$(get_etag "${dispute_ids[$index]}")"
  etag="$(agent_command "${dispute_ids[$index]}" claim "$etag" '{}')"
  if (( index == 2 )); then decision=ACCEPT; else decision=REJECT; fi
  payload="$(jq -cn --arg decision "$decision" \
    --arg internal "Demo-only review completed." \
    --arg customer "This demo dispute has been reviewed." \
    '{decision:$decision,internalResolutionNotes:$internal,customerResolutionMessage:$customer}')"
  agent_command "${dispute_ids[$index]}" resolve "$etag" "$payload" >/dev/null
done

echo "Demo data is ready: OPEN, UNDER_REVIEW, ACCEPTED and REJECTED disputes exist."
echo "Sign in at $base_url as $customer_email (CUSTOMER) or $agent_email (AGENT)."
