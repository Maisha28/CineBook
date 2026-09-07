import { useState } from 'react'
import { exp3 } from '../api'
import SeatGrid from '../components/SeatGrid'
import Terminal from '../components/Terminal'
import { useToast } from '../components/Toast'

const NODE_COLORS = ['#e51937','#1a9e5c','#3b82f6','#e07b00','#7c3aed']

export default function Exp3Page() {
  const toast = useToast()

  const [showId,   setShowId]   = useState('')
  const [userName, setUserName] = useState('')
  const [seats,    setSeats]    = useState([])
  const [selected, setSelected] = useState(null)
  const [booked,   setBooked]   = useState([])
  const [log,      setLog]      = useState([])
  const [loading,  setLoading]  = useState({})
  const [result,   setResult]   = useState(null)

  // Lamport
  const [lamClients, setLamClients] = useState('C1, C2, C3')
  const [lamLog,     setLamLog]     = useState([])

  // Berkeley
  const [berkResult, setBerkResult] = useState(null)

  function addLog(line) { setLog(prev => [...prev, line]) }

  async function fetchSeats() {
    if (!showId.trim()) { toast('Enter a Show ID', 'error'); return }
    setLoading(l => ({ ...l, fetch: true }))
    addLog(`→ [Exp3] getAvailableSeats("${showId}")`)
    try {
      const data = await exp3.getSeats(showId.trim())
      addLog(`← ${data.seats.length} seat(s)`)
      setSeats(data.seats); setSelected(null); setResult(null)
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`); toast(msg, 'error')
    }
    setLoading(l => ({ ...l, fetch: false }))
  }

  async function bookSeat() {
    if (!selected || !userName.trim()) { toast('Select seat and enter name', 'error'); return }
    setLoading(l => ({ ...l, book: true }))
    addLog(`→ bookSeat("${selected}", "${userName}", lamport++)`)
    try {
      const data = await exp3.bookSeat(selected, userName.trim())
      addLog(`← ${data.result}  [lamport=${data.clientLamport ?? '?'}]`)
      setResult(data)
      toast(data.result, data.success ? 'success' : 'error')
      if (data.success) { setBooked(b => [...b, selected]); setSelected(null) }
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`); toast(msg, 'error')
    }
    setLoading(l => ({ ...l, book: false }))
  }

  async function runLamport() {
    const clients = lamClients.split(',').map(c => c.trim()).filter(Boolean)
    if (clients.length < 2) { toast('Enter at least 2 clients', 'error'); return }
    setLoading(l => ({ ...l, lam: true }))
    setLamLog([])
    addLog(`→ Lamport demo: ${clients.join(', ')}`)
    try {
      const data = await exp3.lamport(clients)
      setLamLog(data.log || [])
      ;(data.log || []).forEach(l => addLog(l))
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`); toast(msg, 'error')
    }
    setLoading(l => ({ ...l, lam: false }))
  }

  async function runBerkeley() {
    setLoading(l => ({ ...l, berk: true }))
    setBerkResult(null)
    const now = Date.now()
    const clients = { C1: now - 2500, C2: now + 1800, C3: now - 750 }
    addLog(`→ Berkeley sync (simulated offsets: C1=-2.5s, C2=+1.8s, C3=-0.75s)`)
    try {
      const data = await exp3.berkeley(clients)
      setBerkResult(data.corrections)
      Object.entries(data.corrections).forEach(([n, c]) => addLog(`  ${n}: ${c >= 0 ? '+' : ''}${c}ms`))
      toast('Berkeley sync complete', 'success')
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`); toast(msg, 'error')
    }
    setLoading(l => ({ ...l, berk: false }))
  }

  return (
    <div className="page-wrap">
      <div className="exp-header">
        <div className="exp-header-icon">🕐</div>
        <div>
          <div className="exp-header-num">Experiment 3</div>
          <div className="exp-header-title">Logical Clocks — Lamport & Berkeley</div>
          <div className="exp-header-desc">
            Lamport timestamps attach a logical clock value to every RMI call, establishing causal
            ordering of distributed events. Berkeley algorithm computes clock corrections to synchronize nodes.
          </div>
          <div className="tag-strip">
            {['Lamport Clock', 'Berkeley Sync', 'Logical Time', 'Clock Drift', 'RMI'].map(t => (
              <span key={t} className="concept-tag">{t}</span>
            ))}
          </div>
        </div>
      </div>

      {/* Row 1 — Booking */}
      <div className="two-col">
        <div>
          <div className="card">
            <div className="card-title">Book with Lamport Timestamp</div>
            <div className="field">
              <label>Show ID (UUID)</label>
              <input className="input" placeholder="Show UUID" value={showId}
                onChange={e => setShowId(e.target.value)} onKeyDown={e => e.key === 'Enter' && fetchSeats()} />
            </div>
            <button className="btn btn-outline" onClick={fetchSeats} disabled={loading.fetch} style={{ marginBottom: 10 }}>
              {loading.fetch ? <span className="spinner" /> : '🔍'} Fetch Seats
            </button>
            <div className="field">
              <label>Your Name</label>
              <input className="input" placeholder="e.g. Alice" value={userName}
                onChange={e => setUserName(e.target.value)} />
            </div>
            <button className="btn btn-red" onClick={bookSeat} disabled={!selected || loading.book}>
              {loading.book ? <span className="spinner" /> : '🕐'} Book (Lamport++)
            </button>
            {result && (
              <div className={`result-pill ${result.success ? 'ok' : 'err'}`}>
                {result.result}
                {result.clientLamport != null && (
                  <span style={{ color: 'var(--text-muted)', fontSize: 11, marginLeft: 8 }}>
                    lamport={result.clientLamport}
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Lamport demo */}
          <div className="card" style={{ borderLeft: '3px solid var(--red)' }}>
            <div className="card-title">Lamport Clock Demo</div>
            <div className="field">
              <label>Clients (comma-separated)</label>
              <input className="input" value={lamClients} onChange={e => setLamClients(e.target.value)} />
            </div>
            <button className="btn btn-outline" onClick={runLamport} disabled={loading.lam}>
              {loading.lam ? <span className="spinner" /> : '▶'} Run Lamport Demo
            </button>
            {lamLog.length > 0 && (
              <div style={{ marginTop: 14 }}>
                {lamLog.map((line, i) => {
                  const m = line.match(/^\[([^\]]+)\]/)
                  const who = m ? m[1] : 'SERVER'
                  const idx = ['C1','C2','C3','C4','C5'].indexOf(who)
                  const color = idx >= 0 ? NODE_COLORS[idx] : '#717171'
                  return (
                    <div key={i} style={{ display: 'flex', gap: 8, fontSize: 13, marginBottom: 4, alignItems: 'baseline' }}>
                      <span style={{ color, fontWeight: 700, minWidth: 32, fontFamily: 'var(--mono)' }}>{who}</span>
                      <span style={{ color: 'var(--text-muted)', fontFamily: 'var(--mono)', fontSize: 12 }}>
                        {line.replace(/^\[[^\]]+\]\s*/, '')}
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        <div>
          <div className="card">
            <div className="card-title">
              Seat Map
              {selected && <span className="badge badge-red" style={{ marginLeft: 8 }}>1 selected</span>}
            </div>
            <SeatGrid seats={seats} booked={booked} selected={selected}
              onSelect={id => setSelected(prev => prev === id ? null : id)} />
          </div>

          {/* Berkeley */}
          <div className="card" style={{ borderLeft: '3px solid var(--red)' }}>
            <div className="card-title">Berkeley Clock Synchronization</div>
            <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 12 }}>
              Sends simulated client offsets (C1=−2.5 s, C2=+1.8 s, C3=−0.75 s) to the Java server.
              The server computes an average and returns the correction each node should apply.
            </p>
            <button className="btn btn-outline" onClick={runBerkeley} disabled={loading.berk}>
              {loading.berk ? <span className="spinner" /> : '⏱'} Run Berkeley Sync
            </button>
            {berkResult && (
              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {Object.entries(berkResult).map(([node, correction]) => (
                  <div key={node} className="result-pill info" style={{ marginTop: 0 }}>
                    <strong>{node}</strong> → {correction >= 0 ? '+' : ''}{correction} ms
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
              <div className="card-title" style={{ marginBottom: 0 }}>RMI Call Log</div>
              <button className="btn btn-ghost btn-sm" onClick={() => setLog([])}>Clear</button>
            </div>
            <Terminal lines={log} />
          </div>
        </div>
      </div>
    </div>
  )
}
