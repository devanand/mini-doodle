package com.doodle.scheduler.user;

import java.util.UUID;

@FunctionalInterface
public interface UserExistenceChecker {
    boolean exists(UUID userId);
}
