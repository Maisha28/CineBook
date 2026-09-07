import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Film, Search, MapPin, ChevronDown, FlaskConical, User } from 'lucide-react'
import styles from './Navbar.module.css'

const CITIES = ['Mumbai', 'Delhi-NCR', 'Bengaluru', 'Hyderabad', 'Chennai', 'Pune']

export default function Navbar() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [selectedCity, setSelectedCity] = useState('Mumbai')
  const [cityOpen, setCityOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')

  function handleSearch(e) {
    if (e.key === 'Enter' && searchQuery.trim()) {
      navigate(`/?search=${encodeURIComponent(searchQuery.trim())}`)
    }
  }

  return (
    <header className={styles.header}>
      {/* ── Top Bar ── */}
      <div className={styles.topBar}>
        <div className={`container ${styles.topBarInner}`}>
          {/* Logo */}
          <Link to="/" className={styles.logo}>
            <div className={styles.logoIcon}>
              <Film size={20} color="#FFFFFF" />
            </div>
            <span className={styles.logoText}>CineBook</span>
          </Link>

          {/* Search Box */}
          <div className={styles.searchWrap}>
            <Search size={16} className={styles.searchIcon} />
            <input
              type="text"
              placeholder="Search for movies, cinemas, shows..."
              className={styles.searchInput}
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              onKeyDown={handleSearch}
            />
          </div>

          {/* Right Actions */}
          <div className={styles.rightActions}>
            {/* City Selector */}
            <div className={styles.citySelectorWrap}>
              <button
                type="button"
                className={styles.cityBtn}
                onClick={() => setCityOpen(!cityOpen)}
              >
                <MapPin size={15} color="#E51937" />
                <span>{selectedCity}</span>
                <ChevronDown size={14} />
              </button>

              {cityOpen && (
                <div className={styles.cityDropdown}>
                  <div className={styles.dropdownHeader}>Popular Cities</div>
                  {CITIES.map(c => (
                    <div
                      key={c}
                      className={`${styles.cityOption} ${c === selectedCity ? styles.cityActive : ''}`}
                      onClick={() => {
                        setSelectedCity(c)
                        setCityOpen(false)
                      }}
                    >
                      {c}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Sign In */}
            <button type="button" className={styles.signInBtn}>
              <User size={15} />
              <span>Sign In</span>
            </button>
          </div>
        </div>
      </div>

      {/* ── Secondary Navigation ── */}
      <div className={styles.subBar}>
        <div className={`container ${styles.subBarInner}`}>
          <div className={styles.navLinks}>
            <Link
              to="/"
              className={`${styles.navLink} ${pathname === '/' ? styles.navActive : ''}`}
            >
              Movies
            </Link>
            <Link
              to="/cinemas"
              className={`${styles.navLink} ${pathname === '/cinemas' ? styles.navActive : ''}`}
            >
              Cinemas
            </Link>
            <Link
              to="/offers"
              className={`${styles.navLink} ${pathname === '/offers' ? styles.navActive : ''}`}
            >
              Offers
            </Link>
            <Link
              to="/experiences"
              className={`${styles.navLink} ${pathname === '/experiences' ? styles.navActive : ''}`}
            >
              Experiences
            </Link>
          </div>

          {/* CineBook Lab Link */}
          <Link
            to="/lab"
            className={`${styles.labLink} ${pathname.startsWith('/lab') ? styles.labActive : ''}`}
          >
            <FlaskConical size={14} />
            <span>CineBook Lab</span>
            <span className={styles.labBadge}>Distributed Systems</span>
          </Link>
        </div>
      </div>
    </header>
  )
}
