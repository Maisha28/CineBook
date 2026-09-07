import { useState, useEffect } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { exp1, parseSeat } from '../api'
import { useToast } from '../components/Toast'
import { ChevronLeft, Monitor, Shield, Check, Info } from 'lucide-react'
import styles from './SeatBookingPage.module.css'

export default function SeatBookingPage() {
  const { showId } = useParams()
  const [searchParams] = useSearchParams()
  const movieTitle = searchParams.get('movie') || 'Selected Movie'
  const cinemaName = searchParams.get('cinema') || 'Selected Cinema'

  const navigate = useNavigate()
  const toast = useToast()

  const [seats, setSeats] = useState([])
  const [selectedSeats, setSelectedSeats] = useState([]) // array of { id, label, tier, price }
  const [userName, setUserName] = useState('')
  const [userEmail, setUserEmail] = useState('')
  const [loading, setLoading] = useState(true)
  const [bookingLoading, setBookingLoading] = useState(false)
  const [source, setSource] = useState('')

  useEffect(() => {
    setLoading(true)
    exp1.getSeats(showId)
      .then(data => {
        setSeats(data.seats || [])
        setSource(data.source || 'rmi')
        setLoading(false)
      })
      .catch(err => {
        toast('Unable to fetch seats from remote service', 'error')
        setLoading(false)
      })
  }, [showId, toast])

  // Group seats by row (A, B, C, D, E, F)
  const rowsMap = {}
  seats.forEach(raw => {
    const { label, id } = parseSeat(raw)
    const row = label.charAt(0)
    if (!rowsMap[row]) rowsMap[row] = []

    let tier = 'Classic'
    let price = 220
    if (row === 'A') { tier = 'Recliner'; price = 450; }
    else if (row === 'B' || row === 'C') { tier = 'Prime'; price = 320; }

    rowsMap[row].push({ id, label, tier, price })
  })

  function toggleSeat(seat) {
    const isSelected = selectedSeats.some(s => s.id === seat.id)
    if (isSelected) {
      setSelectedSeats(prev => prev.filter(s => s.id !== seat.id))
    } else {
      if (selectedSeats.length >= 6) {
        toast('Maximum 6 seats per transaction', 'warning')
        return
      }
      setSelectedSeats(prev => [...prev, seat])
    }
  }

  const totalPrice = selectedSeats.reduce((sum, s) => sum + s.price, 0)

  async function handleBooking() {
    if (selectedSeats.length === 0) {
      toast('Please select at least one seat', 'warning')
      return
    }
    if (!userName.trim()) {
      toast('Please enter your full name', 'warning')
      return
    }

    setBookingLoading(true)
    try {
      // Book the selected seats
      const bookedIds = []
      let lastResult = null

      for (const seat of selectedSeats) {
        const res = await exp1.bookSeat(seat.id, userName.trim())
        lastResult = res
        if (res.success) {
          bookedIds.push(seat.id)
        }
      }

      if (bookedIds.length > 0) {
        toast(`Successfully booked ${bookedIds.length} seat(s)!`, 'success')
        // Navigate to confirmation page
        navigate('/booking/confirmation', {
          state: {
            showId,
            movieTitle,
            cinemaName,
            seats: selectedSeats.map(s => s.label),
            userName: userName.trim(),
            userEmail: userEmail.trim(),
            totalPrice,
            bookingId: 'BK-' + Math.random().toString(36).substring(2, 9).toUpperCase(),
            timestamp: new Date().toISOString(),
            distributedDetails: {
              transport: 'HTTP REST / JSON',
              remoteService: 'Java RMI (BookingService)',
              lockMechanism: 'synchronized Method',
              lamportClock: 14,
              persistence: source === 'rmi' ? 'PostgreSQL (Supabase)' : 'Local Resilient State',
              rawResult: lastResult?.result
            }
          }
        })
      } else {
        toast(lastResult?.error || 'Booking failed: Seat unavailable', 'error')
      }
    } catch (e) {
      toast(e.message || 'Error communicating with booking server', 'error')
    }
    setBookingLoading(false)
  }

  return (
    <div className={styles.pageWrap}>
      {/* ── Top Bar with Movie & Cinema Details ── */}
      <div className={styles.showBar}>
        <div className={`container ${styles.showBarInner}`}>
          <button type="button" className={styles.backBtn} onClick={() => navigate(-1)}>
            <ChevronLeft size={16} />
            <span>Change Show</span>
          </button>
          <div className={styles.showDetails}>
            <h2 className={styles.showMovie}>{movieTitle}</h2>
            <div className={styles.showCinema}>{cinemaName}</div>
          </div>
          <div className={styles.backendNotice}>
            <Shield size={13} color="#059669" />
            <span>Service: <strong>RMI Active</strong></span>
          </div>
        </div>
      </div>

      <div className="container" style={{ paddingTop: 28 }}>
        {/* ── Screen Curve ── */}
        <div className={styles.screenWrapper}>
          <div className={styles.screenCurve} />
          <div className={styles.screenText}>
            <Monitor size={14} />
            <span>ALL EYES THIS WAY • SCREEN</span>
          </div>
        </div>

        {/* ── Seat Grid ── */}
        {loading ? (
          <div className={styles.loadingBox}>
            <div className="spinner" />
            <p>Fetching seat layout via Java RMI...</p>
          </div>
        ) : (
          <div className={styles.seatLayout}>
            {Object.entries(rowsMap).map(([rowLetter, rowSeats]) => (
              <div key={rowLetter} className={styles.seatRow}>
                <span className={styles.rowLabel}>{rowLetter}</span>
                <div className={styles.rowSeats}>
                  {rowSeats.map(seat => {
                    const isSelected = selectedSeats.some(s => s.id === seat.id)
                    return (
                      <button
                        key={seat.id}
                        type="button"
                        className={`${styles.seatBtn} ${isSelected ? styles.seatSelected : styles.seatAvailable}`}
                        onClick={() => toggleSeat(seat)}
                        title={`${seat.label} • ${seat.tier} • ₹${seat.price}`}
                      >
                        {seat.label.substring(1)}
                      </button>
                    )
                  })}
                </div>
                <span className={styles.rowLabel}>{rowLetter}</span>
              </div>
            ))}
          </div>
        )}

        {/* ── Legend ── */}
        <div className={styles.legendRow}>
          <div className={styles.legendItem}>
            <div className={`${styles.legendBox} ${styles.availBox}`} />
            <span>Available</span>
          </div>
          <div className={styles.legendItem}>
            <div className={`${styles.legendBox} ${styles.selectedBox}`} />
            <span>Selected</span>
          </div>
          <div className={styles.legendItem}>
            <div className={`${styles.legendBox} ${styles.bookedBox}`} />
            <span>Booked</span>
          </div>
        </div>

        {/* ── Customer Details & Summary ── */}
        <div className={styles.bookingFormCard}>
          <h3 className={styles.formTitle}>Contact & Billing Information</h3>
          <div className={styles.formRow}>
            <div className="field" style={{ flex: 1 }}>
              <label>Full Name *</label>
              <input
                type="text"
                className="input"
                placeholder="e.g. Rahul Sharma"
                value={userName}
                onChange={e => setUserName(e.target.value)}
              />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <label>Email Address</label>
              <input
                type="email"
                className="input"
                placeholder="e.g. rahul@example.com"
                value={userEmail}
                onChange={e => setUserEmail(e.target.value)}
              />
            </div>
          </div>
        </div>
      </div>

      {/* ── Sticky Bottom Checkout Bar ── */}
      <div className={styles.checkoutBar}>
        <div className={`container ${styles.checkoutInner}`}>
          <div className={styles.summaryLeft}>
            <span className={styles.selectedCount}>
              {selectedSeats.length > 0 ? (
                <>Selected: <strong>{selectedSeats.map(s => s.label).join(', ')}</strong> ({selectedSeats.length} Seats)</>
              ) : (
                'No seats selected yet'
              )}
            </span>
            <div className={styles.priceTag}>
              Total Amount: <strong>₹{totalPrice}</strong>
            </div>
          </div>

          <button
            type="button"
            className="btn btn-primary btn-lg"
            onClick={handleBooking}
            disabled={selectedSeats.length === 0 || bookingLoading}
          >
            {bookingLoading ? (
              <>
                <span className="spinner" />
                <span>Invoking RMI Service...</span>
              </>
            ) : (
              <>
                <Check size={16} />
                <span>Confirm & Book Seats</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
