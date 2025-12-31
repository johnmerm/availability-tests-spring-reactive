package com.booking.domain.repository;

import com.booking.domain.entity.EventDate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

@Repository
public interface EventDateRepository extends ReactiveCrudRepository<EventDate, Long> {

    @Query("SELECT * FROM event_date WHERE event_id = :eventId AND date >= :startDate AND date <= :endDate ORDER BY date, start_time LIMIT :limit OFFSET :offset")
    Flux<EventDate> findByEventIdAndDateRange(Long eventId, LocalDate startDate, LocalDate endDate, int limit, int offset);

    @Query("SELECT COUNT(*) FROM event_date WHERE event_id = :eventId AND date >= :startDate AND date <= :endDate")
    Mono<Long> countByEventIdAndDateRange(Long eventId, LocalDate startDate, LocalDate endDate);

    Flux<EventDate> findByEventId(Long eventId);

    @Query("SELECT * FROM event_date WHERE event_id = :eventId AND date = :date AND start_time = :startTime")
    Mono<EventDate> findByEventIdAndDateAndStartTime(Long eventId, LocalDate date, LocalTime startTime);
}
