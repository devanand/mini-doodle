package com.doodle.scheduler.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MeetingRepositoryPort is mocked - what this covers is FindMeetingService's
 * own responsibility: translating Optional.empty() into NotFoundException,
 * same shape as SlotService.getSlotOrThrow.
 */
@ExtendWith(MockitoExtension.class)
class FindMeetingServiceTest {

    @Mock
    private MeetingRepositoryPort meetingRepositoryPort;

    private FindMeetingService findMeetingService;

    @BeforeEach
    void setUp() {
        findMeetingService = new FindMeetingService(meetingRepositoryPort);
    }

    @Test
    void find_returnsMeeting_whenFound() {
        UUID meetingId = UUID.randomUUID();
        Meeting meeting = Meeting.reconstruct(
                meetingId,
                UUID.randomUUID(),
                "Planning sync",
                null,
                UUID.randomUUID(),
                Set.of("a@example.com"),
                Instant.now());
        when(meetingRepositoryPort.findById(meetingId)).thenReturn(Optional.of(meeting));

        assertThat(findMeetingService.find(meetingId)).isEqualTo(meeting);
    }

    @Test
    void find_throwsNotFound_whenMissing() {
        UUID meetingId = UUID.randomUUID();
        when(meetingRepositoryPort.findById(meetingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findMeetingService.find(meetingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(meetingId.toString());
    }
}