package com.doodle.scheduler.meeting;

import com.doodle.scheduler.meeting.application.usecase.BookMeetingUseCase;
import com.doodle.scheduler.meeting.application.usecase.CancelMeetingUseCase;
import com.doodle.scheduler.meeting.application.usecase.FindMeetingUseCase;
import com.doodle.scheduler.meeting.dto.BookMeetingRequest;
import com.doodle.scheduler.meeting.dto.MeetingResponse;
import com.doodle.scheduler.meeting.domain.Meeting;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP boundary for the meeting bounded context. Depends only on
 * BookMeetingUseCase, not BookMeetingService directly - the use-case
 * interface is the seam, same reasoning as MeetingRepositoryPort on the
 * persistence side.
 */
@RestController
@RequestMapping("/meetings")
@RequiredArgsConstructor
class MeetingController {

    private final BookMeetingUseCase bookMeetingUseCase;
    private final FindMeetingUseCase findMeetingUseCase;
    private final CancelMeetingUseCase cancelMeetingUseCase;

    @PostMapping
    ResponseEntity<MeetingResponse> book(@Valid @RequestBody BookMeetingRequest request) {
        Meeting meeting = bookMeetingUseCase.book(request.toCommand());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(meeting.getId())
                .toUri();

        return ResponseEntity.created(location).body(MeetingResponse.from(meeting));
    }

    @GetMapping("/{id}")
    ResponseEntity<MeetingResponse> find(@PathVariable UUID id) {
        Meeting meeting = findMeetingUseCase.find(id);
        return ResponseEntity.ok(MeetingResponse.from(meeting));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        cancelMeetingUseCase.cancel(id);
        return ResponseEntity.noContent().build();
    }
}