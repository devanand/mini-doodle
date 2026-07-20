package com.doodle.scheduler.meeting.application.usecase;

import com.doodle.scheduler.meeting.domain.Meeting;
import java.util.UUID;

public interface FindMeetingUseCase {
    Meeting find(UUID meetingId);
}