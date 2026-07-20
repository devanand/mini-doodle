package com.doodle.scheduler.meeting.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private: only MeetingPersistenceAdapter, in this same package,
 * is allowed to touch Spring Data directly. MeetingService never sees this
 * interface - it depends on MeetingRepositoryPort instead, which is the
 * entire reason the port exists.
 */
interface MeetingJpaRepository extends JpaRepository<MeetingJpaEntity, UUID> {

    boolean existsBySlotId(UUID slotId);
}