-- V2: Create consumption tables for tracking ticket availability across shards

-- Total consumption per event_date and shard
CREATE TABLE consumption (
    event_id BIGINT NOT NULL,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    shard_id INTEGER NOT NULL CHECK (shard_id >= 0),
    shard_current INTEGER NOT NULL DEFAULT 0 CHECK (shard_current >= 0),
    shard_max INTEGER NOT NULL CHECK (shard_max > 0),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, date, start_time, shard_id),
    FOREIGN KEY (event_id, date, start_time) REFERENCES event_date(event_id, date, start_time) ON DELETE CASCADE
);

-- Per-ticket-type consumption per event_date and shard
CREATE TABLE consumption_tt (
    event_id BIGINT NOT NULL,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    ticket_type_id BIGINT NOT NULL REFERENCES ticket_type(id),
    shard_id INTEGER NOT NULL CHECK (shard_id >= 0),
    shard_current INTEGER NOT NULL DEFAULT 0 CHECK (shard_current >= 0),
    shard_max INTEGER,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, date, start_time, ticket_type_id, shard_id),
    FOREIGN KEY (event_id, date, start_time) REFERENCES event_date(event_id, date, start_time) ON DELETE CASCADE
);

-- Comments for documentation
COMMENT ON TABLE consumption IS 'Tracks total ticket consumption across shards for each event date/time';
COMMENT ON TABLE consumption_tt IS 'Tracks per-ticket-type consumption across shards (when per-type limits are defined)';

COMMENT ON COLUMN consumption.shard_id IS 'Shard identifier in range [0, num_shards)';
COMMENT ON COLUMN consumption.shard_current IS 'Current number of tickets sold in this shard';
COMMENT ON COLUMN consumption.shard_max IS 'Maximum capacity for this shard (max_tickets / num_shards)';

COMMENT ON COLUMN consumption_tt.shard_id IS 'Shard identifier in range [0, num_of_shards_per_tt)';
COMMENT ON COLUMN consumption_tt.shard_current IS 'Current number of tickets sold for this type in this shard';
COMMENT ON COLUMN consumption_tt.shard_max IS 'Maximum capacity for this ticket type in this shard (max_per_tt / num_of_shards_per_tt), NULL if no per-type limit';
