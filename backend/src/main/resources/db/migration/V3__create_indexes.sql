-- V3: Create indexes for performance optimization

-- Reservation indexes
-- Critical for expiry cleanup job - finds PENDING reservations that have expired
CREATE INDEX idx_reservation_status_expires
ON reservation(status, expires_at)
WHERE status = 'PENDING';

-- Index for finding reservations by event and date
CREATE INDEX idx_reservation_event_date
ON reservation(event_id, date, start_time);

-- Index for reservation lookups by status
CREATE INDEX idx_reservation_status
ON reservation(status);

-- Ticket indexes
-- Index for finding all tickets in a reservation (used during expiry)
CREATE INDEX idx_tickets_reservation
ON tickets(reservation_id);

-- Index for ticket queries by event and date
CREATE INDEX idx_tickets_event_date
ON tickets(event_id, date, start_time);

-- Index for ticket type queries
CREATE INDEX idx_tickets_type
ON tickets(ticket_type_id);

-- Consumption indexes
-- Critical for concurrent shard updates - allows fast lookup and row locking
CREATE INDEX idx_consumption_current
ON consumption(event_id, date, start_time, shard_id, shard_current);

-- Index for availability queries (summing available capacity across shards)
CREATE INDEX idx_consumption_availability
ON consumption(event_id, date, start_time, shard_max, shard_current);

-- Consumption TT indexes
-- Critical for per-ticket-type shard updates
CREATE INDEX idx_consumption_tt_current
ON consumption_tt(event_id, date, start_time, ticket_type_id, shard_id, shard_current);

-- Index for per-ticket-type availability queries
CREATE INDEX idx_consumption_tt_availability
ON consumption_tt(event_id, date, start_time, ticket_type_id, shard_max, shard_current);

-- Event date indexes
-- Index for date range queries (GET /events/{id}?startDate=...&endDate=...)
CREATE INDEX idx_event_date_range
ON event_date(event_id, date);

-- Index for temporal queries
CREATE INDEX idx_event_date_temporal
ON event_date(date, start_time);

-- Event ticket type indexes
-- Index for finding ticket type limits for an event
CREATE INDEX idx_event_ticket_type_event
ON event_ticket_type(event_id);

-- Index for finding events by ticket type
CREATE INDEX idx_event_ticket_type_type
ON event_ticket_type(ticket_type_id);

-- Comments
COMMENT ON INDEX idx_reservation_status_expires IS 'Partial index for fast expiry cleanup - only PENDING reservations';
COMMENT ON INDEX idx_consumption_current IS 'Critical for concurrent reservation processing - enables fast shard selection';
COMMENT ON INDEX idx_consumption_tt_current IS 'Critical for per-ticket-type capacity checks';
COMMENT ON INDEX idx_event_date_range IS 'Optimizes date range filtering in event availability queries';
