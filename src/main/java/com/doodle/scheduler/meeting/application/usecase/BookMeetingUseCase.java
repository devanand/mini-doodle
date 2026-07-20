package com.doodle.scheduler.meeting.application.usecase;

import com.doodle.scheduler.meeting.application.command.BookMeetingCommand;
import com.doodle.scheduler.meeting.domain.Meeting;

public interface BookMeetingUseCase {
    Meeting book(BookMeetingCommand command);
}