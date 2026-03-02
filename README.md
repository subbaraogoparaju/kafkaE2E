# Kafka Practice — End-to-End Demo

Real-time order event pipeline built with Apache Kafka, Kafka Streams, Spring Boot, and React.

## Architecture

### Application Data Flow

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

### Cluster Topology & MirrorMaker 2 Replication

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  SOURCE CLUSTER  (kafka:9092 · kafka-2:9092 · kafka-3:9092)                 │
│  RF=3  min.isr=2  unclean.leader.election=false                             │
│                                                                             │
│  ┌─────────────────────────────────────────┐                                │
│  │   order-events   (3 partitions, RF=3)   │◀── kafka-producer  (publish)   │
│  │                                         │──▶ kafka-consumer  (consume)   │
│  │                                         │──▶ kafka-fraud-detector        │
│  └────────────────────┬────────────────────┘                                │
└───────────────────────┼─────────────────────────────────────────────────────┘
                        │
               ┌────────┴──────────┐
               │   MirrorMaker 2   │
               │   replicates:     │
               │   · order-events  │
               │   · offsets       │
               │   · heartbeats    │
               │   · checkpoints   │
               └────────┬──────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  DEST CLUSTER  (kafka-dest:9092)                                            │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │   source.order-events   (MM2 prefixes topic name with source alias)   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Modules

| Module | Port | Role |
|---|---|---|
| `kafka-producer` | 8081 | REST API that publishes `OrderEvent` records to Kafka |
| `kafka-consumer` | 8082 | Kafka Streams consumer; routes events by status; pushes to WebSocket |
| `kafka-fraud-detector` | 8083 | Detects 3 consecutive `CANCELLED` events globally; writes to `order-fraud-alerts`; pushes alerts via WebSocket |
| `react-client` | 5173 | Vite/React UI — ProducerPanel (send orders) · ConsumerPanel (live feed) · FraudPanel (fraud alerts) |

## Kafka Topics

| Topic | Cluster | Producer | Consumer | Purpose |
|---|---|---|---|---|
| `order-events` | source | kafka-producer | kafka-consumer, kafka-fraud-detector | All order lifecycle events |
| `order-fraud-alerts` | source | kafka-fraud-detector | — | Fraud alert output (also pushed via WebSocket to UI) |
| `source.order-events` | dest | MirrorMaker2 | — | Replicated copy of `order-events` (MM2 prefixes source alias) |

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

## MirrorMaker 2 — Cross-Cluster Replication

The stack includes a three-broker source cluster (`kafka`, `kafka-2`, `kafka-3`) and a single-broker destination cluster (`kafka-dest`), connected by MirrorMaker 2.

### Cluster layout

| Cluster | Brokers | External ports | Zookeeper | RF | min.isr |
|---|---|---|---|---|---|
| Source | `kafka`, `kafka-2`, `kafka-3` | 19092, 19093, 19095 | `zookeeper:2181` | 3 | 2 |
| Destination | `kafka-dest` | 19094 | `zookeeper-dest:2181` | 1 | 1 |

### What MM2 replicates

- **Topic:** `order-events` (source) → `source.order-events` (dest)
- **Direction:** source → dest only (`dest->source.enabled = false`)
- **Consumer group offsets** synced every 30 s (`sync.group.offsets.enabled = true`)
- **Heartbeat and checkpoint** topics emitted for offset translation

MM2 configuration lives in `mm2/mm2.properties`.

### Verifying replication

**1. Check replicated topic exists on dest:**

```bash
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 --list
# Expected: source.order-events (plus MM2 internal topics)
```

**2. Check end offsets on dest:**

```bash
docker exec kafka-dest kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list kafka-dest:9092 --topic source.order-events
# source.order-events:0:NNN
# source.order-events:1:NNN
# source.order-events:2:NNN
```

**3. Send a test event and confirm it arrives on dest:**

```bash
# Send to source via producer REST API
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"mm2-test","product":"Widget","quantity":1,"price":9.99,"status":"CREATED","message":"MM2 live check"}'

# Wait ~5 s, then consume from dest
docker exec kafka-dest kafka-console-consumer \
  --bootstrap-server kafka-dest:9092 \
  --topic source.order-events \
  --partition 0 --offset latest --max-messages 1 --timeout-ms 8000
```

**Expected output on dest:**
```json
{"orderId":"mm2-test","product":"Widget","quantity":1,"price":9.99,"status":"CREATED","message":"MM2 live check"}
```

Replication latency observed: **< 10 seconds** end-to-end.

### MM2 internal topics created on dest

| Topic | Purpose |
|---|---|
| `mm2-configs.source.internal` | Connector configuration storage (RF=1, dest cluster) |
| `mm2-offsets.source.internal` | Source offset tracking (RF=1, dest cluster) |
| `mm2-status.source.internal` | Connector task status (RF=1, dest cluster) |
| `source.checkpoints.internal` | Consumer group offset checkpoints |
| `source.heartbeats` | Liveness heartbeats from source cluster |

### MM2 operational notes

**Health check — verify MM2 is truly running:**
After startup, confirm the three Connect storage topics appeared on the dest cluster within 30 s.
If they are missing, MM2 is silently broken (see gotchas below).

```bash
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 --list
# Must include: mm2-configs.source.internal, mm2-offsets.source.internal, mm2-status.source.internal
```

**Resetting MM2 after `mm2.properties` changes:**
MM2's embedded Kafka Connect caches connector configs in `mm2-configs.source.internal`.
A container restart reads from that cache — not from the properties file.
To force a clean re-registration, delete the three storage topics before restarting:

```bash
docker stop kafka-mirrormaker2
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-configs.source.internal
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-offsets.source.internal
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-status.source.internal
docker compose up -d kafka-mirrormaker2
```

> **Note:** deleting `mm2-offsets.source.internal` resets MM2's position — it will re-replicate
> from the beginning of the source topic, which may produce duplicates on the dest cluster.

### Failover recipe (manual)

MM2 keeps consumer group offsets synced to the dest cluster every 30 s
(`sync.group.offsets.enabled = true`). On source cluster failure, switch consumers by updating
two environment variables in `docker-compose.yml` and recreating the container — no code change:

```yaml
# kafka-consumer — failover to dest cluster
environment:
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka-dest:9092
  KAFKA_TOPIC_ORDER_EVENTS: source.order-events   # MM2 prefixes source alias by default
```

The consumer will resume from the last synced offset with at most 30 s of re-processing and zero
data loss.

### Known limitations

| Issue | Detail |
|---|---|
| `topics.rename.format` ignored | `DefaultReplicationPolicy.formatRemoteTopic()` in Kafka 3.6.x (cp-kafka:7.6.1) hardcodes `sourceAlias.topic` regardless of this setting. The config is stored but silently not applied. Replicated topic is always `source.order-events`. |
| Connect storage RF must match dest broker count | `offset/status/config.storage.replication.factor` in `mm2.properties` control topics created on the **dest** cluster. Setting these above the dest broker count (1 here) prevents topic creation and silently breaks MM2. Keep at RF=1. |

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
