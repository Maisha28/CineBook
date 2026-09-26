import { useState, useEffect, useRef } from 'react'
import { exp5, parseSeat } from '../../api'
import { useToast } from '../../components/Toast'
import Terminal from '../../components/Terminal'
import {
  Server,
  Play,
  Square,
  Flame,
  Zap,
  RotateCcw,
  ShieldCheck,
  AlertTriangle,
  Database,
  ArrowRight,
  ArrowLeftRight,
  Activity,
  CheckCircle2,
  Clock,
  Layers,
  Sparkles,
  Info
} from 'lucide-react'
import styles from './Lab.module.css'

export default function LabExp5Replication() {
  const toast = useToast()

  // Cluster & Node states
  const [running, setRunning] = useState(false)
  const [primaryState, setPrimaryState] = useState({ alive: false, role: 'DOWN', committedOps: 0, crashArmed: false })
  const [backupState, setBackupState] = useState({ alive: false, role: 'DOWN', committedOps: 0, crashArmed: false })
  const [clientState, setClientState] = useState({ active: false, currentPrimary: 'Primary', missedHeartbeats: 0, lastHeartbeat: '' })
  const [storeType, setStoreType] = useState('In-Memory Replicated Store')

  // Interactive Booking Workbench states
  const [availableSeats, setAvailableSeats] = useState([])
  const [selectedSeat, setSelectedSeat] = useState('')
  const [userName, setUserName] = useState('Alice')
  const [operationId, setOperationId] = useState('')
  const [targetNode, setTargetNode] = useState('auto') // 'auto', 'Primary', 'Backup'
  const [bookingLoading, setBookingLoading] = useState(false)
  const [lastBookingResult, setLastBookingResult] = useState(null)

  // Replica logs & Terminal logs
  const [primaryLogs, setPrimaryLogs] = useState([])
  const [backupLogs, setBackupLogs] = useState([])
  const [inSync, setInSync] = useState(true)
  const [terminalLogs, setTerminalLogs] = useState([])

  // Demo step
  const [demoStep, setDemoStep] = useState(0)
  const [demoRunning, setDemoRunning] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)

  const pollTimerRef = useRef(null)
  const logLenRef = useRef(0)

  // Generate new operation ID on mount
  useEffect(() => {
    generateNewOpId()
  }, [])

  function generateNewOpId() {
    const id = 'op-' + Math.random().toString(36).substring(2, 10)
    setOperationId(id)
  }

  // Poll status & logs
  useEffect(() => {
    fetchStatus()
    startPolling()
    return () => stopPolling()
  }, [])

  function startPolling() {
    if (pollTimerRef.current) return
    pollTimerRef.current = setInterval(async () => {
      try {
        const [statusData, logData, repData] = await Promise.all([
          exp5.status(),
          exp5.getLogs(),
          exp5.getReplicaLogs()
        ])

        if (statusData) {
          setRunning(statusData.running)
          setPrimaryState(statusData.primary || { alive: false, role: 'DOWN', committedOps: 0 })
          setBackupState(statusData.backup || { alive: false, role: 'DOWN', committedOps: 0 })
          setClientState(statusData.client || { active: false, currentPrimary: 'Primary' })
          if (statusData.storeType) setStoreType(statusData.storeType)
          if (typeof statusData.demoStep === 'number') setDemoStep(statusData.demoStep)
          if (typeof statusData.demoRunning === 'boolean') setDemoRunning(statusData.demoRunning)
        }

        if (repData) {
          setPrimaryLogs(repData.primary || [])
          setBackupLogs(repData.backup || [])
          setInSync(repData.inSync ?? true)
        }

        if (logData.logs && logData.logs.length > logLenRef.current) {
          const newLines = logData.logs.slice(logLenRef.current)
          logLenRef.current = logData.logs.length
          setTerminalLogs(prev => [...prev, ...newLines])
        }
      } catch {}
    }, 1200)
  }

  function stopPolling() {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }

  async function fetchStatus() {
    try {
      const data = await exp5.status()
      if (data) {
        setRunning(data.running)
        setPrimaryState(data.primary || {})
        setBackupState(data.backup || {})
        setClientState(data.client || {})
        if (data.storeType) setStoreType(data.storeType)
      }
      loadSeats()
    } catch {}
  }

  async function loadSeats() {
    try {
      const data = await exp5.getSeats()
      if (data.seats && data.seats.length > 0) {
        setAvailableSeats(data.seats)
        const first = parseSeat(data.seats[0])
        setSelectedSeat(first.id)
      }
    } catch {}
  }

  // ── Cluster Lifecycle Handlers ──

  async function handleStartCluster() {
    setActionLoading(true)
    try {
      const data = await exp5.startCluster()
      if (data.status === 'started') {
        toast('Primary-Backup cluster initialized on ports 1201 & 1202', 'success')
        fetchStatus()
        loadSeats()
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error starting cluster', 'error')
    }
    setActionLoading(false)
  }

  async function handleStopCluster() {
    setActionLoading(true)
    try {
      await exp5.stopCluster()
      setRunning(false)
      toast('Cluster and client monitor stopped', 'info')
      fetchStatus()
    } catch (e) {
      toast(e.message || 'Error stopping cluster', 'error')
    }
    setActionLoading(false)
  }

  async function handleReset() {
    setActionLoading(true)
    try {
      await exp5.reset()
      setTerminalLogs([])
      logLenRef.current = 0
      setPrimaryLogs([])
      setBackupLogs([])
      setLastBookingResult(null)
      generateNewOpId()
      toast('Experiment 5 state reset to initial', 'info')
      fetchStatus()
    } catch (e) {
      toast(e.message || 'Error resetting', 'error')
    }
    setActionLoading(false)
  }

  async function handleCrash(node) {
    try {
      const data = await exp5.crash(node)
      if (data.status === 'crashed') {
        toast(`${node} server crashed (unexported from RMI registry)`, 'warning')
        fetchStatus()
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error crashing node', 'error')
    }
  }

  async function handleRestart(node) {
    try {
      toast(`Restarting ${node}... running state recovery sync`, 'info')
      const data = await exp5.restart(node)
      if (data.status === 'restarted') {
        toast(`${node} restarted and synchronized successfully`, 'success')
        fetchStatus()
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error restarting node', 'error')
    }
  }

  async function handleArmCrash(node) {
    try {
      const data = await exp5.armCrash(node)
      if (data.status === 'armed') {
        toast(`Fault injected on ${node}: Will crash right after next DB commit!`, 'warning')
        fetchStatus()
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error arming crash', 'error')
    }
  }

  async function handleActivate(node) {
    try {
      const data = await exp5.activate(node)
      if (data.status === 'activated') {
        toast(`Client activated ${node} -> promoted to ${data.role}`, 'success')
        fetchStatus()
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error activating node', 'error')
    }
  }

  // ── Booking Action ──

  async function handleBookSeat() {
    if (!selectedSeat || !userName.trim()) {
      toast('Seat and Customer Name required', 'warning')
      return
    }

    setBookingLoading(true)
    setLastBookingResult(null)

    try {
      const payload = {
        seatId: selectedSeat,
        userName: userName.trim(),
        operationId: operationId.trim(),
        targetNode: targetNode === 'auto' ? null : targetNode
      }

      const res = await exp5.bookSeat(payload)
      setLastBookingResult(res)

      if (res.success) {
        toast(`Booking confirmed via ${res.executedBy}!`, 'success')
      } else if (res.result && res.result.includes('NOT_PRIMARY')) {
        toast(res.result, 'warning')
      } else {
        toast(res.result || 'Booking could not be completed', 'error')
      }

      // Generate next op id automatically for the next click unless user wants to retry
      // We don't overwrite if it was an error so user can test retrying with the same opId!
      if (res.success) {
        generateNewOpId()
        loadSeats()
      }
    } catch (e) {
      toast(e.message || 'Booking error', 'error')
    }
    setBookingLoading(false)
  }

  // ── 4-Phase Demo Runner ──

  async function handleRunDemoStep(step) {
    setActionLoading(true)
    try {
      const res = await exp5.demoStep(step)
      if (res.message) {
        toast(`Phase ${step} succeeded!`, 'success')
      } else if (res.error) {
        toast(res.error, 'error')
      }
      fetchStatus()
      loadSeats()
    } catch (e) {
      toast(e.message || 'Demo error', 'error')
    }
    setActionLoading(false)
  }

  async function handleRunAutoStory() {
    setActionLoading(true)
    try {
      const res = await exp5.demoStep('auto')
      toast('Automated 4-phase failover demo started! Watch the real-time protocol stream below.', 'info')
      setDemoRunning(true)
    } catch (e) {
      toast(e.message || 'Demo error', 'error')
    }
    setActionLoading(false)
  }

  const isPrimaryActive = primaryState.alive && primaryState.role === 'ACTIVE_PRIMARY'
  const isBackupActive = backupState.alive && backupState.role === 'ACTIVE_PRIMARY'
  const activePrimaryNode = isPrimaryActive ? 'Primary' : isBackupActive ? 'Backup' : 'None'

  return (
    <div className={styles.expContainer}>
      {/* ── Header & Status Row ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 5: Fault Tolerance with Primary-Backup Replication</h2>
          <p className={styles.expSubtitle}>
            A 2-server replicated architecture (Primary :1201, Backup :1202). Synchronous PREPARE/COMMIT replication, heartbeat liveness, idempotent operationId deduplication, and recovery resynchronization.
          </p>
        </div>

        <div className={styles.statusGroup}>
          <span className={styles.statusPill}>
            <span className={running ? styles.statusDotGreen : styles.statusDotRed} />
            <span>Cluster: <strong>{running ? 'Online' : 'Offline'}</strong></span>
          </span>
          <span className={styles.statusPill}>
            <ShieldCheck size={13} color={activePrimaryNode !== 'None' ? '#059669' : '#DC2626'} />
            <span>Active Primary: <strong>{activePrimaryNode}</strong></span>
          </span>
          <span className={styles.statusPill}>
            <Database size={13} color="#2563EB" />
            <span>Store: <strong>{storeType.includes('Postgres') ? 'Supabase DB' : 'In-Memory'}</strong></span>
          </span>
        </div>
      </div>

      {/* ── Cluster Control Bar ── */}
      <div className={styles.clusterControlBar}>
        <div className={styles.clusterInfoText}>
          <strong>Redundancy Cluster:</strong> Two independent JVM RMI processes on ports 1201 & 1202 with client heartbeat monitoring.
        </div>

        <div className={styles.clusterBtns}>
          {!running ? (
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleStartCluster}
              disabled={actionLoading}
            >
              {actionLoading ? <span className="spinner" /> : <Play size={14} />}
              <span>Start Redundant Cluster</span>
            </button>
          ) : (
            <>
              <button
                type="button"
                className="btn btn-outline"
                onClick={handleStopCluster}
                disabled={actionLoading}
              >
                <Square size={14} />
                <span>Stop Cluster</span>
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleReset}
                disabled={actionLoading}
              >
                <RotateCcw size={13} />
                <span>Reset State</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* ── Live Architecture Diagram Grid ── */}
      <div className={styles.exp5ArchGrid}>
        {/* Left Column: Fault-Tolerant Client */}
        <div className={styles.archClientCol}>
          <div className={styles.archCard} style={{ borderColor: '#6366F1', backgroundColor: '#F8FAFC' }}>
            <div className={styles.archCardHead}>
              <div className={styles.archTitleWrap}>
                <Activity size={17} color="#6366F1" />
                <span className={styles.archNodeTitle}>FAULT-TOLERANT CLIENT</span>
              </div>
              <span className="badge badge-blue">HEARTBEAT DAEMON</span>
            </div>

            <div className={styles.archMetaRow}>
              <div>Target Server: <strong>{clientState.currentPrimary || 'None'}</strong></div>
              <div>Heartbeat Interval: <code>1000 ms</code></div>
              <div>
                Failure Threshold: <code>2 missed pings</code>
                {clientState.missedHeartbeats > 0 && (
                  <span className="badge badge-red" style={{ marginLeft: 6 }}>
                    {clientState.missedHeartbeats}/2 Missed
                  </span>
                )}
              </div>
              <div>Last Heartbeat: <code>{clientState.lastHeartbeat || 'Awaiting...'}</code></div>
            </div>

            <div className={styles.archActiveNotice}>
              <span className={styles.pulseGreenDot} />
              <span>Routing requests to: <strong>{clientState.currentPrimary}</strong></span>
            </div>
          </div>
        </div>

        {/* Center Column: Dual Replicated Servers + Replication Channel */}
        <div className={styles.archServersCol}>
          {/* Primary Server Card */}
          <div
            className={`${styles.archCard} ${
              !primaryState.alive
                ? styles.archCardCrashed
                : isPrimaryActive
                ? styles.archCardPrimaryActive
                : styles.archCardBackupStandby
            } ${primaryState.crashArmed ? styles.archCardArmed : ''}`}
          >
            <div className={styles.archCardHead}>
              <div className={styles.archTitleWrap}>
                <Server size={17} color={isPrimaryActive ? '#059669' : !primaryState.alive ? '#DC2626' : '#2563EB'} />
                <span className={styles.archNodeTitle}>PRIMARY SERVER</span>
                <span className={styles.archPortText}>:1201</span>
              </div>

              <div style={{ display: 'flex', gap: 6 }}>
                {primaryState.crashArmed && (
                  <span className={styles.badgeArmed}>
                    <AlertTriangle size={11} style={{ display: 'inline', marginRight: 3 }} />
                    CRASH ARMED
                  </span>
                )}
                <span
                  className={
                    !primaryState.alive
                      ? styles.badgeDown
                      : isPrimaryActive
                      ? styles.badgeCommitted
                      : styles.badgePrepared
                  }
                >
                  {primaryState.alive ? primaryState.role : 'DOWN'}
                </span>
              </div>
            </div>

            <div className={styles.archMetaRow}>
              <div>Committed Operations: <strong>{primaryState.committedOps}</strong></div>
              <div>Status: {isPrimaryActive ? <span className="val-green">Active Primary (Serves bookings)</span> : <span>Standby / Inactive</span>}</div>
            </div>

            {/* Server Controls */}
            <div className={styles.nodeBtnRow}>
              {primaryState.alive ? (
                <>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() => handleCrash('Primary')}
                    title="Simulate process crash (unexport RMI)"
                  >
                    <Flame size={12} color="#DC2626" />
                    <span>Crash</span>
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${primaryState.crashArmed ? 'btn-primary' : 'btn-outline'}`}
                    onClick={() => handleArmCrash('Primary')}
                    title="Arm crash right after next DB commit"
                  >
                    <AlertTriangle size={12} color={primaryState.crashArmed ? '#FFF' : '#D97706'} />
                    <span>{primaryState.crashArmed ? 'Armed' : 'Arm Fault'}</span>
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => handleRestart('Primary')}
                >
                  <RotateCcw size={12} />
                  <span>Restart & Recover</span>
                </button>
              )}
            </div>
          </div>

          {/* Synchronous Replication Channel Badge */}
          <div className={styles.archReplicaChannel}>
            <ArrowLeftRight size={14} color="#0284C7" />
            <span>Synchronous Replication Protocol: PREPARE ➔ ACK ➔ DB Commit ➔ COMMIT</span>
          </div>

          {/* Backup Server Card */}
          <div
            className={`${styles.archCard} ${
              !backupState.alive
                ? styles.archCardCrashed
                : isBackupActive
                ? styles.archCardBackupActive
                : styles.archCardBackupStandby
            } ${backupState.crashArmed ? styles.archCardArmed : ''}`}
          >
            <div className={styles.archCardHead}>
              <div className={styles.archTitleWrap}>
                <Server size={17} color={isBackupActive ? '#059669' : !backupState.alive ? '#DC2626' : '#2563EB'} />
                <span className={styles.archNodeTitle}>BACKUP SERVER</span>
                <span className={styles.archPortText}>:1202</span>
              </div>

              <div style={{ display: 'flex', gap: 6 }}>
                {backupState.crashArmed && (
                  <span className={styles.badgeArmed}>
                    <AlertTriangle size={11} style={{ display: 'inline', marginRight: 3 }} />
                    CRASH ARMED
                  </span>
                )}
                <span
                  className={
                    !backupState.alive
                      ? styles.badgeDown
                      : isBackupActive
                      ? styles.badgeCommitted
                      : styles.badgePrepared
                  }
                >
                  {backupState.alive ? backupState.role : 'DOWN'}
                </span>
              </div>
            </div>

            <div className={styles.archMetaRow}>
              <div>Committed Operations: <strong>{backupState.committedOps}</strong></div>
              <div>Status: {isBackupActive ? <span className="val-green">Active Primary (Promoted)</span> : <span>Standby Replica (Keeps replica log)</span>}</div>
            </div>

            {/* Server Controls */}
            <div className={styles.nodeBtnRow}>
              {backupState.alive ? (
                <>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() => handleCrash('Backup')}
                  >
                    <Flame size={12} color="#DC2626" />
                    <span>Crash</span>
                  </button>
                  {backupState.role === 'STANDBY' && (
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => handleActivate('Backup')}
                      title="Promote Standby to Active Primary"
                    >
                      <Zap size={12} />
                      <span>Promote</span>
                    </button>
                  )}
                  {isBackupActive && (
                    <button
                      type="button"
                      className={`btn btn-sm ${backupState.crashArmed ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleArmCrash('Backup')}
                    >
                      <AlertTriangle size={12} />
                      <span>Arm Fault</span>
                    </button>
                  )}
                </>
              ) : (
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => handleRestart('Backup')}
                >
                  <RotateCcw size={12} />
                  <span>Restart & Resync</span>
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Right Column: Shared Database */}
        <div className={styles.archDbCol}>
          <div className={styles.archCard} style={{ borderColor: '#2563EB', backgroundColor: '#F8FAFC' }}>
            <div className={styles.archCardHead}>
              <div className={styles.archTitleWrap}>
                <Database size={17} color="#2563EB" />
                <span className={styles.archNodeTitle}>SHARED DATABASE</span>
              </div>
              <span className="badge badge-green">ACID TRUTH</span>
            </div>

            <div className={styles.archMetaRow}>
              <div>Engine: <strong>{storeType}</strong></div>
              <div>Isolation: <code>Read Committed / Serializable</code></div>
              <div>Shared By: <code>Primary (:1201) & Backup (:1202)</code></div>
              <div>Resolution: <span>In-doubt PREPARED ops verified against DB</span></div>
            </div>

            <div style={{ fontSize: 11.5, color: 'var(--text-muted)', lineHeight: 1.4 }}>
              Both servers connect to this single database. The primary commits only after the backup acknowledges PREPARE.
            </div>
          </div>
        </div>
      </div>

      {/* ── 4-Phase Guided Walkthrough Bar ── */}
      <div className={styles.testCard}>
        <div className={styles.cardHeaderRow}>
          <div>
            <h3 className={styles.cardHeader} style={{ marginBottom: 2 }}>
              Automated 4-Phase Failover & Recovery Walkthrough
            </h3>
            <span style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>
              Step-by-step interactive verification matching FailoverDemo.java.
            </span>
          </div>

          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={handleRunAutoStory}
            disabled={actionLoading || demoRunning}
          >
            {demoRunning ? <span className="spinner" /> : <Sparkles size={13} />}
            <span>Run Complete 4-Phase Story</span>
          </button>
        </div>

        <div className={styles.stepperGrid}>
          {/* Step 1 */}
          <div className={`${styles.stepCard} ${demoStep === 1 ? styles.stepCardActive : demoStep > 1 ? styles.stepCardDone : ''}`}>
            <div>
              <div className={styles.stepCardNum}>Phase 1</div>
              <div className={styles.stepCardTitle}>Normal Replication</div>
              <div className={styles.stepCardDesc}>
                Synchronous 2-phase replication: PREPARE ➔ ACK ➔ DB Commit ➔ COMMIT. Both server logs match.
              </div>
            </div>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => handleRunDemoStep(1)}
              disabled={actionLoading || demoRunning}
            >
              <span>Run Phase 1 (Alice)</span>
            </button>
          </div>

          {/* Step 2 */}
          <div className={`${styles.stepCard} ${demoStep === 2 ? styles.stepCardActive : demoStep > 2 ? styles.stepCardDone : ''}`}>
            <div>
              <div className={styles.stepCardNum}>Phase 2</div>
              <div className={styles.stepCardTitle}>Crash & Idempotency</div>
              <div className={styles.stepCardDesc}>
                Primary dies right after DB commit. Client retries with SAME operationId on Backup. Zero duplicate bookings!
              </div>
            </div>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => handleRunDemoStep(2)}
              disabled={actionLoading || demoRunning}
            >
              <span>Run Phase 2 (Bob)</span>
            </button>
          </div>

          {/* Step 3 */}
          <div className={`${styles.stepCard} ${demoStep === 3 ? styles.stepCardActive : demoStep > 3 ? styles.stepCardDone : ''}`}>
            <div>
              <div className={styles.stepCardNum}>Phase 3</div>
              <div className={styles.stepCardTitle}>Primary Recovery</div>
              <div className={styles.stepCardDesc}>
                Primary restarts in RECOVERING, syncs ops from Backup, steps down Backup, and re-claims ACTIVE_PRIMARY.
              </div>
            </div>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => handleRunDemoStep(3)}
              disabled={actionLoading || demoRunning}
            >
              <span>Run Phase 3 (Carol)</span>
            </button>
          </div>

          {/* Step 4 */}
          <div className={`${styles.stepCard} ${demoStep === 4 ? styles.stepCardActive : demoStep > 4 ? styles.stepCardDone : ''}`}>
            <div>
              <div className={styles.stepCardNum}>Phase 4</div>
              <div className={styles.stepCardTitle}>Heartbeat Failover</div>
              <div className={styles.stepCardDesc}>
                Primary crashes while idle. Client misses 2 heartbeats, declares primary dead, and auto-promotes Backup.
              </div>
            </div>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => handleRunDemoStep(4)}
              disabled={actionLoading || demoRunning}
            >
              <span>Run Phase 4 (Dave)</span>
            </button>
          </div>
        </div>
      </div>

      {/* ── Interactive Manual Booking & Fault Injection Workbench ── */}
      <div className={styles.twoColGrid}>
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader}>Interactive Seat Booking & Fault Injection</h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {/* Target Seat Picker */}
            <div className="field">
              <label>Target Seat</label>
              <select
                className="input"
                value={selectedSeat}
                onChange={e => setSelectedSeat(e.target.value)}
              >
                {availableSeats.map(s => {
                  const p = parseSeat(s)
                  return (
                    <option key={p.id} value={p.id}>
                      {p.label} (ID: {p.id})
                    </option>
                  )
                })}
              </select>
            </div>

            {/* Customer Name */}
            <div className="field">
              <label>Customer Name</label>
              <input
                type="text"
                className="input"
                value={userName}
                onChange={e => setUserName(e.target.value)}
                placeholder="e.g. Alice"
              />
            </div>

            {/* Operation ID (Idempotency Key) */}
            <div className="field">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <label style={{ margin: 0 }}>
                  Client Operation ID (Idempotency Key)
                </label>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  style={{ padding: '2px 6px', fontSize: 11 }}
                  onClick={generateNewOpId}
                >
                  <RotateCcw size={11} />
                  <span>Generate New</span>
                </button>
              </div>
              <input
                type="text"
                className="input"
                value={operationId}
                onChange={e => setOperationId(e.target.value)}
                placeholder="e.g. op-a1b2c3d4"
              />
              <span style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 3 }}>
                Re-send the same ID to verify idempotency deduplication!
              </span>
            </div>

            {/* Target Routing Selector */}
            <div className="field">
              <label>Request Destination Routing</label>
              <select
                className="input"
                value={targetNode}
                onChange={e => setTargetNode(e.target.value)}
              >
                <option value="auto">Client Auto-Routing (Automated Failover & Retry)</option>
                <option value="Primary">Direct to Primary Server (:1201)</option>
                <option value="Backup">Direct to Backup Server (:1202) [Tests NOT_PRIMARY]</option>
              </select>
            </div>

            {/* Actions */}
            <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
              <button
                type="button"
                className="btn btn-primary"
                style={{ flex: 1 }}
                onClick={handleBookSeat}
                disabled={bookingLoading}
              >
                {bookingLoading ? <span className="spinner" /> : <Play size={14} />}
                <span>Submit Booking Request</span>
              </button>
            </div>
          </div>

          {/* Booking Outcome Feedback */}
          {lastBookingResult && (
            <div
              style={{
                marginTop: 18,
                padding: 14,
                borderRadius: 'var(--radius-sm)',
                backgroundColor: lastBookingResult.success ? '#ECFDF5' : '#FEF2F2',
                border: `1px solid ${lastBookingResult.success ? '#A7F3D0' : '#FECACA'}`
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <strong>Booking Response</strong>
                <span className={`badge ${lastBookingResult.success ? 'badge-green' : 'badge-red'}`}>
                  {lastBookingResult.success ? 'SUCCESS' : 'REJECTED / FAILED'}
                </span>
              </div>
              <div style={{ fontSize: 12.5, color: '#1F2937', marginBottom: 4 }}>
                <code>{lastBookingResult.result}</code>
              </div>
              <div style={{ fontSize: 11.5, color: '#4B5563', display: 'flex', gap: 14, flexWrap: 'wrap' }}>
                <span>Handled by: <strong>{lastBookingResult.executedBy}</strong></span>
                <span>Attempts: <strong>{lastBookingResult.attempts}</strong></span>
                {lastBookingResult.failoverOccurred && (
                  <span className="val-red"><strong>Failover triggered!</strong></span>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Real-Time Dual Replica Log Comparator */}
        <div className={styles.testCard}>
          <div className={styles.cardHeaderRow}>
            <h3 className={styles.cardHeader} style={{ marginBottom: 0 }}>
              Dual Replica Logs (Primary vs Backup)
            </h3>
            <span className={`badge ${inSync ? 'badge-green' : 'badge-red'}`}>
              {inSync ? 'IN SYNC' : 'DESYNCHRONIZED'}
            </span>
          </div>

          <div className={styles.replicaLogsGrid}>
            {/* Primary Replica Log */}
            <div>
              <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6, color: '#059669' }}>
                Primary (:1201) Log ({primaryLogs.length})
              </div>
              <div className={styles.replicaTableWrap}>
                <table className={styles.replicaTable}>
                  <thead>
                    <tr>
                      <th>OpID</th>
                      <th>User</th>
                      <th>State</th>
                    </tr>
                  </thead>
                  <tbody>
                    {primaryLogs.length === 0 ? (
                      <tr>
                        <td colSpan={3} style={{ textAlign: 'center', color: '#9CA3AF' }}>No records</td>
                      </tr>
                    ) : (
                      primaryLogs.map(op => (
                        <tr key={op.operationId}>
                          <td><code>{op.operationId}</code></td>
                          <td>{op.userName}</td>
                          <td>
                            <span className={op.state === 'COMMITTED' ? styles.badgeCommitted : styles.badgePrepared}>
                              {op.state}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Backup Replica Log */}
            <div>
              <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6, color: '#2563EB' }}>
                Backup (:1202) Log ({backupLogs.length})
              </div>
              <div className={styles.replicaTableWrap}>
                <table className={styles.replicaTable}>
                  <thead>
                    <tr>
                      <th>OpID</th>
                      <th>User</th>
                      <th>State</th>
                    </tr>
                  </thead>
                  <tbody>
                    {backupLogs.length === 0 ? (
                      <tr>
                        <td colSpan={3} style={{ textAlign: 'center', color: '#9CA3AF' }}>No records</td>
                      </tr>
                    ) : (
                      backupLogs.map(op => (
                        <tr key={op.operationId}>
                          <td><code>{op.operationId}</code></td>
                          <td>{op.userName}</td>
                          <td>
                            <span className={op.state === 'COMMITTED' ? styles.badgeCommitted : styles.badgePrepared}>
                              {op.state}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div style={{ marginTop: 14, fontSize: 11.5, color: 'var(--text-muted)', lineHeight: 1.4 }}>
            In normal operation, every client operation logs as <code>PREPARED</code> on the Backup, commits to the DB, and transitions to <code>COMMITTED</code> on both nodes.
          </div>
        </div>
      </div>

      {/* ── Live Protocol Message Stream (Terminal) ── */}
      <div className={styles.testCard}>
        <div className={styles.cardHeaderRow}>
          <h3 className={styles.cardHeader} style={{ marginBottom: 0 }}>
            Real-Time RMI Protocol Message Stream
          </h3>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => { setTerminalLogs([]); logLenRef.current = 0 }}
          >
            <RotateCcw size={12} />
            <span>Clear Console</span>
          </button>
        </div>
        <Terminal lines={terminalLogs} />
      </div>

      {/* ── Technical Summary ── */}
      <div className={styles.summaryCard}>
        <h4 className={styles.summaryTitle}>Distributed Systems Theory: Primary-Backup Fault Tolerance</h4>
        <p className={styles.summaryText}>
          <strong>Synchronous Replication:</strong> Before committing any transaction to persistent storage, the active primary transmits a <code>PREPARE</code> message to the backup standby server and awaits an explicit acknowledgement (ACK). Once acknowledged, the booking commits to the database, followed by a <code>COMMIT</code> confirmation to the replica log.
          <br /><br />
          <strong>Client-Driven Failover & Liveness:</strong> The client heartbeat monitor polls the active primary every second. Upon 2 consecutive missed heartbeats (or network failure), the client initiates failover, connects to the backup node, and issues an <code>activate()</code> remote invocation promoting the standby server to <code>ACTIVE_PRIMARY</code>.
          <br /><br />
          <strong>Idempotency via Operation IDs:</strong> Network failures frequently leave clients uncertain whether an in-flight booking succeeded before the server died. By generating a unique client-side <code>operationId</code> per booking intent and reusing it during retries, the promoted backup looks up the operation log and returns the original confirmed booking without risking duplicate reservations.
        </p>
      </div>
    </div>
  )
}
