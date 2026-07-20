package com.doodle.scheduler.slot;

import static com.doodle.scheduler.slot.SlotSpecifications.excludingId;
import static com.doodle.scheduler.slot.SlotSpecifications.overlapping;
import static com.doodle.scheduler.slot.SlotSpecifications.ownedBy;

import com.doodle.scheduler.slot.rule.OverlapChecker;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapts SlotRepository (package-private to slot) to the narrow
 * OverlapChecker capability the rule package depends on, so slot.rule
 * never sees Spring Data at all.
 */
@Component
class SlotOverlapChecker implements OverlapChecker {

    private final SlotRepository slotRepository;

    SlotOverlapChecker(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Override
    public boolean overlaps(UUID ownerId, UUID excludeSlotId, Instant startTime, Instant endTime) {
        // One expression for both cases: on create excludeSlotId is null and
        // excludingId() contributes no predicate. Previously this needed two
        // near-identical JPQL queries.
        return slotRepository.exists(
                ownedBy(ownerId).and(overlapping(startTime, endTime)).and(excludingId(excludeSlotId)));
    }
}
