package com.doodle.scheduler.meeting.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The actual @Entity - a pure data carrier for Hibernate, unlike
 * meeting.domain.Meeting, which has no framework dependency and no setters.
 * Lombok is appropriate here for the same reason it is on user.User and
 * slot.model.TimeSlot: nothing here needs protecting.
 *
 * <p>slotId and organizerId stay plain UUID columns, matching the ID-only
 * cross-context rule everywhere else - no @ManyToOne to slot or user.
 */
@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingJpaEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "slot_id", nullable = false, unique = true)
    private UUID slotId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @ElementCollection
    @CollectionTable(name = "meeting_participants", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "participant_email", nullable = false)
    @Builder.Default
    private Set<String> participantEmails = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}