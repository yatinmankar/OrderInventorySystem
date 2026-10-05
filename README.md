# saga-outbox-demo

A distributed order-processing system demonstrating the **transaction outbox**, a **saga
orchestrator**, **idempotent consumers**, **layered retry**, and a **circuit breaker** — over
local Kafka. Four Spring Boot services (Java 17, Spring Boot 3.2.5) talk asynchronously over
Kafka; each owns its own Postgres database.

```
                 POST /orders
                      |
                      v
             +------------------+      order.events        (transaction outbox + polling relay)
             |  order-service   |--------------------------+
             |  (orchestrator)  |<----------+              |
             +------------------+           |              v
                | ^   | ^                   |     +------------------+
   reserve.cmd  | |   | | process.cmd       |     |  order-service   |
                v |   v |                    reserve.reply / payment.reply
     +------------------+   +------------------+   +---------------------+
     | inventory-service|   | payment-service  |   | notification-service|
     |  reserve/release |   | PSP + breaker    |   |  idempotent notify  |
     +------------------+   +------------------+   +---------------------+
```

## Saga flow

1. `POST /orders` writes the **order row + an `OrderCreated` outbox row in one local transaction**.
2. The **outbox relay** (`@Scheduled`) drains unpublished rows to `order.events` (at-least-once).
3. The **orchestrator** starts a saga and sends `inventory.reserve.cmd`.
4. **inventory-service** reserves stock (idempotent by `orderId`) and replies on `inventory.reserve.reply`.
5. On success the orchestrator sends `payment.process.cmd`; **payment-service** charges a simulated
   PSP (wrapped in retry + circuit breaker) and replies on `payment.process.reply`.
6. **Success** -> order `CONFIRMED`, `notification.cmd` sent.
   **Payment declined** -> compensate with `inventory.release.cmd`, order `CANCELLED`, notify.
   **No stock** -> order `CANCELLED`, notify.

### Idempotency strategy
Every message carries a **deterministic `messageId`** derived from `orderId + step`, so a redelivered
or re-sent message keeps the same id. Dedupe uses **domain state where it exists** (the `saga_instance`,
`reservation`, and `payment` rows keyed by `orderId`) and a **`processed_messages` table** only where the
effect is a pure side-effect with no natural key (notifications). On the wire everything is plain JSON
with no type headers; consumers infer the type from the `@KafkaListener` method signature.

## Prerequisites (Windows)
- **JDK 17** (`java -version` should report 17). Set `JAVA_HOME`.
- **Docker Desktop** (WSL2 backend) for Kafka + Postgres.
- **Maven** on `PATH` (`mvn -v`). No Maven? Either use IntelliJ IDEA's bundled Maven, or add a wrapper:
  `mvn -N wrapper:wrapper` (then use `.\mvnw.cmd` in place of `mvn`).

## Run it (PowerShell)

```powershell
# 1. Start Kafka + Kafka UI + Postgres (creates the 4 databases on first boot)
docker compose up -d

# 2. Build + launch all four services, each in its own window
.\run-all.ps1

# 3. (after ~20s) drive all four scenarios and print final statuses
.\smoke-test.ps1
```

Prefer to run a single service by hand:
```powershell
mvn -q -DskipTests install
mvn -q -pl order-service spring-boot:run
```

### Place an order manually
PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8081/orders -Method Post -ContentType application/json `
  -Body '{"productId":"SKU-1","quantity":2,"amount":42.00}'

Invoke-RestMethod -Uri http://localhost:8081/orders/<orderId>
```
cmd.exe (uses the real `curl.exe` shipped with Windows 10+):
```cmd
curl.exe -X POST http://localhost:8081/orders -H "Content-Type: application/json" -d "{\"productId\":\"SKU-1\",\"quantity\":2,\"amount\":42.00}"
```

## Demo matrix (deterministic triggers)

| Scenario            | Input                          | Result    | What it exercises                                   |
|---------------------|--------------------------------|-----------|-----------------------------------------------------|
| Happy path          | `SKU-1`, amount `42.00`        | CONFIRMED | outbox -> reserve -> pay -> confirm -> notify        |
| Payment declined    | amount ends in `.99` (`12.99`) | CANCELLED | business decline -> **compensation** (release stock) |
| Out of stock        | `SKU-OUT` (seeded at 0)        | CANCELLED | reservation failure -> cancel                        |
| PSP outage          | amount `>= 5000`               | PENDING   | retry (resilience4j) -> breaker -> **Kafka DLQ**     |

`SKU-1` is seeded with 100 units, `SKU-OUT` with 0 (see `inventory V1__init.sql`).

## Observe
- **Kafka UI**: http://localhost:8080 — inspect topics, consumer groups, and the `*.DLT` dead-letter
  topics (the outage order lands in `payment.process.cmd.DLT`).
- **Circuit breaker**: http://localhost:8083/actuator/circuitbreakers — push several `>= 5000` orders
  to watch `psp` move CLOSED -> OPEN.
- **Databases**: `docker exec -it saga-postgres psql -U saga -d orderdb -c "select id,status from orders;"`

## Ports
| Service       | Port | | Infra     | Port |
|---------------|------|-|-----------|------|
| order         | 8081 | | Kafka     | 9092 |
| inventory     | 8082 | | Kafka UI  | 8080 |
| payment       | 8083 | | Postgres  | 5432 |
| notification  | 8084 | |           |      |

> If local Postgres already owns 5432, either stop it or remap the container port in
> `docker-compose.yml` (and the `spring.datasource.url` in each service).

## Retry / circuit breaker (payment-service)
Aspect nesting is `Retry(CircuitBreaker(call))`, configured in `application.yml`:
- **Retry** `psp`: 3 attempts, 500ms base with exponential backoff, **only** on `PspUnavailableException`.
- **CircuitBreaker** `psp`: count-based window of 10, opens at 50% failure, 10s open, records
  `PspUnavailableException`, **ignores** `PaymentDeclinedException` (a business outcome, not a fault).

Transient failures propagate out of the listener so Kafka's `DefaultErrorHandler` retries with
backoff and finally routes the poison record to `<topic>.DLT`. No terminal payment row is written on a
transient failure, so a replay from the DLT can still succeed.

## Deliberate simplifications (production upgrade paths)
- **Outbox lives only in order-service** (the showcase). Inventory/payment rely on domain idempotency
  + deterministic ids instead of their own outbox. Production: outbox everywhere, or Debezium CDC to
  drop the polling relay, or adopt a workflow engine (Temporal) for the orchestration.
- **Orchestrator holds a short tx across the PSP call.** Production: call the provider outside the tx,
  persist in a separate short transaction.
- Single-broker Kafka (replication factor 1) and one Postgres instance with 4 databases — dev topology.
