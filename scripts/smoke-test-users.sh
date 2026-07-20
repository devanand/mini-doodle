#!/usr/bin/env bash
#
# End-to-end smoke test for the user context.
# Requires the app to be running: docker-compose up
#
#   ./scripts/smoke-test-user.sh
#
# Most requests here are SUPPOSED to fail - 400/404/409 are the expected
# result for the validation cases. Each check prints what it expected.

set -u

HOST="${HOST:-http://localhost:8080}"
FAILURE_LOG="${FAILURE_LOG:-smoke-test-user-failures.log}"
PASS=0
FAIL=0

# Truncated at the start of every run, so the file always reflects the most
# recent run rather than accumulating across runs. Gitignored.
: > "$FAILURE_LOG"
{
    printf 'Smoke test run: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf 'Host: %s\n' "$HOST"
} >> "$FAILURE_LOG"

# check <expected-status> <description> <curl args...>
check() {
    local expected="$1" description="$2"
    shift 2
    local body status request
    body=$(curl -s -w '\n%{http_code}' "$@")
    status=$(printf '%s' "$body" | tail -n1)
    body=$(printf '%s' "$body" | sed '$d')

    if [ "$status" = "$expected" ]; then
        printf '  \033[32mPASS\033[0m  %-3s  %s\n' "$status" "$description"
        PASS=$((PASS + 1))
    else
        printf '  \033[31mFAIL\033[0m  %-3s  %s (expected %s)\n' "$status" "$description" "$expected"
        printf '        %s\n' "$body"
        printf '        logged to %s\n' "$FAILURE_LOG"
        FAIL=$((FAIL + 1))

        # %q escapes each argument, so the logged line can be pasted straight
        # back into a shell to reproduce the failure.
        request=$(printf 'curl -s -w "\\n%%{http_code}" '; printf '%q ' "$@")
        {
            printf '\n--------------------------------------------------------------\n'
            printf 'FAILED : %s\n' "$description"
            printf 'Expected: %s\n' "$expected"
            printf 'Actual  : %s\n' "$status"
            printf 'Request : %s\n' "$request"
            printf 'Response: %s\n' "$body"
        } >> "$FAILURE_LOG"
    fi
    LAST_BODY="$body"
}

# Extract a top-level JSON string field without requiring jq.
json_field() {
    printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"
}

JSON='Content-Type: application/json'

echo
echo "USERS"

check 201 "create user" \
    -X POST "$HOST/users" -H "$JSON" \
    -d "{\"name\":\"Ada Lovelace\",\"email\":\"ada+$(date +%s)@example.com\"}"
USER_ID=$(json_field "$LAST_BODY" id)
echo "        userId=$USER_ID"

check 200 "fetch user" "$HOST/users/$USER_ID"

DUP="dup+$(date +%s)@example.com"
check 201 "create user for duplicate check" \
    -X POST "$HOST/users" -H "$JSON" -d "{\"name\":\"First\",\"email\":\"$DUP\"}"
check 409 "duplicate email rejected" \
    -X POST "$HOST/users" -H "$JSON" -d "{\"name\":\"Second\",\"email\":\"$DUP\"}"

check 400 "blank name and bad email rejected" \
    -X POST "$HOST/users" -H "$JSON" -d '{"name":"","email":"not-an-email"}'

check 404 "unknown user" "$HOST/users/00000000-0000-0000-0000-000000000000"

echo
printf '%d passed, %d failed\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    printf 'Failure details written to %s\n' "$FAILURE_LOG"
else
    printf 'No failures.\n' >> "$FAILURE_LOG"
fi
echo
[ "$FAIL" -eq 0 ]