import ProducerPanel from './components/ProducerPanel'
import ConsumerPanel from './components/ConsumerPanel'
import './App.css'

export default function App() {
  return (
    <div className="app">
      <header className="app-header">
        <h1>Kafka Streams Demo</h1>
        <span className="subtitle">Producer → Kafka → Streams → Consumer</span>
      </header>
      <div className="panels">
        <ProducerPanel />
        <div className="divider" />
        <ConsumerPanel />
      </div>
    </div>
  )
}