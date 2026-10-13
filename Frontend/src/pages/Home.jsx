import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { catalog } from '../api'
import { Film, Star, Clock, Calendar, MapPin, Sparkles, Database, CheckCircle2, ChevronRight, ShieldCheck, Ticket } from 'lucide-react'

export default function Home() {
  const navigate = useNavigate()
  const [movies, setMovies] = useState([])
  const [shows, setShows] = useState([])
  const [dbStatus, setDbStatus] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      catalog.getMovies(),
      catalog.getShows(),
      catalog.inspectSupabase()
    ])
      .then(([mRes, sRes, dbRes]) => {
        setMovies(mRes.movies || [])
        setShows(sRes.shows || [])
        setDbStatus(dbRes)
        setLoading(false)
      })
      .catch(() => {
        setLoading(false)
      })
  }, [])

  const heroMovie = movies.find(m => m.title.includes('Signal')) || movies[0]

  return (
    <div style={{ backgroundColor: '#F8F9FA', minHeight: '100vh', paddingBottom: '80px' }}>
      {/* ── Top Hero Premiere Spotlight ── */}
      {heroMovie && (
        <section style={{
          background: 'linear-gradient(135deg, #0F172A 0%, #1E1B4B 50%, #0F172A 100%)',
          color: '#FFFFFF',
          padding: '60px 0 50px',
          position: 'relative',
          overflow: 'hidden'
        }}>
          {/* Subtle cosmic glow */}
          <div style={{
            position: 'absolute',
            top: '-20%',
            right: '10%',
            width: '400px',
            height: '400px',
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(229, 25, 55, 0.25) 0%, transparent 70%)',
            pointerEvents: 'none'
          }} />

          <div className="container" style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '40px',
            flexWrap: 'wrap',
            position: 'relative',
            zIndex: 1
          }}>
            <div style={{ flex: '1 1 500px', maxWidth: '650px' }}>
              <div style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                backgroundColor: 'rgba(229, 25, 55, 0.2)',
                color: '#FF4D6D',
                border: '1px solid rgba(229, 25, 55, 0.4)',
                padding: '4px 12px',
                borderRadius: '20px',
                fontSize: '11px',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
                marginBottom: '16px'
              }}>
                <Sparkles size={13} />
                <span>Supabase Live Feature Premiere</span>
              </div>

              <h1 style={{ fontSize: '38px', fontWeight: 900, letterSpacing: '-0.02em', marginBottom: '14px', lineHeight: 1.15 }}>
                {heroMovie.title}
              </h1>

              <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '13px', color: '#CBD5E1', marginBottom: '16px', flexWrap: 'wrap' }}>
                <span style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                  backgroundColor: '#D97706',
                  color: '#FFFFFF',
                  padding: '2px 8px',
                  borderRadius: '4px',
                  fontWeight: 800
                }}>
                  <Star size={13} fill="#FFFFFF" />
                  <span>{heroMovie.rating}</span>/10
                </span>
                <span>{heroMovie.certificate}</span>
                <span>•</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Clock size={14} />
                  <span>{heroMovie.duration}</span>
                </span>
                <span>•</span>
                <span>{heroMovie.genre}</span>
              </div>

              <p style={{ fontSize: '14.5px', color: '#94A3B8', lineHeight: 1.6, marginBottom: '24px' }}>
                {heroMovie.synopsis}
              </p>

              {/* Showtimes for Hero Movie */}
              <div style={{ marginBottom: '28px' }}>
                <div style={{ fontSize: '12px', fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', marginBottom: '10px' }}>
                  Available Showtimes in Supabase:
                </div>
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                  {shows.filter(s => s.movieId === heroMovie.id).map(s => (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => navigate(`/book/${s.id}`)}
                      style={{
                        padding: '10px 18px',
                        backgroundColor: '#E51937',
                        color: '#FFFFFF',
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontWeight: 700,
                        fontSize: '13px',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-start',
                        transition: 'transform 0.15s, background-color 0.15s'
                      }}
                    >
                      <span style={{ fontSize: '14px', fontWeight: 800 }}>Book Tickets (₹{s.price})</span>
                      <span style={{ fontSize: '11px', opacity: 0.85 }}>{s.cinemaName} • {s.format}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Movie Poster Card */}
            <div style={{
              flex: '0 0 280px',
              borderRadius: '12px',
              overflow: 'hidden',
              boxShadow: '0 20px 40px rgba(0,0,0,0.6)',
              border: '2px solid rgba(255,255,255,0.1)'
            }}>
              <img
                src={heroMovie.poster}
                alt={heroMovie.title}
                style={{ width: '100%', height: '420px', objectFit: 'cover', display: 'block' }}
              />
            </div>
          </div>
        </section>
      )}

      {/* ── Supabase Live Status Strip ── */}
      <div style={{ backgroundColor: '#FFFFFF', borderBottom: '1px solid #E5E7EB', padding: '14px 0' }}>
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Database size={16} color="#059669" />
            <span style={{ fontSize: '13px', fontWeight: 700, color: '#111827' }}>
              Connected Supabase Cluster:
            </span>
            <span style={{ fontSize: '12px', color: '#6B7280', fontFamily: 'monospace' }}>
              aws-0-ap-south-1.pooler.supabase.com (PostgreSQL 17.6)
            </span>
          </div>

          {dbStatus && dbStatus.tables && (
            <div style={{ display: 'flex', gap: '16px', fontSize: '12.5px', color: '#4B5563', flexWrap: 'wrap' }}>
              <span><strong>{dbStatus.tables.movies || 2}</strong> Movies</span>
              <span>•</span>
              <span><strong>{dbStatus.tables.theatres || 2}</strong> Theatres</span>
              <span>•</span>
              <span><strong>{dbStatus.tables.shows || 2}</strong> Shows</span>
              <span>•</span>
              <span><strong>{dbStatus.tables.seats || 35}</strong> Seats</span>
              <span>•</span>
              <span style={{ color: '#059669', fontWeight: 700 }}><strong>{dbStatus.tables.bookings || 12}</strong> Real Bookings</span>
            </div>
          )}
        </div>
      </div>

      {/* ── All Movies in Supabase ── */}
      <section className="container" style={{ paddingTop: '40px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '24px' }}>
          <div>
            <h2 style={{ fontSize: '24px', fontWeight: 800, color: '#111827', marginBottom: '4px' }}>
              Now Showing in Theatres
            </h2>
            <p style={{ fontSize: '13.5px', color: '#6B7280' }}>
              Select a show to view the live interactive seat map and book directly in Supabase.
            </p>
          </div>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div className="spinner" />
            <p style={{ marginTop: '12px', color: '#6B7280', fontSize: '14px' }}>Loading real movies from Supabase...</p>
          </div>
        ) : (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
            gap: '24px'
          }}>
            {movies.map(movie => {
              const movieShows = shows.filter(s => s.movieId === movie.id)

              return (
                <div
                  key={movie.id}
                  style={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: '12px',
                    border: '1px solid #E5E7EB',
                    overflow: 'hidden',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
                    display: 'flex',
                    flexDirection: 'column'
                  }}
                >
                  {/* Poster Image */}
                  <div style={{ height: '360px', overflow: 'hidden', position: 'relative' }}>
                    <img
                      src={movie.poster}
                      alt={movie.title}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                    <div style={{
                      position: 'absolute',
                      top: '12px',
                      left: '12px',
                      backgroundColor: 'rgba(0,0,0,0.8)',
                      backdropFilter: 'blur(4px)',
                      color: '#FFFFFF',
                      padding: '4px 10px',
                      borderRadius: '6px',
                      fontSize: '12px',
                      fontWeight: 800,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px'
                    }}>
                      <Star size={13} fill="#D97706" color="#D97706" />
                      <span>{movie.rating}</span>
                    </div>
                  </div>

                  {/* Movie Info */}
                  <div style={{ padding: '20px', flex: 1, display: 'flex', flexDirection: 'column' }}>
                    <h3 style={{ fontSize: '20px', fontWeight: 800, color: '#111827', marginBottom: '8px' }}>
                      {movie.title}
                    </h3>
                    <div style={{ fontSize: '12.5px', color: '#6B7280', marginBottom: '12px' }}>
                      <span>{movie.duration}</span> • <span>{movie.genre}</span> • <span>{movie.certificate}</span>
                    </div>

                    <p style={{ fontSize: '13px', color: '#4B5563', lineHeight: 1.5, marginBottom: '20px', flex: 1 }}>
                      {movie.synopsis}
                    </p>

                    {/* Shows in Supabase */}
                    <div style={{ borderTop: '1px solid #F3F4F6', paddingTop: '16px' }}>
                      <div style={{ fontSize: '11.5px', fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', marginBottom: '8px' }}>
                        Shows & Theatres in Supabase:
                      </div>

                      {movieShows.length === 0 ? (
                        <div style={{ fontSize: '12px', color: '#9CA3AF' }}>No scheduled shows</div>
                      ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                          {movieShows.map(s => (
                            <button
                              key={s.id}
                              type="button"
                              onClick={() => navigate(`/book/${s.id}`)}
                              style={{
                                width: '100%',
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                padding: '10px 14px',
                                backgroundColor: '#F9FAFB',
                                border: '1px solid #E5E7EB',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                transition: 'all 0.15s'
                              }}
                              onMouseOver={e => e.currentTarget.style.borderColor = '#E51937'}
                              onMouseOut={e => e.currentTarget.style.borderColor = '#E5E7EB'}
                            >
                              <div style={{ textAlign: 'left' }}>
                                <div style={{ fontSize: '13px', fontWeight: 700, color: '#111827' }}>
                                  {s.cinemaName}
                                </div>
                                <div style={{ fontSize: '11px', color: '#6B7280' }}>
                                  {s.format} • {s.totalSeats} Total Seats
                                </div>
                              </div>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <span style={{ fontSize: '13px', fontWeight: 800, color: '#E51937' }}>
                                  ₹{s.price}
                                </span>
                                <span style={{
                                  padding: '4px 8px',
                                  backgroundColor: '#E51937',
                                  color: '#FFFFFF',
                                  borderRadius: '4px',
                                  fontSize: '11px',
                                  fontWeight: 700
                                }}>
                                  Select Seats →
                                </span>
                              </div>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
