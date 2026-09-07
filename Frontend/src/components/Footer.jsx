import { Link } from 'react-router-dom'
import { Film, Server, ShieldCheck, Layers } from 'lucide-react'
import styles from './Footer.module.css'

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={`container ${styles.container}`}>
        {/* Top Info Bar */}
        <div className={styles.topInfo}>
          <div className={styles.brandCol}>
            <div className={styles.logo}>
              <Film size={20} color="#E51937" />
              <span className={styles.logoText}>CineBook</span>
            </div>
            <p className={styles.tagline}>
              Next-generation cinema ticketing powered by a distributed Java RMI backend.
            </p>
          </div>

          <div className={styles.statusPill}>
            <Server size={14} className={styles.statusIcon} />
            <span>Distributed Engine: <strong>Java RMI • Port 1099</strong></span>
            <span className={styles.statusDot} />
            <span>5-Node Cluster</span>
          </div>
        </div>

        {/* Links Columns */}
        <div className={styles.linksGrid}>
          <div className={styles.col}>
            <h4 className={styles.colTitle}>Movies Now Showing</h4>
            <Link to="/" className={styles.link}>Kalki 2898 AD (Hindi, IMAX 3D)</Link>
            <Link to="/" className={styles.link}>Stree 2 (Hindi)</Link>
            <Link to="/" className={styles.link}>Jawan (Action, 2D)</Link>
            <Link to="/" className={styles.link}>Dune: Part Two (IMAX 3D)</Link>
            <Link to="/" className={styles.link}>Interstellar (IMAX 70mm)</Link>
          </div>

          <div className={styles.col}>
            <h4 className={styles.colTitle}>Partner Cinemas</h4>
            <span className={styles.textItem}>PVR INOX: Phoenix Palladium, Lower Parel</span>
            <span className={styles.textItem}>INOX: R-City, Ghatkopar West</span>
            <span className={styles.textItem}>Cinepolis: Viviana Mall, Thane</span>
            <span className={styles.textItem}>PVR ICON: Infiniti Mall, Versova</span>
          </div>

          <div className={styles.col}>
            <h4 className={styles.colTitle}>CineBook Lab</h4>
            <Link to="/lab?tab=rmi" className={styles.link}>RMI Remote Service Test</Link>
            <Link to="/lab?tab=concurrency" className={styles.link}>Concurrent Booking & Race Condition</Link>
            <Link to="/lab?tab=clocks" className={styles.link}>Berkeley Sync & Lamport Clocks</Link>
            <Link to="/lab?tab=election" className={styles.link}>Bully Leader Election Algorithm</Link>
          </div>

          <div className={styles.col}>
            <h4 className={styles.colTitle}>System Architecture</h4>
            <div className={styles.techStack}>
              <span className={styles.techPill}><Layers size={12} /> React 19 Frontend</span>
              <span className={styles.techPill}><Server size={12} /> Java HTTP Bridge</span>
              <span className={styles.techPill}><ShieldCheck size={12} /> Java RMI Registry</span>
              <span className={styles.techPill}><Server size={12} /> PostgreSQL Database</span>
            </div>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className={styles.bottomBar}>
          <p className={styles.copy}>
            CineBook — Movie Ticket Booking System & Distributed Computing Lab Demonstration.
          </p>
          <div className={styles.legalLinks}>
            <Link to="/lab" className={styles.labDirectLink}>
              Launch CineBook Lab →
            </Link>
          </div>
        </div>
      </div>
    </footer>
  )
}
