package com.doodle.scheduler.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doodle.scheduler.shared.exception.BadRequestException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Meeting.builder()...schedule() carries real validation - unlike User and
 * TimeSlot, which lean on Bean Validation at the DTO boundary and have no
 * dedicated domain test of their own. schedule()'s javadoc is explicit that
 * these checks exist so the domain is correct on its own terms even if the
 * DTO layer is bypassed, so they're worth verifying independently of any
 * service or controller.
 */
class MeetingTest {

    private final UUID slotId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final Set<String> participantEmails = Set.of("a@example.com");

    @Test
    void schedule_buildsMeeting_whenAllFieldsAreValid() {
        Meeting meeting = Meeting.builder()
                .slotId(slotId)
                .organizerId(organizerId)
                .title("Planning sync")
                .description("Q3 roadmap")
                .participantEmails(participantEmails)
                .schedule();

        assertThat(meeting.getSlotId()).isEqualTo(slotId);
        assertThat(meeting.getOrganizerId()).isEqualTo(organizerId);
        assertThat(meeting.getTitle()).isEqualTo("Planning sync");
        assertThat(meeting.getDescription()).isEqualTo("Q3 roadmap");
        assertThat(meeting.getParticipantEmails()).containsExactly("a@example.com");
        assertThat(meeting.getId()).isNull(); // assigned by persistence, not on schedule()
        assertThat(meeting.getCreatedAt()).isNotNull();
    }

    @Test
    void schedule_normalizesParticipantEmailCase_toLowercase() {
        Meeting meeting = Meeting.builder()
                .slotId(slotId)
                .organizerId(organizerId)
                .title("Planning sync")
                .participantEmails(Set.of("A@Example.com", "a@example.com", "B@Example.com"))
                .schedule();

        // "A@Example.com" and "a@example.com" collapse to one participant once
        // normalized - this is the actual bug this change fixes, not just a
        // cosmetic lowercase check.
        assertThat(meeting.getParticipantEmails()).containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    void schedule_throwsBadRequest_whenSlotIdIsMissing() {
        assertThatThrownBy(() -> Meeting.builder()
                .organizerId(organizerId)
                .title("Planning sync")
                .participantEmails(participantEmails)
                .schedule())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void schedule_throwsBadRequest_whenOrganizerIdIsMissing() {
        assertThatThrownBy(() -> Meeting.builder()
                .slotId(slotId)
                .title("Planning sync")
                .participantEmails(participantEmails)
                .schedule())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("organizer");
    }

    @Test
    void schedule_throwsBadRequest_whenTitleIsBlank() {
        assertThatThrownBy(() -> Meeting.builder()
                .slotId(slotId)
                .organizerId(organizerId)
                .title("   ")
                .participantEmails(participantEmails)
                .schedule())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("title");
    }

    @Test
    void schedule_throwsBadRequest_whenTitleIsNull() {
        assertThatThrownBy(() -> Meeting.builder()
                .slotId(slotId)
                .organizerId(organizerId)
                .participantEmails(participantEmails)
                .schedule())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("title");
    }

    @Test
    void schedule_throwsBadRequest_whenParticipantEmailsIsEmpty() {
        assertThatThrownBy(() -> Meeting.builder()
                .slotId(slotId)
                .organizerId(organizerId)
                .title("Planning sync")
                .participantEmails(Set.of())
                .schedule())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("participant");
    }

    @Test
    void schedule_throwsBadRequest_whenParticipantEmailsIsNull() {
        assertThatThrownBy(() -> Meeting.builder()
                .slotId(slotId)
                .organizerId(organizerId)
                .title("Planning sync")
                .schedule())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("participant");
    }

    @Test
    void reconstruct_throwsBadRequest_onInvalidData_sameAsSchedule() {
        // reconstruct() now runs the same constructor validation as schedule() -
        // proving a future direct write to the meetings table (bypassing
        // MeetingPersistenceAdapter.save()) fails loudly on read instead of
        // silently producing an invalid Meeting.
        assertThatThrownBy(() -> Meeting.reconstruct(UUID.randomUUID(), null, "", null, null, Set.of(), null))
                .isInstanceOf(BadRequestException.class);
    }
}