package com.doodle.scheduler.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.doodle.scheduler.meeting.application.command.BookMeetingCommand;
import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.slot.SlotBookingPort;
import com.doodle.scheduler.slot.model.BookedSlot;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SlotBookingPort and MeetingRepositoryPort are mocked as opaque
 * collaborators - what this covers is BookMeetingService's own
 * responsibility: calling markBusy before save, and handing the resulting
 * BookedSlot into BookMeetingCommand.toMeeting() unchanged. Meeting's own
 * validation is covered by MeetingTest, not repeated here.
 */
@ExtendWith(MockitoExtension.class)
class BookMeetingServiceTest {

    @Mock
    private SlotBookingPort slotBookingPort;

    @Mock
    private MeetingRepositoryPort meetingRepositoryPort;

    private BookMeetingService bookMeetingService;

    private final UUID slotId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookMeetingService = new BookMeetingService(slotBookingPort, meetingRepositoryPort);
    }

    @Test
    void book_marksSlotBusyThenSavesMeeting() {
        BookMeetingCommand command =
                new BookMeetingCommand(slotId, "Planning sync", "Q3 roadmap", Set.of("a@example.com"));
        BookedSlot bookedSlot = new BookedSlot(slotId, organizerId);
        when(slotBookingPort.markBusy(slotId)).thenReturn(bookedSlot);
        when(meetingRepositoryPort.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = bookMeetingService.book(command);

        verify(slotBookingPort).markBusy(slotId);
        assertThat(result.getSlotId()).isEqualTo(slotId);
        assertThat(result.getOrganizerId()).isEqualTo(organizerId);
        assertThat(result.getTitle()).isEqualTo("Planning sync");
    }

    @Test
    void book_propagatesConflict_whenSlotIsAlreadyBooked() {
        BookMeetingCommand command =
                new BookMeetingCommand(slotId, "Planning sync", "Q3 roadmap", Set.of("a@example.com"));
        when(slotBookingPort.markBusy(slotId))
                .thenThrow(new ConflictException("This slot is already booked"));

        assertThatThrownBy(() -> bookMeetingService.book(command))
                .isInstanceOf(ConflictException.class);
    }
}