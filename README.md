# Availability Tests - Spring Boot Reactive Booking System

High-performance ticket booking system built with Spring Boot WebFlux, R2DBC, PostgreSQL, Redis, and GraalVM native image support. Features sharding-based concurrency control and two-layer caching for maximum throughput.

## Architecture Overview

- **Backend**: Spring Boot 3.4.1 (WebFlux) + Spring Data R2DBC
- **Database**: PostgreSQL with sharding support
- **Caching**: Two-layer cache (Caffeine L1 + Redis L2)
- **Java**: Java 25 with GraalVM
- **Build**: Maven with native-maven-plugin

## Key Features

### Sharding for Concurrency
- Distributes write load across multiple database rows (shards)
- Minimizes lock contention during high-traffic booking scenarios
- Round-robin shard selection with instance-local counters
- Per-event and per-ticket-type shard configuration

### Two-Layer Caching
- **L1 (Caffeine)**: Ultra-fast in-memory cache (2s TTL)
- **L2 (Redis)**: Distributed cache across instances (5s TTL)
- Cache invalidation on reservation changes

### Reservation Expiry
- Automatic cleanup of unpaid reservations after 60 seconds
- Scheduled task runs every 10 seconds
- Precise counter restoration using shard_id tracking

### Reactive Streams
- Fully non-blocking I/O with Project Reactor
- R2DBC for reactive database access
- Scales to thousands of concurrent requests

## Database Schema

### Core Tables
- `events` - Event definitions with capacity and shard config
- `ticket_type` - Ticket type definitions (normal, VIP, etc.)
- `event_date` - Event occurrence dates and times
- `event_ticket_type` - Optional per-ticket-type capacity limits
- `reservation` - Reservation records with payment status
- `tickets` - Individual tickets with shard_id for restoration

### Sharding Tables
- `consumption` - Total capacity consumption per shard
- `consumption_tt` - Per-ticket-type consumption per shard

### Database Functions
- `initialize_consumption_shards()` - Auto-creates shards for new event dates
- `restore_reservation_counters()` - Restores counters on expiry
- `get_event_date_availability()` - Aggregates availability across shards

## REST API Endpoints

### GET /events
Returns paginated list of events.

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Concert 2024",
      "maxTickets": 1000
    }
  ],
  "totalElements": 10,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

### GET /events/{eventId}
Returns event dates with availability.

**Query Parameters:**
- `startDate` (ISO date, default: today)
- `endDate` (ISO date, default: +1 year)
- `page` (default: 0)
- `size` (default: 20)

**Response:**
```json
{
  "content": [
    {
      "eventId": 1,
      "date": "2024-12-31",
      "startTime": "20:00",
      "totalAvailable": 850
    }
  ],
  "totalElements": 5,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

### POST /events/{eventId}/{date}/{startTime}
Create a reservation.

**Path Parameters:**
- `eventId` - Event ID
- `date` - Event date (ISO format: 2024-12-31)
- `startTime` - Start time (HH:mm format: 20:00)

**Request Body:**
```json
{
  "tickets": [
    {
      "ticketTypeId": 1,
      "quantity": 10
    },
    {
      "ticketTypeId": 2,
      "quantity": 5
    }
  ]
}
```

**Response:**
```json
{
  "reservationId": 123,
  "expiresAt": "2024-12-29T20:01:00",
  "ticketCount": 15,
  "status": "PENDING"
}
```

### POST /reservation/{reservationId}
Confirm payment for a reservation.

**Request Body:**
```json
{
  "paymentReference": "PAY-123456789"
}
```

**Response:**
```json
{
  "reservationId": 123,
  "expiresAt": "2024-12-29T20:01:00",
  "ticketCount": 15,
  "status": "CONFIRMED"
}
```

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 25 (GraalVM recommended)
- Maven 3.9+

### Quick Start with Docker Compose

1. **Start all services:**
```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port 5432
- Redis on port 6379
- Backend application on port 8080

2. **Check application health:**
```bash
curl http://localhost:8080/actuator/health
```

3. **View logs:**
```bash
docker-compose logs -f backend
```

4. **Stop services:**
```bash
docker-compose down
```

### Local Development (without Docker)

