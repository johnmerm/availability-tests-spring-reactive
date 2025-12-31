package com.booking.service;

import com.booking.domain.entity.Reservation;
import com.booking.domain.entity.Ticket;
import com.booking.domain.repository.ConsumptionRepository;
import com.booking.domain.repository.ConsumptionTTRepository;
import com.booking.domain.repository.ReservationRepository;
import com.booking.domain.repository.TicketRepository;
import com.booking.dto.request.TicketRequest;
import com.booking.dto.response.ReservationResponse;
import com.booking.exception.InsufficientCapacityException;
import com.booking.exception.ReservationNotFoundException;
import com.booking.sharding.ShardSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ConsumptionTTRepository consumptionTTRepository;
    private final ShardSelector shardSelector;
    private final CacheService cacheService;

    @Value("${booking.reservation.ttl-seconds:60}")
    private long reservationTtlSeconds;

    /**
     * Create a new reservation (transactional)
     */
    @Transactional
    public Mono<ReservationResponse> createReservation(
            Long eventId,
            LocalDate date,
            LocalTime startTime,
            List<TicketRequest> ticketRequests) {

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(reservationTtlSeconds);

        // Create reservation
        Reservation reservation = Reservation.builder()
                .eventId(eventId)
                .date(date)
                .startTime(startTime)
                .status(Reservation.ReservationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        return reservationRepository.save(reservation)
                .flatMap(savedReservation ->
                    // Process each ticket type request
                    Flux.fromIterable(ticketRequests)
                            .flatMap(ticketReq ->
                                processTicketRequest(savedReservation, ticketReq)
                            )
                            .collectList()
                            .map(tickets -> toReservationResponse(savedReservation, tickets.size()))
                )
                .doOnSuccess(response ->
                    cacheService.invalidateEventDateCache(eventId, date, startTime).subscribe()
                );
    }

    /**
     * Process a single ticket type request (select shard, update counters, create tickets)
     */
    private Mono<List<Ticket>> processTicketRequest(Reservation reservation, TicketRequest ticketRequest) {
        return shardSelector.selectShard(
                        reservation.getEventId(),
                        reservation.getDate(),
                        reservation.getStartTime(),
                        ticketRequest.getTicketTypeId()
                )
                .flatMap(shardId ->
                    updateConsumptionCounters(
                            reservation.getEventId(),
                            reservation.getDate(),
                            reservation.getStartTime(),
                            ticketRequest.getTicketTypeId(),
                            shardId,
                            ticketRequest.getQuantity()
                    )
                    .flatMap(success -> {
                        if (!success) {
                            return Mono.error(new InsufficientCapacityException(
                                    "Shard " + shardId + " full for ticket type " + ticketRequest.getTicketTypeId()
                            ));
                        }

                        // Create tickets
                        return createTickets(reservation, ticketRequest, shardId);
                    })
                );
    }

    /**
     * Update consumption counters atomically
     */
    private Mono<Boolean> updateConsumptionCounters(
            Long eventId, LocalDate date, LocalTime startTime,
            Long ticketTypeId, Integer shardId, Integer quantity) {

        // Update total consumption
        Mono<Boolean> totalUpdate = consumptionRepository
                .incrementShardCurrent(eventId, date, startTime, shardId, quantity)
                .map(rowsUpdated -> rowsUpdated > 0);

        // Update per-ticket-type consumption (if applicable)
        Mono<Boolean> ttUpdate = consumptionTTRepository
                .incrementShardCurrent(eventId, date, startTime, ticketTypeId, shardId, quantity)
                .map(rowsUpdated -> rowsUpdated > 0)
                .defaultIfEmpty(true); // If no per-type limit, assume success

        return Mono.zip(totalUpdate, ttUpdate)
                .map(tuple -> tuple.getT1() && tuple.getT2());
    }

    /**
     * Create individual ticket records
     */
    private Mono<List<Ticket>> createTickets(Reservation reservation, TicketRequest ticketRequest, Integer shardId) {
        List<Ticket> tickets = new java.util.ArrayList<>();
        for (int i = 0; i < ticketRequest.getQuantity(); i++) {
            tickets.add(Ticket.builder()
                    .eventId(reservation.getEventId())
                    .date(reservation.getDate())
                    .startTime(reservation.getStartTime())
                    .ticketTypeId(ticketRequest.getTicketTypeId())
                    .reservationId(reservation.getId())
                    .shardId(shardId)
                    .build());
        }

        return ticketRepository.saveAll(tickets).collectList();
    }

    /**
     * Confirm payment for a reservation
     */
    @Transactional
    public Mono<ReservationResponse> confirmPayment(Long reservationId, String paymentRef) {
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException("Reservation " + reservationId + " not found")))
                .flatMap(reservation -> {
                    if (reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Reservation " + reservationId + " is not pending"
                        ));
                    }

                    if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalStateException(
                                "Reservation " + reservationId + " has expired"
                        ));
                    }

                    return reservationRepository.confirmReservation(reservationId, paymentRef)
                            .flatMap(rowsUpdated ->
                                ticketRepository.countByReservationId(reservationId)
                                        .map(ticketCount ->
                                            ReservationResponse.builder()
                                                    .reservationId(reservationId)
                                                    .expiresAt(reservation.getExpiresAt())
                                                    .ticketCount(ticketCount.intValue())
                                                    .status("CONFIRMED")
                                                    .build()
                                        )
                            );
                });
    }

    private ReservationResponse toReservationResponse(Reservation reservation, int ticketCount) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .expiresAt(reservation.getExpiresAt())
                .ticketCount(ticketCount)
                .status(reservation.getStatus().name())
                .build();
    }
}
