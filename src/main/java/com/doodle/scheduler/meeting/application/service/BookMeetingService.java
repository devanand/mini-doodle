package com.doodle.scheduler.meeting.application.service;

import com.doodle.scheduler.meeting.application.command.BookMeetingCommand;
import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.application.usecase.BookMeetingUseCase;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.slot.SlotBookingPort;
import com.doodle.scheduler.slot.model.BookedSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates the meeting booking use case.
 *
 * <p>This class deliberately contains orchestration rather than business
 * rules. The Meeting aggregate is responsible for validating what
 * constitutes a valid meeting, while the TimeSlot aggregate is responsible
 * for deciding whether it can transition from FREE to BUSY.
 *
 * <p>Its responsibility is simply to execute those two domain operations
 * inside a single transaction so that booking a slot and creating a meeting
 * either both succeed or both roll back.
 *
 * <p>The service depends only on ports. It has no knowledge of JPA,
 * repositories, entities or database concerns, preserving the application
 * layer's independence from infrastructure.
 */
@Service
@RequiredArgsConstructor
@Transactional
class BookMeetingService implements BookMeetingUseCase {

    private final SlotBookingPort slotBookingPort;
    private final MeetingRepositoryPort meetingRepositoryPort;

    @Override
    public Meeting book(BookMeetingCommand command) {

        BookedSlot timeSlot = slotBookingPort.markBusy(command.slotId());

        Meeting meeting = command.toMeeting(timeSlot);

        return meetingRepositoryPort.save(meeting);
    }
}