package com.booking.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("reservation")
public class Reservation {

    @Id
    @Column("id")
    private Long id;

    @Column("event_id")
    private Long eventId;

    @Column("date")
    private LocalDate date;

    @Column("start_time")
    private LocalTime startTime;

    @Column("payment_ref")
    private String paymentRef;

    @Column("status")
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum ReservationStatus {
        PENDING,
        CONFIRMED,
        EXPIRED,
        CANCELLED
    }
}
