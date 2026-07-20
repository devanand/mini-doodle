package com.doodle.scheduler.slot;

import com.doodle.scheduler.slot.model.BookedSlot;
import com.doodle.scheduler.slot.model.TimeSlot;
import java.util.UUID;

/**
 * What meeting is allowed to do to a slot - narrow on purpose. Meeting
 * depends on this, never on SlotRepository or SlotService directly.
 *
 * <p>markBusy returns BookedSlot, not TimeSlot: meeting only ever needs a
 * slot's id and owner to build a Meeting, and handing back the full
 * TimeSlot aggregate would leak slot's internal domain model (status,
 * version, start/end time) across the bounded-context boundary for no
 * reason. markFree returns nothing at all - cancelling a meeting needs the
 * slot freed, not any data back from having freed it.
 */
public interface SlotBookingPort {

    BookedSlot markBusy(UUID slotId);

    void markFree(UUID slotId);
}