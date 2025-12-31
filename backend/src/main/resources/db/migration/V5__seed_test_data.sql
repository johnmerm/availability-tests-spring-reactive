-- V5: Seed test data for development and testing

-- Insert ticket types
INSERT INTO ticket_type (id, name, created_at) VALUES
(1, 'Normal', NOW()),
(2, 'VIP', NOW()),
(3, 'Student Discount', NOW()),
(4, 'Early Bird', NOW())
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for ticket_type
SELECT setval('ticket_type_id_seq', (SELECT MAX(id) FROM ticket_type));

-- Insert events
INSERT INTO events (id, name, max_tickets, num_shards, created_at, updated_at) VALUES
(1, 'Rock Concert 2024', 1000, 10, NOW(), NOW()),
(2, 'Jazz Night', 500, 5, NOW(), NOW()),
(3, 'Classical Symphony', 800, 8, NOW(), NOW()),
(4, 'Electronic Music Festival', 2000, 20, NOW(), NOW()),
(5, 'Comedy Show', 300, 3, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for events
SELECT setval('events_id_seq', (SELECT MAX(id) FROM events));

-- Insert event ticket type limits for Rock Concert
INSERT INTO event_ticket_type (event_id, ticket_type_id, max_per_tt, num_of_shards_per_tt, created_at) VALUES
(1, 1, 600, 6, NOW()),  -- Normal: 600 tickets, 6 shards
(1, 2, 200, 4, NOW()),  -- VIP: 200 tickets, 4 shards
(1, 3, 300, 5, NOW())   -- Student: 300 tickets, 5 shards
ON CONFLICT (event_id, ticket_type_id) DO NOTHING;

-- Insert event ticket type limits for Jazz Night
INSERT INTO event_ticket_type (event_id, ticket_type_id, max_per_tt, num_of_shards_per_tt, created_at) VALUES
(2, 1, 300, 3, NOW()),
(2, 2, 150, 3, NOW()),
(2, 4, 100, 2, NOW())
ON CONFLICT (event_id, ticket_type_id) DO NOTHING;

-- Insert event ticket type limits for Classical Symphony
INSERT INTO event_ticket_type (event_id, ticket_type_id, max_per_tt, num_of_shards_per_tt, created_at) VALUES
(3, 1, 500, 5, NOW()),
(3, 2, 200, 4, NOW()),
(3, 3, 200, 4, NOW())
ON CONFLICT (event_id, ticket_type_id) DO NOTHING;

-- Insert event dates for Rock Concert (next 7 days)
INSERT INTO event_date (event_id, date, start_time, created_at)
SELECT
    1,
    CURRENT_DATE + i,
    '20:00:00',
    NOW()
FROM generate_series(1, 7) AS i
ON CONFLICT (event_id, date, start_time) DO NOTHING;

-- Insert event dates for Jazz Night (Fridays and Saturdays)
INSERT INTO event_date (event_id, date, start_time, created_at)
SELECT
    2,
    CURRENT_DATE + i,
    '21:00:00',
    NOW()
FROM generate_series(1, 14) AS i
WHERE EXTRACT(DOW FROM CURRENT_DATE + i) IN (5, 6)  -- Friday and Saturday
ON CONFLICT (event_id, date, start_time) DO NOTHING;

-- Insert event dates for Classical Symphony (Sundays)
INSERT INTO event_date (event_id, date, start_time, created_at)
SELECT
    3,
    CURRENT_DATE + i,
    '18:00:00',
    NOW()
FROM generate_series(1, 30) AS i
WHERE EXTRACT(DOW FROM CURRENT_DATE + i) = 0  -- Sunday
ON CONFLICT (event_id, date, start_time) DO NOTHING;

-- Insert event dates for Electronic Music Festival (weekend special)
INSERT INTO event_date (event_id, date, start_time, created_at) VALUES
(4, CURRENT_DATE + 10, '22:00:00', NOW()),
(4, CURRENT_DATE + 11, '22:00:00', NOW()),
(4, CURRENT_DATE + 12, '22:00:00', NOW())
ON CONFLICT (event_id, date, start_time) DO NOTHING;

-- Insert event dates for Comedy Show (multiple shows per day)
INSERT INTO event_date (event_id, date, start_time, created_at)
SELECT
    5,
    CURRENT_DATE + i,
    t.start_time,
    NOW()
FROM generate_series(1, 5) AS i
CROSS JOIN (VALUES ('19:00:00'::TIME), ('21:30:00'::TIME)) AS t(start_time)
ON CONFLICT (event_id, date, start_time) DO NOTHING;

-- Note: Consumption shards will be automatically created by the trigger
-- defined in V4__create_functions.sql when event_date rows are inserted
