package com.booking.controller;

import com.booking.dto.response.EventDetailResponse;
import com.booking.dto.response.EventResponse;
import com.booking.dto.response.PagedResponse;
import com.booking.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * GET /events?page=0&size=20
     * Returns paginated list of events
     */
    @GetMapping
    public Mono<PagedResponse<EventResponse>> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return eventService.getAllEvents(page, size)
                .collectList()
                .zipWith(eventService.countEvents())
                .map(tuple -> PagedResponse.<EventResponse>builder()
                        .content(tuple.getT1())
                        .totalElements(tuple.getT2())
                        .page(page)
                        .size(size)
                        .totalPages((tuple.getT2() + size - 1) / size)
                        .build());
    }

    /**
     * GET /events/{eventId}?startDate=2024-01-01&endDate=2024-12-31&page=0&size=20
     * Returns event dates with availability
     */
    @GetMapping("/{eventId}")
    public Mono<PagedResponse<EventDetailResponse>> getEventDetails(
            @PathVariable Long eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.now();
        LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now().plusYears(1);

        return eventService.getEventDetails(eventId, effectiveStartDate, effectiveEndDate, page, size)
                .collectList()
                .map(eventDetails -> PagedResponse.<EventDetailResponse>builder()
                        .content(eventDetails)
                        .totalElements(eventDetails.size())
                        .page(page)
                        .size(size)
                        .totalPages((eventDetails.size() + size - 1) / size)
                        .build());
    }
}
