package com.doodle.scheduler.meeting.domain;

import com.doodle.scheduler.shared.exception.BadRequestException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A booking of exactly one TimeSlot. Plain domain object - no JPA
 * annotations, no Spring dependency - deliberately, since meeting is the
 * one bounded context in this project given the full domain/application/
 * infrastructure split.
 *
 * <p>slotId and organizerId are plain UUIDs, same ID-only cross-context
 * rule as everywhere else in this codebase - this class has no reference
 * to slot.model.TimeSlot or user.User.
 *
 * <p>Validation lives in the constructor, not just in Builder.schedule() -
 * every path to a Meeting instance is checked, including reconstruct(),
 * so this class has no state a caller could reach that violates its own
 * invariants, regardless of how new write paths to the meetings table
 * might be added in the future.
 */
public class Meeting {

    private final UUID id;
    private final UUID slotId;
    private final String title;
    private final String description;
    private final UUID organizerId;
    private final Set<String> participantEmails;
    private final Instant createdAt;

    private Meeting(
            UUID id,
            UUID slotId,
            String title,
            String description,
            UUID organizerId,
            Set<String> participantEmails,
            Instant createdAt) {
        if (slotId == null) {
            throw new BadRequestException("A meeting must be booked against a slot");
        }
        if (organizerId == null) {
            throw new BadRequestException("A meeting must have an organizer");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("A meeting must have a title");
        }
        if (participantEmails == null || participantEmails.isEmpty()) {
            throw new BadRequestException("A meeting must have at least one participant");
        }

        this.id = id;
        this.slotId = slotId;
        this.title = title;
        this.description = description;
        this.organizerId = organizerId;
        // Case-insensitive: "Foo@Example.com" and "foo@example.com" are the
        // same recipient, and Set<String>'s exact-match equality would
        // otherwise let both coexist as if they were different participants.
        // Normalizing here, not just in schedule(), means reconstruct() is
        // covered too - a row somehow written with mixed-case duplicates
        // still comes out deduplicated on read.
        this.participantEmails = participantEmails.stream()
                .map(email -> email.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toUnmodifiableSet());
        this.createdAt = createdAt;
    }

    /**
     * Starts building a brand-new meeting. A builder, not a positional
     * factory method: slotId and organizerId are both UUID, and a
     * positional (UUID, String, String, UUID, Set) signature makes them
     * silently transposable with no compiler protection.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Rebuilds a Meeting from already-persisted data. Runs the same
     * invariant checks the constructor always runs - not skipped for
     * reconstruct() specifically, since a row bypassing schedule() (a
     * future write path other than MeetingPersistenceAdapter.save())
     * should fail loudly on read rather than silently produce an
     * invalid Meeting. Used only by the infrastructure-layer mapper.
     */
    public static Meeting reconstruct(
            UUID id,
            UUID slotId,
            String title,
            String description,
            UUID organizerId,
            Set<String> participantEmails,
            Instant createdAt) {
        return new Meeting(id, slotId, title, description, organizerId, participantEmails, createdAt);
    }

    public static final class Builder {
        private UUID slotId;
        private String title;
        private String description;
        private UUID organizerId;
        private Set<String> participantEmails;

        private Builder() {}

        public Builder slotId(UUID slotId) {
            this.slotId = slotId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder organizerId(UUID organizerId) {
            this.organizerId = organizerId;
            return this;
        }

        public Builder participantEmails(Set<String> participantEmails) {
            this.participantEmails = participantEmails;
            return this;
        }

        /**
         * Builds. Named schedule(), not build(): this is the moment
         * "assemble the fields" and "this is now a real, bookable meeting"
         * happen together - Bean Validation on the web DTO already rejects
         * an obviously-blank title or empty participant set before this is
         * ever reached, but the constructor enforces the same checks
         * regardless, so the domain is correct on its own terms even if
         * the DTO is bypassed.
         */
        public Meeting schedule() {
            return new Meeting(null, slotId, title, description, organizerId, participantEmails, Instant.now());
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public Set<String> getParticipantEmails() {
        return participantEmails;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}