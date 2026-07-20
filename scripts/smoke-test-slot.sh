#!/usr/bin/env bash
#
# End-to-end smoke test for the slot context.
# Requires the app to be running: docker-compose up
#
#   ./scripts/smoke-test-slot.sh
#
# Slots need an owner, so this creates one prerequisite user first - user
# validation itself is covered by smoke-test-user.sh, not repeated here.
#
# Most requests here are SUPPOSED to fail - 400/404/409 are the expected
# result for the validation cases. Each check prints what it expected.

set -u

HOST="${HOST:-http://localhost:8080}"
FAILURE_LOG="${FAILURE_LOG:-smoke-test-slot-failures.log}"
PASS=0
FAIL=0

: > "$FAILURE_LOG"
{
    printf 'Smoke test run: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf 'Host: %s\n' "$HOST"
} >> "$FAILURE_LOG"

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

json_field() {
    printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"
}

JSON='Content-Type: application/json'

echo
echo "PREREQUISITE USER"

check 201 "create owner user" \
    -X POST "$HOST/users" -H "$JSON" \
    -d "{\"name\":\"Grace Hopper\",\"email\":\"grace+$(date +%s)@example.com\"}"
USER_ID=$(json_field "$LAST_BODY" id)
echo "        userId=$USER_ID"

echo
echo "SLOT CREATION"

check 201 "create slot 09:00-10:00" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T09:00:00Z","durationMinutes":60}'
SLOT_ID=$(json_field "$LAST_BODY" id)
echo "        slotId=$SLOT_ID"

check 201 "adjacent slot 10:00-11:00 allowed (touching is not overlapping)" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T10:00:00Z","durationMinutes":60}'

check 409 "one minute of real overlap rejected" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T09:59:00Z","durationMinutes":60}'

check 400 "past start time rejected (NotInPastRule before NoOverlapRule)" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2020-01-01T09:00:00Z","durationMinutes":60}'

check 404 "unknown owner rejected (OwnerExistsRule runs first)" \
    -X POST "$HOST/users/00000000-0000-0000-0000-000000000000/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T14:00:00Z","durationMinutes":60}'

check 400 "zero duration rejected" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T15:00:00Z","durationMinutes":0}'

check 400 "timestamp without offset rejected" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T16:00:00","durationMinutes":60}'

echo
echo "AVAILABILITY"

WINDOW="from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z"

check 200 "no status filter (hasStatus(null) contributes nothing)" \
    "$HOST/users/$USER_ID/availability?$WINDOW"
echo "        $LAST_BODY"

check 200 "status=FREE" "$HOST/users/$USER_ID/availability?$WINDOW&status=FREE"
check 200 "status=BUSY (expect empty slots array)" "$HOST/users/$USER_ID/availability?$WINDOW&status=BUSY"
echo "        $LAST_BODY"

check 400 "window wider than 100 days rejected" \
    "$HOST/users/$USER_ID/availability?from=2026-01-01T00:00:00Z&to=2026-12-31T00:00:00Z"

check 400 "from after to rejected" \
    "$HOST/users/$USER_ID/availability?from=2026-09-02T00:00:00Z&to=2026-09-01T00:00:00Z"

check 400 "missing from/to rejected" "$HOST/users/$USER_ID/availability"

echo
echo "SLOT MODIFICATION"

check 200 "move slot to 08:00-08:30" \
    -X PUT "$HOST/slots/$SLOT_ID" -H "$JSON" \
    -d '{"startTime":"2026-09-01T08:00:00Z","durationMinutes":30}'

check 200 "re-save at its own time (proves excludingId() applies)" \
    -X PUT "$HOST/slots/$SLOT_ID" -H "$JSON" \
    -d '{"startTime":"2026-09-01T08:00:00Z","durationMinutes":30}'

check 409 "move onto another slot rejected" \
    -X PUT "$HOST/slots/$SLOT_ID" -H "$JSON" \
    -d '{"startTime":"2026-09-01T10:30:00Z","durationMinutes":30}'

check 400 "move into the past rejected" \
    -X PUT "$HOST/slots/$SLOT_ID" -H "$JSON" \
    -d '{"startTime":"2020-01-01T08:00:00Z","durationMinutes":30}'

check 404 "modify unknown slot" \
    -X PUT "$HOST/slots/00000000-0000-0000-0000-000000000000" -H "$JSON" \
    -d '{"startTime":"2026-09-01T08:00:00Z","durationMinutes":30}'

echo
echo "SLOT DELETION"

check 204 "delete a free slot" -X DELETE "$HOST/slots/$SLOT_ID"
check 404 "delete it again" -X DELETE "$HOST/slots/$SLOT_ID"

echo
printf '%d passed, %d failed\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    printf 'Failure details written to %s\n' "$FAILURE_LOG"
else
    printf 'No failures.\n' >> "$FAILURE_LOG"
fi
echo
[ "$FAIL" -eq 0 ]