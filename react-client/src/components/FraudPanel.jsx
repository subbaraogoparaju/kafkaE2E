import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.js'
import './FraudPanel.css'

export default function FraudPanel() {
  const [alerts, setAlerts] = useState([])
  const [connected, setConnected] = useState(false)
  const [flash, setFlash] = useState(false)
  const clientRef = useRef(null)
  const flashTimer = useRef(null)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8083/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/fraud-alerts', (msg) => {
          const alert = JSON.parse(msg.body)
          setAlerts((prev) => [{ ...alert, receivedAt: new Date() }, ...prev].slice(0, 50))

          // flash the panel border on new alert
          setFlash(true)
          clearTimeout(flashTimer.current)
          flashTimer.current = setTimeout(() => setFlash(false), 1500)
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => {
      clearTimeout(flashTimer.current)
      client.deactivate()
    }
  }, [])

  return (
    <div className={`panel fraud-panel ${flash ? 'fraud-flash' : ''}`}>
      <div className="panel-title">
        <span className="dot dot-red" />
        Fraud Detector
        <span className="tag">:8083</span>
        <span className={`conn-badge ${connected ? 'connected' : 'disconnected'}`}>
          {connected ? 'Connected' : 'Disconnected'}
        </span>
      </div>

      <div className="fraud-count-bar">
        <span className="fraud-count">
          {alerts.length === 0
            ? 'No alerts'
            : `${alerts.length} alert${alerts.length !== 1 ? 's' : ''}`}
        </span>
        {alerts.length > 0 && (
          <button className="fraud-clear-btn" onClick={() => setAlerts([])}>
            Clear
          </button>
        )}
      </div>

      <div className="fraud-rule">
        Rule: 3 consecutive <span className="rule-status">CANCELLED</span> events globally
      </div>

      <div className="fraud-alerts">
        {alerts.length === 0 && (
          <div className="empty">
            {connected ? 'Monitoring for fraud patterns…' : 'Connecting to ws://localhost:8083/ws…'}
          </div>
        )}
        {alerts.map((a, i) => (
          <div key={i} className={`fraud-card ${i === 0 ? 'fraud-card-new' : ''}`}>
            <div className="fraud-card-header">
              <span className="fraud-icon">⚠</span>
              <span className="fraud-label">Fraud Alert</span>
              <span className="fraud-count-badge">×{a.consecutiveCount}</span>
              <span className="fraud-time">{a.receivedAt.toLocaleTimeString()}</span>
            </div>
            <div className="fraud-card-body">
              <div className="fraud-field">
                <span className="fraud-field-label">Order ID</span>
                <span className="fraud-field-value">{a.orderId}</span>
              </div>
              <div className="fraud-field">
                <span className="fraud-field-label">Status</span>
                <span className="status-badge status-CANCELLED">{a.status}</span>
              </div>
              <div className="fraud-field">
                <span className="fraud-field-label">Consecutive</span>
                <span className="fraud-consecutive">{a.consecutiveCount} in a row</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}