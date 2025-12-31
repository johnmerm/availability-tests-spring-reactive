package com.booking.sharding;

import com.booking.domain.repository.ConsumptionRepository;
import com.booking.domain.repository.ConsumptionTTRepository;
import com.booking.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShardHealthMonitor {

    private final ConsumptionRepository consumptionRepository;
    private final ConsumptionTTRepository consumptionTTRepository;
    private final CacheService cacheService;

    /**
     * Get list of available shard IDs for total consumption (when ticketTypeId is null)
     * or for specific ticket type consumption
     */
    public Mono<List<Integer>> getAvailableShards(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId) {
        if (ticketTypeId == null) {
            return getAvailableShardsForTotal(eventId, date, startTime);
        } else {
            return getAvailableShardsForTicketType(eventId, date, startTime, ticketTypeId);
        }
    }

    private Mono<List<Integer>> getAvailableShardsForTotal(Long eventId, LocalDate date, LocalTime startTime) {
        String cacheKey = buildShardCacheKey(eventId, date, startTime, null);

        return cacheService.get(cacheKey, List.class)
                .switchIfEmpty(
                        consumptionRepository.findAvailableShards(eventId, date, startTime)
                                .map(consumption -> consumption.getShardId())
                                .collectList()
                                .flatMap(shards ->
                                    cacheService.set(cacheKey, shards, 5).thenReturn(shards)
                                )
                );
    }

    private Mono<List<Integer>> getAvailableShardsForTicketType(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId) {
        String cacheKey = buildShardCacheKey(eventId, date, startTime, ticketTypeId);

        return cacheService.get(cacheKey, List.class)
                .switchIfEmpty(
                        consumptionTTRepository.findAvailableShards(eventId, date, startTime, ticketTypeId)
                                .map(consumption -> consumption.getShardId())
                                .collectList()
                                .flatMap(shards ->
                                    cacheService.set(cacheKey, shards, 5).thenReturn(shards)
                                )
                );
    }

    private String buildShardCacheKey(Long eventId, LocalDate date, LocalTime startTime, Long ticketTypeId) {
        return String.format("shard:availability:%d:%s:%s:%s",
                eventId, date, startTime, ticketTypeId != null ? ticketTypeId : "total");
    }
}
