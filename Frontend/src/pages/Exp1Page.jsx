import { useState } from 'react'
import { exp1 } from '../api'
import SeatGrid from '../components/SeatGrid'
import Terminal from '../components/Terminal'
import { useToast } from '../components/Toast'

export default function Exp1Page() {
  const toast = useToast()
  const [showId,   setShowId]   = useState('')
  const [userName, setUserName] = useState('')
  const [seats,    setSeats]    = useState([])
  const [selected, setSelected] = useState(null)
  const [booked,   setBooked]   = useState([])
  const [log,      setLog]      = useState([])
  const [loading,  setLoading]  = useState({})
  const [result,   setResult]   = useState(null)

  function addLog(line) { setLog(prev => [...prev, line]) }

  async function fetchSeats() {
    if (!showId.trim()) { toast('Enter a Show ID', 'error'); return }
    setLoading(l => ({ ...l, fetch: true }))
    addLog(`→ getAvailableSeats("${showId}")`)
    try {
      const data = await exp1.getSeats(showId.trim())
      addLog(`← ${data.seats.length} seat(s) returned`)
      setSeats(data.seats)
      setSelected(null)
      setResult(null)
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`)
      toast(msg, 'error')
    }
    setLoading(l => ({ ...l, fetch: false }))
  }

  async function bookSeat() {
    if (!selected)        { toast('Select a seat first', 'error'); return }
    if (!userName.trim()) { toast('Enter your name', 'error'); return }
    setLoading(l => ({ ...l, book: true }))
    addLog(`→ bookSeat("${selected}", "${userName}")`)
    try {
      const data = await exp1.bookSeat(selected, userName.trim())
      addLog(`← ${data.result}`)
      setResult(data)
      toast(data.result, data.success ? 'success' : 'error')
      if (data.success) { setBooked(b => [...b, selected]); setSelected(null) }
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`)
      toast(msg, 'error')
    }
    setLoading(l => ({ ...l, book: false }))
  }

  return (
    <div className="page-wrap">
      <div className="exp-header">
        <div className="exp-header-icon">🔗</div>
        <div>
          <div className="exp-header-num">Experiment 1</div>
          <div className="exp-header-title">Java RMI Basics</div>
          <div className="exp-header-desc">
            Client connects to a remote BookingService via RMI Registry and books a movie seat.
            Demonstrates Remote Method Invocation as if calling a local Java method.
          </div>
          <div className="tag-strip">
            {['RMI', 'Remote Interface', 'RMI Registry', 'PostgreSQL'].map(t => (
              <span key={t} className="concept-tag">{t}</span>
            ))}
          </div>
        </div>
      </div>

      <div className="two-col">
        {/* Left — Controls */}
        <div>
          <div className="card">
            <div className="card-title">Fetch Available Seats</div>
            <div className="field">
              <label>Show ID (UUID)</label>
              <input
                className="input"
                placeholder="e.g. 3fa85f64-5717-4562-b3fc-2c963f66afa6"
                value={showId}
                onChange={e => setShowId(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && fetchSeats()}
              />
            </div>
            <button className="btn btn-outline" onClick={fetchSeats} disabled={loading.fetch}>
              {loading.fetch ? <span className="spinner" /> : '🔍'} Fetch Seats
            </button>
          </div>

          <div className="card">
            <div className="card-title">Book a Seat</div>
            <div className="field">
              <label>Your Name</label>
              <input
                className="input"
                placeholder="e.g. Alice"
                value={userName}
                onChange={e => setUserName(e.target.value)}
              />
            </div>
            <button
              className="btn btn-red"
              onClick={bookSeat}
              disabled={!selected || loading.book}
            >
              {loading.book ? <span className="spinner" /> : '🎟'} Book Selected Seat
            </button>
            {result && (
              <div className={`result-pill ${result.success ? 'ok' : 'err'}`}>
                {result.result}
              </div>
            )}
          </div>
        </div>

        {/* Right — Seat grid + terminal */}
        <div>
          <div className="card">
            <div className="card-title">
              Seat Map
              {selected && <span className="badge badge-red" style={{ marginLeft: 8 }}>1 selected</span>}
            </div>
            <SeatGrid
              seats={seats}
              booked={booked}
              selected={selected}
              onSelect={(id) => setSelected(prev => prev === id ? null : id)}
            />
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
