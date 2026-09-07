import { useState } from 'react'
import { useLocation, useNavigate, Link } from 'react-router-dom'
import { CheckCircle2, ChevronDown, ChevronUp, Server, Shield, Layers, Clock, Database, ArrowRight, Printer } from 'lucide-react'
import styles from './BookingConfirmationPage.module.css'

export default function BookingConfirmationPage() {
  const { state } = useLocation()
  const navigate = useNavigate()
  const [detailsOpen, setDetailsOpen] = useState(false)

  // Fallback defaults if accessed directly
  const booking = state || {
    bookingId: 'BK-DEMO-991',
    movieTitle: 'Kalki 2898 AD',
    cinemaName: 'PVR INOX: Phoenix Palladium, Lower Parel',
    seats: ['A4', 'A5'],
    userName: 'Akash Mehta',
    userEmail: 'akash@example.com',
    totalPrice: 900,
    timestamp: new Date().toISOString(),
    distributedDetails: {
      transport: 'HTTP REST / JSON',
      remoteService: 'Java RMI (BookingService)',
      lockMechanism: 'synchronized Method',
      lamportClock: 12,
      persistence: 'PostgreSQL Database',
      rawResult: 'SUCCESS: seat booked for Akash Mehta'
    }
  }

  const details = booking.distributedDetails || {}

  return (
    <div className={`container ${styles.pageWrap}`}>
      {/* ── Confirmation Card ── */}
      <div className={styles.ticketCard}>
        {/* Card Header */}
        <div className={styles.cardHeader}>
          <div className={styles.statusRow}>
            <CheckCircle2 size={24} color="#059669" />
            <h1 className={styles.title}>Booking Confirmed!</h1>
          </div>
          <span className={styles.bookingId}>ID: {booking.bookingId}</span>
        </div>

        {/* Ticket Details */}
        <div className={styles.ticketBody}>
          <div className={styles.movieSection}>
            <h2 className={styles.movieTitle}>{booking.movieTitle}</h2>
            <div className={styles.cinemaText}>{booking.cinemaName}</div>
          </div>

          <div className={styles.detailsGrid}>
            <div className={styles.detailBlock}>
              <span className={styles.detailLabel}>SEATS</span>
              <span className={styles.seatsValue}>{booking.seats.join(', ')}</span>
            </div>

            <div className={styles.detailBlock}>
              <span className={styles.detailLabel}>CUSTOMER</span>
              <span className={styles.detailValue}>{booking.userName}</span>
            </div>

            <div className={styles.detailBlock}>
              <span className={styles.detailLabel}>SHOWTIME</span>
              <span className={styles.detailValue}>Today • Evening Show</span>
            </div>

            <div className={styles.detailBlock}>
              <span className={styles.detailLabel}>TOTAL PAID</span>
              <span className={styles.priceValue}>₹{booking.totalPrice}</span>
            </div>
          </div>

          {/* Barcode representation */}
          <div className={styles.barcodeWrap}>
            <div className={styles.barcodeLines} />
            <span className={styles.barcodeNumber}>{booking.bookingId}</span>
          </div>
        </div>

        {/* ── Expandable Distributed Systems Processing Details ── */}
        <div className={styles.processingAccordion}>
          <button
            type="button"
            className={styles.accordionHeader}
            onClick={() => setDetailsOpen(!detailsOpen)}
          >
            <div className={styles.accordionTitleWrap}>
              <Server size={15} color="#E51937" />
              <span>View Distributed Processing Details</span>
            </div>
            {detailsOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>

          {detailsOpen && (
            <div className={styles.accordionBody}>
              <div className={styles.techRow}>
                <span className={styles.techLabel}>
                  <Layers size={13} />
                  <span>Transport Protocol</span>
                </span>
                <span className={styles.techValue}>{details.transport || 'HTTP REST'}</span>
              </div>

              <div className={styles.techRow}>
                <span className={styles.techLabel}>
                  <Server size={13} />
                  <span>Remote Service</span>
                </span>
                <span className={styles.techValue}>{details.remoteService || 'Java RMI'}</span>
              </div>

              <div className={styles.techRow}>
                <span className={styles.techLabel}>
                  <Shield size={13} />
                  <span>Concurrency Control</span>
                </span>
                <span className={styles.techValue}>{details.lockMechanism || 'synchronized'}</span>
              </div>

              <div className={styles.techRow}>
                <span className={styles.techLabel}>
                  <Clock size={13} />
                  <span>Logical Timestamp</span>
                </span>
                <span className={styles.techValue}>Lamport: <strong>{details.lamportClock || 12}</strong></span>
              </div>

              <div className={styles.techRow}>
                <span className={styles.techLabel}>
                  <Database size={13} />
                  <span>Persistence</span>
                </span>
                <span className={styles.techValue}>{details.persistence || 'PostgreSQL'}</span>
              </div>

              {details.rawResult && (
                <div className={styles.rawResultBox}>
                  <code>{details.rawResult}</code>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Actions */}
        <div className={styles.cardActions}>
          <button type="button" className="btn btn-outline" onClick={() => window.print()}>
            <Printer size={15} />
            <span>Print Receipt</span>
          </button>

          <Link to="/" className="btn btn-primary">
            <span>Browse More Movies</span>
            <ArrowRight size={15} />
          </Link>
        </div>
      </div>
    </div>
  )
}
