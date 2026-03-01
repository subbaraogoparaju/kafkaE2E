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

**ProducerPanel** — form to publish `OrderEvent` records to `kafka-producer` (port 8081) via REST.

**ConsumerPanel** — live feed of events received from `kafka-consumer` (port 8082) via STOMP/WebSocket at `ws://localhost:8082/ws`, subscribed to `/topic/orders`.

## Environment

Backend URLs are hardcoded for local development:

| Service | URL |
|---|---|
| kafka-producer REST | `http://localhost:8081` |
| kafka-consumer WebSocket | `ws://localhost:8082/ws` |

See the [root README](../README.md) for full project documentation.