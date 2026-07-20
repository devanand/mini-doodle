package com.doodle.scheduler.slot;

import com.doodle.scheduler.slot.dto.AvailabilityResponse;
import com.doodle.scheduler.slot.dto.CreateSlotRequest;
import com.doodle.scheduler.slot.dto.ModifySlotRequest;
import com.doodle.scheduler.slot.dto.SlotResponse;
import com.doodle.scheduler.slot.model.Calendar;
import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SlotController {

    private final SlotService slotService;

    SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping("/users/{userId}/slots")
    ResponseEntity<SlotResponse> createSlot(
            @PathVariable UUID userId, @Valid @RequestBody CreateSlotRequest request) {
        TimeSlot slot = slotService.createSlot(request.toCommand(userId));
        SlotResponse response = SlotResponse.from(slot);
        return ResponseEntity.created(URI.create("/slots/" + slot.getId())).body(response);
    }

    @PutMapping("/slots/{slotId}")
    SlotResponse modifySlot(@PathVariable UUID slotId, @Valid @RequestBody ModifySlotRequest request) {
        TimeSlot slot = slotService.modifySlot(request.toCommand(slotId));
        return SlotResponse.from(slot);
    }

    @DeleteMapping("/slots/{slotId}")
    ResponseEntity<Void> deleteSlot(@PathVariable UUID slotId) {
        slotService.deleteSlot(slotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/availability")
    AvailabilityResponse getAvailability(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) SlotStatus status) {
        Calendar calendar = slotService.getAvailability(userId, from, to, status);
        return AvailabilityResponse.from(calendar);
    }
}