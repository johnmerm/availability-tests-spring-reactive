package com.booking.service;

import com.booking.domain.repository.ConsumptionRepository;
import com.booking.domain.repository.ConsumptionTTRepository;
import com.booking.domain.repository.ReservationRepository;
import com.booking.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryService {

    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ConsumptionTTRepository consumptionTTRepository;
    private final CacheService cacheService;

    /**
     * Scheduled task to cleanup expired reservations
     * Runs every 10 seconds
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 30000)
    public void cleanupExpiredReservations() {
        log.debug("Running expired reservation cleanup");

        reservationRepository.findExpiredReservations(LocalDateTime.now(), 100)
                .flatMap(this::expireReservation)
                .collectList()
                .subscribe(
                        expired -> {
                            if (!expired.isEmpty()) {
                                log.info("Expired {} reservations", expired.size());
                            }
                        },
                        error -> log.error("Error during expiry cleanup", error)
                );
    }

    /**
     * Expire a single reservation and restore counters
     */
    @Transactional
    public Mono<Long> expireReservation(com.booking.domain.entity.Reservation reservation) {
        log.info("Expiring reservation {}", reservation.getId());

        return ticketRepository.findByReservationId(reservation.getId())
                .collectList()
                .flatMap(tickets -> {
                    // Group tickets by (ticket_type_id, shard_id) to restore counters efficiently
                    Map<String, Integer> totalCountsByTicketType = tickets.stream()
                            .collect(Collectors.groupingBy(
                                    ticket -> ticket.getTicketTypeId() + ":" + ticket.getShardId(),
                                    Collectors.summingInt(ticket -> 1)
                            ));

                    // Restore consumption counters
                    return Mono.when(
                            tickets.stream()
                                    .collect(Collectors.groupingBy(
                                            ticket -> ticket.getShardId(),
                                            Collectors.summingInt(ticket -> 1)
                                    ))
                                    .entrySet()
                                    .stream()
                                    .map(entry ->
                                            consumptionRepository.decrementShardCurrent(
                                                    reservation.getEventId(),
                                                    reservation.getDate(),
                                                    reservation.getStartTime(),
                                                    entry.getKey(),
                                                    entry.getValue()
                                            )
                                    )
                                    .toList()
                    )
                    .then(
                            Mono.when(
                                    tickets.stream()
                                            .collect(Collectors.groupingBy(
                                                    ticket -> ticket.getTicketTypeId() + ":" + ticket.getShardId()
                                            ))
                                            .entrySet()
                                            .stream()
                                            .map(entry -> {
                                                String[] parts = entry.getKey().split(":");
                                                Long ticketTypeId = Long.parseLong(parts[0]);
                                                Integer shardId = Integer.parseInt(parts[1]);
                                                int quantity = entry.getValue().size();

                                                return consumptionTTRepository.decrementShardCurrent(
                                                        reservation.getEventId(),
                                                        reservation.getDate(),
                                                        reservation.getStartTime(),
                                                        ticketTypeId,
                                                        shardId,
                                                        quantity
                                                );
                                            })
                                            .toList()
                            )
                    );
                })
                .then(reservationRepository.markAsExpired(reservation.getId()))
                .map(Long::valueOf)
                .doOnSuccess(v ->
                        cacheService.invalidateEventDateCache(
                                reservation.getEventId(),
                                reservation.getDate(),
                                reservation.getStartTime()
                        ).subscribe()
                );
    }
}
