package com.doodle.scheduler.user;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(CreateUserCommand command) {
        // Fast path: gives a clear message for the common case. This is NOT
        // the enforcement mechanism - two concurrent requests can both pass
        // this check (time-of-check to time-of-use race), so the unique
        // constraint on users.email is what actually guarantees uniqueness.
        if (userRepository.existsByEmail(command.email())) {
            throw new ConflictException("A user with email '%s' already exists".formatted(command.email()));
        }

        try {
            // saveAndFlush, not save: with generated UUIDs Hibernate defers the
            // INSERT to commit time, which is after this method returns - the
            // constraint violation would escape this try block entirely.
            return userRepository.saveAndFlush(command.toUser());
        } catch (DataIntegrityViolationException ex) {
            // Lost the race: another transaction committed this email first.
            throw new ConflictException("A user with email '%s' already exists".formatted(command.email()));
        }
    }

    public User getUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException("No user found with id '%s'".formatted(userId)));
    }
}
