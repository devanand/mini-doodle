#!/usr/bin/env bash
#
# Proves that booking the same slot from N simultaneous requests results in
# exactly one winner - protected by TimeSlot's @Version optimistic lock
# (SlotService.markBusy) and backstopped by the meetings.slot_id unique
# constraint (MeetingPersistenceAdapter.save via ConstraintViolationTranslator).
#
#   ./scripts/race-test-meeting.sh

set -u

HOST="${HOST:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-10}"
JSON='Content-Type: application/json'
TMPDIR_RACE=$(mktemp -d)
trap 'rm -rf "$TMPDIR_RACE"' EXIT

json_field() {
    printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"
}

echo
echo "Creating a user..."
USER_BODY=$(curl -s -X POST "$HOST/users" -H "$JSON" \
    -d "{\"name\":\"Race Tester\",\"email\":\"race+$(date +%s)@example.com\"}")
USER_ID=$(json_field "$USER_BODY" id)
echo "  userId=$USER_ID"

START="2027-0$(( (RANDOM % 9) + 1 ))-$(printf '%02d' $(( (RANDOM % 28) + 1 )))T09:00:00Z"

echo
echo "Creating a single slot ($START)..."
SLOT_BODY=$(curl -s -X POST "$HOST/users/$USER_ID/slots" -H "$JSON" \
    -d "{\"startTime\":\"$START\",\"durationMinutes\":60}")
SLOT_ID=$(json_field "$SLOT_BODY" id)
echo "  slotId=$SLOT_ID"

echo
echo "Firing $CONCURRENCY simultaneous bookings against that one slot..."

for i in $(seq 1 "$CONCURRENCY"); do
    curl -s -o "$TMPDIR_RACE/body.$i" -w '%{http_code}' \
        -X POST "$HOST/meetings" -H "$JSON" \
        -d "{\"slotId\":\"$SLOT_ID\",\"title\":\"Race meeting $i\",\"participantEmails\":[\"racer$i@example.com\"]}" \
        > "$TMPDIR_RACE/status.$i" &
done
wait

CREATED=0
CONFLICT=0
OTHER=0

for i in $(seq 1 "$CONCURRENCY"); do
    status=$(cat "$TMPDIR_RACE/status.$i")
    case "$status" in
        201) CREATED=$((CREATED + 1)) ;;
        409) CONFLICT=$((CONFLICT + 1)) ;;
        *)
            OTHER=$((OTHER + 1))
            printf '  unexpected %s: %s\n' "$status" "$(cat "$TMPDIR_RACE/body.$i")"
            ;;
    esac
done

echo
printf '  201 Created : %d\n' "$CREATED"
printf '  409 Conflict: %d\n' "$CONFLICT"
printf '  other       : %d\n' "$OTHER"
echo

if [ "$CREATED" -eq 1 ] && [ "$OTHER" -eq 0 ]; then
    printf '\033[32mPASS\033[0m  exactly one request won the race\n\n'
    exit 0
fi

printf '\033[31mFAIL\033[0m  expected exactly one 201 and no unexpected statuses\n'
exit 1