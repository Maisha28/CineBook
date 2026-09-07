import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { catalog } from '../api'
import { Film, Star, Clock, Calendar, ChevronRight, Server, Search, Sparkles } from 'lucide-react'
import styles from './Home.module.css'

const CATEGORIES = ['All Movies', 'Hindi', 'English', 'Action', 'Comedy', 'Sci-Fi', 'IMAX']

export default function Home() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const searchFilter = searchParams.get('search') || ''

  const [movies, setMovies] = useState([])
  const [cinemas, setCinemas] = useState([])
  const [activeCategory, setActiveCategory] = useState('All Movies')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([catalog.getMovies(), catalog.getCinemas()])
      .then(([mRes, cRes]) => {
        setMovies(mRes.movies || [])
        setCinemas(cRes.cinemas || [])
        setLoading(false)
      })
      .catch(() => {
        setLoading(false)
      })
  }, [])

  // Filter movies based on category and search query
  const filteredMovies = movies.filter(m => {
    const matchesSearch = !searchFilter ||
      m.title.toLowerCase().includes(searchFilter.toLowerCase()) ||
      m.genre.toLowerCase().includes(searchFilter.toLowerCase()) ||
      m.language.toLowerCase().includes(searchFilter.toLowerCase())

    if (!matchesSearch) return false

    if (activeCategory === 'All Movies') return true
    if (activeCategory === 'Hindi') return m.language.includes('Hindi')
    if (activeCategory === 'English') return m.language.includes('English')
    if (activeCategory === 'Action') return m.genre.includes('Action')
    if (activeCategory === 'Comedy') return m.genre.includes('Comedy')
    if (activeCategory === 'Sci-Fi') return m.genre.includes('Sci-Fi')
    if (activeCategory === 'IMAX') return m.format.includes('IMAX')
    return true
  })

  const heroMovie = movies[0] || null

  return (
    <div className={styles.homeWrap}>
      {/* ── Spotlight Hero Banner ── */}
      {heroMovie && !searchFilter && (
        <section className={styles.heroSection}>
          <div className={`container ${styles.heroContainer}`}>
            <div className={styles.heroContent}>
              <div className={styles.heroBadge}>
                <Sparkles size={13} />
                <span>Featured Premiere</span>
              </div>
              <h1 className={styles.heroTitle}>{heroMovie.title}</h1>
              <div className={styles.heroMeta}>
                <span className={styles.ratingBadge}>
                  <Star size={13} fill="#D97706" color="#D97706" />
                  <strong>{heroMovie.rating}</strong>/10
                </span>
                <span>{heroMovie.certificate}</span>
                <span>•</span>
                <span>{heroMovie.duration}</span>
                <span>•</span>
                <span>{heroMovie.genre}</span>
              </div>
              <p className={styles.heroSynopsis}>{heroMovie.synopsis}</p>
              <div className={styles.heroActions}>
                <button
                  type="button"
                  className="btn btn-primary btn-lg"
                  onClick={() => navigate(`/movie/${heroMovie.id}`)}
                >
                  <Film size={16} />
                  <span>Book Tickets</span>
                </button>
                <button
                  type="button"
                  className="btn btn-outline btn-lg"
                  onClick={() => navigate(`/movie/${heroMovie.id}`)}
                >
                  <span>View Details</span>
                </button>
              </div>
            </div>

            {/* Poster Card */}
            <div className={styles.heroPosterWrap} onClick={() => navigate(`/movie/${heroMovie.id}`)}>
              <div
                className={styles.heroPoster}
                style={{ backgroundColor: heroMovie.posterColor || '#1A1A24' }}
              >
                <div className={styles.posterPattern} />
                <div className={styles.posterInner}>
                  <span className={styles.posterFormatBadge}>{heroMovie.format}</span>
                  <div className={styles.posterGraphicTitle}>{heroMovie.posterText || heroMovie.title}</div>
                  <div className={styles.posterLanguages}>{heroMovie.language}</div>
                </div>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* ── Category Filters Rail ── */}
      <section className={styles.filterSection}>
        <div className={`container ${styles.filterContainer}`}>
          <div className={styles.filterTabs}>
            {CATEGORIES.map(cat => (
              <button
                key={cat}
                type="button"
                className={`${styles.filterBtn} ${activeCategory === cat ? styles.filterBtnActive : ''}`}
                onClick={() => setActiveCategory(cat)}
              >
                {cat}
              </button>
            ))}
          </div>

          {searchFilter && (
            <div className={styles.searchActivePill}>
              <Search size={14} />
              <span>Results for: "<strong>{searchFilter}</strong>"</span>
              <button type="button" className={styles.clearSearch} onClick={() => navigate('/')}>
                Clear
              </button>
            </div>
          )}
        </div>
      </section>

      {/* ── Recommended Movies / Now Showing ── */}
      <section className={styles.moviesSection}>
        <div className="container">
          <div className="section-title">
            <span>Now Showing in Mumbai</span>
            <span className={styles.movieCount}>{filteredMovies.length} Movies Available</span>
          </div>

          {loading ? (
            <div className={styles.loadingState}>
              <div className="spinner" />
              <span>Loading movies from catalog...</span>
            </div>
          ) : filteredMovies.length === 0 ? (
            <div className={styles.emptyState}>
              <Film size={32} color="#9CA3AF" />
              <p>No movies found matching your search.</p>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setActiveCategory('All Movies')}>
                Reset Filters
              </button>
            </div>
          ) : (
            <div className={styles.movieGrid}>
              {filteredMovies.map(movie => (
                <div
                  key={movie.id}
                  className={styles.movieCard}
                  onClick={() => navigate(`/movie/${movie.id}`)}
                >
                  {/* Poster Element */}
                  <div
                    className={styles.cardPoster}
                    style={{ backgroundColor: movie.posterColor || '#1E1E2D' }}
                  >
                    <div className={styles.cardPosterPattern} />
                    <div className={styles.cardPosterContent}>
                      <span className={styles.cardFormatBadge}>{movie.format.split(',')[0]}</span>
                      <h3 className={styles.cardPosterTitle}>{movie.posterText || movie.title}</h3>
                      <span className={styles.cardCertificate}>{movie.certificate}</span>
                    </div>

                    <div className={styles.ratingStrip}>
                      <Star size={13} fill="#D97706" color="#D97706" />
                      <strong>{movie.rating}</strong>
                      <span className={styles.ratingVotes}>/10</span>
                    </div>
                  </div>

                  {/* Metadata */}
                  <div className={styles.cardDetails}>
                    <h3 className={styles.cardTitle}>{movie.title}</h3>
                    <p className={styles.cardGenre}>{movie.genre}</p>
                    <p className={styles.cardLanguages}>{movie.language}</p>
                    <div className={styles.cardFooter}>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={e => {
                          e.stopPropagation()
                          navigate(`/movie/${movie.id}`)
                        }}
                      >
                        Book Tickets
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* ── Partner Cinemas ── */}
      <section className={styles.cinemasSection}>
        <div className="container">
          <div className="section-title">
            <span>Popular Theatres in Mumbai</span>
          </div>

          <div className={styles.cinemasGrid}>
            {cinemas.map(c => (
              <div key={c.id} className={styles.cinemaCard}>
                <div className={styles.cinemaHead}>
                  <h4 className={styles.cinemaName}>{c.name}</h4>
                  <span className={styles.cinemaLocation}>{c.location}</span>
                </div>
                <div className={styles.formatsRow}>
                  {c.formats.map(f => (
                    <span key={f} className={styles.formatTag}>{f}</span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Subtle Technical Lab Promotion Strip ── */}
      <section className={styles.platformSection}>
        <div className="container">
          <div className={styles.platformCard}>
            <div className={styles.platformInfo}>
              <div className={styles.platformTag}>
                <Server size={14} />
                <span>Distributed Systems Architecture</span>
              </div>
              <h3 className={styles.platformTitle}>Powered by Real Distributed Computing Concepts</h3>
              <p className={styles.platformDesc}>
                Behind CineBook's consumer booking experience runs an academic distributed architecture: Java RMI remote booking services,
                synchronized concurrency controls, Lamport & Berkeley clock synchronization, and a 5-node Bully leader election cluster.
              </p>
            </div>
            <button
              type="button"
              className="btn btn-outline"
              onClick={() => navigate('/lab')}
            >
              <span>Explore CineBook Lab</span>
              <ChevronRight size={15} />
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}
