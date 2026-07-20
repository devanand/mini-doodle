package com.doodle.scheduler.user.dto;

import com.doodle.scheduler.user.CreateUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(@NotBlank String name, @NotBlank @Email String email) {

    public CreateUserCommand toCommand() {
        return new CreateUserCommand(name, email);
    }
}
