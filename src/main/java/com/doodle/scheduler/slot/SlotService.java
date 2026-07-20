package com.doodle.scheduler.slot;

import static com.doodle.scheduler.slot.SlotSpecifications.hasStatus;
import static com.doodle.scheduler.slot.SlotSpecifications.ownedBy;
import static com.doodle.scheduler.slot.SlotSpecifications.startsWithin;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.shared.persistence.ConstraintViolationTranslator;
import com.doodle.scheduler.slot.command.CreateSlotCommand;
import com.doodle.scheduler.slot.command.ModifySlotCommand;
import com.doodle.scheduler.slot.model.BookedSlot;
import com.doodle.scheduler.slot.model.Calendar;
import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import com.doodle.scheduler.slot.rule.set.CreateSlotRules;
import com.doodle.scheduler.slot.rule.set.ModifySlotRules;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns all slot state and its transitions. Other bounded contexts (meeting)
 * never touch SlotRepository directly - they depend on SlotBookingPort,
 * which this class implements, exposing only markBusy/markFree. Everything
 * else here (create/modify/delete/availability) is slot's own concern and
 * is reached only through SlotController.
 *
 * <p>Validation is delegated to CreateSlotRules / ModifySlotRules, each a
 * composite of SlotRule beans. Open/closed in practice: adding a rule means
 * adding a @Component implementing CreateSlotRule and/or ModifySlotRule -
 * Spring collects it into the right composite automatically, and no
 * existing class is touched.
 */
@Service
public class SlotService implements SlotBookingPort {

    /**
     * Availability is bounded two ways: callers must supply a window, and
     * that window may not exceed MAX_WINDOW, with at most MAX_RESULTS rows
     * returned. This caps the worst case cheaply without changing the
     * response shape. Keyset (cursor) pagination is the natural next step
     * if dense windows become common - see README.
     */
    private static final Duration MAX_WINDOW = Duration.ofDays(100);

    private static final int MAX_RESULTS = 500;

    private final SlotRepository slotRepository;
    private final CreateSlotRules createRules;
    private final ModifySlotRules modifyRules;

    SlotService(SlotRepository slotRepository, CreateSlotRules createRules, ModifySlotRules modifyRules) {
        this.slotRepository = slotRepository;
        this.createRules = createRules;
        this.modifyRules = modifyRules;
    }

    @Transactional
    public TimeSlot createSlot(CreateSlotCommand command) {
        createRules.apply(command.toCandidate());

        return saveGuardingOverlap(command.toTimeSlot());
    }

    @Transactional
    public TimeSlot modifySlot(ModifySlotCommand command) {
        TimeSlot slot = getSlotOrThrow(command.slotId());

        modifyRules.apply(command.toCandidate(slot));
        command.applyTo(slot);

        return saveGuardingOverlap(slot);
    }

    @Transactional
    public void deleteSlot(UUID slotId) {
        TimeSlot slot = getSlotOrThrow(slotId);

        if (!slot.isFree()) {
            throw new ConflictException("Cannot delete a slot that is already booked - cancel the meeting first");
        }

        slotRepository.delete(slot);
    }

    public Calendar getAvailability(UUID ownerId, Instant from, Instant to, SlotStatus statusFilter) {
        if (!from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new BadRequestException(
                    "Requested window exceeds the maximum of %d days".formatted(MAX_WINDOW.toDays()));
        }

        // Status is filtered in SQL, not in memory: filtering after the row
        // limit would silently return fewer rows than the limit allows, which
        // a client could not distinguish from "no more slots exist".
        //
        // findBy with a fluent query rather than findAll(spec, Pageable):
        // a Page would issue a second COUNT query, and nothing here needs a
        // total. This is limit-only.
        List<TimeSlot> slots = slotRepository.findBy(
                ownedBy(ownerId).and(startsWithin(from, to)).and(hasStatus(statusFilter)),
                query -> query.sortBy(Sort.by(Sort.Direction.ASC, "startTime"))
                        .limit(MAX_RESULTS)
                        .all());

        return new Calendar(ownerId, from, to, slots);
    }

    // --- Called by MeetingService only. ---

    @Transactional
    public BookedSlot markBusy(UUID slotId) {
        TimeSlot slot = getSlotOrThrow(slotId);

        if (slot.isInThePast()) {
            throw new BadRequestException("Cannot book a slot that has already started");
        }
        if (!slot.isFree()) {
            throw new ConflictException("This slot is already booked");
        }

        slot.setStatus(SlotStatus.BUSY);
        // save() re-checks the @Version column at flush time - if another
        // request booked this slot concurrently, Hibernate throws
        // ObjectOptimisticLockingFailureException, mapped to 409 by
        // GlobalExceptionHandler.
        TimeSlot saved = slotRepository.save(slot);
        return new BookedSlot(saved.getId(), saved.getOwnerId());
    }

    @Transactional
    public void markFree(UUID slotId) {
        TimeSlot slot = getSlotOrThrow(slotId);
        slot.setStatus(SlotStatus.FREE);
        slotRepository.save(slot);
    }

    /**
     * NoOverlapRule already checked for overlap, but that check reads before
     * it writes: two concurrent requests can both pass it. The
     * no_overlapping_slots_per_owner exclusion constraint is what actually
     * guarantees the invariant, and this translates its violation into the
     * same ConflictException the rule would have thrown.
     *
     * <p>saveAndFlush, not save: with generated UUIDs Hibernate defers the
     * INSERT to commit time, which is after this method returns - the
     * violation would escape this try block entirely.
     */
    private TimeSlot saveGuardingOverlap(TimeSlot slot) {
        return ConstraintViolationTranslator.guard(
                () -> slotRepository.saveAndFlush(slot),
                "no_overlapping_slots_per_owner",
                "This time range overlaps an existing slot for this user");
    }

    private TimeSlot getSlotOrThrow(UUID slotId) {
        return slotRepository
                .findById(slotId)
                .orElseThrow(() -> new NotFoundException("No slot found with id '%s'".formatted(slotId)));
    }
}