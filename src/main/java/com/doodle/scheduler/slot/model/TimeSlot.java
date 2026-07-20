package com.doodle.scheduler.slot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An atomic, whole-unit bookable window owned by a single user. JPA
 * annotations live directly on this class rather than behind a port -
 * no invariant here is complex enough to need isolating from the
 * persistence framework. ownerId is a plain UUID, not a JPA relationship
 * to User - no cross-context join, no import of the user package at all.
 *
 * <p>Table is time_slots, not slots - the class name and table name
 * deliberately differ, so @Table(name = ...) is required, not optional.
 */
@Entity
@Table(
        name = "time_slots",
        indexes = {@Index(name = "idx_slot_owner_start", columnList = "owner_id,start_time")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private SlotStatus status = SlotStatus.FREE;

    // Optimistic lock: whichever concurrent request loses the version race
    // at commit time gets ObjectOptimisticLockingFailureException instead of
    // silently overwriting the other's change. This is what actually closes
    // the double-booking race - an application-level check alone (read
    // status, then write) can't, since two requests can both read FREE
    // before either commits.
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isFree() {
        return status == SlotStatus.FREE;
    }

    public boolean isInThePast() {
        return startTime.isBefore(Instant.now());
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startTime.isBefore(otherEnd) && otherStart.isBefore(endTime);
    }
}
