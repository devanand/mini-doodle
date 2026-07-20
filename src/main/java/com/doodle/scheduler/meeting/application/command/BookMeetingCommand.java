package com.doodle.scheduler.meeting.application.command;

import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.slot.model.BookedSlot;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the data required by the application layer to schedule a meeting.
 *
 * <p>The command acts as the bridge between the API and the domain. It carries
 * validated data into the application layer and knows how to construct the
 * corresponding domain aggregate once the required information has been
 * obtained from other bounded contexts.
 */
public record BookMeetingCommand(UUID slotId, String title, String description, Set<String> participantEmails) {

    public Meeting toMeeting(BookedSlot bookedSlot) {
        return Meeting.builder()
                .slotId(bookedSlot.id())
                .organizerId(bookedSlot.ownerId())
                .title(title)
                .description(description)
                .participantEmails(participantEmails)
                .schedule();
    }
}