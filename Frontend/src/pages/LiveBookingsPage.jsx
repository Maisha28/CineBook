import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { catalog } from '../api'
import { Ticket, Search, RefreshCw, Database, Server, Calendar, User, CheckCircle2 } from 'lucide-react'

export default function LiveBookingsPage() {
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  useEffect(() => {
    fetchBookings()
  }, [])

  function fetchBookings() {
    setLoading(true)
    catalog.getBookings()
      .then(res => {
        setBookings(res.bookings || [])
        setLoading(false)
      })
      .catch(() => setLoading(false))
  }

  const filtered = bookings.filter(b => {
    const q = search.toLowerCase()
    return (
      (b.userName && b.userName.toLowerCase().includes(q)) ||
      (b.seatNumber && b.seatNumber.toLowerCase().includes(q)) ||
      (b.movieTitle && b.movieTitle.toLowerCase().includes(q)) ||
      (b.serverNode && b.serverNode.toLowerCase().includes(q))
    )
  })

  return (
    <div className="container" style={{ padding: '36px 20px 80px' }}>
      {/* ── Page Header ── */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        flexWrap: 'wrap',
        gap: '20px',
        paddingBottom: '24px',
        borderBottom: '1px solid #E5E7EB',
        marginBottom: '28px'
      }}>
        <div>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            backgroundColor: '#ECFDF5',
            color: '#059669',
            border: '1px solid #A7F3D0',
            padding: '4px 10px',
            borderRadius: '16px',
            fontSize: '11px',
            fontWeight: 700,
            textTransform: 'uppercase',
            marginBottom: '8px'
          }}>
            <Database size={13} />
            <span>Supabase PostgreSQL • Public Bookings Table</span>
          </div>
          <h1 style={{ fontSize: '28px', fontWeight: 800, color: '#111827', marginBottom: '6px' }}>
            Live Booking Records
          </h1>
          <p style={{ fontSize: '14px', color: '#6B7280', maxWidth: '640px' }}>
            Direct real-time query of the <code>bookings</code> table in your Supabase database.
            Includes records created by you and your team across all distributed computing experiments.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <button
            type="button"
            onClick={fetchBookings}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '9px 16px',
              backgroundColor: '#FFFFFF',
              border: '1px solid #D1D5DB',
              borderRadius: '6px',
              fontSize: '13px',
              fontWeight: 600,
              cursor: 'pointer',
              color: '#374151'
            }}
          >
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
            <span>Refresh Table</span>
          </button>
          <Link
            to="/"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '9px 16px',
              backgroundColor: '#E51937',
              color: '#FFFFFF',
              borderRadius: '6px',
              fontSize: '13px',
              fontWeight: 700,
              textDecoration: 'none'
            }}
          >
            <Ticket size={15} />
            <span>Book New Seat</span>
          </Link>
        </div>
      </div>

      {/* ── Search & Filter Bar ── */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '20px',
        gap: '16px',
        flexWrap: 'wrap'
      }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          backgroundColor: '#FFFFFF',
          border: '1px solid #D1D5DB',
          borderRadius: '8px',
          padding: '8px 14px',
          width: '100%',
          maxWidth: '380px'
        }}>
          <Search size={16} color="#9CA3AF" />
          <input
            type="text"
            placeholder="Search by customer name, seat, or server node..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ border: 'none', outline: 'none', width: '100%', fontSize: '13px' }}
          />
        </div>

        <div style={{ fontSize: '13px', color: '#6B7280', fontWeight: 600 }}>
          Total Persisted Bookings: <span style={{ color: '#111827', fontWeight: 800 }}>{filtered.length}</span>
        </div>
      </div>

      {/* ── Bookings Grid / Table ── */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div className="spinner" />
          <p style={{ marginTop: '12px', color: '#6B7280', fontSize: '14px' }}>Loading bookings from Supabase...</p>
        </div>
      ) : filtered.length === 0 ? (
        <div style={{
          textAlign: 'center',
          padding: '60px 20px',
          backgroundColor: '#FFFFFF',
          borderRadius: '10px',
          border: '1px solid #E5E7EB'
        }}>
          <Ticket size={40} color="#9CA3AF" style={{ margin: '0 auto 12px' }} />
          <h3 style={{ fontSize: '16px', fontWeight: 700, color: '#111827', marginBottom: '6px' }}>No Bookings Match Search</h3>
          <p style={{ color: '#6B7280', fontSize: '13px' }}>Try adjusting your search filter or book a new seat from the home page.</p>
        </div>
      ) : (
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: '10px',
          border: '1px solid #E5E7EB',
          overflow: 'hidden',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
        }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
            <thead>
              <tr style={{ backgroundColor: '#F9FAFB', borderBottom: '1px solid #E5E7EB', color: '#6B7280', fontWeight: 700 }}>
                <th style={{ padding: '12px 16px' }}>Customer Name</th>
                <th style={{ padding: '12px 16px' }}>Seat Number</th>
                <th style={{ padding: '12px 16px' }}>Movie</th>
                <th style={{ padding: '12px 16px' }}>Server Node</th>
                <th style={{ padding: '12px 16px' }}>Booked Timestamp (UTC)</th>
                <th style={{ padding: '12px 16px' }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(b => (
                <tr key={b.id} style={{ borderBottom: '1px solid #F3F4F6', transition: 'background-color 0.15s' }}>
                  <td style={{ padding: '12px 16px', fontWeight: 700, color: '#111827' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <div style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '50%',
                        backgroundColor: '#F3F4F6',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#4B5563',
                        fontWeight: 700,
                        fontSize: '12px'
                      }}>
                        {b.userName ? b.userName.charAt(0).toUpperCase() : 'U'}
                      </div>
                      <span>{b.userName}</span>
                    </div>
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{
                      backgroundColor: '#FEE2E2',
                      color: '#DC2626',
                      fontWeight: 800,
                      padding: '3px 8px',
                      borderRadius: '4px',
                      fontFamily: 'monospace'
                    }}>
                      {b.seatNumber || 'Seat'}
                    </span>
                  </td>
                  <td style={{ padding: '12px 16px', fontWeight: 600, color: '#374151' }}>
                    {b.movieTitle || 'The Signal'}
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{
                      backgroundColor: '#EEF2FF',
                      color: '#4F46E5',
                      padding: '3px 8px',
                      borderRadius: '4px',
                      fontSize: '11.5px',
                      fontWeight: 700,
                      fontFamily: 'monospace'
                    }}>
                      {b.serverNode || 'node1'}
                    </span>
                  </td>
                  <td style={{ padding: '12px 16px', color: '#6B7280', fontSize: '12px', fontFamily: 'monospace' }}>
                    {b.bookedAt ? new Date(b.bookedAt).toLocaleString() : 'N/A'}
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      color: '#059669',
                      fontWeight: 700,
                      fontSize: '12px'
                    }}>
                      <CheckCircle2 size={13} />
                      <span>Confirmed</span>
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
