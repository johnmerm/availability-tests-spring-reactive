package com.booking.domain.repository;

import com.booking.domain.entity.EventTicketType;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface EventTicketTypeRepository extends ReactiveCrudRepository<EventTicketType, Long> {

    Flux<EventTicketType> findByEventId(Long eventId);

    @Query("SELECT * FROM event_ticket_type WHERE event_id = :eventId AND ticket_type_id = :ticketTypeId")
    Mono<EventTicketType> findByEventIdAndTicketTypeId(Long eventId, Long ticketTypeId);
}
