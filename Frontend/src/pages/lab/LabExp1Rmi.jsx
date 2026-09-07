import { useState } from 'react'
import { exp1, parseSeat } from '../../api'
import { useToast } from '../../components/Toast'
import { Server, ArrowRight, Play, CheckCircle2, Shield, RefreshCw } from 'lucide-react'
import styles from './Lab.module.css'

const SAMPLE_SHOWS = [
  { id: '3fa85f64-5717-4562-b3fc-2c963f66afa6', label: 'Kalki 2898 AD (10:30 AM • PVR Palladium)' },
  { id: 's-kalki-eve', label: 'Kalki 2898 AD (06:45 PM • INOX R-City)' },
  { id: 's-stree-morn', label: 'Stree 2 (11:15 AM • INOX R-City)' },
  { id: 's-jawan-noon', label: 'Jawan (01:30 PM • PVR Palladium)' }
]

export default function LabExp1Rmi() {
  const toast = useToast()
  const [selectedShow, setSelectedShow] = useState(SAMPLE_SHOWS[0].id)
  const [seats, setSeats] = useState([])
  const [targetSeat, setTargetSeat] = useState('')
  const [userName, setUserName] = useState('Prof. Evaluator')
  const [fetching, setFetching] = useState(false)
  const [booking, setBooking] = useState(false)
  const [bookingResult, setBookingResult] = useState(null)
  const [source, setSource] = useState('')

  async function fetchSeats() {
    setFetching(true)
    setBookingResult(null)
    try {
      const data = await exp1.getSeats(selectedShow)
      setSeats(data.seats || [])
      setSource(data.source || 'rmi')
      toast(`Fetched ${data.seats?.length || 0} seats via RMI`, 'success')
      if (data.seats?.length > 0) {
        const first = parseSeat(data.seats[0])
        setTargetSeat(first.id)
      }
    } catch (e) {
      toast(e.message || 'Error communicating with RMI', 'error')
    }
    setFetching(false)
  }

  async function handleBook() {
    if (!targetSeat || !userName.trim()) {
      toast('Seat and Name are required', 'warning')
      return
    }
    setBooking(true)
    try {
      const data = await exp1.bookSeat(targetSeat, userName.trim())
      setBookingResult(data)
      if (data.success) {
        toast('RMI bookSeat call succeeded!', 'success')
        // Refresh seats
        fetchSeats()
      } else {
        toast(data.result || 'Booking rejected by server', 'error')
      }
    } catch (e) {
      toast(e.message || 'RMI error', 'error')
    }
    setBooking(false)
  }

  return (
    <div className={styles.expContainer}>
      {/* ── Header & Status ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 1: Java RMI Remote Booking Service</h2>
          <p className={styles.expSubtitle}>
            Direct invocation of remote methods on the Java RMI backend over port 1099.
          </p>
        </div>

        <div className={styles.statusGroup}>
          <span className={styles.statusPill}>
            <span className={styles.statusDotGreen} />
            <span>RMI Registry: <strong>1099</strong></span>
          </span>
          <span className={styles.statusPill}>
            <span className={styles.statusDotGreen} />
            <span>Service: <strong>BookingService</strong></span>
          </span>
        </div>
      </div>

      {/* ── Request Flow Diagram ── */}
      <div className={styles.flowCard}>
        <div className={styles.flowHeader}>Compact Architecture Flow</div>
        <div className={styles.flowNodes}>
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>Browser</span>
            <span className={styles.flowSub}>React HTTP Client</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>HTTP Bridge</span>
            <span className={styles.flowSub}>Port 8080</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>RMI Registry</span>
            <span className={styles.flowSub}>Port 1099</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={`${styles.flowNode} ${styles.flowNodeActive}`}>
            <span className={styles.flowLabel}>BookingServiceImpl</span>
            <span className={styles.flowSub}>UnicastRemoteObject</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>PostgreSQL</span>
            <span className={styles.flowSub}>seats & bookings table</span>
          </div>
        </div>
      </div>

      {/* ── Test Operations Grid ── */}
      <div className={styles.twoColGrid}>
        {/* Step 1: Query Seats */}
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader}>1. Query Available Seats</h3>
          <div className="field">
            <label>Select Show</label>
            <select
              className="input"
              value={selectedShow}
              onChange={e => setSelectedShow(e.target.value)}
            >
              {SAMPLE_SHOWS.map(s => (
                <option key={s.id} value={s.id}>{s.label}</option>
              ))}
            </select>
          </div>

          <div className={styles.actionRow}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={fetchSeats}
              disabled={fetching}
            >
              {fetching ? <span className="spinner" /> : <Play size={14} />}
              <span>Execute getAvailableSeats(showId)</span>
            </button>
          </div>

          {/* Seat Results */}
          {seats.length > 0 && (
            <div className={styles.seatsResultWrap}>
              <div className={styles.resultLabel}>
                <span>Remote Result: {seats.length} Available Seats</span>
                <span className="badge badge-neutral">Transport: {source}</span>
              </div>
              <div className={styles.chipsWrap}>
                {seats.map(raw => {
                  const s = parseSeat(raw)
                  const isTarget = targetSeat === s.id
                  return (
                    <button
                      key={s.id}
                      type="button"
                      className={`${styles.seatChip} ${isTarget ? styles.seatChipActive : ''}`}
                      onClick={() => setTargetSeat(s.id)}
                    >
                      {s.label}
                    </button>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* Step 2: Book Seat Test */}
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader}>2. Remote Booking Test</h3>
          <div className="field">
            <label>Target Seat ID</label>
            <input
              type="text"
              className="input"
              placeholder="Select a seat above or enter UUID"
              value={targetSeat}
              onChange={e => setTargetSeat(e.target.value)}
            />
          </div>

          <div className="field">
            <label>Customer Name</label>
            <input
              type="text"
              className="input"
              value={userName}
              onChange={e => setUserName(e.target.value)}
            />
          </div>

          <div className={styles.actionRow}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleBook}
              disabled={!targetSeat || booking}
            >
              {booking ? <span className="spinner" /> : <CheckCircle2 size={14} />}
              <span>Execute bookSeat(seatId, name)</span>
            </button>
          </div>

          {bookingResult && (
            <div className={`${styles.resultBanner} ${bookingResult.success ? styles.bannerOk : styles.bannerErr}`}>
              <strong>Server Response:</strong>
              <div>{bookingResult.result}</div>
            </div>
          )}
        </div>
      </div>

      {/* ── Technical Summary ── */}
      <div className={styles.summaryCard}>
        <h4 className={styles.summaryTitle}>What This Demonstrates</h4>
        <p className={styles.summaryText}>
          In Experiment 1, the client communicates with the server using Java's built-in <strong>Remote Method Invocation (RMI)</strong> mechanism.
          The <code>BookingService</code> remote interface defines operations that can be called transparently across the network, while
          <code>UnicastRemoteObject</code> exports the implementation on port 1099. The method handles database transactions with atomic commits.
        </p>
      </div>
    </div>
  )
}
