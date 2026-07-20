-- Users: owners of time slots, organizers of meetings.
CREATE TABLE users (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Time slots: atomic, whole-unit bookable windows owned by a single user.
-- owner_id is a plain UUID reference (not a cross-context join) - see README
-- for the bounded-context reasoning behind this.
CREATE TABLE time_slots (
    id         UUID PRIMARY KEY,
    owner_id   UUID         NOT NULL REFERENCES users (id),
    start_time TIMESTAMPTZ  NOT NULL,
    end_time   TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'FREE',
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_slot_status CHECK (status IN ('FREE', 'BUSY')),
    CONSTRAINT chk_slot_time_order CHECK (start_time < end_time)
);

-- Hot-path index: every availability/overlap query is scoped by owner and
-- filtered/ordered by start_time.
CREATE INDEX idx_slot_owner_start ON time_slots (owner_id, start_time);

-- Meetings: a 1:1 booking of exactly one slot. UNIQUE on slot_id enforces
-- "only one meeting per slot" at the database level.
CREATE TABLE meetings (
    id            UUID PRIMARY KEY,
    slot_id       UUID         NOT NULL UNIQUE REFERENCES time_slots (id),
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    organizer_id  UUID         NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Meeting participants: plain emails, not FK'd to users - lets non-platform
-- users be invited, same as real Doodle behavior.
CREATE TABLE meeting_participants (
    meeting_id        UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    participant_email VARCHAR(255) NOT NULL,
    PRIMARY KEY (meeting_id, participant_email)
);