import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { catalog } from '../api'
import { useToast } from '../components/Toast'
import { ChevronLeft, Database, CheckCircle2, XCircle, Ticket, Server, User, AlertCircle, RefreshCw } from 'lucide-react'

export default function SeatBookingPage() {
  const { showId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()

  const [show, setShow] = useState(null)
  const [seats, setSeats] = useState([])
  const [selectedSeat, setSelectedSeat] = useState(null)
  const [userName, setUserName] = useState('')
  const [serverNode, setServerNode] = useState('node1')
  const [loading, setLoading] = useState(true)
  const [bookingLoading, setBookingLoading] = useState(false)
  const [confirmedBooking, setConfirmedBooking] = useState(null)

  useEffect(() => {
    fetchShowAndSeats()
  }, [showId])

  function fetchShowAndSeats() {
    setLoading(true)
    Promise.all([
      catalog.getShows(),
      catalog.getSeats(showId)
    ])
      .then(([showsRes, seatsRes]) => {
        const found = (showsRes.shows || []).find(s => s.id === showId) || {
          movieTitle: 'The Signal',
          cinemaName: 'CineBook Downtown',
          format: 'IMAX 3D',
          price: 380
        }
        setShow(found)
        setSeats(seatsRes.seats || [])
        setLoading(false)
      })
      .catch(() => {
        setLoading(false)
      })
  }

  function handleSelectSeat(seat) {
    if (seat.status === 'booked') {
      toast(`Seat ${seat.seatNumber} is already booked in Supabase!`, 'warning')
      return
    }
    if (selectedSeat && selectedSeat.id === seat.id) {
      setSelectedSeat(null)
    } else {
      setSelectedSeat(seat)
    }
  }

  async function handleConfirmBooking() {
    if (!selectedSeat) {
      toast('Please click an available seat on the map', 'warning')
      return
    }
    if (!userName.trim()) {
      toast('Please enter your full name', 'warning')
      return
    }

    setBookingLoading(true)
    try {
      const res = await catalog.bookSeat({
        seatId: selectedSeat.id,
        userName: userName.trim(),
        serverNode
      })

      if (res.success) {
        setConfirmedBooking({
          bookingId: res.bookingId,
          seatNumber: selectedSeat.seatNumber,
          userName: userName.trim(),
          serverNode,
          movieTitle: show?.movieTitle || 'The Signal',
          cinemaName: show?.cinemaName || 'CineBook Downtown'
        })
        toast(`Seat ${selectedSeat.seatNumber} successfully booked in Supabase!`, 'success')
        setSelectedSeat(null)
        // Refresh seats from database
        fetchShowAndSeats()
      } else {
        toast(res.message || 'Booking failed: Seat unavailable', 'error')
        fetchShowAndSeats()
      }
    } catch (e) {
      toast(e.message || 'Database booking transaction failed', 'error')
    } finally {
      setBookingLoading(false)
    }
  }

  // Split seats into rows
  const rows = {}
  seats.forEach(s => {
    const rowLetter = s.seatNumber ? s.seatNumber.charAt(0) : 'A'
    if (!rows[rowLetter]) rows[rowLetter] = []
    rows[rowLetter].push(s)
  })

  return (
    <div style={{ backgroundColor: '#0F172A', minHeight: '100vh', color: '#FFFFFF', paddingBottom: '80px' }}>
      {/* ── Sub Header ── */}
      <div style={{
        backgroundColor: '#1E293B',
        borderBottom: '1px solid #334155',
        padding: '14px 0'
      }}>
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <button
              type="button"
              onClick={() => navigate('/')}
              style={{
                background: 'none',
                border: '1px solid #475569',
                color: '#FFFFFF',
                borderRadius: '6px',
                padding: '6px 10px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '12.5px'
              }}
            >
              <ChevronLeft size={16} />
              <span>Back</span>
            </button>

            <div>
              <h2 style={{ fontSize: '18px', fontWeight: 800, margin: 0 }}>
                {show?.movieTitle || 'The Signal'}
              </h2>
              <div style={{ fontSize: '12px', color: '#94A3B8' }}>
                {show?.cinemaName || 'CineBook Downtown'} • {show?.format || 'IMAX 3D'}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              borderRadius: '12px',
              fontSize: '11px',
              fontWeight: 700,
              backgroundColor: 'rgba(16, 185, 129, 0.15)',
              color: '#34D399',
              border: '1px solid rgba(16, 185, 129, 0.3)'
            }}>
              <Database size={12} />
              <span>Supabase Live Seat Grid</span>
            </span>

            <button
              type="button"
              onClick={fetchShowAndSeats}
              style={{
                background: '#334155',
                border: 'none',
                color: '#FFFFFF',
                borderRadius: '6px',
                padding: '6px 10px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '12px'
              }}
            >
              <RefreshCw size={12} className={loading ? 'spin' : ''} />
              <span>Sync</span>
            </button>
          </div>
        </div>
      </div>

      <div className="container" style={{ paddingTop: '36px' }}>
        {/* ── Curved Screen Graphic ── */}
        <div style={{ textAlign: 'center', marginBottom: '40px', maxWidth: '680px', margin: '0 auto 40px' }}>
          <div style={{
            height: '6px',
            borderRadius: '50%',
            backgroundColor: '#38BDF8',
            boxShadow: '0 0 20px #38BDF8',
            marginBottom: '8px'
          }} />
          <div style={{ fontSize: '11px', color: '#64748B', letterSpacing: '0.1em', textTransform: 'uppercase', fontWeight: 700 }}>
            All Eyes This Way — Cinema Screen
          </div>
        </div>

        {/* ── Seat Map Grid ── */}
        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div className="spinner" />
            <p style={{ marginTop: '12px', color: '#94A3B8', fontSize: '14px' }}>Loading real seats from Supabase...</p>
          </div>
        ) : (
          <div style={{
            backgroundColor: '#1E293B',
            borderRadius: '12px',
            border: '1px solid #334155',
            padding: '30px 20px',
            maxWidth: '720px',
            margin: '0 auto 30px',
            boxShadow: '0 10px 25px rgba(0,0,0,0.4)'
          }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', alignItems: 'center' }}>
              {Object.keys(rows).sort().map(r => (
                <div key={r} style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ width: '20px', fontSize: '13px', fontWeight: 800, color: '#94A3B8', textAlign: 'center' }}>
                    {r}
                  </div>
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', justifyContent: 'center' }}>
                    {rows[r].map(s => {
                      const isBooked = s.status === 'booked'
                      const isSelected = selectedSeat && selectedSeat.id === s.id

                      let bg = '#334155'
                      let border = '#475569'
                      let color = '#FFFFFF'

                      if (isBooked) {
                        bg = '#1E293B'
                        border = '#334155'
                        color = '#64748B'
                      } else if (isSelected) {
                        bg = '#E51937'
                        border = '#E51937'
                        color = '#FFFFFF'
                      } else {
                        bg = 'rgba(16, 185, 129, 0.15)'
                        border = '#10B981'
                        color = '#34D399'
                      }

                      return (
                        <button
                          key={s.id}
                          type="button"
                          disabled={isBooked}
                          onClick={() => handleSelectSeat(s)}
                          style={{
                            width: '38px',
                            height: '34px',
                            borderRadius: '6px',
                            border: `1.5px solid ${border}`,
                            backgroundColor: bg,
                            color,
                            fontWeight: 700,
                            fontSize: '11px',
                            cursor: isBooked ? 'not-allowed' : 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            transition: 'all 0.15s',
                            boxShadow: isSelected ? '0 0 10px rgba(229, 25, 55, 0.5)' : 'none',
                            textDecoration: isBooked ? 'line-through' : 'none'
                          }}
                          title={`Seat ${s.seatNumber} (${isBooked ? 'Booked' : 'Available - ₹' + s.price})`}
                        >
                          {s.seatNumber}
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>

            {/* Seat Legend */}
            <div style={{
              display: 'flex',
              justifyContent: 'center',
              gap: '24px',
              borderTop: '1px solid #334155',
              paddingTop: '20px',
              marginTop: '24px',
              fontSize: '12px',
              color: '#94A3B8'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <div style={{ width: '16px', height: '16px', borderRadius: '4px', border: '1.5px solid #10B981', backgroundColor: 'rgba(16, 185, 129, 0.15)' }} />
                <span>Available</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <div style={{ width: '16px', height: '16px', borderRadius: '4px', backgroundColor: '#E51937' }} />
                <span>Selected</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <div style={{ width: '16px', height: '16px', borderRadius: '4px', backgroundColor: '#1E293B', border: '1.5px solid #334155' }} />
                <span>Booked (Supabase)</span>
              </div>
            </div>
          </div>
        )}

        {/* ── Booking Confirmation Drawer ── */}
        <div style={{
          backgroundColor: '#1E293B',
          borderRadius: '12px',
          border: '1px solid #334155',
          padding: '24px',
          maxWidth: '720px',
          margin: '0 auto'
        }}>
          <h3 style={{ fontSize: '16px', fontWeight: 800, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Ticket size={18} color="#E51937" />
            <span>Complete Reservation Details</span>
          </h3>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '20px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#94A3B8', marginBottom: '6px' }}>
                Customer Name (Stored in Database)
              </label>
              <input
                type="text"
                placeholder="e.g. Maisha"
                value={userName}
                onChange={e => setUserName(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: '6px',
                  backgroundColor: '#0F172A',
                  border: '1px solid #475569',
                  color: '#FFFFFF',
                  fontSize: '13px',
                  outline: 'none'
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#94A3B8', marginBottom: '6px' }}>
                Backend Routing Server Node
              </label>
              <select
                value={serverNode}
                onChange={e => setServerNode(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: '6px',
                  backgroundColor: '#0F172A',
                  border: '1px solid #475569',
                  color: '#FFFFFF',
                  fontSize: '13px',
                  outline: 'none'
                }}
              >
                <option value="node1">node1 (Primary Server)</option>
                <option value="node2">node2 (Backup Server)</option>
                <option value="Server-1">Server-1 (Load Balancer Node 1)</option>
                <option value="Server-2">Server-2 (Load Balancer Node 2)</option>
                <option value="Server-3">Server-3 (Load Balancer Node 3)</option>
              </select>
            </div>
          </div>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            backgroundColor: '#0F172A',
            padding: '14px 18px',
            borderRadius: '8px',
            marginBottom: '20px'
          }}>
            <div>
              <div style={{ fontSize: '12px', color: '#94A3B8' }}>Selected Seat:</div>
              <div style={{ fontSize: '16px', fontWeight: 800, color: selectedSeat ? '#34D399' : '#64748B' }}>
                {selectedSeat ? `${selectedSeat.seatNumber} (${selectedSeat.tier})` : 'None Selected'}
              </div>
            </div>

            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '12px', color: '#94A3B8' }}>Total Payable:</div>
              <div style={{ fontSize: '20px', fontWeight: 900, color: '#E51937' }}>
                ₹{selectedSeat ? selectedSeat.price : 0}
              </div>
            </div>
          </div>

          <button
            type="button"
            disabled={!selectedSeat || bookingLoading}
            onClick={handleConfirmBooking}
            style={{
              width: '100%',
              padding: '12px',
              backgroundColor: !selectedSeat ? '#475569' : '#E51937',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: '8px',
              fontWeight: 800,
              fontSize: '14px',
              cursor: !selectedSeat || bookingLoading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
              transition: 'background-color 0.15s'
            }}
          >
            <CheckCircle2 size={16} />
            <span>{bookingLoading ? 'Writing to Supabase Database...' : 'Confirm & Commit Booking in Supabase'}</span>
          </button>
        </div>

        {/* ── Booking Receipt Modal ── */}
        {confirmedBooking && (
          <div style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0,0,0,0.7)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 100,
            padding: '20px'
          }}>
            <div style={{
              backgroundColor: '#1E293B',
              borderRadius: '12px',
              border: '1px solid #334155',
              padding: '28px',
              maxWidth: '460px',
              width: '100%',
              boxShadow: '0 20px 40px rgba(0,0,0,0.5)',
              textAlign: 'center'
            }}>
              <div style={{
                width: '54px',
                height: '54px',
                borderRadius: '50%',
                backgroundColor: 'rgba(16, 185, 129, 0.15)',
                color: '#34D399',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px'
              }}>
                <CheckCircle2 size={32} />
              </div>

              <h3 style={{ fontSize: '20px', fontWeight: 800, marginBottom: '6px' }}>
                Booking Confirmed!
              </h3>
              <p style={{ fontSize: '13px', color: '#94A3B8', marginBottom: '20px' }}>
                Record successfully written to Supabase PostgreSQL table <code>bookings</code>.
              </p>

              <div style={{
                backgroundColor: '#0F172A',
                borderRadius: '8px',
                padding: '16px',
                textAlign: 'left',
                fontSize: '12.5px',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
                marginBottom: '20px'
              }}>
                <div><span style={{ color: '#94A3B8' }}>Customer:</span> <strong>{confirmedBooking.userName}</strong></div>
                <div><span style={{ color: '#94A3B8' }}>Movie:</span> <strong>{confirmedBooking.movieTitle}</strong></div>
                <div><span style={{ color: '#94A3B8' }}>Seat:</span> <strong style={{ color: '#E51937' }}>{confirmedBooking.seatNumber}</strong></div>
                <div><span style={{ color: '#94A3B8' }}>Server Node:</span> <span style={{ fontFamily: 'monospace', color: '#38BDF8' }}>{confirmedBooking.serverNode}</span></div>
                <div><span style={{ color: '#94A3B8' }}>Booking UUID:</span> <span style={{ fontFamily: 'monospace', fontSize: '11px', color: '#9CA3AF' }}>{confirmedBooking.bookingId}</span></div>
              </div>

              <div style={{ display: 'flex', gap: '10px' }}>
                <button
                  type="button"
                  onClick={() => setConfirmedBooking(null)}
                  style={{
                    flex: 1,
                    padding: '10px',
                    backgroundColor: '#334155',
                    color: '#FFFFFF',
                    border: 'none',
                    borderRadius: '6px',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  Book Another
                </button>
                <Link
                  to="/bookings"
                  style={{
                    flex: 1,
                    padding: '10px',
                    backgroundColor: '#E51937',
                    color: '#FFFFFF',
                    borderRadius: '6px',
                    fontWeight: 700,
                    textDecoration: 'none',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}
                >
                  View in Supabase →
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
