package com.booking.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final Cache<String, Object> localCache;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    /**
     * Get from two-layer cache: L1 (Caffeine) -> L2 (Redis) -> empty
     */
    public <T> Mono<T> get(String key, Class<T> type) {
        // Try L1 cache first
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            log.debug("L1 cache hit for key: {}", key);
            return Mono.just(type.cast(cached));
        }

        // Try L2 cache (Redis)
        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(value -> {
                    log.debug("L2 cache hit for key: {}", key);
                    // Update L1 cache
                    localCache.put(key, value);
                })
                .map(type::cast)
                .doOnError(error -> log.warn("Redis error for key {}: {}", key, error.getMessage()));
    }

    /**
     * Set value in both L1 and L2 caches
     */
    public Mono<Void> set(String key, Object value, long ttlSeconds) {
        // Store in L1 cache
        localCache.put(key, value);

        // Store in L2 cache (Redis)
        return redisTemplate.opsForValue()
                .set(key, value, Duration.ofSeconds(ttlSeconds))
                .then()
                .doOnError(error -> log.warn("Redis set error for key {}: {}", key, error.getMessage()))
                .onErrorResume(e -> Mono.empty()); // Don't fail if Redis is down
    }

    /**
     * Invalidate cache for specific event_date (used after reservation changes)
     */
    public Mono<Void> invalidateEventDateCache(Long eventId, LocalDate date, LocalTime startTime) {
        String pattern = String.format("*:%d:%s:%s:*", eventId, date, startTime);

        // Clear L1 cache
        localCache.invalidateAll();

        // Clear L2 cache by pattern
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then()
                .doOnSuccess(v -> log.debug("Invalidated cache for event {} on {} at {}", eventId, date, startTime))
                .onErrorResume(e -> {
                    log.warn("Redis invalidation error: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Clear all caches
     */
    public Mono<Void> clearAll() {
        localCache.invalidateAll();
        return redisTemplate.execute(connection -> connection.serverCommands().flushDb())
                .then()
                .onErrorResume(e -> Mono.empty());
    }
}
