package com.doodle.scheduler.meeting.application.port;

import com.doodle.scheduler.meeting.domain.Meeting;
import java.util.Optional;
import java.util.UUID;

/**
 * What MeetingService needs from persistence, and nothing more - no JPA
 * type appears anywhere in this interface. The infrastructure layer
 * provides the only implementation, translating between Meeting and
 * whatever the actual storage technology needs.
 *
 * <p>Unlike slot.SlotRepository (package-private, since slot stays flat and
 * nothing outside it needs repository-shaped access), this is public: it is
 * the literal seam hexagonal architecture is built around here, and
 * MeetingService (in this same application package) depends on it by
 * design, not as a workaround.
 */
public interface MeetingRepositoryPort {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(UUID meetingId);

    boolean existsBySlotId(UUID slotId);

    void deleteById(UUID meetingId);
}