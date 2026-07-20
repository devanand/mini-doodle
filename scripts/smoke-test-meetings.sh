#!/usr/bin/env bash
#
# End-to-end smoke test for the meeting context.
# Requires the app to be running: docker-compose up
#
#   ./scripts/smoke-test-meeting.sh
#
# Meetings need a user and a free slot, so this creates both as
# prerequisites first - user and slot validation themselves are covered by
# smoke-test-user.sh and smoke-test-slot.sh, not repeated here.
#
# Most requests here are SUPPOSED to fail - 400/404/409 are the expected
# result for the validation cases. Each check prints what it expected.

set -u

HOST="${HOST:-http://localhost:8080}"
FAILURE_LOG="${FAILURE_LOG:-smoke-test-meeting-failures.log}"
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
echo "PREREQUISITE USER AND SLOT"

check 201 "create organizer user" \
    -X POST "$HOST/users" -H "$JSON" \
    -d "{\"name\":\"Katherine Johnson\",\"email\":\"katherine+$(date +%s)@example.com\"}"
USER_ID=$(json_field "$LAST_BODY" id)
echo "        userId=$USER_ID"

check 201 "create slot 09:00-10:00" \
    -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d '{"startTime":"2026-09-01T09:00:00Z","durationMinutes":60}'
SLOT_ID=$(json_field "$LAST_BODY" id)
echo "        slotId=$SLOT_ID"

echo
echo "BOOKING VALIDATION"

check 400 "missing slotId rejected" \
    -X POST "$HOST/meetings" -H "$JSON" \
    -d '{"title":"Planning sync","participantEmails":["a@example.com"]}'

check 400 "blank title rejected" \
    -X POST "$HOST/meetings" -H "$JSON" \
    -d "{\"slotId\":\"$SLOT_ID\",\"title\":\"\",\"participantEmails\":[\"a@example.com\"]}"

check 400 "empty participantEmails rejected" \
    -X POST "$HOST/meetings" -H "$JSON" \
    -d "{\"slotId\":\"$SLOT_ID\",\"title\":\"Planning sync\",\"participantEmails\":[]}"

check 404 "booking against unknown slot rejected" \
    -X POST "$HOST/meetings" -H "$JSON" \
    -d '{"slotId":"00000000-0000-0000-0000-000000000000","title":"Planning sync","participantEmails":["a@example.com"]}'

echo
echo "BOOKING"

check 201 "book the slot" \
    -X POST "$HOST/meetings" -H "$JSON" \
    -d "{\"slotId\":\"$SLOT_ID\",\"title\":\"Planning sync\",\"description\":\"Q3 roadmap\",\"participantEmails\":[\"a@example.com\",\"b@example.com\"]}"
MEETING_ID=$(json_field "$LAST_BODY" id)
echo "        meetingId=$MEETING_ID"

check 200 "fetch the booked meeting" "$HOST/meetings/$MEETING_ID"

check 404 "fetch unknown meeting" "$HOST/meetings/00000000-0000-0000-0000-000000000000"

check 409 "booking an already-booked slot rejected" \
    -X POST "$HOST/meetings" -H "$JSON" \
    -d "{\"slotId\":\"$SLOT_ID\",\"title\":\"Double booking\",\"participantEmails\":[\"c@example.com\"]}"

echo
echo "SLOT STATE AFTER BOOKING"

WINDOW="from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z"
check 200 "slot now shows as BUSY" "$HOST/users/$USER_ID/availability?$WINDOW&status=BUSY"
echo "        $LAST_BODY"

echo
echo "CANCELLATION"

check 204 "cancel the meeting" -X DELETE "$HOST/meetings/$MEETING_ID"
check 404 "cancel it again" -X DELETE "$HOST/meetings/$MEETING_ID"
check 404 "meeting no longer found after cancel" "$HOST/meetings/$MEETING_ID"

echo
echo "SLOT STATE AFTER CANCELLATION"

check 200 "slot is FREE again (markFree ran)" "$HOST/users/$USER_ID/availability?$WINDOW&status=FREE"
echo "        $LAST_BODY"

echo
printf '%d passed, %d failed\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    printf 'Failure details written to %s\n' "$FAILURE_LOG"
else
    printf 'No failures.\n' >> "$FAILURE_LOG"
fi
echo
[ "$FAIL" -eq 0 ]