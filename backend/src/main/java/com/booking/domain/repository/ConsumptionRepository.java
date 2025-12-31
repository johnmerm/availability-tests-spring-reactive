package com.booking.domain.repository;

import com.booking.domain.entity.Consumption;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

@Repository
public interface ConsumptionRepository extends ReactiveCrudRepository<Consumption, Long> {

    @Query("SELECT * FROM consumption WHERE event_id = :eventId AND date = :date AND start_time = :startTime AND shard_current < shard_max ORDER BY shard_id")
    Flux<Consumption> findAvailableShards(Long eventId, LocalDate date, LocalTime startTime);

    @Query("SELECT * FROM consumption WHERE event_id = :eventId AND date = :date AND start_time = :startTime AND shard_id = :shardId")
    Mono<Consumption> findByEventDateAndShard(Long eventId, LocalDate date, LocalTime startTime, Integer shardId);

    @Modifying
    @Query("UPDATE consumption SET shard_current = shard_current + :quantity, updated_at = NOW() " +
           "WHERE event_id = :eventId AND date = :date AND start_time = :startTime AND shard_id = :shardId " +
           "AND shard_current + :quantity <= shard_max")
    Mono<Integer> incrementShardCurrent(Long eventId, LocalDate date, LocalTime startTime, Integer shardId, Integer quantity);

    @Modifying
    @Query("UPDATE consumption SET shard_current = GREATEST(0, shard_current - :quantity), updated_at = NOW() " +
           "WHERE event_id = :eventId AND date = :date AND start_time = :startTime AND shard_id = :shardId")
    Mono<Integer> decrementShardCurrent(Long eventId, LocalDate date, LocalTime startTime, Integer shardId, Integer quantity);

    @Query("SELECT SUM(shard_max - shard_current) as available FROM consumption WHERE event_id = :eventId AND date = :date AND start_time = :startTime")
    Mono<Integer> getTotalAvailability(Long eventId, LocalDate date, LocalTime startTime);

    Flux<Consumption> findByEventIdAndDateAndStartTime(Long eventId, LocalDate date, LocalTime startTime);
}
