package com.booking.domain.repository;

import com.booking.domain.entity.ConsumptionTT;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

@Repository
public interface ConsumptionTTRepository extends ReactiveCrudRepository<ConsumptionTT, Long> {

    @Query("SELECT * FROM consumption_tt WHERE event_id = :eventId AND date = :date AND start_time = :startTime " +
           "AND ticket_type_id = :ticketTypeId AND shard_max IS NOT NULL AND shard_current < shard_max ORDER BY shard_id")
    Flux<ConsumptionTT> findAvailableShards(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId);

    @Query("SELECT * FROM consumption_tt WHERE event_id = :eventId AND date = :date AND start_time = :startTime " +
           "AND ticket_type_id = :ticketTypeId AND shard_id = :shardId")
    Mono<ConsumptionTT> findByEventDateTicketTypeAndShard(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId, Integer shardId);

    @Modifying
    @Query("UPDATE consumption_tt SET shard_current = shard_current + :quantity, updated_at = NOW() " +
           "WHERE event_id = :eventId AND date = :date AND start_time = :startTime " +
           "AND ticket_type_id = :ticketTypeId AND shard_id = :shardId " +
           "AND (shard_max IS NULL OR shard_current + :quantity <= shard_max)")
    Mono<Integer> incrementShardCurrent(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId, Integer shardId, Integer quantity);

    @Modifying
    @Query("UPDATE consumption_tt SET shard_current = GREATEST(0, shard_current - :quantity), updated_at = NOW() " +
           "WHERE event_id = :eventId AND date = :date AND start_time = :startTime " +
           "AND ticket_type_id = :ticketTypeId AND shard_id = :shardId")
    Mono<Integer> decrementShardCurrent(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId, Integer shardId, Integer quantity);

    @Query("SELECT ticket_type_id, SUM(shard_max - shard_current) as available FROM consumption_tt " +
           "WHERE event_id = :eventId AND date = :date AND start_time = :startTime AND shard_max IS NOT NULL " +
           "GROUP BY ticket_type_id")
    Flux<ConsumptionTT> getAvailabilityByTicketType(Long eventId, LocalDate date, LocalTime startTime);

    Flux<ConsumptionTT> findByEventIdAndDateAndStartTime(Long eventId, LocalDate date, LocalTime startTime);
}
