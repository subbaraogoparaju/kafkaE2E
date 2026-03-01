# react-client

React + Vite frontend for the Kafka Practice demo.

## Prerequisites

- Node.js 18+

## Setup

```bash
npm install
npm run dev
```

Open **http://localhost:5173**

## Panels

**ProducerPanel** — form to publish `OrderEvent` records to `kafka-producer` (port 8081) via REST. Supports manual send and auto-streaming at configurable intervals.

**ConsumerPanel** — live feed of order events received from `kafka-consumer` (port 8082) via STOMP/WebSocket, subscribed to `/topic/orders`.

**FraudPanel** — live fraud alert feed received from `kafka-fraud-detector` (port 8083) via STOMP/WebSocket, subscribed to `/topic/fraud-alerts`. Displays a red alert card for each `FraudAlert` (triggered when 3 or more consecutive `CANCELLED` events are detected globally). The panel border flashes red on each new alert.

## WebSocket connections

| Panel | Host | Endpoint | Subscription |
|---|---|---|---|
| ConsumerPanel | `localhost:8082` | `/ws` | `/topic/orders` |
| FraudPanel | `localhost:8083` | `/ws` | `/topic/fraud-alerts` |

## Environment

Backend URLs are hardcoded for local development:

| Service | URL |
|---|---|
| kafka-producer REST | `http://localhost:8081` |
| kafka-consumer WebSocket | `http://localhost:8082/ws` |
| kafka-fraud-detector WebSocket | `http://localhost:8083/ws` |

See the [root README](../README.md) for full project documentation.