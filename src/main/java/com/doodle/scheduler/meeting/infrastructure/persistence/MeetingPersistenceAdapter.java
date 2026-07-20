package com.doodle.scheduler.meeting.infrastructure.persistence;

import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.ConflictException;
import java.util.Optional;
import java.util.UUID;

import com.doodle.scheduler.shared.persistence.ConstraintViolationTranslator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * The only implementation of MeetingRepositoryPort - translates every call
 * through MeetingMapper and delegates to MeetingJpaRepository, which
 * neither MeetingService nor anything outside this package ever sees
 * directly.
 */
@Component
class MeetingPersistenceAdapter implements MeetingRepositoryPort {
    private static final String SLOT_ID_UNIQUE_CONSTRAINT = "meetings_slot_id_key"; // Postgres default name - column has no explicit CONSTRAINT clause in V1

    private final MeetingJpaRepository meetingJpaRepository;

    MeetingPersistenceAdapter(MeetingJpaRepository meetingJpaRepository) {
        this.meetingJpaRepository = meetingJpaRepository;
    }

    /**
     * The meetings.slot_id unique constraint is what actually guarantees
     * one meeting per slot - two concurrent bookings could both reach here
     * if they interleave between SlotService.markBusy's optimistic lock
     * and this insert. Same shape as SlotService.saveGuardingOverlap:
     * catch the constraint violation, translate to ConflictException,
     * here rather than in BookMeetingService, since DataIntegrityViolationException
     * is a Spring Data type that BookMeetingRepositoryPort exists specifically
     * to keep out of the application layer.
     *
     * <p>saveAndFlush, not save: with generated UUIDs Hibernate defers the
     * INSERT to commit time, which is after this method returns - a
     * deferred insert would let the violation escape this try block
     * entirely.
     */

    @Override
    public Meeting save(Meeting meeting) {
        MeetingJpaEntity saved = ConstraintViolationTranslator.guard(
                () -> meetingJpaRepository.saveAndFlush(MeetingMapper.toEntity(meeting)),
                SLOT_ID_UNIQUE_CONSTRAINT,
                "This slot already has a meeting booked against it");
        return MeetingMapper.toDomain(saved);
    }

    @Override
    public Optional<Meeting> findById(UUID meetingId) {
        return meetingJpaRepository.findById(meetingId).map(MeetingMapper::toDomain);
    }

    @Override
    public boolean existsBySlotId(UUID slotId) {
        return meetingJpaRepository.existsBySlotId(slotId);
    }

    @Override
    public void deleteById(UUID meetingId) {
        meetingJpaRepository.deleteById(meetingId);
    }
}