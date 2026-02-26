import { useState, useRef, useCallback } from 'react'
import './ProducerPanel.css'

const STATUSES = ['CREATED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']

const PRODUCTS = [
  'MacBook Pro', 'iPhone 15 Pro', 'AirPods Pro', 'iPad Air',
  'Samsung Galaxy S24', 'Sony WH-1000XM5', 'Dell XPS 15',
  'Nike Air Max', 'Mechanical Keyboard', 'Gaming Chair',
  'LG OLED TV', 'Dyson V15', 'GoPro Hero 12', 'Kindle Paperwhite',
]

const INTERVALS = [
  { label: '0.5s', ms: 500 },
  { label: '1s',   ms: 1000 },
  { label: '2s',   ms: 2000 },
  { label: '5s',   ms: 5000 },
]

const defaultForm = {
  product:  'Laptop',
  quantity: 1,
  price:    999.99,
  status:   'CREATED',
}

function randomOrder() {
  return {
    product:  PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)],
    quantity: Math.floor(Math.random() * 9) + 1,
    price:    parseFloat((Math.random() * 1900 + 9.99).toFixed(2)),
    status:   STATUSES[Math.floor(Math.random() * STATUSES.length)],
  }
}

async function postOrder(order) {
  const res = await fetch('http://localhost:8081/api/orders', {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify(order),
  })
  return { ok: res.ok, text: await res.text() }
}

export default function ProducerPanel() {
  const [form, setForm]           = useState(defaultForm)
  const [log, setLog]             = useState([])
  const [sending, setSending]     = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [intervalMs, setIntervalMs] = useState(1000)
  const [streamCount, setStreamCount] = useState(0)

  const timerRef    = useRef(null)
  const countRef    = useRef(0)

  const addLog = useCallback((entry) =>
    setLog((prev) => [entry, ...prev].slice(0, 100)), [])

  // ── Manual send ──────────────────────────────────────────────
  const send = async () => {
    setSending(true)
    try {
      const { ok, text } = await postOrder(form)
      addLog({ ok, message: text, order: { ...form }, time: new Date() })
    } catch (err) {
      addLog({ ok: false, message: err.message, order: { ...form }, time: new Date() })
    } finally {
      setSending(false)
    }
  }

  // ── Dynamic stream ────────────────────────────────────────────
  const startStream = () => {
    countRef.current = 0
    setStreamCount(0)
    setStreaming(true)

    const tick = async () => {
      const order = randomOrder()
      try {
        const { ok, text } = await postOrder(order)
        addLog({ ok, message: text, order, time: new Date(), streamed: true })
      } catch (err) {
        addLog({ ok: false, message: err.message, order, time: new Date(), streamed: true })
      }
      countRef.current += 1
      setStreamCount(countRef.current)
    }

    tick()                                      // fire immediately
    timerRef.current = setInterval(tick, intervalMs)
  }

  const stopStream = () => {
    clearInterval(timerRef.current)
    timerRef.current = null
    setStreaming(false)
  }

  const toggleStream = () => (streaming ? stopStream() : startStream())

  return (
    <div className="panel producer-panel">
      <div className="panel-title">
        <span className="dot dot-blue" />
        Producer
        <span className="tag">:8081</span>
        {streaming && <span className="streaming-badge">● STREAMING</span>}
      </div>

      {/* ── Manual form ── */}
      <div className="form">
        <label>Product</label>
        <input
          value={form.product}
          onChange={(e) => setForm({ ...form, product: e.target.value })}
          disabled={streaming}
        />

        <div className="form-row">
          <div>
            <label>Quantity</label>
            <input
              type="number" min={1}
              value={form.quantity}
              onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })}
              disabled={streaming}
            />
          </div>
          <div>
            <label>Price ($)</label>
            <input
              type="number" step="0.01"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
              disabled={streaming}
            />
          </div>
        </div>

        <label>Status</label>
        <select
          value={form.status}
          onChange={(e) => setForm({ ...form, status: e.target.value })}
          disabled={streaming}
        >
          {STATUSES.map((s) => <option key={s}>{s}</option>)}
        </select>

        <button
          className="btn btn-primary"
          onClick={send}
          disabled={sending || streaming}
        >
          {sending ? 'Sending…' : 'Send Order →'}
        </button>
      </div>

      {/* ── Stream controls ── */}
      <div className="stream-section">
        <div className="stream-label">Dynamic Stream</div>

        <div className="interval-row">
          <span className="interval-hint">Interval:</span>
          {INTERVALS.map(({ label, ms }) => (
            <button
              key={ms}
              className={`interval-btn ${intervalMs === ms ? 'active' : ''}`}
              onClick={() => setIntervalMs(ms)}
              disabled={streaming}
            >
              {label}
            </button>
          ))}
        </div>

        <button
          className={`btn stream-btn ${streaming ? 'btn-stop' : 'btn-start'}`}
          onClick={toggleStream}
        >
          {streaming
            ? `■ Stop Stream  (${streamCount} sent)`
            : '▶ Start Stream'}
        </button>

        {streaming && (
          <div className="stream-info">
            Auto-generating random orders every {intervalMs}ms
          </div>
        )}
      </div>

      {/* ── Sent log ── */}
      <div className="log-header">
        Sent log
        {log.length > 0 && (
          <button className="clear-log-btn" onClick={() => setLog([])}>clear</button>
        )}
      </div>
      <div className="log">
        {log.length === 0 && <div className="empty">No messages sent yet.</div>}
        {log.map((entry, i) => (
          <div key={i} className={`log-entry ${entry.ok ? 'ok' : 'err'}`}>
            <div className="log-meta">
              <span className={`status-badge status-${entry.order.status}`}>
                {entry.order.status}
              </span>
              <span className="log-product">{entry.order.product}</span>
              {entry.streamed && <span className="auto-tag">auto</span>}
              <span className="log-time">{entry.time.toLocaleTimeString()}</span>
            </div>
            <div className="log-msg">{entry.message}</div>
          </div>
        ))}
      </div>
    </div>
  )
}