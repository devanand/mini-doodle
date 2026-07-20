package com.doodle.scheduler.meeting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodle.scheduler.meeting.application.command.BookMeetingCommand;
import com.doodle.scheduler.meeting.application.usecase.BookMeetingUseCase;
import com.doodle.scheduler.meeting.application.usecase.CancelMeetingUseCase;
import com.doodle.scheduler.meeting.application.usecase.FindMeetingUseCase;
import com.doodle.scheduler.meeting.domain.Meeting;
import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * @WebMvcTest, not a full context - same pattern as User/SlotControllerDocumentationTest.
 * Mocks the three use-case interfaces directly, not a MeetingService, since
 * MeetingController depends on BookMeetingUseCase/FindMeetingUseCase/
 * CancelMeetingUseCase rather than a single service class - the hexagonal
 * split carries through into what gets mocked here.
 */
@WebMvcTest(MeetingController.class)
@ExtendWith(RestDocumentationExtension.class)
class MeetingControllerDocumentationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BookMeetingUseCase bookMeetingUseCase;

    @MockitoBean
    private FindMeetingUseCase findMeetingUseCase;

    @MockitoBean
    private CancelMeetingUseCase cancelMeetingUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void book_returns201AndDocumentsTheContract() throws Exception {
        UUID slotId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        Meeting booked = Meeting.reconstruct(
                meetingId,
                slotId,
                "Planning sync",
                "Q3 roadmap",
                organizerId,
                Set.of("a@example.com", "b@example.com"),
                Instant.parse("2027-01-01T00:00:00Z"));

        when(bookMeetingUseCase.book(any(BookMeetingCommand.class))).thenReturn(booked);

        mockMvc.perform(post("/meetings")
                        .contentType("application/json")
                        .content(
                                """
                                {"slotId":"%s","title":"Planning sync","description":"Q3 roadmap","participantEmails":["a@example.com","b@example.com"]}
                                """
                                        .formatted(slotId)))
                .andExpect(status().isCreated())
                .andDo(document(
                        "book-meeting",
                        requestFields(
                                fieldWithPath("slotId").description("The slot to book. Must exist and be FREE."),
                                fieldWithPath("title").description("Must not be blank"),
                                fieldWithPath("description")
                                        .description("Optional")
                                        .optional(),
                                fieldWithPath("participantEmails")
                                        .description("Must contain at least one email address")),
                        responseFields(
                                fieldWithPath("id").description("Generated meeting id"),
                                fieldWithPath("slotId").description("Echoes the request"),
                                fieldWithPath("organizerId").description("The slot owner - not supplied by the client"),
                                fieldWithPath("title").description("Echoes the request"),
                                fieldWithPath("description").description("Echoes the request"),
                                fieldWithPath("participantEmails").description("Echoes the request"),
                                fieldWithPath("createdAt").description("Server-assigned creation timestamp"))));
    }

    @Test
    void book_returns404_whenSlotDoesNotExist() throws Exception {
        UUID slotId = UUID.randomUUID();
        when(bookMeetingUseCase.book(any(BookMeetingCommand.class)))
                .thenThrow(new NotFoundException("No slot found with id '%s'".formatted(slotId)));

        mockMvc.perform(post("/meetings")
                        .contentType("application/json")
                        .content(
                                """
                                {"slotId":"%s","title":"Planning sync","participantEmails":["a@example.com"]}
                                """
                                        .formatted(slotId)))
                .andExpect(status().isNotFound())
                .andDo(document("book-meeting-not-found"));
    }

    @Test
    void book_returns409_whenSlotAlreadyBooked() throws Exception {
        UUID slotId = UUID.randomUUID();
        when(bookMeetingUseCase.book(any(BookMeetingCommand.class)))
                .thenThrow(new ConflictException("This slot is already booked"));

        mockMvc.perform(post("/meetings")
                        .contentType("application/json")
                        .content(
                                """
                                {"slotId":"%s","title":"Planning sync","participantEmails":["a@example.com"]}
                                """
                                        .formatted(slotId)))
                .andExpect(status().isConflict())
                .andDo(document("book-meeting-conflict"));
    }

    @Test
    void book_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(post("/meetings")
                        .contentType("application/json")
                        .content(
                                """
                                {"title":"","participantEmails":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andDo(document("book-meeting-validation-error"));
    }

    @Test
    void find_returns200AndDocumentsTheContract() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        Meeting meeting = Meeting.reconstruct(
                meetingId,
                slotId,
                "Planning sync",
                "Q3 roadmap",
                organizerId,
                Set.of("a@example.com"),
                Instant.parse("2027-01-01T00:00:00Z"));

        when(findMeetingUseCase.find(meetingId)).thenReturn(meeting);

        mockMvc.perform(get("/meetings/{id}", meetingId))
                .andExpect(status().isOk())
                .andDo(document(
                        "get-meeting",
                        pathParameters(parameterWithName("id").description("The meeting's id")),
                        responseFields(
                                fieldWithPath("id").description("Meeting id"),
                                fieldWithPath("slotId").description("The booked slot"),
                                fieldWithPath("organizerId").description("The slot owner"),
                                fieldWithPath("title").description("Meeting title"),
                                fieldWithPath("description").description("Meeting description"),
                                fieldWithPath("participantEmails").description("Invited participants"),
                                fieldWithPath("createdAt").description("Creation timestamp"))));
    }

    @Test
    void find_returns404_whenNotFound() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(findMeetingUseCase.find(meetingId))
                .thenThrow(new NotFoundException("No meeting found with id '%s'".formatted(meetingId)));

        mockMvc.perform(get("/meetings/{id}", meetingId))
                .andExpect(status().isNotFound())
                .andDo(document("get-meeting-not-found"));
    }

    @Test
    void cancel_returns204AndDocumentsTheContract() throws Exception {
        UUID meetingId = UUID.randomUUID();
        doNothing().when(cancelMeetingUseCase).cancel(meetingId);

        mockMvc.perform(delete("/meetings/{id}", meetingId))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "cancel-meeting",
                        pathParameters(parameterWithName("id").description("The meeting to cancel"))));
    }

    @Test
    void cancel_returns404_whenNotFound() throws Exception {
        UUID meetingId = UUID.randomUUID();
        doThrow(new NotFoundException("No meeting found with id '%s'".formatted(meetingId)))
                .when(cancelMeetingUseCase)
                .cancel(meetingId);

        mockMvc.perform(delete("/meetings/{id}", meetingId))
                .andExpect(status().isNotFound())
                .andDo(document("cancel-meeting-not-found"));
    }
}