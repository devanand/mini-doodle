package com.doodle.scheduler.user;

/**
 * Service-layer input for user creation.
 *
 * <p>Deliberately not the web DTO: CreateUserRequest belongs to the HTTP
 * contract and carries Bean Validation annotations. Keeping a separate
 * command means the service's input can evolve independently of the API
 * shape, and adding a field is one change here rather than a signature
 * change rippling through every call site.
 */
public record CreateUserCommand(String name, String email) {

    /**
     * Maps inward to the domain type. Same direction as
     * CreateUserRequest.toCommand(): each layer knows how to translate
     * itself into the next one in, never the reverse.
     */
    User toUser() {
        return User.builder().name(name).email(email).build();
    }
}
