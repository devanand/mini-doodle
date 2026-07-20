package com.doodle.scheduler.meeting.application.usecase;

import java.util.UUID;

public interface CancelMeetingUseCase {
    void cancel(UUID meetingId);
}