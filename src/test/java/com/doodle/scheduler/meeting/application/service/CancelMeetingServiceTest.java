package com.doodle.scheduler.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.slot.SlotBookingPort;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MeetingRepositoryPort and SlotBookingPort are mocked - what this covers
 * is CancelMeetingService's own responsibility: looking the meeting up
 * before acting (so an unknown id 404s instead of silently no-op deleting),
 * and ordering deleteById before markFree, per the javadoc on
 * CancelMeetingService.cancel.
 */
@ExtendWith(MockitoExtension.class)
class CancelMeetingServiceTest {

    @Mock
    private MeetingRepositoryPort meetingRepositoryPort;

    @Mock
    private SlotBookingPort slotBookingPort;

    private CancelMeetingService cancelMeetingService;

    private final UUID slotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cancelMeetingService = new CancelMeetingService(meetingRepositoryPort, slotBookingPort);
    }

    @Test
    void cancel_deletesMeetingThenFreesSlot() {
        UUID meetingId = UUID.randomUUID();
        Meeting meeting = Meeting.reconstruct(
                meetingId, slotId, "Planning sync", null, UUID.randomUUID(), Set.of("a@example.com"), Instant.now());
        when(meetingRepositoryPort.findById(meetingId)).thenReturn(Optional.of(meeting));

        cancelMeetingService.cancel(meetingId);

        InOrder inOrder = inOrder(meetingRepositoryPort, slotBookingPort);
        inOrder.verify(meetingRepositoryPort).deleteById(meetingId);
        inOrder.verify(slotBookingPort).markFree(slotId);
    }

    @Test
    void cancel_throwsNotFound_whenMeetingDoesNotExist() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepositoryPort.findById(meetingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelMeetingService.cancel(meetingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(meetingId.toString());

        verify(meetingRepositoryPort, never()).deleteById(any());
        verify(slotBookingPort, never()).markFree(any());
    }


}