import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { catalog } from '../api'
import { Star, Clock, Calendar, MapPin, ChevronLeft, Ticket, Shield } from 'lucide-react'
import styles from './MovieDetailsPage.module.css'

const DATES = [
  { label: 'TODAY', date: '07 SEP' },
  { label: 'TOMORROW', date: '08 SEP' },
  { label: 'WED', date: '09 SEP' },
  { label: 'THU', date: '10 SEP' },
]

export default function MovieDetailsPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [movie, setMovie] = useState(null)
  const [selectedDate, setSelectedDate] = useState('TODAY')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    setLoading(true)
    catalog.getMovieById(id)
      .then(data => {
        setMovie(data)
        setLoading(false)
      })
      .catch(err => {
        setError(err.message || 'Failed to load movie')
        setLoading(false)
      })
  }, [id])

  if (loading) {
    return (
      <div className="container" style={{ padding: '60px 0', textAlign: 'center' }}>
        <div className="spinner" />
        <p style={{ marginTop: 12, color: 'var(--text-muted)' }}>Loading movie details...</p>
      </div>
    )
  }

  if (error || !movie) {
    return (
      <div className="container" style={{ padding: '60px 0', textAlign: 'center' }}>
        <h2>Movie Not Found</h2>
        <p style={{ margin: '12px 0', color: 'var(--text-muted)' }}>{error || 'Unable to retrieve this movie.'}</p>
        <button type="button" className="btn btn-outline" onClick={() => navigate('/')}>
          Back to Movies
        </button>
      </div>
    )
  }

  // Group shows by cinema
  const showsByCinema = {}
  if (movie.shows) {
    movie.shows.forEach(s => {
      if (!showsByCinema[s.cinemaId]) {
        showsByCinema[s.cinemaId] = {
          name: s.cinemaName,
          location: s.location,
          shows: []
        }
      }
      showsByCinema[s.cinemaId].shows.push(s)
    })
  }

  return (
    <div className={styles.pageWrap}>
      {/* ── Movie Banner Header ── */}
      <section className={styles.bannerSection}>
        <div className={`container ${styles.bannerContainer}`}>
          {/* Back Button */}
          <button type="button" className={styles.backBtn} onClick={() => navigate('/')}>
            <ChevronLeft size={16} />
            <span>Back to Movies</span>
          </button>

          <div className={styles.movieHeaderRow}>
            {/* Poster Card */}
            <div
              className={styles.posterCard}
              style={{ backgroundColor: movie.posterColor || '#1A1A24' }}
            >
              <div className={styles.posterPattern} />
              <div className={styles.posterInner}>
                <span className={styles.posterBadge}>{movie.format.split(',')[0]}</span>
                <div className={styles.posterText}>{movie.posterText || movie.title}</div>
                <div className={styles.posterCert}>{movie.certificate}</div>
              </div>
            </div>

            {/* Information Column */}
            <div className={styles.infoCol}>
              <h1 className={styles.movieTitle}>{movie.title}</h1>

              <div className={styles.ratingRow}>
                <div className={styles.ratingBox}>
                  <Star size={16} fill="#D97706" color="#D97706" />
                  <strong>{movie.rating}</strong>
                  <span>/10</span>
                </div>
                <span className={styles.votesText}>Verified Audience Rating</span>
              </div>

              <div className={styles.tagsRow}>
                <span className={styles.formatTag}>{movie.format}</span>
                <span className={styles.langTag}>{movie.language}</span>
              </div>

              <div className={styles.metaRow}>
                <span>{movie.duration}</span>
                <span>•</span>
                <span>{movie.genre}</span>
                <span>•</span>
                <span>{movie.certificate}</span>
              </div>

              <p className={styles.synopsisText}>{movie.synopsis}</p>

              {movie.cast && movie.cast.length > 0 && (
                <div className={styles.castSection}>
                  <span className={styles.castLabel}>Starring:</span>
                  <span className={styles.castList}>{movie.cast.join(', ')}</span>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ── Date Picker Filter ── */}
      <section className={styles.dateBar}>
        <div className={`container ${styles.dateContainer}`}>
          <div className={styles.dateTabs}>
            {DATES.map(d => (
              <button
                key={d.label}
                type="button"
                className={`${styles.dateTab} ${selectedDate === d.label ? styles.dateTabActive : ''}`}
                onClick={() => setSelectedDate(d.label)}
              >
                <span className={styles.dateDay}>{d.label}</span>
                <span className={styles.dateNum}>{d.date}</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* ── Cinemas & Showtimes ── */}
      <section className={styles.showsSection}>
        <div className="container">
          <div className="section-title">
            <span>Available Theatres & Showtimes in Mumbai</span>
          </div>

          {Object.keys(showsByCinema).length === 0 ? (
            <div className={styles.noShows}>
              <Ticket size={28} color="#9CA3AF" />
              <p>No showtimes scheduled for this date.</p>
            </div>
          ) : (
            <div className={styles.cinemaList}>
              {Object.entries(showsByCinema).map(([cinemaId, cinemaData]) => (
                <div key={cinemaId} className={styles.cinemaRow}>
                  <div className={styles.cinemaInfo}>
                    <h3 className={styles.cinemaTitle}>{cinemaData.name}</h3>
                    <div className={styles.cinemaMeta}>
                      <MapPin size={13} color="#9CA3AF" />
                      <span>{cinemaData.location}</span>
                    </div>
                  </div>

                  <div className={styles.showtimesGrid}>
                    {cinemaData.shows.map(show => (
                      <button
                        key={show.id}
                        type="button"
                        className={styles.timeChip}
                        onClick={() => navigate(`/book/${show.id}?movie=${encodeURIComponent(movie.title)}&cinema=${encodeURIComponent(cinemaData.name)}`)}
                      >
                        <span className={styles.chipTime}>{show.time}</span>
                        <span className={styles.chipFormat}>{show.format}</span>
                        <span className={styles.chipPrice}>₹{show.price}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
