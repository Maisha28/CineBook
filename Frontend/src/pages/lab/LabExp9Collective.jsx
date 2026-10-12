import { useState, useEffect } from 'react'
import { exp9 } from '../../api'
import { useToast } from '../../components/Toast'
import Terminal from '../../components/Terminal'
import {
  Server,
  Play,
  RotateCcw,
  Radio,
  Share2,
  Merge,
  Sigma,
  Network,
  Flame,
  ShieldCheck,
  Sparkles,
  ArrowRight
} from 'lucide-react'
import styles from './Lab.module.css'

const DEFAULT_MESSAGE = "PROMO50 | Today's Lineup: Dune Part Two, Interstellar, Inception"

function defaultWorkload(n) {
  return Array.from({ length: Math.max(n, 0) }, (_, i) => i * 10).join(', ')
}

export default function LabExp9Collective() {
  const toast = useToast()

  // Communicator (COMM_WORLD) state
  const [size, setSize] = useState(0)
  const [processes, setProcesses] = useState([])
  const [lastBroadcastMessage, setLastBroadcastMessage] = useState(null)
  const [lastScatterInput, setLastScatterInput] = useState(null)
  const [lastGatherResult, setLastGatherResult] = useState(null)
  const [lastReduceSum, setLastReduceSum] = useState(null)
  const [counts, setCounts] = useState({ broadcast: 0, scatter: 0, gather: 0, reduce: 0 })
  const [logs, setLogs] = useState([])

  // Form state
  const [sizeInput, setSizeInput] = useState(4)
  const [messageInput, setMessageInput] = useState(DEFAULT_MESSAGE)
  const [valuesInput, setValuesInput] = useState('0, 10, 20, 30')

  // Loading flags
  const [rebuildLoading, setRebuildLoading] = useState(false)
  const [bcastLoading, setBcastLoading] = useState(false)
  const [scatterLoading, setScatterLoading] = useState(false)
  const [gatherLoading, setGatherLoading] = useState(false)
  const [reduceLoading, setReduceLoading] = useState(false)
  const [pipelineLoading, setPipelineLoading] = useState(false)
  const [resetLoading, setResetLoading] = useState(false)

  useEffect(() => {
    fetchStatus()
    fetchLogs()
  }, [])

  function applyStatus(data) {
    if (!data) return
    setSize(data.size || 0)
    setProcesses(data.processes || [])
    setLastBroadcastMessage(data.lastBroadcastMessage ?? null)
    setLastScatterInput(data.lastScatterInput ?? null)
    setLastGatherResult(data.lastGatherResult ?? null)
    setLastReduceSum(data.lastReduceSum ?? null)
    if (data.counts) setCounts(data.counts)
  }

  async function fetchStatus() {
    try {
      const data = await exp9.status()
      applyStatus(data)
      if (data && data.size) {
        setSizeInput(data.size)
        setValuesInput(defaultWorkload(data.size))
      }
    } catch {}
  }

  async function fetchLogs() {
    try {
      const data = await exp9.getLogs()
      if (Array.isArray(data)) setLogs(data)
    } catch {}
  }

  function parseValues() {
    return valuesInput
      .split(',')
      .map(s => parseInt(s.trim(), 10))
      .filter(n => !Number.isNaN(n))
  }

  async function handleRebuild() {
    const n = Math.max(2, Math.min(parseInt(sizeInput, 10) || 4, 8))
    setRebuildLoading(true)
    try {
      const data = await exp9.start(n)
      applyStatus(data)
      setValuesInput(defaultWorkload(data.size))
      toast(`COMM_WORLD rebuilt with ${data.size} ranks (rank 0 = HQ Coordinator)`, 'success')
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Failed to rebuild cluster', 'error')
    }
    setRebuildLoading(false)
  }

  async function handleBroadcast() {
    if (!messageInput.trim()) {
      toast('Broadcast message required', 'warning')
      return
    }
    setBcastLoading(true)
    try {
      const data = await exp9.broadcast(messageInput.trim())
      applyStatus(data)
      toast(`MPI_Bcast delivered from Rank 0 to all ${data.size} ranks`, 'success')
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Broadcast failed', 'error')
    }
    setBcastLoading(false)
  }

  async function handleScatter() {
    const values = parseValues()
    if (values.length !== size) {
      toast(`Scatter requires exactly ${size} comma-separated values (one per rank)`, 'warning')
      return
    }
    setScatterLoading(true)
    try {
      const data = await exp9.scatter(values)
      applyStatus(data)
      toast(`MPI_Scatter distributed ${values.length} chunks — one per rank`, 'success')
      fetchLogs()
    } catch (e) {
      toast(e.response?.data?.error || e.message || 'Scatter failed', 'error')
    }
    setScatterLoading(false)
  }

  async function handleGather() {
    setGatherLoading(true)
    try {
      const data = await exp9.gather()
      applyStatus(data)
      toast('MPI_Gather collected computed values back at Rank 0', 'success')
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Gather failed', 'error')
    }
    setGatherLoading(false)
  }

  async function handleReduce() {
    setReduceLoading(true)
    try {
      const data = await exp9.reduce()
      applyStatus(data)
      toast(`MPI_Reduce(SUM) completed — total = ${data.lastReduceSum}`, 'success')
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Reduce failed', 'error')
    }
    setReduceLoading(false)
  }

  async function handleRunAll() {
    const values = parseValues()
    if (values.length !== size) {
      toast(`Need exactly ${size} scatter values to run the full pipeline`, 'warning')
      return
    }
    setPipelineLoading(true)
    try {
      const data = await exp9.runAll(messageInput.trim() || DEFAULT_MESSAGE, values)
      applyStatus(data)
      toast('Full pipeline executed: Bcast → Scatter → Gather → Reduce', 'success')
      fetchLogs()
    } catch (e) {
      toast(e.response?.data?.error || e.message || 'Pipeline run failed', 'error')
    }
    setPipelineLoading(false)
  }

  async function handleFault(rank, currentlyAlive) {
    try {
      const data = await exp9.fault(rank, currentlyAlive ? 'crash' : 'recover')
      applyStatus(data)
      toast(
        currentlyAlive ? `Rank ${rank} crashed (offline)` : `Rank ${rank} recovered (online)`,
        currentlyAlive ? 'warning' : 'success'
      )
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Fault update failed', 'error')
    }
  }

  async function handleReset() {
    setResetLoading(true)
    try {
      const data = await exp9.reset()
      applyStatus(data)
      toast('Communicator state reset — ranks remain registered', 'info')
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Reset failed', 'error')
    }
    setResetLoading(false)
  }

  const healthyCount = processes.filter(p => p.alive).length

  return (
    <div className={styles.expContainer}>
      {/* ── Header & Status ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 9: MPI Collective Communication</h2>
          <p className={styles.expSubtitle}>
            Models the CineBook Booking Cluster as an MPI communicator (<code>COMM_WORLD</code>): Rank 0 is the HQ
            Coordinator and ranks 1..N-1 are regional branch nodes. Demonstrates <strong>Broadcast</strong>,{' '}
            <strong>Scatter</strong>, <strong>Gather</strong>, and <strong>Reduce(SUM)</strong>, each executed
            concurrently — one real thread per rank.
          </p>
        </div>

        <div className={styles.statusGroup}>
          <span className={styles.statusPill}>
            <Network size={13} color={healthyCount === size && size > 0 ? '#059669' : '#DC2626'} />
            <span>Ranks Online: <strong>{healthyCount} / {size}</strong></span>
          </span>
          <span className={styles.statusPill}>
            <Radio size={13} color="#2563EB" />
            <span>Broadcasts: <strong>{counts.broadcast}</strong></span>
          </span>
          <span className={styles.statusPill}>
            <Sigma size={13} color="#7C3AED" />
            <span>Reduces: <strong>{counts.reduce}</strong></span>
          </span>
        </div>
      </div>

      {/* ── Compact Architecture Flow ── */}
      <div className={styles.flowCard}>
        <div className={styles.flowHeader}>Collective Communication Flow</div>
        <div className={styles.flowNodes}>
          <div className={`${styles.flowNode} ${styles.flowNodeActive}`}>
            <span className={styles.flowLabel}>Rank 0</span>
            <span className={styles.flowSub}>HQ Coordinator (root)</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>MPI_Bcast / MPI_Scatter</span>
            <span className={styles.flowSub}>root → all ranks</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>Ranks 1..N-1</span>
            <span className={styles.flowSub}>Branch nodes (parallel threads)</span>
          </div>
          <ArrowRight size={16} className={styles.flowArrow} />
          <div className={styles.flowNode}>
            <span className={styles.flowLabel}>MPI_Gather / MPI_Reduce</span>
            <span className={styles.flowSub}>all ranks → root</span>
          </div>
        </div>
      </div>

      {/* ── Cluster Control Bar ── */}
      <div className={styles.clusterControlBar}>
        <div className={styles.clusterInfoText}>
          <strong>Communicator Size:</strong> Rebuild <code>COMM_WORLD</code> with 2–8 ranks (one HQ Coordinator + N-1 branch nodes).
        </div>

        <div className={styles.clusterBtns}>
          <input
            type="number"
            className="input"
            style={{ width: 70 }}
            min={2}
            max={8}
            value={sizeInput}
            onChange={e => setSizeInput(e.target.value)}
          />
          <button type="button" className="btn btn-outline btn-sm" onClick={handleRebuild} disabled={rebuildLoading}>
            {rebuildLoading ? <span className="spinner" /> : <RotateCcw size={13} />}
            <span>Rebuild Cluster</span>
          </button>
          <button type="button" className="btn btn-primary btn-sm" onClick={handleRunAll} disabled={pipelineLoading}>
            {pipelineLoading ? <span className="spinner" /> : <Sparkles size={13} />}
            <span>Run Full Pipeline</span>
          </button>
          <button type="button" className="btn btn-ghost btn-sm" onClick={handleReset} disabled={resetLoading}>
            <RotateCcw size={13} />
            <span>Reset State</span>
          </button>
        </div>
      </div>

      {/* ── Rank Cluster Grid ── */}
      <div className={styles.nodeGrid}>
        {processes.map(p => (
          <div
            key={p.rank}
            className={`${styles.nodeBox} ${!p.alive ? styles.nodeBoxCrashed : ''} ${p.isRoot ? styles.nodeBoxLeader : ''}`}
          >
            <div className={styles.nodeHeader}>
              <div className={styles.nodeIdentity}>
                <Server size={16} color={p.isRoot ? '#059669' : !p.alive ? '#DC2626' : '#111827'} />
                <span className={styles.nodeIdText}>RANK {p.rank}</span>
              </div>
              <span className={`badge ${!p.alive ? 'badge-red' : p.isRoot ? 'badge-green' : 'badge-neutral'}`}>
                {!p.alive ? 'OFFLINE' : p.isRoot ? 'ROOT' : 'WORKER'}
              </span>
            </div>

            <div className={styles.nodePortText}>{p.name}</div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 11.5, color: 'var(--text-body)', marginTop: 2 }}>
              <div
                style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                title={p.receivedBroadcast || ''}
              >
                Broadcast: <strong>{p.receivedBroadcast ? `"${p.receivedBroadcast}"` : '—'}</strong>
              </div>
              <div>Scatter value: <strong>{p.scatterValue ?? '—'}</strong></div>
              <div>Computed (scatter + rank): <strong>{p.computedValue ?? '—'}</strong></div>
            </div>

            {!p.isRoot && (
              <div className={styles.nodeBtnRow}>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={() => handleFault(p.rank, p.alive)}
                >
                  {p.alive ? <Flame size={12} color="#DC2626" /> : <ShieldCheck size={12} color="#059669" />}
                  <span>{p.alive ? 'Crash' : 'Recover'}</span>
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* ── Collective Operation Panels ── */}
      <div className={styles.threeColGrid}>
        {/* Panel A: Broadcast */}
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Radio size={16} color="#2563EB" />
            <span>MPI_Bcast</span>
          </h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
            Root sends identical data to every rank (e.g. today's promo code / show lineup).
          </p>
          <div className="field">
            <label>Message</label>
            <input
              type="text"
              className="input"
              value={messageInput}
              onChange={e => setMessageInput(e.target.value)}
            />
          </div>
          <div className={styles.actionRow}>
            <button type="button" className="btn btn-primary btn-sm" onClick={handleBroadcast} disabled={bcastLoading} style={{ width: '100%' }}>
              {bcastLoading ? <span className="spinner" /> : <Play size={13} />}
              <span>Broadcast to All Ranks</span>
            </button>
          </div>
          {lastBroadcastMessage && (
            <div className={`${styles.resultBanner} ${styles.bannerOk}`}>
              Last broadcast: <strong>"{lastBroadcastMessage}"</strong>
            </div>
          )}
        </div>

        {/* Panel B: Scatter */}
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Share2 size={16} color="#7C3AED" />
            <span>MPI_Scatter</span>
          </h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
            Root splits an array of {size || 'N'} booking-workload values — one chunk per rank.
          </p>
          <div className="field">
            <label>Workload values (comma-separated, {size} required)</label>
            <input
              type="text"
              className="input"
              value={valuesInput}
              onChange={e => setValuesInput(e.target.value)}
            />
          </div>
          <div className={styles.actionRow}>
            <button type="button" className="btn btn-primary btn-sm" onClick={handleScatter} disabled={scatterLoading} style={{ width: '100%' }}>
              {scatterLoading ? <span className="spinner" /> : <Play size={13} />}
              <span>Scatter Workload</span>
            </button>
          </div>
          {lastScatterInput && (
            <div className={styles.chipsWrap} style={{ marginTop: 12 }}>
              {lastScatterInput.map((v, i) => (
                <span key={i} className={styles.seatChip}>R{i}: {v}</span>
              ))}
            </div>
          )}
        </div>

        {/* Panel C: Gather & Reduce */}
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Merge size={16} color="#059669" />
            <span>MPI_Gather / MPI_Reduce</span>
          </h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
            Each rank computes <code>scatterValue + rank</code> locally, then results flow back to Rank 0.
          </p>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="btn btn-outline btn-sm" onClick={handleGather} disabled={gatherLoading} style={{ flex: 1 }}>
              {gatherLoading ? <span className="spinner" /> : <Merge size={13} />}
              <span>Gather</span>
            </button>
            <button type="button" className="btn btn-outline btn-sm" onClick={handleReduce} disabled={reduceLoading} style={{ flex: 1 }}>
              {reduceLoading ? <span className="spinner" /> : <Sigma size={13} />}
              <span>Reduce (SUM)</span>
            </button>
          </div>
          {lastGatherResult && (
            <div className={styles.chipsWrap} style={{ marginTop: 14 }}>
              {lastGatherResult.map((v, i) => (
                <span key={i} className={styles.seatChip}>R{i}: {v ?? 'offline'}</span>
              ))}
            </div>
          )}
          {lastReduceSum !== null && (
            <div className={`${styles.resultBanner} ${styles.bannerOk}`}>
              MPI_Reduce(SUM) total: <strong>{lastReduceSum}</strong>
            </div>
          )}
        </div>
      </div>

      {/* ── Live Collective Communication Log ── */}
      <div className={styles.testCard}>
        <h3 className={styles.cardHeader}>Real-Time Collective Communication Message Stream</h3>
        <Terminal lines={logs} />
      </div>

      {/* ── Technical Summary ── */}
      <div className={styles.summaryCard}>
        <h4 className={styles.summaryTitle}>What This Demonstrates</h4>
        <p className={styles.summaryText}>
          <strong>Broadcast (MPI_Bcast):</strong> The root process (rank 0) sends identical data to every other
          process in the communicator in one collective call, instead of N-1 separate point-to-point sends.
          <br /><br />
          <strong>Scatter (MPI_Scatter):</strong> The root splits an array into equal chunks and distributes exactly
          one chunk per rank — the classic pattern for spreading a parallel workload across workers.
          <br /><br />
          <strong>Gather (MPI_Gather):</strong> The inverse of scatter — every rank sends its locally computed value
          back to the root, which collects them into a single ordered array.
          <br /><br />
          <strong>Reduce (MPI_Reduce):</strong> Like gather, but the root aggregates the values with an operator
          (here, <code>SUM</code>) as they arrive, producing a single result without materializing the full array —
          this is the Problem Statement #1 extension over the reference <code>mpi_collective.py</code> program.
        </p>
      </div>
    </div>
  )
}
