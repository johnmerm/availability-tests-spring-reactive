package com.booking.controller;

import com.booking.domain.entity.*;
import com.booking.domain.repository.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/test-data")
@RequiredArgsConstructor
public class TestDataController {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventDateRepository eventDateRepository;
    private final EventTicketTypeRepository eventTicketTypeRepository;
    private final DatabaseClient databaseClient;

    /**
     * POST /api/test-data/seed
     * Creates comprehensive test data
     */
    @PostMapping("/seed")
    public Mono<SeedResponse> seedTestData() {
        log.info("Seeding test data...");

        return createTicketTypes()
                .thenMany(createEvents())
                .collectList()
                .flatMap(events ->
                    createEventTicketTypes()
                            .thenMany(createEventDates())
                            .collectList()
                            .map(dates -> new SeedResponse(
                                    4, // ticket types
                                    events.size(),
                                    dates.size(),
                                    "Test data seeded successfully"
                            ))
                );
    }

    /**
     * POST /api/test-data/event
     * Create a single event with dates
     */
    @PostMapping("/event")
    public Mono<Event> createTestEvent(@RequestBody CreateEventRequest request) {
        Event event = Event.builder()
                .name(request.getName())
                .maxTickets(request.getMaxTickets())
                .numShards(request.getNumShards())
                .build();

        return eventRepository.save(event)
                .flatMap(savedEvent -> {
                    // Create event dates
                    return Flux.fromIterable(request.getDates())
                            .flatMap(dateReq -> {
                                EventDate eventDate = EventDate.builder()
                                        .eventId(savedEvent.getId())
                                        .date(dateReq.getDate())
                                        .startTime(dateReq.getStartTime())
                                        .build();
                                return eventDateRepository.save(eventDate);
                            })
                            .then(Mono.just(savedEvent));
                });
    }

    /**
     * DELETE /api/test-data/clear
     * Clear all test data (careful!)
     */
    @DeleteMapping("/clear")
    public Mono<String> clearAllData() {
        log.warn("Clearing all test data!");

        return databaseClient.sql("TRUNCATE TABLE tickets, reservation, consumption_tt, consumption, event_ticket_type, event_date, events, ticket_type RESTART IDENTITY CASCADE")
                .fetch()
                .rowsUpdated()
                .map(count -> "All data cleared successfully");
    }

    /**
     * GET /api/test-data/stats
     * Get statistics about current data
     */
    @GetMapping("/stats")
    public Mono<DataStats> getStats() {
        Mono<Long> eventCount = eventRepository.count();
        Mono<Long> ticketTypeCount = ticketTypeRepository.count();

        Mono<Long> eventDateCount = databaseClient.sql("SELECT COUNT(*) FROM event_date")
                .fetch()
                .one()
                .map(row -> ((Number) row.get("count")).longValue());

        Mono<Long> reservationCount = databaseClient.sql("SELECT COUNT(*) FROM reservation")
                .fetch()
                .one()
                .map(row -> ((Number) row.get("count")).longValue());

        return Mono.zip(eventCount, ticketTypeCount, eventDateCount, reservationCount)
                .map(tuple -> new DataStats(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4()
                ));
    }

    // Helper methods
    private Flux<TicketType> createTicketTypes() {
        List<TicketType> types = List.of(
                TicketType.builder().name("Normal").build(),
                TicketType.builder().name("VIP").build(),
                TicketType.builder().name("Student Discount").build(),
                TicketType.builder().name("Early Bird").build()
        );
        return ticketTypeRepository.saveAll(types);
    }

    private Flux<Event> createEvents() {
        List<Event> events = List.of(
                Event.builder().name("Rock Concert 2024").maxTickets(1000).numShards(10).build(),
                Event.builder().name("Jazz Night").maxTickets(500).numShards(5).build(),
                Event.builder().name("Classical Symphony").maxTickets(800).numShards(8).build(),
                Event.builder().name("Electronic Music Festival").maxTickets(2000).numShards(20).build(),
                Event.builder().name("Comedy Show").maxTickets(300).numShards(3).build()
        );
        return eventRepository.saveAll(events);
    }

    private Flux<EventTicketType> createEventTicketTypes() {
        List<EventTicketType> configs = List.of(
                // Rock Concert
                EventTicketType.builder().eventId(1L).ticketTypeId(1L).maxPerTt(600).numOfShardsPerTt(6).build(),
                EventTicketType.builder().eventId(1L).ticketTypeId(2L).maxPerTt(200).numOfShardsPerTt(4).build(),
                EventTicketType.builder().eventId(1L).ticketTypeId(3L).maxPerTt(300).numOfShardsPerTt(5).build(),
                // Jazz Night
                EventTicketType.builder().eventId(2L).ticketTypeId(1L).maxPerTt(300).numOfShardsPerTt(3).build(),
                EventTicketType.builder().eventId(2L).ticketTypeId(2L).maxPerTt(150).numOfShardsPerTt(3).build(),
                EventTicketType.builder().eventId(2L).ticketTypeId(4L).maxPerTt(100).numOfShardsPerTt(2).build()
        );
        return eventTicketTypeRepository.saveAll(configs);
    }

    private Flux<EventDate> createEventDates() {
        LocalDate today = LocalDate.now();

        // Rock Concert - next 7 days at 8 PM
        Flux<EventDate> rockConcert = Flux.range(1, 7)
                .map(i -> EventDate.builder()
                        .eventId(1L)
                        .date(today.plusDays(i))
                        .startTime(LocalTime.of(20, 0))
                        .build());

        // Jazz Night - next 3 Fridays at 9 PM
        Flux<EventDate> jazzNight = Flux.range(1, 21)
                .filter(i -> today.plusDays(i).getDayOfWeek().getValue() == 5) // Friday
                .take(3)
                .map(i -> EventDate.builder()
                        .eventId(2L)
                        .date(today.plusDays(i))
                        .startTime(LocalTime.of(21, 0))
                        .build());

        // Classical Symphony - next 4 Sundays at 6 PM
        Flux<EventDate> classical = Flux.range(1, 28)
                .filter(i -> today.plusDays(i).getDayOfWeek().getValue() == 7) // Sunday
                .take(4)
                .map(i -> EventDate.builder()
                        .eventId(3L)
                        .date(today.plusDays(i))
                        .startTime(LocalTime.of(18, 0))
                        .build());

        // Comedy Show - next 5 days, 2 shows per day
        Flux<EventDate> comedy = Flux.range(1, 5)
                .flatMap(i -> Flux.just(
                        EventDate.builder()
                                .eventId(5L)
                                .date(today.plusDays(i))
                                .startTime(LocalTime.of(19, 0))
                                .build(),
                        EventDate.builder()
                                .eventId(5L)
                                .date(today.plusDays(i))
                                .startTime(LocalTime.of(21, 30))
                                .build()
                ));

        return eventDateRepository.saveAll(
                Flux.concat(rockConcert, jazzNight, classical, comedy)
        );
    }

    // DTOs
    @Data
    @AllArgsConstructor
    public static class SeedResponse {
        private int ticketTypes;
        private int events;
        private int eventDates;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class DataStats {
        private long events;
        private long ticketTypes;
        private long eventDates;
        private long reservations;
    }

    @Data
    public static class CreateEventRequest {
        private String name;
        private Integer maxTickets;
        private Integer numShards;
        private List<DateRequest> dates;
    }

    @Data
    public static class DateRequest {
        private LocalDate date;
        private LocalTime startTime;
    }
}
