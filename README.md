# Kafka Practice — End-to-End Demo

Real-time order event pipeline built with Apache Kafka, Kafka Streams, Spring Boot, and React.

## Architecture

```
┌─────────────────┐     order-events topic      ┌──────────────────────┐
│  kafka-producer │ ───────────────────────────▶│   kafka-consumer     │
│  (port 8081)    │                             │   (port 8082)        │
│                 │        ┌────────────────────│   Kafka Streams DSL  │
│  REST API to    │        │                    │   Routes by status   │
│  publish order  │        │                    │   WebSocket push     │
│  events         │        │                    └──────────┬───────────┘
└─────────────────┘        │                               │ WS /topic/orders
                           │                               │
                           │  order-events topic           ▼
                           │  ┌────────────────────┐  ┌──────────────────────┐
                           └─▶│ kafka-fraud-detector│  │    react-client      │
                              │ (port 8083)         │  │    (port 5173)       │
                              │ Kafka Streams DSL   │  │                      │
                              │ Global CANCELLED    │  │  ProducerPanel       │
                              │ counter (RocksDB)   │  │  ConsumerPanel       │
                              │ WebSocket push      ├─▶│  FraudPanel          │
                              └──────────┬──────────┘  └──────────────────────┘
                                         │  WS /topic/fraud-alerts
                                         ▼
                                  order-fraud-alerts topic
```

## Modules

| Module | Port | Role |
|---|---|---|
| `kafka-producer` | 8081 | REST API that publishes `OrderEvent` records to Kafka |
| `kafka-consumer` | 8082 | Kafka Streams consumer; routes events by status; pushes to WebSocket |
| `kafka-fraud-detector` | 8083 | Detects 3 consecutive `CANCELLED` events globally; writes to `order-fraud-alerts`; pushes alerts via WebSocket |
| `react-client` | 5173 | Vite/React UI — ProducerPanel (send orders) · ConsumerPanel (live feed) · FraudPanel (fraud alerts) |

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `order-events` | kafka-producer | kafka-consumer, kafka-fraud-detector | All order lifecycle events |
| `order-fraud-alerts` | kafka-fraud-detector | — | Fraud alert output (also pushed via WebSocket to UI) |

## Prerequisites

- Java 17
- Maven 3.8+
- Node.js 18+
- Docker Desktop

## Running with Docker Compose

### 1. Build all jars

```bash
mvn package -DskipTests
```

### 2. Start the stack

```bash
docker compose up -d
```

Services start in dependency order: Zookeeper → Kafka → Spring Boot apps.

### 3. Check status

```bash
docker compose ps
docker compose logs -f kafka-fraud-detector
```

### 4. Start the UI

```bash
cd react-client
npm install
npm run dev
```

Open **http://localhost:5173**

> **Note (Windows):** Port 9092 may be reserved by Hyper-V/WSL. The compose file maps Kafka's external port to `19092` to avoid this. Internal container-to-container traffic still uses `kafka:9092`.

---

## Running Services Individually (without Docker)

Start a local Kafka broker first, then:

```bash
# Terminal 1 — Producer
cd kafka-producer && mvn spring-boot:run

# Terminal 2 — Consumer
cd kafka-consumer && mvn spring-boot:run

# Terminal 3 — Fraud Detector
cd kafka-fraud-detector && mvn spring-boot:run

# Terminal 4 — UI
cd react-client && npm run dev
```

---

## API — kafka-producer

### Publish a custom order event

```http
POST http://localhost:8081/api/orders
Content-Type: application/json

{
  "orderId": "ord-001",
  "product": "Laptop",
  "quantity": 1,
  "price": 999.99,
  "status": "CREATED",
  "message": "New order placed"
}
```

Supported `status` values: `CREATED` · `CONFIRMED` · `SHIPPED` · `DELIVERED` · `CANCELLED`

### Publish a sample order

```http
POST http://localhost:8081/api/orders/sample
```

---

## Fraud Detection

`kafka-fraud-detector` tracks a **global** consecutive `CANCELLED` counter using a Kafka Streams `KeyValueStore` backed by RocksDB.

**Trigger condition:** 3 or more `CANCELLED` events in a row across any orders (any non-`CANCELLED` event resets the counter to 0).

**Output:** A `FraudAlert` record is written to the `order-fraud-alerts` Kafka topic.

```json
{
  "orderId": "ord-007",
  "status": "CANCELLED",
  "consecutiveCount": 3,
  "detectedAt": "2026-03-01T15:30:00"
}
```

### UI — FraudPanel

The React `FraudPanel` connects to the fraud-detector's WebSocket endpoint and displays live alerts:

| Detail | Value |
|---|---|
| WebSocket URL | `ws://localhost:8083/ws` (SockJS/STOMP) |
| Subscription | `/topic/fraud-alerts` |
| Payload | `FraudAlert` JSON (see schema above) |

Each alert card shows the triggering `orderId`, the `CANCELLED` status badge, the consecutive count, and the detection timestamp. The panel border flashes red on every new alert.

### Test fraud detection manually

Send 3 consecutive CANCELLED events:

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"test-1","product":"Widget","quantity":1,"price":9.99,"status":"CANCELLED"}'

curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"test-2","product":"Widget","quantity":1,"price":9.99,"status":"CANCELLED"}'

curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"test-3","product":"Widget","quantity":1,"price":9.99,"status":"CANCELLED"}'
```

Watch for the alert in the fraud-detector logs:

```bash
docker compose logs -f kafka-fraud-detector
# [FRAUD DETECTED] 3 consecutive CANCELLED events — last orderId=test-3
```

### Configuration

The consecutive threshold is configurable in `kafka-fraud-detector/src/main/resources/application.yml`:

```yaml
fraud:
  detection:
    consecutive-threshold: 3
```

Or override at runtime via environment variable:

```bash
FRAUD_DETECTION_CONSECUTIVE_THRESHOLD=5
```

---

## Rebuilding after code changes

```bash
# Rebuild a single module
mvn -pl kafka-fraud-detector package -DskipTests

# Copy new jar into running container (avoids full image rebuild)
docker cp kafka-fraud-detector/target/kafka-fraud-detector-1.0.0.jar \
  kafka-fraud-detector:/app/app.jar
docker restart kafka-fraud-detector
```

## Stopping everything

```bash
docker compose down          # stop containers, keep volumes
docker compose down -v       # stop containers AND delete volumes (resets all state)
```
