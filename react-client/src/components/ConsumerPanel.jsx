import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.js'
import './ConsumerPanel.css'

export default function ConsumerPanel() {
  const [events, setEvents] = useState([])
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8082/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/orders', (msg) => {
          const event = JSON.parse(msg.body)
          setEvents((prev) => [{ ...event, receivedAt: new Date() }, ...prev].slice(0, 100))
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => client.deactivate()
  }, [])

  return (
    <div className="panel consumer-panel">
      <div className="panel-title">
        <span className="dot dot-green" />
        Consumer (Kafka Streams)
        <span className="tag">:8082</span>
        <span className={`conn-badge ${connected ? 'connected' : 'disconnected'}`}>
          {connected ? 'Connected' : 'Disconnected'}
        </span>
      </div>

      <div className="events-count">
        {events.length} event{events.length !== 1 ? 's' : ''} received
        {events.length > 0 && (
          <button className="clear-btn" onClick={() => setEvents([])}>
            Clear
          </button>
        )}
      </div>

      <div className="events">
        {events.length === 0 && (
          <div className="empty">
            {connected
              ? 'Waiting for events…'
              : 'Connecting to ws://localhost:8082/ws…'}
          </div>
        )}
        {events.map((e, i) => (
          <div key={i} className="event-card">
            <div className="event-header">
              <span className={`status-badge status-${e.status}`}>{e.status}</span>
              <span className="event-id">{e.orderId}</span>
              <span className="event-time">{e.receivedAt.toLocaleTimeString()}</span>
            </div>
            <div className="event-body">
              <span>{e.product}</span>
              <span>×{e.quantity}</span>
              <span className="event-price">${e.price?.toFixed(2)}</span>
            </div>
            {e.message && (
              <div className="event-message">{e.message}</div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}