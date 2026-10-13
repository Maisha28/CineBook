import { useState, useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Film, Database, Ticket, FlaskConical, CheckCircle2, ShieldCheck } from 'lucide-react'
import { catalog } from '../api'
import styles from './Navbar.module.css'

export default function Navbar() {
  const { pathname } = useLocation()
  const [supabaseOnline, setSupabaseOnline] = useState(true)

  useEffect(() => {
    catalog.getSystemStatus()
      .then(res => setSupabaseOnline(res.supabaseConnected !== false))
      .catch(() => setSupabaseOnline(false))
  }, [pathname])

  return (
    <header className={styles.header}>
      {/* ── Top Bar ── */}
      <div className={styles.topBar}>
        <div className={`container ${styles.topBarInner}`}>
          {/* Logo */}
          <Link to="/" className={styles.logo}>
            <div className={styles.logoIcon}>
              <Film size={22} color="#FFFFFF" />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span className={styles.logoText}>CineBook</span>
              <span style={{ fontSize: '10px', color: '#9CA3AF', letterSpacing: '0.05em', textTransform: 'uppercase', fontWeight: 600 }}>
                Distributed Ticketing
              </span>
            </div>
          </Link>

          {/* Database Live Indicator Badge */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            backgroundColor: supabaseOnline ? 'rgba(16, 185, 129, 0.12)' : 'rgba(239, 68, 68, 0.12)',
            border: supabaseOnline ? '1px solid rgba(16, 185, 129, 0.3)' : '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: '20px',
            padding: '5px 14px',
            fontSize: '12px',
            fontWeight: 700,
            color: supabaseOnline ? '#10B981' : '#EF4444'
          }}>
            <span style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: supabaseOnline ? '#10B981' : '#EF4444',
              boxShadow: supabaseOnline ? '0 0 8px #10B981' : '0 0 8px #EF4444'
            }} />
            <span>{supabaseOnline ? 'Supabase PostgreSQL (Live)' : 'Database Offline'}</span>
          </div>

          {/* Right Links */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <Link
              to="/supabase"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '13px',
                fontWeight: 600,
                color: pathname === '/supabase' ? '#E51937' : '#D1D5DB',
                textDecoration: 'none',
                padding: '6px 12px',
                borderRadius: '6px',
                backgroundColor: pathname === '/supabase' ? 'rgba(229, 25, 55, 0.12)' : 'transparent',
                transition: 'all 0.15s'
              }}
            >
              <Database size={15} />
              <span>DB Inspector</span>
            </Link>

            <Link
              to="/bookings"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '13px',
                fontWeight: 600,
                color: pathname === '/bookings' ? '#E51937' : '#D1D5DB',
                textDecoration: 'none',
                padding: '6px 12px',
                borderRadius: '6px',
                backgroundColor: pathname === '/bookings' ? 'rgba(229, 25, 55, 0.12)' : 'transparent',
                transition: 'all 0.15s'
              }}
            >
              <Ticket size={15} />
              <span>Live Bookings</span>
            </Link>

            <Link
              to="/lab"
              className={styles.labLink}
            >
              <FlaskConical size={14} />
              <span>Systems Lab</span>
              <span className={styles.labBadge}>Exp 1-7</span>
            </Link>
          </div>
        </div>
      </div>
    </header>
  )
}
