import { useState, useEffect } from 'react'
import { exp2, catalog } from '../../api'
import { useToast } from '../../components/Toast'
import { Zap, Play, Shield, Users, CheckCircle2, XCircle, AlertCircle } from 'lucide-react'
import styles from './Lab.module.css'

export default function LabExp2Concurrency() {
  const toast = useToast()
  const [availableSeats, setAvailableSeats] = useState([])
  const [seatId, setSeatId] = useState('')
  const [threadCount, setThreadCount] = useState(5)
  const [running, setRunning] = useState(false)
  const [results, setResults] = useState(null)
  const [stats, setStats] = useState(null)

  useEffect(() => {
    fetchAvailableSeats()
  }, [])

  async function fetchAvailableSeats() {
    try {
      const showsRes = await catalog.getShows()
      const showId = showsRes?.shows?.[0]?.id || '8fd0925d-364b-485e-837c-b2aaea27fa56'
      const seatsRes = await catalog.getSeats(showId)
      if (seatsRes?.seats?.length > 0) {
        const avail = seatsRes.seats.filter(s => s.status === 'available')
        setAvailableSeats(avail)
        if (avail.length > 0) {
          setSeatId(avail[0].id)
        }
      }
    } catch {}
  }

  async function handleRunTest() {
    if (!seatId.trim()) {
      toast('Seat ID is required', 'warning')
      return
    }

    setRunning(true)
    setResults(null)
    setStats(null)

    // Generate user thread names
    const users = []
    for (let i = 1; i <= threadCount; i++) {
      users.push(`Thread-${i} (User ${i})`)
    }

    try {
      const data = await exp2.concurrent(seatId.trim(), users)
      if (data.results) {
        setResults(data.results)

        let successCount = 0
        let failedCount = 0
        Object.values(data.results).forEach(r => {
          if (r && r.startsWith('SUCCESS')) successCount++
          else failedCount++
        })

        setStats({
          seat: seatId.trim(),
          total: users.length,
          success: successCount,
          rejected: failedCount
        })

        toast(`Concurrent test completed: ${successCount} won, ${failedCount} rejected`, 'info')
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error running test', 'error')
    }
    setRunning(false)
  }

  return (
    <div className={styles.expContainer}>
      {/* ── Header & Status ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 2: Concurrent Seat Booking & Race Conditions</h2>
          <p className={styles.expSubtitle}>
            Multiple client threads competing to book the exact same shared seat simultaneously.
          </p>
        </div>

        <div className={styles.statusGroup}>
          <span className={styles.statusPill}>
            <Shield size={13} color="#059669" />
            <span>Isolation: <strong>synchronized Method</strong></span>
          </span>
        </div>
      </div>

      {/* ── Test Configuration ── */}
      <div className={styles.testCard}>
        <h3 className={styles.cardHeader}>Configure Concurrent Race Harness</h3>

        <div className={styles.formRow}>
          <div className="field" style={{ flex: 2 }}>
            <label>Target Shared Seat {availableSeats.length > 0 && <span style={{ color: '#059669', fontSize: '0.8rem' }}>(Live Supabase Seats Loaded)</span>}</label>
            {availableSeats.length > 0 ? (
              <select
                className="input"
                value={seatId}
                onChange={e => setSeatId(e.target.value)}
              >
                {availableSeats.map(s => (
                  <option key={s.id} value={s.id}>
                    Seat {s.seatNumber} ({s.tier || 'Prime'} • ₹{s.price}) [UUID: {s.id.substring(0, 8)}...]
                  </option>
                ))}
              </select>
            ) : (
              <input
                type="text"
                className="input"
                value={seatId}
                onChange={e => setSeatId(e.target.value)}
                placeholder="Enter Seat UUID"
              />
            )}
          </div>

          <div className="field" style={{ flex: 1 }}>
            <label>Concurrent Threads</label>
            <select
              className="input"
              value={threadCount}
              onChange={e => setThreadCount(Number(e.target.value))}
            >
              <option value={2}>2 Threads</option>
              <option value={3}>3 Threads</option>
              <option value={5}>5 Threads</option>
              <option value={8}>8 Threads</option>
              <option value={10}>10 Threads</option>
            </select>
          </div>

          <div className="field" style={{ flex: 1 }}>
            <label>Execution Mode</label>
            <input
              type="text"
              className="input"
              value="Synchronized Lock"
              disabled
              style={{ backgroundColor: '#F3F4F6' }}
            />
          </div>
        </div>

        <div className={styles.actionRow} style={{ marginTop: 16 }}>
          <button
            type="button"
            className="btn btn-primary btn-lg"
            onClick={handleRunTest}
            disabled={running}
          >
            {running ? <span className="spinner" /> : <Zap size={15} />}
            <span>Launch Concurrent Booking Test</span>
          </button>
        </div>
      </div>

      {/* ── Results Outcome Summary ── */}
      {stats && (
        <div className={styles.metricsRow}>
          <div className={styles.metricCard}>
            <span className={styles.metricLabel}>SHARED RESOURCE</span>
            <span className={styles.metricValHighlight}>{stats.seat}</span>
          </div>

          <div className={styles.metricCard}>
            <span className={styles.metricLabel}>TOTAL REQUESTS</span>
            <span className={styles.metricVal}>{stats.total} Threads</span>
          </div>

          <div className={styles.metricCard}>
            <span className={styles.metricLabel}>SUCCESSFUL BOOKINGS</span>
            <span className={`${styles.metricVal} ${styles.valGreen}`}>{stats.success} (Winner)</span>
          </div>

          <div className={styles.metricCard}>
            <span className={styles.metricLabel}>REJECTED REQUESTS</span>
            <span className={`${styles.metricVal} ${styles.valRed}`}>{stats.rejected}</span>
          </div>
        </div>
      )}

      {/* ── Request Stream ── */}
      {results && (
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader}>Thread Arrival & Execution Log</h3>
          <div className={styles.streamList}>
            {Object.entries(results).map(([threadName, res]) => {
              const isSuccess = res && res.startsWith('SUCCESS')
              return (
                <div
                  key={threadName}
                  className={`${styles.streamItem} ${isSuccess ? styles.streamItemSuccess : styles.streamItemFailed}`}
                >
                  <div className={styles.streamThread}>
                    {isSuccess ? <CheckCircle2 size={16} color="#059669" /> : <XCircle size={16} color="#DC2626" />}
                    <strong>{threadName}</strong>
                  </div>
                  <div className={styles.streamResult}>{res}</div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* ── Technical Summary ── */}
      <div className={styles.summaryCard}>
        <h4 className={styles.summaryTitle}>What This Demonstrates</h4>
        <p className={styles.summaryText}>
          When multiple clients attempt to book the same seat simultaneously, a classic <strong>race condition</strong> occurs in the critical section
          (checking seat availability followed by updating its status). The Java backend guards this with <code>public synchronized String bookSeat(...)</code>.
          This ensures mutual exclusion: exactly one thread acquires the lock, verifies availability, and books the seat. All subsequent threads see the
          seat as already booked and are safely rejected, preventing double-booking.
        </p>
      </div>
    </div>
  )
}
