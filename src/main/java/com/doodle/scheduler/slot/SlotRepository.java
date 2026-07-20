package com.doodle.scheduler.slot;

import java.util.UUID;

import com.doodle.scheduler.slot.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Package-private: the compiler, not convention, keeps other bounded
 * contexts out. They reach slots only through SlotService's public methods
 * or a published port.
 *
 * <p>No @Query methods - every query in this context is assembled from
 * SlotSpecifications and executed through JpaSpecificationExecutor. The
 * overlap check and the availability query share predicates rather than
 * duplicating them as near-identical query strings.
 */
interface SlotRepository extends JpaRepository<TimeSlot, UUID>, JpaSpecificationExecutor<TimeSlot> {}
