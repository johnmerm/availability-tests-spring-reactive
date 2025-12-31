package com.booking.sharding;

import com.booking.domain.repository.ConsumptionRepository;
import com.booking.domain.repository.ConsumptionTTRepository;
import com.booking.exception.InsufficientCapacityException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShardSplitter {

    private final ConsumptionRepository consumptionRepository;
    private final ConsumptionTTRepository consumptionTTRepository;

    /**
     * Split a large reservation request across multiple shards
     */
    public Mono<List<ShardAllocation>> splitReservation(
            Long eventId, LocalDate date, LocalTime startTime,
            Long ticketTypeId, int requestedQuantity) {

        if (ticketTypeId == null) {
            return splitForTotalConsumption(eventId, date, startTime, requestedQuantity);
        } else {
            return splitForTicketTypeConsumption(eventId, date, startTime, ticketTypeId, requestedQuantity);
        }
    }

    private Mono<List<ShardAllocation>> splitForTotalConsumption(
            Long eventId, LocalDate date, LocalTime startTime, int requestedQuantity) {

        return consumptionRepository.findByEventIdAndDateAndStartTime(eventId, date, startTime)
                .collectList()
                .map(shards -> {
                    List<ShardAllocation> allocations = new ArrayList<>();
                    int remaining = requestedQuantity;

                    for (var shard : shards) {
                        if (remaining <= 0) break;

                        int available = shard.getShardMax() - shard.getShardCurrent();
                        if (available > 0) {
                            int allocate = Math.min(remaining, available);
                            allocations.add(new ShardAllocation(shard.getShardId(), allocate));
                            remaining -= allocate;
                        }
                    }

                    if (remaining > 0) {
                        throw new InsufficientCapacityException(
                                "Cannot allocate " + requestedQuantity + " tickets, only " +
                                (requestedQuantity - remaining) + " available"
                        );
                    }

                    log.debug("Split {} tickets across {} shards", requestedQuantity, allocations.size());
                    return allocations;
                });
    }

    private Mono<List<ShardAllocation>> splitForTicketTypeConsumption(
            Long eventId, LocalDate date, LocalTime startTime,
            Long ticketTypeId, int requestedQuantity) {

        return consumptionTTRepository.findByEventIdAndDateAndStartTime(eventId, date, startTime)
                .filter(ctt -> ctt.getTicketTypeId().equals(ticketTypeId))
                .filter(ctt -> ctt.getShardMax() != null)
                .collectList()
                .map(shards -> {
                    List<ShardAllocation> allocations = new ArrayList<>();
                    int remaining = requestedQuantity;

                    for (var shard : shards) {
                        if (remaining <= 0) break;

                        int available = shard.getShardMax() - shard.getShardCurrent();
                        if (available > 0) {
                            int allocate = Math.min(remaining, available);
                            allocations.add(new ShardAllocation(shard.getShardId(), allocate));
                            remaining -= allocate;
                        }
                    }

                    if (remaining > 0) {
                        throw new InsufficientCapacityException(
                                "Cannot allocate " + requestedQuantity + " tickets of type " +
                                ticketTypeId + ", only " + (requestedQuantity - remaining) + " available"
                        );
                    }

                    return allocations;
                });
    }

    @Data
    @AllArgsConstructor
    public static class ShardAllocation {
        private Integer shardId;
        private Integer quantity;
    }
}
