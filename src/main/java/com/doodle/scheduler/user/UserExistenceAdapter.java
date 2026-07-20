package com.doodle.scheduler.user;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UserExistenceAdapter implements UserExistenceChecker {

    private final UserRepository userRepository;

    UserExistenceAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean exists(UUID userId) {
        return userRepository.existsById(userId);
    }
}
