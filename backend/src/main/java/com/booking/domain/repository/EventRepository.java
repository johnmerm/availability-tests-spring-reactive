package com.booking.domain.repository;

import com.booking.domain.entity.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface EventRepository extends ReactiveCrudRepository<Event, Long> {

    @Query("SELECT * FROM events ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Event> findAllPaginated(int limit, int offset);

    @Query("SELECT COUNT(*) FROM events")
    Mono<Long> countAll();

    Mono<Event> findByName(String name);
}
