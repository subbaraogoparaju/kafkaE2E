# Kafka Practice — Help

## Project Structure

```
kafkapractice/
├── docker-compose.yml
├── help.md
├── pom.xml                          (parent POM — Spring Boot 3.2.3, Java 17)
├── kafka-producer/                  (port 8081)
│   ├── Dockerfile
│   ├── src/main/java/.../
│   │   ├── config/KafkaTopicConfig.java   (creates order-events, 3 partitions)
│   │   ├── config/CorsConfig.java
│   │   ├── controller/OrderController.java
│   │   ├── model/OrderEvent.java
│   │   └── service/OrderEventProducer.java
│   └── src/main/resources/
│       ├── application.yml
│       └── static/index.html             (fallback plain HTML UI)
├── kafka-consumer/                  (port 8082)
│   ├── Dockerfile
│   ├── src/main/java/.../
│   │   ├── config/KafkaStreamsConfig.java
│   │   ├── config/WebSocketConfig.java
│   │   ├── model/OrderEvent.java
│   │   ├── streams/OrderEventStream.java  (Kafka Streams topology + WebSocket broadcast)
│   │   └── listener/OrderEventListener.java (legacy — superseded by Streams)
│   └── src/main/resources/application.yml
└── react-client/                    (port 5173)
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.jsx
        ├── components/
        │   ├── ProducerPanel.jsx     (sends to :8081, dynamic stream mode)
        │   └── ConsumerPanel.jsx     (WebSocket from :8082/ws)
        └── index.css
```

---

## Event Flow

```
React UI (ProducerPanel)
    │  POST /api/orders
    ▼
kafka-producer :8081
    │  KafkaTemplate → order-events topic
    ▼
kafka-broker :9092
    │  3 partitions
    ▼
kafka-consumer :8082
    │  Kafka Streams topology (OrderEventStream)
    │  branches by status: CREATED / CONFIRMED / SHIPPED / DELIVERED / CANCELLED
    │  SimpMessagingTemplate → /topic/orders
    ▼
React UI (ConsumerPanel)
    WebSocket STOMP  ws://localhost:8082/ws
```

---

## Services & Ports

| Service        | Port  | Description                        |
|----------------|-------|------------------------------------|
| zookeeper      | 2181  | Kafka coordination                 |
| kafka-broker   | 9092  | Kafka broker                       |
| kafka-producer | 8081  | Spring Boot REST producer          |
| kafka-consumer | 8082  | Spring Boot Kafka Streams + WS     |
| react-client   | 5173  | Vite dev server (React UI)         |

---

## Quick Start

### 1. Build Java JARs
```bash
cd kafkapractice
mvn clean package -DskipTests
```

### 2. Start backend (Docker)
```bash
docker compose up -d
```
Startup order is automatic:
`zookeeper (healthy) → kafka-broker (healthy) → kafka-producer + kafka-consumer`

### 3. Start React client
```bash
cd react-client
npm run dev
```
Open **http://localhost:5173**

---

## Docker Commands

| Action                        | Command                              |
|-------------------------------|--------------------------------------|
| Start all containers          | `docker compose up -d`               |
| Stop all containers           | `docker compose down`                |
| Stop + delete volumes         | `docker compose down -v`             |
| View container status         | `docker compose ps`                  |
| Stream logs (all)             | `docker compose logs -f`             |
| Stream logs (one service)     | `docker compose logs -f kafka-broker`|
| Restart one service           | `docker compose restart kafka-consumer` |
| Rebuild images                | `docker compose up -d --build`       |

---

## API Endpoints (kafka-producer :8081)

### Send a custom order
```
POST http://localhost:8081/api/orders
Content-Type: application/json

{
  "product":  "Laptop",
  "quantity": 2,
  "price":    999.99,
  "status":   "CREATED"
}
```

### Send a pre-built sample order
```
POST http://localhost:8081/api/orders/sample
```

---

## WebSocket (kafka-consumer :8082)

| Property        | Value                        |
|-----------------|------------------------------|
| Endpoint        | `ws://localhost:8082/ws`     |
| Protocol        | STOMP over SockJS            |
| Subscribe topic | `/topic/orders`              |

---

## React UI — Producer Panel

**Manual mode** — fill Product / Quantity / Price / Status then click **Send Order**.

**Dynamic Stream mode** — click **▶ Start Stream** to auto-generate random orders.

| Interval | Rate          |
|----------|---------------|
| 0.5s     | ~2 / second   |
| 1s       | 1 / second    |
| 2s       | 1 every 2s    |
| 5s       | 1 every 5s    |

Click **■ Stop Stream** to halt. The counter shows total events sent in the session.

---

## Kafka Topic

| Property          | Value            |
|-------------------|------------------|
| Topic name        | `order-events`   |
| Partitions        | 3                |
| Replication       | 1                |
| Consumer group    | `order-consumer-group` |

---

## Troubleshooting

**kafka-broker fails to start**
ZooKeeper may not be healthy yet. Tear down with volumes and restart:
```bash
docker compose down -v
docker compose up -d
```

**React consumer panel shows "Disconnected"**
kafka-consumer on :8082 is not yet running. Check:
```bash
docker compose ps
docker compose logs kafka-consumer
```

**`Order event queued` appears in sent log but nothing arrives in consumer panel**
- kafka-consumer may still be starting (Kafka Streams takes ~10s to initialise)
- Check: `docker compose logs -f kafka-consumer`

**Port already in use**
Find and kill the process using the port:
```bash
# Windows
netstat -ano | findstr :8081
taskkill /PID <pid> /F
```