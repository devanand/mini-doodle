package com.doodle.scheduler.meeting.dto;

import com.doodle.scheduler.meeting.domain.Meeting;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the meeting returned to API clients.
 *
 * <p>This DTO exposes the information required by consumers while keeping the
 * domain model isolated from HTTP-specific concerns.
 */
public record MeetingResponse(UUID id, UUID slotId, UUID organizerId,
                              String title, String description, Set<String> participantEmails,
                              Instant createdAt) {
    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getSlotId(),
                meeting.getOrganizerId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getParticipantEmails(),
                meeting.getCreatedAt());
    }
}