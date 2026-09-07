import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Server, Zap, Clock, ShieldCheck, Layers, Cpu, Database, Activity } from 'lucide-react'
import LabExp1Rmi from './LabExp1Rmi'
import LabExp2Concurrency from './LabExp2Concurrency'
import LabExp3Clocks from './LabExp3Clocks'
import LabExp4Election from './LabExp4Election'
import styles from './Lab.module.css'

const TABS = [
  { id: 'rmi', label: 'RMI Remote Service', icon: Server, expNum: 'Exp 1' },
  { id: 'concurrency', label: 'Concurrent Booking Test', icon: Zap, expNum: 'Exp 2' },
  { id: 'clocks', label: 'Clock Synchronization', icon: Clock, expNum: 'Exp 3' },
  { id: 'election', label: 'Bully Leader Election', icon: ShieldCheck, expNum: 'Exp 4' }
]

export default function LabHub() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawTab = searchParams.get('tab') || 'rmi'
  const tabAliases = { exp1: 'rmi', exp2: 'concurrency', exp3: 'clocks', exp4: 'election' }
  const currentTab = tabAliases[rawTab] || rawTab

  function switchTab(tabId) {
    setSearchParams({ tab: tabId })
  }

  return (
    <div className={`container ${styles.hubWrap}`}>
      {/* ── Top Diagnostic Header ── */}
      <div className={styles.hubHeader}>
        <div>
          <div className={styles.hubBadge}>
            <Cpu size={14} />
            <span>Distributed Systems Engineering</span>
          </div>
          <h1 className={styles.hubTitle}>CineBook Systems Lab</h1>
          <p className={styles.hubDesc}>
            Interactive test workbench demonstrating the core distributed computing experiments running behind the booking platform.
          </p>
        </div>

        {/* System Diagnostics Strip */}
        <div className={styles.diagStrip}>
          <div className={styles.diagItem}>
            <Activity size={15} color="#059669" />
            <div>
              <div className={styles.diagVal}>Bridge :8080</div>
              <div className={styles.diagSub}>REST-to-RMI Active</div>
            </div>
          </div>

          <div className={styles.diagItem}>
            <Server size={15} color="#059669" />
            <div>
              <div className={styles.diagVal}>RMI :1099</div>
              <div className={styles.diagSub}>LocateRegistry Ready</div>
            </div>
          </div>

          <div className={styles.diagItem}>
            <Database size={15} color="#2563EB" />
            <div>
              <div className={styles.diagVal}>PostgreSQL</div>
              <div className={styles.diagSub}>ACID Persistence</div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Horizontal Navigation Tabs ── */}
      <div className={styles.tabBar}>
        {TABS.map(tab => {
          const Icon = tab.icon
          const isActive = currentTab === tab.id
          return (
            <button
              key={tab.id}
              type="button"
              className={`${styles.tabBtn} ${isActive ? styles.tabBtnActive : ''}`}
              onClick={() => switchTab(tab.id)}
            >
              <Icon size={16} />
              <div className={styles.tabTextCol}>
                <span className={styles.tabLabel}>{tab.label}</span>
                <span className={styles.tabExpNum}>{tab.expNum}</span>
              </div>
            </button>
          )
        })}
      </div>

      {/* ── Active Experiment Panel ── */}
      <div className={styles.tabContent}>
        {currentTab === 'rmi' && <LabExp1Rmi />}
        {currentTab === 'concurrency' && <LabExp2Concurrency />}
        {currentTab === 'clocks' && <LabExp3Clocks />}
        {currentTab === 'election' && <LabExp4Election />}
      </div>
    </div>
  )
}
