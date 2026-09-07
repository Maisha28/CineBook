import { useState } from 'react'
import { exp3 } from '../../api'
import { useToast } from '../../components/Toast'
import { Clock, RefreshCw, Play, ArrowDown, Server, Laptop, Layers, CheckCircle2 } from 'lucide-react'
import styles from './Lab.module.css'

export default function LabExp3Clocks() {
  const toast = useToast()
  const [activeSubTab, setActiveSubTab] = useState('berkeley')

  // Berkeley state
  const [berkeleyRunning, setBerkeleyRunning] = useState(false)
  const [corrections, setCorrections] = useState(null)
  const [baseTimes, setBaseTimes] = useState(() => {
    const now = Date.now()
    return {
      SERVER: now,
      C1: now - 5000,
      C2: now + 8000,
      C3: now + 4000,
      C4: now + 6000
    }
  })

  // Lamport state
  const [lamportRunning, setLamportRunning] = useState(false)
  const [lamportTimeline, setLamportTimeline] = useState([])

  async function handleRunBerkeley() {
    setBerkeleyRunning(true)
    setCorrections(null)
    try {
      const clientTimes = {
        C1: baseTimes.C1,
        C2: baseTimes.C2,
        C3: baseTimes.C3,
        C4: baseTimes.C4
      }
      const data = await exp3.berkeley(clientTimes)
      if (data.corrections) {
        setCorrections(data.corrections)
        toast('Berkeley clock synchronization calculated by server', 'success')
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error running Berkeley sync', 'error')
    }
    setBerkeleyRunning(false)
  }

  async function handleRunLamport() {
    setLamportRunning(true)
    setLamportTimeline([])
    try {
      const clients = ['Client-1', 'Client-2', 'Client-3']
      const data = await exp3.lamport(clients)
      if (data.log) {
        setLamportTimeline(data.log)
        toast('Lamport event ordering timeline generated', 'success')
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error running Lamport clock demo', 'error')
    }
    setLamportRunning(false)
  }

  function formatMsTime(ms) {
    const d = new Date(ms)
    return d.toTimeString().split(' ')[0] + '.' + String(d.getMilliseconds()).padStart(3, '0')
  }

  return (
    <div className={styles.expContainer}>
      {/* ── Header & Sub-Tabs ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 3: Distributed Clock Synchronization</h2>
          <p className={styles.expSubtitle}>
            Physical clock synchronization via Berkeley Algorithm and logical causal ordering via Lamport Timestamps.
          </p>
        </div>

        <div className={styles.subTabNav}>
          <button
            type="button"
            className={`${styles.subTabBtn} ${activeSubTab === 'berkeley' ? styles.subTabActive : ''}`}
            onClick={() => setActiveSubTab('berkeley')}
          >
            <RefreshCw size={13} />
            <span>Berkeley Algorithm</span>
          </button>
          <button
            type="button"
            className={`${styles.subTabBtn} ${activeSubTab === 'lamport' ? styles.subTabActive : ''}`}
            onClick={() => setActiveSubTab('lamport')}
          >
            <Clock size={13} />
            <span>Lamport Logical Clocks</span>
          </button>
        </div>
      </div>

      {/* ── Tab 1: Berkeley Synchronization ── */}
      {activeSubTab === 'berkeley' && (
        <div>
          <div className={styles.testCard}>
            <div className={styles.cardHeaderRow}>
              <h3 className={styles.cardHeader}>Berkeley Clock Sync Process</h3>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleRunBerkeley}
                disabled={berkeleyRunning}
              >
                {berkeleyRunning ? <span className="spinner" /> : <Play size={13} />}
                <span>Execute Berkeley Synchronization</span>
              </button>
            </div>

            {/* Nodes Table */}
            <div className={styles.tableWrap}>
              <table className={styles.clockTable}>
                <thead>
                  <tr>
                    <th>NODE ROLE</th>
                    <th>LOCAL TIME (BEFORE)</th>
                    <th>INITIAL OFFSET</th>
                    <th>SERVER COMPUTED CORRECTION</th>
                    <th>SYNCHRONIZED TIME (AFTER)</th>
                  </tr>
                </thead>
                <tbody>
                  {['SERVER', 'C1', 'C2', 'C3', 'C4'].map(node => {
                    const isServer = node === 'SERVER'
                    const initTime = baseTimes[node]
                    const offsetSec = ((initTime - baseTimes.SERVER) / 1000).toFixed(1)
                    const corr = corrections ? corrections[node] : null
                    const afterTime = corr != null ? initTime + corr : null

                    return (
                      <tr key={node} className={isServer ? styles.serverRow : ''}>
                        <td>
                          <div className={styles.nodeCell}>
                            {isServer ? <Server size={14} color="#E51937" /> : <Laptop size={14} color="#6B7280" />}
                            <strong>{isServer ? 'COORDINATOR (SERVER)' : `CLIENT ${node.substring(1)}`}</strong>
                          </div>
                        </td>
                        <td className={styles.monoCell}>{formatMsTime(initTime)}</td>
                        <td className={styles.monoCell}>
                          <span className={offsetSec > 0 ? styles.valRed : offsetSec < 0 ? styles.valBlue : ''}>
                            {offsetSec > 0 ? `+${offsetSec}s` : offsetSec < 0 ? `${offsetSec}s` : '0.0s (Master)'}
                          </span>
                        </td>
                        <td className={styles.monoCell}>
                          {corr != null ? (
                            <span className={styles.valGreen}>
                              {corr >= 0 ? `+${corr} ms` : `${corr} ms`}
                            </span>
                          ) : (
                            <span className={styles.valMuted}>Pending calculation</span>
                          )}
                        </td>
                        <td className={styles.monoCell}>
                          {afterTime != null ? (
                            <strong>{formatMsTime(afterTime)}</strong>
                          ) : (
                            <span className={styles.valMuted}>-</span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {/* Algorithm Steps Flow */}
            <div className={styles.berkeleyStepsRow}>
              <div className={styles.bStep}>
                <span className={styles.bStepNum}>1</span>
                <span>Poll Nodes for Clocks</span>
              </div>
              <ArrowDown size={14} className={styles.bStepArrow} />
              <div className={styles.bStep}>
                <span className={styles.bStepNum}>2</span>
                <span>Compute Fault-Tolerant Average</span>
              </div>
              <ArrowDown size={14} className={styles.bStepArrow} />
              <div className={styles.bStep}>
                <span className={styles.bStepNum}>3</span>
                <span>Send Relative Clock Adjustments</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Tab 2: Lamport Logical Clocks ── */}
      {activeSubTab === 'lamport' && (
        <div>
          <div className={styles.testCard}>
            <div className={styles.cardHeaderRow}>
              <h3 className={styles.cardHeader}>Lamport Logical Timestamps Demonstration</h3>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleRunLamport}
                disabled={lamportRunning}
              >
                {lamportRunning ? <span className="spinner" /> : <Play size={13} />}
                <span>Run Distributed Event Timeline</span>
              </button>
            </div>

            <p className={styles.infoText}>
              In distributed systems without synchronized physical clocks, <strong>Lamport Timestamps</strong> enforce causal ordering.
              Each node maintains a logical counter: on local events it increments ($L = L + 1$), and on message reception it advances
              to $\max(L_{local}, L_{received}) + 1$.
            </p>

            {/* Timeline Stream */}
            <div className={styles.lamportTimelineWrap}>
              {lamportTimeline.length === 0 ? (
                <div className={styles.emptyPrompt}>
                  <Clock size={24} color="#9CA3AF" />
                  <p>Click "Run Distributed Event Timeline" to observe logical clock advances across clients and server.</p>
                </div>
              ) : (
                <div className={styles.timelineList}>
                  {lamportTimeline.map((line, idx) => {
                    const isServer = line.includes('SERVER')
                    return (
                      <div key={idx} className={`${styles.timelineEntry} ${isServer ? styles.timelineServer : ''}`}>
                        <span className={styles.timelineIndex}>{idx + 1}</span>
                        <span className={styles.timelineText}>{line}</span>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── Technical Summary ── */}
      <div className={styles.summaryCard}>
        <h4 className={styles.summaryTitle}>What This Demonstrates</h4>
        <p className={styles.summaryText}>
          Experiment 3 addresses two fundamental problems in distributed computing: physical clock drift and distributed event ordering.
          The <strong>Berkeley Algorithm</strong> allows a master server to pull times from client nodes, compute an average, and send back
          relative time offsets rather than absolute timestamps, preventing clock skew anomalies.
          <strong>Lamport Logical Clocks</strong> provide a strict causal partial ordering of booking operations across remote clients.
        </p>
      </div>
    </div>
  )
}
