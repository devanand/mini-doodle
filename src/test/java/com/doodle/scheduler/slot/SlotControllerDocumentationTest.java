package com.doodle.scheduler.slot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.slot.command.CreateSlotCommand;
import com.doodle.scheduler.slot.command.ModifySlotCommand;
import com.doodle.scheduler.slot.model.Calendar;
import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import java.time.Instant;
import java.util.List;
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
 * @WebMvcTest, not a full context: this documents the HTTP contract by
 * mocking SlotService, which is consistent with dropping Testcontainers -
 * repository/query correctness is verified separately by
 * scripts/smoke-test.sh and scripts/race-test.sh against a real database,
 * not by these tests.
 *
 * <p>Only the two endpoints judged "interesting" are documented here
 * (create, with its overlap/validation behavior, and availability, with its
 * window bounds) - see README for why the remaining endpoints are described
 * in prose rather than generated snippets.
 */
@WebMvcTest(SlotController.class)
@ExtendWith(RestDocumentationExtension.class)
class SlotControllerDocumentationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SlotService slotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void createSlot_returns201AndDocumentsTheContract() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Instant start = Instant.parse("2027-01-01T09:00:00Z");
        Instant end = start.plusSeconds(3600);

        TimeSlot created = TimeSlot.builder()
                .id(slotId)
                .ownerId(ownerId)
                .startTime(start)
                .endTime(end)
                .status(SlotStatus.FREE)
                .createdAt(Instant.now())
                .build();

        when(slotService.createSlot(any(CreateSlotCommand.class))).thenReturn(created);

        mockMvc.perform(post("/users/{userId}/slots", ownerId)
                        .contentType("application/json")
                        .content(
                                """
                                {"startTime":"2027-01-01T09:00:00Z","durationMinutes":60}
                                """))
                .andExpect(status().isCreated())
                .andDo(document(
                        "create-slot",
                        requestFields(
                                fieldWithPath("startTime")
                                        .description("ISO-8601 timestamp with an explicit UTC offset. "
                                                + "Timestamps without an offset are rejected."),
                                fieldWithPath("durationMinutes")
                                        .description("Slot length in minutes. Must be positive; "
                                                + "endTime is derived as startTime + durationMinutes.")),
                        responseFields(
                                fieldWithPath("id").description("Generated slot id"),
                                fieldWithPath("ownerId").description("The user this slot belongs to"),
                                fieldWithPath("startTime").description("Echoes the request"),
                                fieldWithPath("endTime").description("startTime + durationMinutes"),
                                fieldWithPath("status").description("FREE or BUSY - always FREE on creation"),
                                fieldWithPath("createdAt").description("Server-assigned creation timestamp"))));
    }

    @Test
    void createSlot_returns409_whenOverlappingAnExistingSlot() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(slotService.createSlot(any(CreateSlotCommand.class)))
                .thenThrow(new ConflictException("This time range overlaps an existing slot for this user"));

        mockMvc.perform(post("/users/{userId}/slots", ownerId)
                        .contentType("application/json")
                        .content(
                                """
                                {"startTime":"2027-01-01T09:30:00Z","durationMinutes":60}
                                """))
                .andExpect(status().isConflict())
                .andDo(document("create-slot-conflict"));
    }

    @Test
    void getAvailability_returns200AndDocumentsTheContract() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-01-02T00:00:00Z");

        // A populated example, not an empty list: REST Docs cannot infer a
        // JSON type for slots[].* fields with zero array elements to
        // introspect, and throws FieldTypeRequiredException rather than
        // silently skipping them.
        TimeSlot exampleSlot = TimeSlot.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .startTime(from.plusSeconds(3600))
                .endTime(from.plusSeconds(7200))
                .status(SlotStatus.FREE)
                .createdAt(Instant.now())
                .build();

        when(slotService.getAvailability(eq(ownerId), eq(from), eq(to), isNull()))
                .thenReturn(new Calendar(ownerId, from, to, List.of(exampleSlot)));

        mockMvc.perform(get("/users/{userId}/availability", ownerId)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andDo(document(
                        "get-availability",
                        queryParameters(
                                parameterWithName("from")
                                        .description("Window start, inclusive. ISO-8601 with UTC offset. Required."),
                                parameterWithName("to")
                                        .description("Window end, exclusive. ISO-8601 with UTC offset. Required. "
                                                + "Window may not exceed 100 days."),
                                parameterWithName("status")
                                        .description("Optional filter: FREE or BUSY. Omit for both.")
                                        .optional()),
                        responseFields(
                                fieldWithPath("ownerId").description("Echoes the path variable"),
                                fieldWithPath("from").description("Echoes the from parameter"),
                                fieldWithPath("to").description("Echoes the to parameter"),
                                fieldWithPath("slots")
                                        .description("Slots in the window, ascending by startTime, capped at 500 results"),
                                fieldWithPath("slots[].id").description("Slot id"),
                                fieldWithPath("slots[].ownerId").description("Slot owner"),
                                fieldWithPath("slots[].startTime").description("Slot start"),
                                fieldWithPath("slots[].endTime").description("Slot end"),
                                fieldWithPath("slots[].status").description("FREE or BUSY"),
                                fieldWithPath("slots[].createdAt").description("Slot creation timestamp"))));
    }

    @Test
    void modifySlot_returns200AndDocumentsTheContract() throws Exception {
        UUID slotId = UUID.randomUUID();
        Instant newStart = Instant.parse("2027-01-01T08:00:00Z");

        TimeSlot updated = TimeSlot.builder()
                .id(slotId)
                .ownerId(UUID.randomUUID())
                .startTime(newStart)
                .endTime(newStart.plusSeconds(1800))
                .status(SlotStatus.FREE)
                .createdAt(Instant.now())
                .build();

        when(slotService.modifySlot(any(ModifySlotCommand.class))).thenReturn(updated);

        mockMvc.perform(put("/slots/{slotId}", slotId)
                        .contentType("application/json")
                        .content(
                                """
                                {"startTime":"2027-01-01T08:00:00Z","durationMinutes":30}
                                """))
                .andExpect(status().isOk())
                .andDo(document(
                        "modify-slot",
                        pathParameters(parameterWithName("slotId").description("The slot to modify")),
                        requestFields(
                                fieldWithPath("startTime")
                                        .description("New start time. Must not be in the past."),
                                fieldWithPath("durationMinutes")
                                        .description("New duration in minutes. New end time is "
                                                + "derived as startTime + durationMinutes.")),
                        responseFields(
                                fieldWithPath("id").description("Slot id"),
                                fieldWithPath("ownerId").description("Slot owner"),
                                fieldWithPath("startTime").description("Updated start time"),
                                fieldWithPath("endTime").description("Updated end time"),
                                fieldWithPath("status").description("FREE or BUSY"),
                                fieldWithPath("createdAt").description("Original creation timestamp - unchanged by modification"))));
    }

    @Test
    void modifySlot_returns404_whenSlotDoesNotExist() throws Exception {
        UUID slotId = UUID.randomUUID();
        when(slotService.modifySlot(any(ModifySlotCommand.class)))
                .thenThrow(new NotFoundException("No slot found with id '%s'".formatted(slotId)));

        mockMvc.perform(put("/slots/{slotId}", slotId)
                        .contentType("application/json")
                        .content(
                                """
                                {"startTime":"2027-01-01T08:00:00Z","durationMinutes":30}
                                """))
                .andExpect(status().isNotFound())
                .andDo(document("modify-slot-not-found"));
    }

    @Test
    void modifySlot_returns409_whenMoveWouldOverlap() throws Exception {
        UUID slotId = UUID.randomUUID();
        when(slotService.modifySlot(any(ModifySlotCommand.class)))
                .thenThrow(new ConflictException("This change would overlap an existing slot for this user"));

        mockMvc.perform(put("/slots/{slotId}", slotId)
                        .contentType("application/json")
                        .content(
                                """
                                {"startTime":"2027-01-01T10:30:00Z","durationMinutes":30}
                                """))
                .andExpect(status().isConflict())
                .andDo(document("modify-slot-conflict"));
    }

    @Test
    void deleteSlot_returns204AndDocumentsTheContract() throws Exception {
        UUID slotId = UUID.randomUUID();
        doNothing().when(slotService).deleteSlot(slotId);

        mockMvc.perform(delete("/slots/{slotId}", slotId))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "delete-slot",
                        pathParameters(parameterWithName("slotId").description("The slot to delete"))));
    }

    @Test
    void deleteSlot_returns404_whenSlotDoesNotExist() throws Exception {
        UUID slotId = UUID.randomUUID();
        doThrow(new NotFoundException("No slot found with id '%s'".formatted(slotId)))
                .when(slotService)
                .deleteSlot(slotId);

        mockMvc.perform(delete("/slots/{slotId}", slotId))
                .andExpect(status().isNotFound())
                .andDo(document("delete-slot-not-found"));
    }

    @Test
    void deleteSlot_returns409_whenSlotIsAlreadyBooked() throws Exception {
        UUID slotId = UUID.randomUUID();
        doThrow(new ConflictException("Cannot delete a slot that is already booked - cancel the meeting first"))
                .when(slotService)
                .deleteSlot(slotId);

        mockMvc.perform(delete("/slots/{slotId}", slotId))
                .andExpect(status().isConflict())
                .andDo(document("delete-slot-conflict"));
    }
}