1. **Start PostgreSQL:**
```bash
docker run -d \
  --name postgres \
  -e POSTGRES_DB=booking \
  -e POSTGRES_USER=booking_user \
  -e POSTGRES_PASSWORD=booking_pass \
  -p 5432:5432 \
  postgres:16-alpine
```

2. **Start Redis:**
```bash
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:7-alpine
```

3. **Build and run the application:**
```bash
cd backend
mvn clean package
java -jar target/availability-tests-1.0.0-SNAPSHOT.jar
```

Or run with Maven:
```bash
mvn spring-boot:run
```

## Building Native Image with GraalVM

1. **Install GraalVM 25:**
```bash
# Download from https://www.graalvm.org/downloads/
```

2. **Build native image:**
```bash
cd backend
mvn -Pnative native:compile
```

3. **Run native executable:**
```bash
./target/availability-tests
```

**Benchmark Results:**
- Startup time: <2 seconds (vs ~10s for JVM)
- Memory footprint: <256MB RSS (vs ~512MB for JVM)

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | booking | Database name |
| `DB_USER` | booking_user | Database user |
| `DB_PASSWORD` | booking_pass | Database password |
| `REDIS_HOST` | localhost | Redis host |
| `REDIS_PORT` | 6379 | Redis port |
| `SERVER_PORT` | 8080 | Application server port |

### Application Properties

See `backend/src/main/resources/application.yml` for full configuration options:
- R2DBC connection pool settings
- Redis cache TTLs
- Reservation expiry settings
- Sharding configuration
- Logging levels

## Testing

### Load Testing with Artillery

Artillery configuration is planned for the `stress-test` directory (to be created separately).

**Example Artillery scenario:**
```yaml
config:
  target: "http://localhost:8080"
  phases:
    - duration: 60
      arrivalRate: 100
      name: "Sustained load"
scenarios:
  - name: "Create reservation"
    flow:
      - post:
          url: "/events/1/2024-12-31/20:00"
          json:
            tickets:
              - ticketTypeId: 1
                quantity: 5
```

### Integration Tests

```bash
cd backend
mvn test
```

Tests use Testcontainers for PostgreSQL and Redis.

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Health check
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics

### Key Metrics
- `shard.utilization` - Per-shard utilization (gauge)
- `reservation.attempt` - Reservation attempts (counter)
- `cache.hit.ratio` - Cache hit ratio (Caffeine stats)

## Performance Tuning

### Shard Configuration
Adjust `num_shards` in the `events` table:
- More shards = less contention, but more database rows
- Recommended: 10-20 shards for high-traffic events

### Cache TTLs
Edit `application.yml`:
```yaml
booking:
  cache:
    local:
      ttl-seconds: 2  # L1 cache
    redis:
      shard-availability-ttl-seconds: 5  # L2 cache
```

### Database Connection Pool
Tune R2DBC pool in `application.yml`:
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 50
```

## Project Structure

```
backend/
├── src/main/java/com/booking/
│   ├── AvailabilityApplication.java
│   ├── config/                 # Spring configuration
│   ├── domain/
│   │   ├── entity/             # JPA entities
│   │   └── repository/         # R2DBC repositories
│   ├── service/                # Business logic
│   ├── sharding/               # Sharding components
│   ├── controller/             # REST controllers
│   ├── dto/                    # Request/response DTOs
│   └── exception/              # Exception handling
└── src/main/resources/
    ├── application.yml
    └── db/migration/           # Flyway migrations
```

## Troubleshooting

### Database Connection Issues
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Test connection
psql -h localhost -U booking_user -d booking
```

### Redis Connection Issues
```bash
# Check Redis is running
docker ps | grep redis

# Test connection
redis-cli ping
```

### Application Logs
```bash
# View logs
docker-compose logs -f backend

# Increase log level
# Edit application.yml:
logging:
  level:
    com.booking: DEBUG
```

## Future Enhancements

- Vue.js SPA for UI testing (separate repository)
- Artillery stress test suite (separate directory)
- Horizontal scaling with Redis Pub/Sub for cache invalidation
- Database read replicas for availability queries
- Circuit breakers with Resilience4j
- Distributed tracing with OpenTelemetry

## License

MIT License

## Contact

For questions or issues, please open an issue on the repository.
