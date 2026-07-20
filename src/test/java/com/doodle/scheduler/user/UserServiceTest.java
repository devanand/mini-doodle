package com.doodle.scheduler.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Representative, not exhaustive - covers the duplicate-email fast path
 * and the race it can't close alone (see createUser's javadoc). Getters
 * and constructor wiring aren't tested here.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void createUser_savesAndReturnsUser_whenEmailIsNew() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(new CreateUserCommand("Ada Lovelace", "ada@example.com"));

        assertThat(result.getName()).isEqualTo("Ada Lovelace");
        assertThat(result.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void createUser_throwsConflict_whenExistsByEmailCheckFindsDuplicate() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(new CreateUserCommand("Ada", "ada@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ada@example.com");
    }

    @Test
    void createUser_throwsConflict_whenConcurrentInsertViolatesUniqueConstraint() {
        // The fast path (existsByEmail) passes - this simulates two requests
        // racing, where the other one committed first.
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> userService.createUser(new CreateUserCommand("Ada", "ada@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ada@example.com");
    }

    @Test
    void getUser_returnsUser_whenFound() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).name("Ada").email("ada@example.com").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(userService.getUser(id)).isEqualTo(user);
    }

    @Test
    void getUser_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}