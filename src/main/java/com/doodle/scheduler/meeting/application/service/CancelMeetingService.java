package com.doodle.scheduler.meeting.application.service;

import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.application.usecase.CancelMeetingUseCase;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.slot.SlotBookingPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates the meeting cancellation use case: deletes the Meeting and
 * frees its slot in one transaction, the mirror image of BookMeetingService
 * marking a slot busy and creating a Meeting together. Both operations
 * either succeed or roll back - a meeting can't be deleted while its slot
 * stays BUSY, and a slot can't be freed while the meeting still exists.
 */
@Service
@RequiredArgsConstructor
@Transactional
class CancelMeetingService implements CancelMeetingUseCase {

    private final MeetingRepositoryPort meetingRepositoryPort;
    private final SlotBookingPort slotBookingPort;

    @Override
    public void cancel(UUID meetingId) {
        Meeting meeting = meetingRepositoryPort
                .findById(meetingId)
                .orElseThrow(() -> new NotFoundException("No meeting found with id '%s'".formatted(meetingId)));

        meetingRepositoryPort.deleteById(meeting.getId());
        slotBookingPort.markFree(meeting.getSlotId());
    }
}