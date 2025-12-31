-- V4: Create database functions for shard initialization and expiry cleanup

-- Function to initialize consumption shards for a new event_date
-- Called automatically when a new event_date is created
CREATE OR REPLACE FUNCTION initialize_consumption_shards(
    p_event_id BIGINT,
    p_date DATE,
    p_start_time TIME
) RETURNS VOID AS $$
DECLARE
    v_num_shards INTEGER;
    v_max_tickets INTEGER;
    v_shard_max INTEGER;
    v_shard_id INTEGER;
    v_ticket_type RECORD;
BEGIN
    -- Get event configuration
    SELECT num_shards, max_tickets
    INTO v_num_shards, v_max_tickets
    FROM events
    WHERE id = p_event_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Event % not found', p_event_id;
    END IF;

    -- Calculate shard_max for total consumption
    v_shard_max := CEIL(v_max_tickets::DECIMAL / v_num_shards);

    -- Create consumption shards for total capacity
    FOR v_shard_id IN 0..(v_num_shards - 1) LOOP
        INSERT INTO consumption (event_id, date, start_time, shard_id, shard_current, shard_max)
        VALUES (p_event_id, p_date, p_start_time, v_shard_id, 0, v_shard_max)
        ON CONFLICT (event_id, date, start_time, shard_id) DO NOTHING;
    END LOOP;

    -- Initialize consumption_tt shards if per-type limits exist
    FOR v_ticket_type IN
        SELECT ticket_type_id, max_per_tt, num_of_shards_per_tt
        FROM event_ticket_type
        WHERE event_id = p_event_id
    LOOP
        -- Create shards for this ticket type
        FOR v_shard_id IN 0..(v_ticket_type.num_of_shards_per_tt - 1) LOOP
            INSERT INTO consumption_tt (
                event_id,
                date,
                start_time,
                ticket_type_id,
                shard_id,
                shard_current,
                shard_max
            )
            VALUES (
                p_event_id,
                p_date,
                p_start_time,
                v_ticket_type.ticket_type_id,
                v_shard_id,
                0,
                CEIL(v_ticket_type.max_per_tt::DECIMAL / v_ticket_type.num_of_shards_per_tt)
            )
            ON CONFLICT (event_id, date, start_time, ticket_type_id, shard_id) DO NOTHING;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Initialized shards for event % on % at %', p_event_id, p_date, p_start_time;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION initialize_consumption_shards IS 'Initializes consumption and consumption_tt shards when a new event_date is created';


-- Function to restore consumption counters when expiring a reservation
-- This is called by the ExpiryService for each expired reservation
CREATE OR REPLACE FUNCTION restore_reservation_counters(
    p_reservation_id BIGINT
) RETURNS TABLE(affected_rows INTEGER) AS $$
DECLARE
    v_total_restored INTEGER := 0;
    v_ticket RECORD;
BEGIN
    -- For each ticket in the reservation, restore the consumption counters
    FOR v_ticket IN
        SELECT
            t.event_id,
            t.date,
            t.start_time,
            t.ticket_type_id,
            t.shard_id,
            COUNT(*) as quantity
        FROM tickets t
        WHERE t.reservation_id = p_reservation_id
        GROUP BY t.event_id, t.date, t.start_time, t.ticket_type_id, t.shard_id
    LOOP
        -- Restore total consumption counter
        UPDATE consumption
        SET shard_current = GREATEST(0, shard_current - v_ticket.quantity),
            updated_at = NOW()
        WHERE event_id = v_ticket.event_id
          AND date = v_ticket.date
          AND start_time = v_ticket.start_time
          AND shard_id = v_ticket.shard_id;

        GET DIAGNOSTICS v_total_restored = ROW_COUNT;

        -- Restore per-ticket-type consumption counter
        UPDATE consumption_tt
        SET shard_current = GREATEST(0, shard_current - v_ticket.quantity),
            updated_at = NOW()
        WHERE event_id = v_ticket.event_id
          AND date = v_ticket.date
          AND start_time = v_ticket.start_time
          AND ticket_type_id = v_ticket.ticket_type_id
          AND shard_id = v_ticket.shard_id;

        RAISE NOTICE 'Restored % tickets for event % on % at % (shard %, type %)',
            v_ticket.quantity, v_ticket.event_id, v_ticket.date, v_ticket.start_time,
            v_ticket.shard_id, v_ticket.ticket_type_id;
    END LOOP;

    RETURN QUERY SELECT v_total_restored;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION restore_reservation_counters IS 'Restores consumption counters when a reservation expires, using shard_id from tickets table';


-- Function to get available capacity for an event date (aggregated across all shards)
CREATE OR REPLACE FUNCTION get_event_date_availability(
    p_event_id BIGINT,
    p_date DATE,
    p_start_time TIME
) RETURNS TABLE(
    total_available INTEGER,
    total_capacity INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        SUM(shard_max - shard_current)::INTEGER as total_available,
        SUM(shard_max)::INTEGER as total_capacity
    FROM consumption
    WHERE event_id = p_event_id
      AND date = p_date
      AND start_time = p_start_time;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_event_date_availability IS 'Returns total available and capacity for an event date by summing across all shards';


-- Function to get per-ticket-type availability for an event date
CREATE OR REPLACE FUNCTION get_ticket_type_availability(
    p_event_id BIGINT,
    p_date DATE,
    p_start_time TIME
) RETURNS TABLE(
    ticket_type_id BIGINT,
    ticket_type_name VARCHAR,
    available INTEGER,
    capacity INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        ctt.ticket_type_id,
        tt.name as ticket_type_name,
        SUM(ctt.shard_max - ctt.shard_current)::INTEGER as available,
        SUM(ctt.shard_max)::INTEGER as capacity
    FROM consumption_tt ctt
    JOIN ticket_type tt ON tt.id = ctt.ticket_type_id
    WHERE ctt.event_id = p_event_id
      AND ctt.date = p_date
      AND ctt.start_time = p_start_time
      AND ctt.shard_max IS NOT NULL
    GROUP BY ctt.ticket_type_id, tt.name;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_ticket_type_availability IS 'Returns per-ticket-type availability by summing across all shards';


-- Trigger to automatically initialize shards when a new event_date is created
CREATE OR REPLACE FUNCTION trigger_initialize_shards()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM initialize_consumption_shards(NEW.event_id, NEW.date, NEW.start_time);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER event_date_after_insert
AFTER INSERT ON event_date
FOR EACH ROW
EXECUTE FUNCTION trigger_initialize_shards();

COMMENT ON TRIGGER event_date_after_insert ON event_date IS 'Automatically initializes consumption shards when a new event_date is created';
