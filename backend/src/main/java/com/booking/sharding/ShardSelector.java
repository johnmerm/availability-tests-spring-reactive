package com.booking.sharding;

import com.booking.exception.NoShardsAvailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShardSelector {

    private final ShardHealthMonitor healthMonitor;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, AtomicInteger> instanceCounters = new ConcurrentHashMap<>();

    /**
     * Select a shard using round-robin with instance-local counter
     */
    public Mono<Integer> selectShard(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId) {
        return healthMonitor.getAvailableShards(eventId, date, startTime, ticketTypeId)
                .map(availableShards -> {
                    if (availableShards.isEmpty()) {
                        throw new NoShardsAvailableException(
                                "No available shards for event " + eventId + " on " + date + " at " + startTime
                        );
                    }

                    String key = buildCounterKey(eventId, date, startTime, ticketTypeId);
                    AtomicInteger counter = instanceCounters.computeIfAbsent(key, k -> new AtomicInteger(0));

                    int idx = Math.abs(counter.getAndIncrement()) % availableShards.size();
                    Integer selectedShard = availableShards.get(idx);

                    log.debug("Selected shard {} from {} available shards for event {}",
                             selectedShard, availableShards.size(), eventId);

                    return selectedShard;
                });
    }

    /**
     * Select a random shard from available shards
     */
    public Mono<Integer> selectRandomShard(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId) {
        return healthMonitor.getAvailableShards(eventId, date, startTime, ticketTypeId)
                .map(availableShards -> {
                    if (availableShards.isEmpty()) {
                        throw new NoShardsAvailableException(
                                "No available shards for event " + eventId + " on " + date + " at " + startTime
                        );
                    }

                    int idx = random.nextInt(availableShards.size());
                    return availableShards.get(idx);
                });
    }

    private String buildCounterKey(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId) {
        return String.format("%d:%s:%s:%d", eventId, date, startTime, ticketTypeId != null ? ticketTypeId : 0);
    }
}
