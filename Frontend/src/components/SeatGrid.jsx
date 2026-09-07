import { parseSeat } from '../api'
import styles from './SeatGrid.module.css'

/**
 * @param {string[]} seats    - Raw seat strings from Java: "A1 (id=<uuid>)"
 * @param {string[]} booked   - Array of seat IDs already booked this session
 * @param {string}   selected - Currently selected seat ID
 * @param {Function} onSelect - (seatId, label) => void
 */
export default function SeatGrid({ seats = [], booked = [], selected, onSelect }) {
  if (seats.length === 0) {
    return <p className={styles.empty}>No available seats. Check the Show ID or start the Java server.</p>
  }

  return (
    <div>
      <div className={styles.legend}>
        <span className={styles.dot} style={{ background: 'var(--seat-avail)' }} /> Available
        <span className={styles.dot} style={{ background: 'var(--red)' }} /> Selected
        <span className={styles.dot} style={{ background: 'var(--seat-booked)' }} /> Booked
      </div>
      <div className={styles.grid}>
        {seats.map(raw => {
          const { label, id } = parseSeat(raw)
          const isBooked   = booked.includes(id)
          const isSelected = selected === id
          const cls = isBooked ? styles.booked : isSelected ? styles.selected : styles.available
          return (
            <button
              key={id}
              className={`${styles.seat} ${cls}`}
              disabled={isBooked}
              onClick={() => !isBooked && onSelect(id, label)}
              title={isBooked ? 'Already booked' : id}
            >
              {label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
