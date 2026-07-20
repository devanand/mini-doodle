package com.doodle.scheduler.slot.rule;

import java.time.Instant;
import java.util.UUID;

/**
 * The one capability NoOverlapRule needs - deliberately narrower than
 * SlotRepository, which stays package-private to the slot package.
 * SlotService (which does have repository access) supplies this as a
 * lambda, so the rule package never depends on Spring Data at all.
 */
@FunctionalInterface
public interface OverlapChecker {
    boolean overlaps(UUID ownerId, UUID excludeSlotId, Instant startTime, Instant endTime);
}
