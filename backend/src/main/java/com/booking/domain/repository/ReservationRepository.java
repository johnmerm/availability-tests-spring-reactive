package com.booking.domain.repository;

import com.booking.domain.entity.Reservation;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ReservationRepository extends ReactiveCrudRepository<Reservation, Long> {

    @Query("SELECT * FROM reservation WHERE status = 'PENDING' AND expires_at < :now ORDER BY expires_at LIMIT :limit FOR UPDATE SKIP LOCKED")
    Flux<Reservation> findExpiredReservations(LocalDateTime now, int limit);

    @Modifying
    @Query("UPDATE reservation SET status = 'EXPIRED', updated_at = NOW() WHERE id = :id")
    Mono<Integer> markAsExpired(Long id);

    @Modifying
    @Query("UPDATE reservation SET status = 'CONFIRMED', payment_ref = :paymentRef, updated_at = NOW() WHERE id = :id AND status = 'PENDING'")
    Mono<Integer> confirmReservation(Long id, String paymentRef);

    Flux<Reservation> findByEventIdAndDateAndStartTime(Long eventId, java.time.LocalDate date, java.time.LocalTime startTime);
}
