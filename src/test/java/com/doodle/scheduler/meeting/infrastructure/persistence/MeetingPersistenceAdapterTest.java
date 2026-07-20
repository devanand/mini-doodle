package com.doodle.scheduler.meeting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.ConflictException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * MeetingJpaRepository is mocked - what this covers is
 * MeetingPersistenceAdapter's own responsibility: mapping through
 * MeetingMapper, delegating to the repository, and - the interesting part -
 * ConstraintViolationTranslator actually distinguishing the meetings.slot_id
 * violation from an unrelated one by constraint name, not just by exception
 * type. This is deliberately more careful than UserServiceTest/
 * SlotServiceTest's DataIntegrityViolationException mocks, which have no
 * cause at all - see the note on that gap from when ConstraintViolationTranslator
 * was introduced.
 */
@ExtendWith(MockitoExtension.class)
class MeetingPersistenceAdapterTest {

    private static final String SLOT_ID_UNIQUE_CONSTRAINT = "uk_meetings_slot_id";

    @Mock
    private MeetingJpaRepository meetingJpaRepository;

    private MeetingPersistenceAdapter adapter;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        adapter = new MeetingPersistenceAdapter(meetingJpaRepository);
        meeting = Meeting.builder()
                .slotId(UUID.randomUUID())
                .organizerId(UUID.randomUUID())
                .title("Planning sync")
                .participantEmails(Set.of("a@example.com"))
                .schedule();
    }

    @Test
    void save_returnsMappedMeeting_onSuccess() {
        UUID generatedId = UUID.randomUUID();
        MeetingJpaEntity saved = MeetingMapper.toEntity(meeting);
        saved.setId(generatedId);
        when(meetingJpaRepository.saveAndFlush(any(MeetingJpaEntity.class))).thenReturn(saved);

        Meeting result = adapter.save(meeting);

        assertThat(result.getId()).isEqualTo(generatedId);
        assertThat(result.getTitle()).isEqualTo("Planning sync");
    }

    @Test
    void save_throwsConflict_whenSlotIdUniqueConstraintIsViolated() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn(SLOT_ID_UNIQUE_CONSTRAINT);
        when(meetingJpaRepository.saveAndFlush(any(MeetingJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("insert failed", cause));

        assertThatThrownBy(() -> adapter.save(meeting)).isInstanceOf(ConflictException.class);
    }

    @Test
    void save_rethrowsUnchanged_whenViolationIsAnUnrelatedConstraint() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("some_other_constraint");
        DataIntegrityViolationException original = new DataIntegrityViolationException("insert failed", cause);
        when(meetingJpaRepository.saveAndFlush(any(MeetingJpaEntity.class))).thenThrow(original);

        assertThatThrownBy(() -> adapter.save(meeting)).isSameAs(original);
    }

    @Test
    void findById_returnsMappedMeeting_whenPresent() {
        UUID meetingId = UUID.randomUUID();
        MeetingJpaEntity entity = MeetingMapper.toEntity(meeting);
        entity.setId(meetingId);
        when(meetingJpaRepository.findById(meetingId)).thenReturn(Optional.of(entity));

        Optional<Meeting> result = adapter.findById(meetingId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(meetingId);
    }

    @Test
    void findById_returnsEmpty_whenMissing() {
        UUID meetingId = UUID.randomUUID();
        when(meetingJpaRepository.findById(meetingId)).thenReturn(Optional.empty());

        assertThat(adapter.findById(meetingId)).isEmpty();
    }

    @Test
    void existsBySlotId_delegatesToRepository() {
        UUID slotId = UUID.randomUUID();
        when(meetingJpaRepository.existsBySlotId(slotId)).thenReturn(true);

        assertThat(adapter.existsBySlotId(slotId)).isTrue();
    }

    @Test
    void deleteById_delegatesToRepository() {
        UUID meetingId = UUID.randomUUID();

        adapter.deleteById(meetingId);

        verify(meetingJpaRepository).deleteById(meetingId);
    }
}