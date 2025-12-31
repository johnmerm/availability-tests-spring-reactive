package com.booking.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("event_ticket_type")
public class EventTicketType {

    @Column("event_id")
    private Long eventId;

    @Column("ticket_type_id")
    private Long ticketTypeId;

    @Column("max_per_tt")
    private Integer maxPerTt;

    @Column("num_of_shards_per_tt")
    private Integer numOfShardsPerTt;

    @Column("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
