package com.booking.domain.repository;

import com.booking.domain.entity.Ticket;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TicketRepository extends ReactiveCrudRepository<Ticket, Long> {

    Flux<Ticket> findByReservationId(Long reservationId);

    @Query("SELECT COUNT(*) FROM tickets WHERE reservation_id = :reservationId")
    Mono<Long> countByReservationId(Long reservationId);

    Flux<Ticket> findByEventIdAndDateAndStartTime(Long eventId, java.time.LocalDate date, java.time.LocalTime startTime);
}
