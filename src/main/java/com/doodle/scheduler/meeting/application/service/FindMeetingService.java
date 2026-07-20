package com.doodle.scheduler.meeting.application.service;

import com.doodle.scheduler.meeting.application.port.MeetingRepositoryPort;
import com.doodle.scheduler.meeting.application.usecase.FindMeetingUseCase;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up a single meeting by id. Split from BookMeetingService rather
 * than folded into one MeetingService - each use case gets its own
 * narrow service, same reasoning as BookMeetingUseCase.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FindMeetingService implements FindMeetingUseCase {

    private final MeetingRepositoryPort meetingRepositoryPort;

    @Override
    public Meeting find(UUID meetingId) {
        return meetingRepositoryPort
                .findById(meetingId)
                .orElseThrow(() -> new NotFoundException("No meeting found with id '%s'".formatted(meetingId)));
    }
}