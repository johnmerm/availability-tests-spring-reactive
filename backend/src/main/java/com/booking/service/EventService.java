package com.booking.service;

import com.booking.domain.entity.Event;
import com.booking.domain.entity.EventDate;
import com.booking.domain.repository.ConsumptionRepository;
import com.booking.domain.repository.ConsumptionTTRepository;
import com.booking.domain.repository.EventDateRepository;
import com.booking.domain.repository.EventRepository;
import com.booking.dto.response.AvailabilityResponse;
import com.booking.dto.response.EventDetailResponse;
import com.booking.dto.response.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventDateRepository eventDateRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ConsumptionTTRepository consumptionTTRepository;

    /**
     * Get all events (paginated)
     */
    public Flux<EventResponse> getAllEvents(int page, int size) {
        int offset = page * size;
        return eventRepository.findAllPaginated(size, offset)
                .map(this::toEventResponse);
    }

    /**
     * Get total count of events
     */
    public Mono<Long> countEvents() {
        return eventRepository.countAll();
    }

    /**
     * Get event details with dates and availability
     */
    public Flux<EventDetailResponse> getEventDetails(
            Long eventId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {

        int offset = page * size;

        return eventDateRepository.findByEventIdAndDateRange(eventId, startDate, endDate, size, offset)
                .flatMap(eventDate ->
                    getAvailabilityForEventDate(eventDate)
                            .map(availability -> toEventDetailResponse(eventDate, availability))
                );
    }

    /**
     * Get availability for a specific event date
     */
    private Mono<AvailabilityResponse> getAvailabilityForEventDate(EventDate eventDate) {
        Mono<Integer> totalAvailable = consumptionRepository
                .getTotalAvailability(eventDate.getEventId(), eventDate.getDate(), eventDate.getStartTime())
                .defaultIfEmpty(0);

        return totalAvailable.map(available ->
                AvailabilityResponse.builder()
                        .totalAvailable(available)
                        .build()
        );
    }

    private EventResponse toEventResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .maxTickets(event.getMaxTickets())
                .build();
    }

    private EventDetailResponse toEventDetailResponse(EventDate eventDate, AvailabilityResponse availability) {
        return EventDetailResponse.builder()
                .eventId(eventDate.getEventId())
                .date(eventDate.getDate())
                .startTime(eventDate.getStartTime())
                .totalAvailable(availability.getTotalAvailable())
                .build();
    }
}
