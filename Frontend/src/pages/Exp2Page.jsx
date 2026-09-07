import { useState } from 'react'
import { exp2 } from '../api'
import SeatGrid from '../components/SeatGrid'
import Terminal from '../components/Terminal'
import { useToast } from '../components/Toast'

export default function Exp2Page() {
  const toast = useToast()

  // Normal booking
  const [showId,   setShowId]   = useState('')
  const [userName, setUserName] = useState('')
  const [seats,    setSeats]    = useState([])
  const [selected, setSelected] = useState(null)
  const [booked,   setBooked]   = useState([])
  const [log,      setLog]      = useState([])
  const [loading,  setLoading]  = useState({})
  const [result,   setResult]   = useState(null)

  // Concurrent demo
  const [ccSeatId, setCcSeatId]   = useState('')
  const [ccUsers,  setCcUsers]    = useState('Alice, Bob, Carol')
  const [ccResults,setCcResults]  = useState(null)

  function addLog(line) { setLog(prev => [...prev, line]) }

  async function fetchSeats() {
    if (!showId.trim()) { toast('Enter a Show ID', 'error'); return }
    setLoading(l => ({ ...l, fetch: true }))
    addLog(`→ [Exp2] getAvailableSeats("${showId}")`)
    try {
      const data = await exp2.getSeats(showId.trim())
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
    addLog(`→ bookSeat("${selected}", "${userName}")`)
    try {
      const data = await exp2.bookSeat(selected, userName.trim())
      addLog(`← ${data.result}`)
      setResult(data)
      toast(data.result, data.success ? 'success' : 'error')
      if (data.success) { setBooked(b => [...b, selected]); setSelected(null) }
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`); toast(msg, 'error')
    }
    setLoading(l => ({ ...l, book: false }))
  }

  async function runConcurrent() {
    const seatId = ccSeatId.trim() || selected
    if (!seatId) { toast('Select a seat or enter Seat ID', 'error'); return }
    const users = ccUsers.split(',').map(u => u.trim()).filter(Boolean)
    if (users.length < 2) { toast('Enter at least 2 users separated by commas', 'error'); return }

    setLoading(l => ({ ...l, cc: true }))
    setCcResults(null)
    addLog(`→ [CONCURRENT] ${users.length} users racing for seat ${seatId}`)
    try {
      const data = await exp2.concurrent(seatId, users)
      setCcResults(data.results)
      const successCount = Object.values(data.results).filter(r => r?.startsWith('SUCCESS')).length
      addLog(`← Done: ${successCount} booking(s) succeeded out of ${users.length}`)
      Object.entries(data.results).forEach(([u, r]) => addLog(`  ${u} → ${r}`))
    } catch (e) {
      const msg = e.response?.data?.error || e.message
      addLog(`✗ ${msg}`); toast(msg, 'error')
    }
    setLoading(l => ({ ...l, cc: false }))
  }

  return (
    <div className="page-wrap">
      <div className="exp-header">
        <div className="exp-header-icon">⚡</div>
        <div>
          <div className="exp-header-num">Experiment 2</div>
          <div className="exp-header-title">Concurrent Booking & Race Conditions</div>
          <div className="exp-header-desc">
            Multiple threads try to book the same seat simultaneously. The <code>synchronized</code> keyword
            prevents double-booking; remove it to observe the race condition.
          </div>
          <div className="tag-strip">
            {['Concurrency', 'Race Condition', 'synchronized', 'Threads', 'RMI'].map(t => (
              <span key={t} className="concept-tag">{t}</span>
            ))}
          </div>
        </div>
      </div>

      <div className="two-col">
        <div>
          <div className="card">
            <div className="card-title">Fetch Seats</div>
            <div className="field">
              <label>Show ID (UUID)</label>
              <input className="input" placeholder="Show UUID" value={showId}
                onChange={e => setShowId(e.target.value)} onKeyDown={e => e.key === 'Enter' && fetchSeats()} />
            </div>
            <button className="btn btn-outline" onClick={fetchSeats} disabled={loading.fetch}>
              {loading.fetch ? <span className="spinner" /> : '🔍'} Fetch Seats
            </button>
          </div>

          <div className="card">
            <div className="card-title">Single Booking</div>
            <div className="field">
              <label>Your Name</label>
              <input className="input" placeholder="e.g. Alice" value={userName}
                onChange={e => setUserName(e.target.value)} />
            </div>
            <button className="btn btn-red" onClick={bookSeat} disabled={!selected || loading.book}>
              {loading.book ? <span className="spinner" /> : '🎟'} Book Selected Seat
            </button>
            {result && (
              <div className={`result-pill ${result.success ? 'ok' : 'err'}`}>{result.result}</div>
            )}
          </div>

          {/* Concurrent demo */}
          <div className="card" style={{ borderLeft: '3px solid var(--red)' }}>
            <div className="card-title">⚡ Concurrent Race Demo</div>
            <div className="field">
              <label>Seat ID (paste UUID or select from map)</label>
              <input className="input" placeholder="Seat UUID"
                value={ccSeatId || selected || ''}
                onChange={e => setCcSeatId(e.target.value)} />
            </div>
            <div className="field">
              <label>Competing Users (comma-separated)</label>
              <input className="input" value={ccUsers} onChange={e => setCcUsers(e.target.value)} />
            </div>
            <button className="btn btn-red" onClick={runConcurrent} disabled={loading.cc}>
              {loading.cc ? <span className="spinner" /> : '⚡'} Run Concurrent Booking
            </button>
            {ccResults && (
              <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {Object.entries(ccResults).map(([user, res]) => {
                  const ok = res?.startsWith('SUCCESS')
                  return (
                    <div key={user} className={`result-pill ${ok ? 'ok' : 'err'}`} style={{ marginTop: 0 }}>
                      <strong>{user}</strong>: {res}
                    </div>
                  )
                })}
                {(() => {
                  const wins = Object.values(ccResults).filter(r => r?.startsWith('SUCCESS')).length
                  return (
                    <div className={`result-pill ${wins === 1 ? 'ok' : 'info'}`} style={{ marginTop: 0 }}>
                      {wins === 1
                        ? '✓ synchronized keyword worked — exactly 1 booking succeeded'
                        : `⚠ ${wins} bookings succeeded — race condition visible`}
                    </div>
                  )
                })()}
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
          <div className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
              <div className="card-title" style={{ marginBottom: 0 }}>Server Log</div>
              <button className="btn btn-ghost btn-sm" onClick={() => setLog([])}>Clear</button>
            </div>
            <Terminal lines={log} />
          </div>
        </div>
      </div>
    </div>
  )
}
