-- V1: Create base tables for the booking system

-- Events table
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    max_tickets INTEGER NOT NULL CHECK (max_tickets > 0),
    num_shards INTEGER NOT NULL CHECK (num_shards > 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Ticket types table
CREATE TABLE ticket_type (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Event dates table (many-to-many between events and dates)
CREATE TABLE event_date (
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, date, start_time)
);

-- Event ticket type limits table
CREATE TABLE event_ticket_type (
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    ticket_type_id BIGINT NOT NULL REFERENCES ticket_type(id) ON DELETE CASCADE,
    max_per_tt INTEGER NOT NULL CHECK (max_per_tt > 0),
    num_of_shards_per_tt INTEGER NOT NULL CHECK (num_of_shards_per_tt > 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, ticket_type_id)
);

-- Reservations table
CREATE TABLE reservation (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    payment_ref VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (event_id, date, start_time) REFERENCES event_date(event_id, date, start_time)
);

-- Tickets table
CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    ticket_type_id BIGINT NOT NULL REFERENCES ticket_type(id),
    reservation_id BIGINT NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    shard_id INTEGER NOT NULL CHECK (shard_id >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (event_id, date, start_time) REFERENCES event_date(event_id, date, start_time)
);

-- Comments for documentation
COMMENT ON TABLE events IS 'Stores event definitions with capacity and sharding configuration';
COMMENT ON TABLE ticket_type IS 'Defines different types of tickets (e.g., normal, VIP, discount)';
COMMENT ON TABLE event_date IS 'Links events to specific dates and times when they occur';
COMMENT ON TABLE event_ticket_type IS 'Optional per-ticket-type capacity limits for events';
COMMENT ON TABLE reservation IS 'Stores reservation records with payment status';
COMMENT ON TABLE tickets IS 'Individual ticket records linked to reservations, includes shard_id for counter restoration';

COMMENT ON COLUMN tickets.shard_id IS 'Tracks which shard was used for this ticket, enables precise counter restoration on expiry';
COMMENT ON COLUMN reservation.expires_at IS 'Timestamp when unpaid reservation expires (typically 60 seconds after creation)';
COMMENT ON COLUMN reservation.status IS 'Reservation status: PENDING (awaiting payment), CONFIRMED (paid), EXPIRED (timeout), CANCELLED (manually cancelled)';
