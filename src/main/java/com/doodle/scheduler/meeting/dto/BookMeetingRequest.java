package com.doodle.scheduler.meeting.dto;

import com.doodle.scheduler.meeting.application.command.BookMeetingCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the data required from an API client to schedule a meeting.
 *
 * <p>This DTO forms the HTTP boundary of the application. It is responsible
 * only for validating client input and translating it into an application
 * command. Business rules are intentionally delegated to the application and
 * domain layers.
 */
public record BookMeetingRequest(

        @NotNull
        UUID slotId,

        @NotBlank
        String title,

        String description,

        @NotEmpty
        Set<@Email String> participantEmails

) {

    public BookMeetingCommand toCommand() {
        return new BookMeetingCommand(
                slotId,
                title,
                description,
                participantEmails
        );
    }
}