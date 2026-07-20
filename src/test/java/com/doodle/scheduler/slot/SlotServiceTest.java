package com.doodle.scheduler.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.slot.command.CreateSlotCommand;
import com.doodle.scheduler.slot.command.ModifySlotCommand;
import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import com.doodle.scheduler.slot.rule.set.CreateSlotRules;
import com.doodle.scheduler.slot.rule.set.ModifySlotRules;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * CreateSlotRules/ModifySlotRules are mocked as opaque collaborators here -
 * their own logic is exercised in the rule.check tests instead. This test
 * covers what SlotService itself is responsible for: orchestration, the
 * overlap-constraint translation, and the availability window guards.
 */
@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private CreateSlotRules createRules;

    @Mock
    private ModifySlotRules modifyRules;

    private SlotService slotService;

    private final UUID ownerId = UUID.randomUUID();
    private final Instant start = Instant.parse("2027-01-01T09:00:00Z");

    @BeforeEach
    void setUp() {
        slotService = new SlotService(slotRepository, createRules, modifyRules);
    }

    @Test
    void createSlot_appliesCreateRulesThenSaves() {
        CreateSlotCommand command = new CreateSlotCommand(ownerId, start, 60);
        when(slotRepository.saveAndFlush(any(TimeSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimeSlot result = slotService.createSlot(command);

        verify(createRules).apply(any());
        assertThat(result.getOwnerId()).isEqualTo(ownerId);
        assertThat(result.getStartTime()).isEqualTo(start);
    }

    @Test
    void createSlot_translatesConstraintViolationToConflict() {
        CreateSlotCommand command = new CreateSlotCommand(ownerId, start, 60);
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("no_overlapping_slots_per_owner");
        when(slotRepository.saveAndFlush(any(TimeSlot.class)))
                .thenThrow(new DataIntegrityViolationException("dup", cause));

        assertThatThrownBy(() -> slotService.createSlot(command)).isInstanceOf(ConflictException.class);
    }

    @Test
    void modifySlot_appliesModifyRulesThenSaves() {
        UUID slotId = UUID.randomUUID();
        TimeSlot existing =
                TimeSlot.builder().id(slotId).ownerId(ownerId).startTime(start).endTime(start.plusSeconds(3600)).build();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(existing));
        when(slotRepository.saveAndFlush(any(TimeSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant newStart = start.plusSeconds(7200);
        slotService.modifySlot(new ModifySlotCommand(slotId, newStart, 30));

        verify(modifyRules).apply(any());
        assertThat(existing.getStartTime()).isEqualTo(newStart);
    }

    @Test
    void modifySlot_throwsNotFound_whenSlotDoesNotExist() {
        UUID slotId = UUID.randomUUID();
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.modifySlot(new ModifySlotCommand(slotId, start, 30)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteSlot_deletes_whenFree() {
        UUID slotId = UUID.randomUUID();
        TimeSlot slot = TimeSlot.builder().id(slotId).status(SlotStatus.FREE).build();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        slotService.deleteSlot(slotId);

        verify(slotRepository).delete(slot);
    }

    @Test
    void deleteSlot_throwsConflict_whenBooked() {
        UUID slotId = UUID.randomUUID();
        TimeSlot slot = TimeSlot.builder().id(slotId).status(SlotStatus.BUSY).build();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> slotService.deleteSlot(slotId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void getAvailability_throwsBadRequest_whenFromIsAfterTo() {
        Instant from = Instant.parse("2027-01-02T00:00:00Z");
        Instant to = Instant.parse("2027-01-01T00:00:00Z");

        assertThatThrownBy(() -> slotService.getAvailability(ownerId, from, to, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAvailability_throwsBadRequest_whenWindowExceedsMaximum() {
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant to = from.plusSeconds(200L * 24 * 3600); // 200 days, over the 100-day cap

        assertThatThrownBy(() -> slotService.getAvailability(ownerId, from, to, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100 days");
    }

    @Test
    void markBusy_throwsBadRequest_whenSlotIsInThePast() {
        UUID slotId = UUID.randomUUID();
        TimeSlot pastSlot = TimeSlot.builder()
                .id(slotId)
                .status(SlotStatus.FREE)
                .startTime(Instant.parse("2020-01-01T00:00:00Z"))
                .build();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(pastSlot));

        assertThatThrownBy(() -> slotService.markBusy(slotId)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void markBusy_throwsConflict_whenAlreadyBooked() {
        UUID slotId = UUID.randomUUID();
        TimeSlot busySlot = TimeSlot.builder()
                .id(slotId)
                .status(SlotStatus.BUSY)
                .startTime(start)
                .build();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(busySlot));

        assertThatThrownBy(() -> slotService.markBusy(slotId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void modifySlot_translatesConstraintViolationToConflict() {
        UUID slotId = UUID.randomUUID();
        TimeSlot existing =
                TimeSlot.builder().id(slotId).ownerId(ownerId).startTime(start).endTime(start.plusSeconds(3600)).build();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(existing));

        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("no_overlapping_slots_per_owner");
        when(slotRepository.saveAndFlush(any(TimeSlot.class)))
                .thenThrow(new DataIntegrityViolationException("dup", cause));

        Instant newStart = start.plusSeconds(7200);

        assertThatThrownBy(() -> slotService.modifySlot(new ModifySlotCommand(slotId, newStart, 30)))
                .isInstanceOf(ConflictException.class);
    }
}