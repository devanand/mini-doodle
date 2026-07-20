package com.doodle.scheduler.slot.rule.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import com.doodle.scheduler.user.UserExistenceChecker;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Create-only by design (see the rule's javadoc): only forCreate candidates
 * are exercised here, since ModifySlotRule membership was deliberately not
 * implemented - a slot's owner cannot change on modify.
 */
@ExtendWith(MockitoExtension.class)
class OwnerExistsRuleTest {

    @Mock
    private UserExistenceChecker userExistenceChecker;

    @Test
    void passes_whenOwnerExists() {
        OwnerExistsRule rule = new OwnerExistsRule(userExistenceChecker);
        UUID ownerId = UUID.randomUUID();
        when(userExistenceChecker.exists(ownerId)).thenReturn(true);

        SlotCandidate candidate =
                SlotCandidate.forCreate(ownerId, Instant.now(), Instant.now().plusSeconds(3600));

        assertThatCode(() -> rule.validate(candidate)).doesNotThrowAnyException();
    }

    @Test
    void throwsNotFound_whenOwnerDoesNotExist() {
        OwnerExistsRule rule = new OwnerExistsRule(userExistenceChecker);
        UUID ownerId = UUID.randomUUID();
        when(userExistenceChecker.exists(ownerId)).thenReturn(false);

        SlotCandidate candidate =
                SlotCandidate.forCreate(ownerId, Instant.now(), Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> rule.validate(candidate))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(ownerId.toString());
    }
}