CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE time_slots
    ADD CONSTRAINT no_overlapping_slots_per_owner
        EXCLUDE USING gist (
            owner_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
        );