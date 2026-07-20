package com.doodle.scheduler.meeting.infrastructure.persistence;

import com.doodle.scheduler.meeting.domain.Meeting;

/**
 * Translates between the domain Meeting and the JPA-specific
 * MeetingJpaEntity. Package-private - only MeetingPersistenceAdapter, in
 * this same package, calls it. Nothing here validates anything: toEntity
 * only ever receives an already-persisted row, so reconstruct() is the
 * correct domain factory to call on the way back - it runs the same
 * invariant checks the constructor always runs.
 */
final class MeetingMapper {

    private MeetingMapper() {}

    static MeetingJpaEntity toEntity(Meeting meeting) {
        return MeetingJpaEntity.builder()
                .id(meeting.getId())
                .slotId(meeting.getSlotId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .organizerId(meeting.getOrganizerId())
                .participantEmails(meeting.getParticipantEmails())
                .createdAt(meeting.getCreatedAt())
                .build();
    }

    static Meeting toDomain(MeetingJpaEntity entity) {
        return Meeting.reconstruct(
                entity.getId(),
                entity.getSlotId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getOrganizerId(),
                entity.getParticipantEmails(),
                entity.getCreatedAt());
    }
}