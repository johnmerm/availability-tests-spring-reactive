package com.booking.domain.repository;

import com.booking.domain.entity.TicketType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface TicketTypeRepository extends ReactiveCrudRepository<TicketType, Long> {

    Mono<TicketType> findByName(String name);
}
