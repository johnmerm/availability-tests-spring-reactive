package com.booking.controller;

import com.booking.dto.request.PaymentRequest;
import com.booking.dto.request.ReservationRequest;
import com.booking.dto.response.ReservationResponse;
import com.booking.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * POST /events/{eventId}/{date}/{startTime}
     * Create a new reservation
     */
    @PostMapping("/events/{eventId}/{date}/{startTime}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ReservationResponse> createReservation(
            @PathVariable Long eventId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
            @Valid @RequestBody ReservationRequest request) {

        log.info("Creating reservation for event {} on {} at {} with {} ticket types",
                eventId, date, startTime, request.getTickets().size());

        return reservationService.createReservation(eventId, date, startTime, request.getTickets());
    }

    /**
     * POST /reservation/{reservationId}
     * Confirm payment for a reservation
     */
    @PostMapping("/reservation/{reservationId}")
    public Mono<ReservationResponse> confirmPayment(
            @PathVariable Long reservationId,
            @Valid @RequestBody PaymentRequest request) {

        log.info("Confirming payment for reservation {} with reference {}",
                reservationId, request.getPaymentReference());

        return reservationService.confirmPayment(reservationId, request.getPaymentReference());
    }
}
