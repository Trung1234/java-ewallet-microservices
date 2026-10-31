#!/usr/bin/env bash
# End-to-end run through the API gateway (T-E2E-1):
# register two users, top up, transfer, check balances, notifications and the ledger,
# then show the two failure paths: insufficient funds and saga compensation.
#
# Usage: scripts/e2e.sh [gateway-url]      (default http://localhost:8080; kind: http://localhost:8000)
# Needs: curl, jq
set -euo pipefail

BASE=${1:-${BASE_URL:-http://localhost:8080}}
RUN=$(date +%s)
PASSWORD=password-123
BODY_FILE=$(mktemp)
trap 'rm -f "$BODY_FILE"' EXIT

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[0;32m    ok: %s\033[0m\n' "$*"; }
fail() { printf '\033[0;31m    FAILED: %s\033[0m\n' "$*" >&2; exit 1; }

# call METHOD PATH [TOKEN] [BODY] [EXTRA_HEADER] -> prints the response body and sets $STATUS.
# $STATUS is only visible to the caller when call runs in the current shell, not inside $(...).
call() {
  local method=$1 path=$2 token=${3:-} body=${4:-} extra=${5:-}
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $extra ]] && args+=(-H "$extra")
  [[ -n $body ]] && args+=(-d "$body")
  STATUS=$(curl "${args[@]}")
  cat "$BODY_FILE"
}

register_and_login() {
  local email="$1-$RUN@example.com"
  call POST /auth/register "" "{\"email\":\"$email\",\"password\":\"$PASSWORD\",\"fullName\":\"$1\"}" >/dev/null
  [[ $STATUS == 201 ]] || fail "register $email returned $STATUS"
  call POST /auth/login "" "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}" | jq -r .accessToken
}

# The fake bank fails ~30% of calls on purpose; the wallet retries 3 times, the script a few more.
top_up() {
  local token=$1 amount=$2
  for attempt in 1 2 3 4 5; do
    call POST /wallets/me/topup "$token" "{\"amount\":$amount}" "Idempotency-Key: topup-$RUN-$RANDOM" >/dev/null
    [[ $STATUS == 200 ]] && return 0
    echo "    bank not available (HTTP $STATUS), retrying top-up ($attempt)"
  done
  fail "top-up kept failing"
}

balance() { call GET /wallets/me "$1" | jq -r .balance; }

# New wallets reach transfer-service through a WalletCreated event, so retry RECIPIENT_NOT_FOUND briefly.
create_transfer() {
  local token=$1 to=$2 amount=$3 key=$4
  for _ in $(seq 1 20); do
    call POST /transfers "$token" "{\"toWalletId\":\"$to\",\"amount\":$amount}" "Idempotency-Key: $key" >/dev/null
    [[ $STATUS == 202 ]] && { jq -r .id "$BODY_FILE"; return 0; }
    sleep 1
  done
  fail "transfer not accepted: HTTP $STATUS $(cat "$BODY_FILE")"
}

wait_transfer() {
  local token=$1 id=$2 status
  for _ in $(seq 1 30); do
    status=$(call GET "/transfers/$id" "$token" | jq -r .status)
    [[ $status == COMPLETED || $status == FAILED ]] && { echo "$status"; return 0; }
    sleep 1
  done
  fail "transfer $id still $status after 30s"
}

step "Gateway at $BASE"
curl -sf "$BASE/actuator/health/readiness" >/dev/null || fail "gateway is not up"
ok "gateway ready"

step "Register and log in Alice and Bob"
ALICE=$(register_and_login alice)
BOB=$(register_and_login bob)
ok "two JWTs issued"

step "Open wallets"
ALICE_WALLET=$(call GET /wallets/me "$ALICE" | jq -r .id)
BOB_WALLET=$(call GET /wallets/me "$BOB" | jq -r .id)
ok "alice=$ALICE_WALLET bob=$BOB_WALLET"

step "Alice tops up 60.000.000 VND through the flaky bank"
top_up "$ALICE" 60000000
[[ $(balance "$ALICE") == 60000000 ]] || fail "unexpected balance $(balance "$ALICE")"
ok "balance 60000000"

step "Alice sends 150.000 VND to Bob"
KEY="e2e-$RUN-1"
ID=$(create_transfer "$ALICE" "$BOB_WALLET" 150000 "$KEY")
SAME=$(call POST /transfers "$ALICE" "{\"toWalletId\":\"$BOB_WALLET\",\"amount\":150000}" "Idempotency-Key: $KEY" | jq -r .id)
[[ $SAME == "$ID" ]] || fail "same Idempotency-Key gave a second transfer"
ok "transfer $ID accepted (retry with the same key returned the same id)"
[[ $(wait_transfer "$ALICE" "$ID") == COMPLETED ]] || fail "transfer did not complete"
ok "COMPLETED"
[[ $(balance "$ALICE") == 59850000 && $(balance "$BOB") == 150000 ]] || fail "balances wrong"
ok "alice=59850000 bob=150000"

step "Bob gets a notification"
for _ in $(seq 1 15); do
  COUNT=$(call GET /notifications/unread-count "$BOB" | jq -r .count)
  [[ $COUNT -ge 1 ]] && break
  sleep 1
done
[[ $COUNT -ge 1 ]] || fail "no notification for Bob"
call GET /notifications "$BOB" | jq -r '.[0] | "    \(.title): \(.body)"'
ok "unread=$COUNT"

step "Ledger agrees"
for _ in $(seq 1 15); do
  LEDGER_BOB=$(call GET "/ledger/accounts/wallet:$BOB_WALLET/balance" "$BOB" | jq -r .balance)
  [[ $LEDGER_BOB == 150000 ]] && break
  sleep 1
done
[[ $LEDGER_BOB == 150000 ]] || fail "ledger shows $LEDGER_BOB for Bob"
[[ $(call GET /ledger/trial-balance "$BOB" | jq -r .balanced) == true ]] || fail "trial balance not balanced"
ok "ledger balance of Bob's wallet = 150000, debits = credits"

step "Bob tries to send more than he has -> FAILED, nothing moves"
ID=$(create_transfer "$BOB" "$ALICE_WALLET" 999999 "e2e-$RUN-2")
[[ $(wait_transfer "$BOB" "$ID") == FAILED ]] || fail "expected FAILED"
REASON=$(call GET "/transfers/$ID" "$BOB" | jq -r .failureReason)
[[ $(balance "$BOB") == 150000 ]] || fail "Bob's balance changed"
ok "FAILED ($REASON), balance still 150000"

step "Alice sends 55.000.000 VND (above the ledger limit) -> compensation releases the hold"
ID=$(create_transfer "$ALICE" "$BOB_WALLET" 55000000 "e2e-$RUN-3")
[[ $(wait_transfer "$ALICE" "$ID") == FAILED ]] || fail "expected FAILED"
REASON=$(call GET "/transfers/$ID" "$ALICE" | jq -r .failureReason)
WALLET=$(call GET /wallets/me "$ALICE")
[[ $(jq -r .balance <<<"$WALLET") == 59850000 && $(jq -r .held <<<"$WALLET") == 0 ]] || fail "money not returned: $WALLET"
ok "HELD -> COMPENSATING -> FAILED ($REASON), balance 59850000, held 0"

step "Missing JWT is rejected at the gateway"
call GET /wallets/me >/dev/null
[[ $STATUS == 401 ]] || fail "expected 401, got $STATUS"
ok "401"

printf '\n\033[1;32mEnd-to-end run passed.\033[0m\n'
