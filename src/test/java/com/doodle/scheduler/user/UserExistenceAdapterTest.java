package com.doodle.scheduler.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Thin, but this is the published port slot and (later) meeting depend on
 * for OwnerExistsRule - worth a regression guard even at one line of real
 * logic, since a wrong delegation here would silently let slots attach to
 * nonexistent owners.
 */
@ExtendWith(MockitoExtension.class)
class UserExistenceAdapterTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void exists_delegatesToRepositoryExistsById() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        UserExistenceAdapter adapter = new UserExistenceAdapter(userRepository);

        assertThat(adapter.exists(id)).isTrue();
    }

    @Test
    void exists_returnsFalse_whenRepositoryReportsNoSuchUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        UserExistenceAdapter adapter = new UserExistenceAdapter(userRepository);

        assertThat(adapter.exists(id)).isFalse();
    }
}