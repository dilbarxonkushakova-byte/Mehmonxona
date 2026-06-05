# HotelOS — Real-Time Hotel Management System

**BTEC HND Level 4 · Unit 4: Programming · GrandStay Hotel case study**

HotelOS is an event-driven, microservices implementation of the GrandStay
front-of-house workflow: reception, housekeeping, room service, and
maintenance, all connected through a RabbitMQ topic exchange and a
WebSocket dashboard.

---

## Tech stack

| Concern              | Choice                              | Why (full justification in Task 1.4) |
|----------------------|-------------------------------------|--------------------------------------|
| Language / runtime   | **Java 21 (LTS)**                   | Sealed types, records, pattern matching → event modelling. |
| Framework            | **Spring Boot 3.3**                 | Production-ready starters for Web, AMQP, JPA, WebSocket. |
| Build tool           | **Maven (multi-module)**            | One `mvn package` builds everything; standard for HND markers. |
| Message broker       | **RabbitMQ 3.13** (topic exchange)  | Simple, durable, decouples the four departments. |
| Database             | **PostgreSQL 16**                   | Reception persists rooms & bookings. |
| Real-time UI         | **Thymeleaf + SockJS + STOMP**      | No SPA build step; runs inside the Java stack. |
| Container runtime    | **Docker Compose**                  | One command to start the whole hotel. |
| Tests                | **JUnit 5**                         | Unit tests on the pure algorithms. |

---

## Architecture

```
                  ┌───────────────────────────────┐
                  │     RabbitMQ topic exchange   │
                  │        "hotelos.events"       │
                  └─────┬───────┬───────┬─────┬───┘
                        │       │       │     │
        room.cleaned ───┘       │       │     └─── #  (catch-all)
        order.completed ────────┘       │           │
        maintenance.resolved ───────────┘           │
                                                    │
   ┌─────────────┐  ┌──────────────┐  ┌──────────┐  │  ┌─────────────┐
   │ Reception   │  │ Housekeeping │  │ Room Svc │  │  │ Maintenance │
   │  :8081      │  │   :8082      │  │  :8083   │  │  │   :8084     │
   │ Postgres    │  │ FIFO queue   │  │ in-mem   │  │  │ priority Q  │
   └─────────────┘  └──────────────┘  └──────────┘  │  └─────────────┘
                                                    ▼
                                            ┌──────────────┐
                                            │  Dashboard   │
                                            │   :8080      │
                                            │ Thymeleaf +  │
                                            │ STOMP/WS     │
                                            └──────────────┘
```

---

## One-command start (Docker)

```bash
docker compose up --build
```

That's it. After ~60 seconds:

* Dashboard → http://localhost:8080
* RabbitMQ UI → http://localhost:15672 (guest / guest)
* Reception API → http://localhost:8081/api/reception/rooms

To stop and wipe state:

```bash
docker compose down -v
```

---

## Local development (without Docker)

You need: JDK 21, Maven 3.9+, a local Postgres and RabbitMQ.

```bash
# 1. infra (in another terminal)
docker run -d --name rabbit -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_USER=hotelos -e POSTGRES_PASSWORD=hotelos -e POSTGRES_DB=hotelos_reception \
  postgres:16-alpine

# 2. build everything once
mvn -DskipTests package

# 3. run each service (one terminal per service)
java -jar reception-service/target/reception-service-1.0.0.jar
java -jar housekeeping-service/target/housekeeping-service-1.0.0.jar
java -jar roomservice-service/target/roomservice-service-1.0.0.jar
java -jar maintenance-service/target/maintenance-service-1.0.0.jar
java -jar dashboard-service/target/dashboard-service-1.0.0.jar
```

---

## Smoke test (mirrors brief test scenarios TS-01..TS-03)

```bash
# TS-01 — book a 2-bed room for guest G-100
curl -X POST http://localhost:8081/api/reception/bookings \
     -H 'Content-Type: application/json' \
     -d '{"guestId":"G-100","beds":2,"checkIn":"2026-06-10T14:00:00Z","checkOut":"2026-06-12T11:00:00Z"}'

# place a room-service order on the assigned room (replace 101 if needed)
curl -X POST http://localhost:8083/api/roomservice/orders \
     -H 'Content-Type: application/json' \
     -d '{"roomNumber":101,"item":"Club Sandwich"}'

# deliver it (copy the orderId from the previous response)
curl -X POST http://localhost:8083/api/roomservice/orders/<orderId>/deliver

# TS-02 — check out → bill includes room nights + extras
curl -X POST http://localhost:8081/api/reception/checkout/101

# TS-03 — housekeeping confirms cleaning, room flips back to READY
curl -X POST 'http://localhost:8082/api/housekeeping/clean/101?cleaner=Aziza'
```

Watch the dashboard at http://localhost:8080 — each step appears in the
live feed within ~1 second and the room tile changes colour
(blue=OCCUPIED, amber=DIRTY, green=READY).

---

## Repository layout

```
HotelOS/
├── pom.xml                      ← parent (multi-module aggregator)
├── docker-compose.yml           ← one-command launcher
├── Dockerfile.service           ← shared multi-stage Docker build
├── common/                      ← shared events, routing keys
├── reception-service/           ← bookings, rooms, billing  (port 8081)
├── housekeeping-service/        ← FIFO cleaning queue       (port 8082)
├── roomservice-service/         ← menu + orders             (port 8083)
├── maintenance-service/         ← priority ticket queue     (port 8084)
├── dashboard-service/           ← Thymeleaf + STOMP UI      (port 8080)
└── docs/
    ├── HotelOS_Report.docx      ← Tasks 1, 2, 4 written analysis
    └── git_log.txt              ← export of git log --oneline
```

---

## Tests

```bash
mvn test
```

Currently covers `RoomAllocator` (best-fit selection, dirty rooms skipped,
bed requirement, invalid input). Extend in `reception-service/src/test/...`.

---

## Conventions (Task 4.3 coding standard — full document inside report)

* Java 21, 4-space indent, 120-col soft wrap.
* `com.grandstay.hotelos.<service>.<layer>` package layout
  (`domain`, `service`, `web`, `config`, `bootstrap`).
* Constructor injection only — no `@Autowired` on fields.
* Domain objects expose **behaviour**, not setters
  (see `Room.markOccupied()`, `Booking.addCharge()`).
* DTOs and events are immutable `record`s.
* Every controller exception goes through `GlobalExceptionHandler` — the
  client never sees a stack trace (Task 3.2).

---

## Brief compliance checklist

| Brief requirement                                             | Where implemented                                     |
|---------------------------------------------------------------|--------------------------------------------------------|
| Microservices                                                 | 5 Maven modules, separate Spring Boot apps             |
| Message broker between services                               | RabbitMQ `hotelos.events` topic exchange               |
| WebSocket live updates to operations dashboard                | `dashboard-service` STOMP `/topic/events`              |
| 1 command to run                                              | `docker compose up --build`                            |
| 2 floors × 10 rooms acceptable                                | `RoomSeeder` seeds 20 rooms                            |
| No raw stack traces leaked to user                            | `GlobalExceptionHandler`                               |
| Don't leak PII over the wire to dashboard                     | `DashboardForwarder.sanitise()` drops guestId, price   |
| At least 10 meaningful git commits + exported log             | `docs/git_log.txt` (commit during your handover)      |
