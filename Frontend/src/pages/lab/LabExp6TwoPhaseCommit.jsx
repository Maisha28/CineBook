import { useState, useEffect } from 'react'
import { exp6 } from '../../api'
import { useToast } from '../../components/Toast'
import {
  Layers,
  Play,
  RotateCcw,
  ShieldCheck,
  Database,
  RefreshCw,
} from 'lucide-react'
import styles from './Lab.module.css'

export default function LabExp6TwoPhaseCommit() {
  const toast = useToast()

  // 2PC Cluster State
  const [participants, setParticipants] = useState([])
  const [quorumInfo, setQuorumInfo] = useState({ N: 5, W: 3, R: 3, strongConsistency: true })
  const [txCount, setTxCount] = useState(0)

  // Transaction Form State
  const [txId, setTxId] = useState('')
  const [seatId, setSeatId] = useState('seat-A1')
  const [userName, setUserName] = useState('Alice')
  const [amount, setAmount] = useState('450')
  const [txLoading, setTxLoading] = useState(false)
  const [lastTxResult, setLastTxResult] = useState(null)

  // Quorum State
  const [NVal, setNVal] = useState(5)
  const [WVal, setWVal] = useState(3)
  const [RVal, setRVal] = useState(3)
  const [quorumLoading, setQuorumLoading] = useState(false)
  const [lastQuorumResult, setLastQuorumResult] = useState(null)

  useEffect(() => {
    generateNewTxId()
    fetchStatus()
  }, [])

  function generateNewTxId() {
    setTxId('tx-' + Math.random().toString(36).substring(2, 9))
  }

  async function fetchStatus() {
    try {
      const data = await exp6.status()
      if (data) {
        setParticipants(data.participants || [])
        if (data.quorum) setQuorumInfo(data.quorum)
        setTxCount(data.transactionCount || 0)
      }
    } catch {}
  }

  async function handleExecuteTransaction() {
    if (!userName.trim() || !seatId.trim()) {
      toast('Customer Name and Seat required', 'warning')
      return
    }

    setTxLoading(true)
    setLastTxResult(null)

    try {
      const res = await exp6.transaction({
        txId,
        seatId,
        userName: userName.trim(),
        amount: parseFloat(amount) || 450
      })

      setLastTxResult(res)

      if (res.success) {
        toast(`Transaction ${res.transactionId} COMMITTED globally!`, 'success')
      } else {
        toast(`Transaction ${res.transactionId} ABORTED globally!`, 'error')
      }

      fetchStatus()
      generateNewTxId()
    } catch (e) {
      toast(e.message || 'Transaction execution failed', 'error')
    }
    setTxLoading(false)
  }

  async function handleToggleFault(nodeId, action) {
    try {
      await exp6.fault(nodeId, action)
      toast(`Updated ${nodeId} (${action})`, 'info')
      fetchStatus()
    } catch (e) {
      toast(e.message || 'Fault update failed', 'error')
    }
  }

  async function handleUpdateQuorumConfig() {
    setQuorumLoading(true)
    try {
      await exp6.quorumConfig(NVal, WVal, RVal)
      toast(`Quorum reconfigured: N=${NVal}, W=${WVal}, R=${RVal}`, 'success')
      fetchStatus()
    } catch (e) {
      toast(e.message || 'Quorum config failed', 'error')
    }
    setQuorumLoading(false)
  }

  async function handleQuorumWrite() {
    setQuorumLoading(true)
    try {
      const res = await exp6.quorumWrite({ version: Math.floor(Date.now() / 1000 % 1000) })
      setLastQuorumResult(res)
      if (res.success) toast(res.details, 'success')
      else toast(res.details, 'error')
    } catch (e) {
      toast(e.message || 'Quorum write failed', 'error')
    }
    setQuorumLoading(false)
  }

  async function handleQuorumRead() {
    setQuorumLoading(true)
    try {
      const res = await exp6.quorumRead()
      setLastQuorumResult(res)
      if (res.success) toast(res.details, res.details.includes('STALE') ? 'warning' : 'success')
      else toast(res.details, 'error')
    } catch (e) {
      toast(e.message || 'Quorum read failed', 'error')
    }
    setQuorumLoading(false)
  }

  async function handleReset() {
    try {
      await exp6.reset()
      setLastTxResult(null)
      setLastQuorumResult(null)
      toast('Exp 6 state reset', 'info')
      fetchStatus()
    } catch (e) {
      toast(e.message || 'Reset error', 'error')
    }
  }

  const isStrong = quorumInfo.W + quorumInfo.R > quorumInfo.N

  return (
    <div className={styles.expContainer}>
      {/* ── Header ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 6: Distributed Two-Phase Commit (2PC) & Quorum Consensus</h2>
          <p className={styles.expSubtitle}>
            Atomic multi-participant transactions across Seat Reservation, Payment Gateway, and Inventory services using a 2PC coordinator with Phase 1 Voting and Phase 2 Global Commit/Rollback, alongside a Quorum Consensus engine ($N, W, R$).
          </p>
        </div>

        <div className={styles.statusGroup}>
          <span className={styles.statusPill}>
            <ShieldCheck size={13} color="#059669" />
            <span>2PC Coordinator: <strong>ACTIVE</strong></span>
          </span>
          <span className={styles.statusPill}>
            <Layers size={13} color="#2563EB" />
            <span>Participants: <strong>{participants.length} Services</strong></span>
          </span>
          <span className={styles.statusPill}>
            <Database size={13} color={isStrong ? '#059669' : '#DC2626'} />
            <span>Quorum: <strong>{isStrong ? 'Strong Consistency' : 'Eventual (Weak)'}</strong></span>
          </span>
        </div>
      </div>

      {/* ── 2PC Architecture & Participant Control Grid ── */}
      <div className={styles.testCard}>
        <div className={styles.cardHeaderRow}>
          <div>
            <h3 className={styles.cardHeader} style={{ marginBottom: 2 }}>
              Distributed 2PC Participant Services & Fault Injection
            </h3>
            <span style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>
              Simulate service failures, forced aborts, or network timeouts to verify atomic 2PC rollbacks.
            </span>
          </div>

          <button type="button" className="btn btn-ghost btn-sm" onClick={handleReset}>
            <RotateCcw size={12} />
            <span>Reset Services</span>
          </button>
        </div>

        <div className={styles.stepperGrid} style={{ marginTop: 14 }}>
          {/* Coordinator Card */}
          <div className={styles.stepCard} style={{ backgroundColor: '#F8FAFC', borderColor: '#6366F1' }}>
            <div className={styles.stepCardNum} style={{ backgroundColor: '#EEF2FF', color: '#4F46E5' }}>
              COORDINATOR
            </div>
            <div className={styles.stepCardTitle}>2PC Master Coordinator</div>
            <div className={styles.stepCardDesc}>
              Orchestrates Phase 1 VOTE_REQUEST and enforces Phase 2 GLOBAL_COMMIT or GLOBAL_ABORT.
            </div>
            <div style={{ marginTop: 10, fontSize: 11.5, color: '#4B5563' }}>
              Executed Transactions: <strong>{txCount}</strong>
            </div>
          </div>

          {/* Participants Cards */}
          {participants.map(p => (
            <div
              key={p.nodeId}
              className={`${styles.stepCard} ${!p.alive ? styles.stepCardDone : p.forceAbort || p.simulateTimeout ? styles.stepCardActive : ''}`}
            >
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                  <span className={styles.stepCardNum}>{p.nodeId}</span>
                  <span className={`badge ${!p.alive ? 'badge-red' : p.forceAbort ? 'badge-amber' : p.simulateTimeout ? 'badge-blue' : 'badge-green'}`}>
                    {!p.alive ? 'DOWN' : p.forceAbort ? 'FORCE ABORT' : p.simulateTimeout ? 'TIMEOUT' : 'HEALTHY'}
                  </span>
                </div>
                <div className={styles.stepCardTitle}>{p.serviceName}</div>
                <div className={styles.stepCardDesc}>
                  {!p.alive ? 'Service is offline (votes ABORT)' : p.forceAbort ? 'Injected fault: Votes VOTE_ABORT' : p.simulateTimeout ? 'Injected timeout during Phase 1' : 'Ready to prepare local lock'}
                </div>
              </div>

              <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
                {p.alive ? (
                  <>
                    <button
                      type="button"
                      className={`btn btn-sm ${p.forceAbort ? 'btn-primary' : 'btn-outline'}`}
                      style={{ padding: '3px 8px', fontSize: 11 }}
                      onClick={() => handleToggleFault(p.nodeId, 'abort')}
                    >
                      <span>{p.forceAbort ? 'Force Commit' : 'Force Abort'}</span>
                    </button>
                    <button
                      type="button"
                      className={`btn btn-sm ${p.simulateTimeout ? 'btn-primary' : 'btn-outline'}`}
                      style={{ padding: '3px 8px', fontSize: 11 }}
                      onClick={() => handleToggleFault(p.nodeId, 'timeout')}
                    >
                      <span>{p.simulateTimeout ? 'Clear Timeout' : 'Timeout'}</span>
                    </button>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      style={{ padding: '3px 8px', fontSize: 11, color: '#DC2626' }}
                      onClick={() => handleToggleFault(p.nodeId, 'crash')}
                    >
                      <span>Crash</span>
                    </button>
                  </>
                ) : (
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    style={{ padding: '3px 8px', fontSize: 11 }}
                    onClick={() => handleToggleFault(p.nodeId, 'recover')}
                  >
                    <span>Recover Node</span>
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ── 2PC Transaction Execution & Result ── */}
      <div className={styles.twoColGrid}>
        <div className={styles.testCard}>
          <h3 className={styles.cardHeader}>Execute Distributed 2PC Transaction</h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="field">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <label style={{ margin: 0 }}>Transaction ID</label>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  style={{ padding: '2px 6px', fontSize: 11 }}
                  onClick={generateNewTxId}
                >
                  <RotateCcw size={11} />
                  <span>Generate</span>
                </button>
              </div>
              <input type="text" className="input" value={txId} onChange={e => setTxId(e.target.value)} />
            </div>

            <div className="field">
              <label>Customer Name</label>
              <input type="text" className="input" value={userName} onChange={e => setUserName(e.target.value)} />
            </div>

            <div className="field">
              <label>Seat ID</label>
              <input type="text" className="input" value={seatId} onChange={e => setSeatId(e.target.value)} />
            </div>

            <div className="field">
              <label>Transaction Amount (₹)</label>
              <input type="text" className="input" value={amount} onChange={e => setAmount(e.target.value)} />
            </div>

            <button
              type="button"
              className="btn btn-primary"
              onClick={handleExecuteTransaction}
              disabled={txLoading}
            >
              {txLoading ? <span className="spinner" /> : <Play size={14} />}
              <span>Initiate 2-Phase Commit</span>
            </button>
          </div>

          {lastTxResult && (
            <div
              style={{
                marginTop: 18,
                padding: 14,
                borderRadius: 'var(--radius-sm)',
                backgroundColor: lastTxResult.success ? '#ECFDF5' : '#FEF2F2',
                border: `1px solid ${lastTxResult.success ? '#A7F3D0' : '#FECACA'}`
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <strong>2PC Global Decision</strong>
                <span className={`badge ${lastTxResult.success ? 'badge-green' : 'badge-red'}`}>
                  {lastTxResult.globalDecision}
                </span>
              </div>
              <div style={{ fontSize: 12.5, color: '#1F2937', marginBottom: 8 }}>
                {lastTxResult.summary}
              </div>

              <div style={{ fontSize: 11.5, color: '#4B5563' }}>
                <strong>Phase 1 Votes:</strong>
                <div style={{ display: 'flex', gap: 10, marginTop: 4, flexWrap: 'wrap' }}>
                  {Object.entries(lastTxResult.votes || {}).map(([node, vote]) => (
                    <span key={node} className={`badge ${vote === 'VOTE_COMMIT' ? 'badge-green' : 'badge-red'}`}>
                      {node}: {vote}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* ── Quorum Consensus Sandbox ── */}
        <div className={styles.testCard}>
          <div className={styles.cardHeaderRow}>
            <h3 className={styles.cardHeader} style={{ marginBottom: 0 }}>
              Quorum Consensus Sandbox (N, W, R)
            </h3>
            <span className={`badge ${isStrong ? 'badge-green' : 'badge-red'}`}>
              {isStrong ? 'W + R > N (Strong)' : 'W + R <= N (Weak)'}
            </span>
          </div>

          <div style={{ display: 'flex', gap: 12, margin: '14px 0' }}>
            <div className="field" style={{ flex: 1 }}>
              <label>Total Nodes (N)</label>
              <input type="number" className="input" value={NVal} onChange={e => setNVal(parseInt(e.target.value) || 1)} min="1" max="10" />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <label>Write Quorum (W)</label>
              <input type="number" className="input" value={WVal} onChange={e => setWVal(parseInt(e.target.value) || 1)} min="1" max={NVal} />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <label>Read Quorum (R)</label>
              <input type="number" className="input" value={RVal} onChange={e => setRVal(parseInt(e.target.value) || 1)} min="1" max={NVal} />
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
            <button type="button" className="btn btn-outline btn-sm" onClick={handleUpdateQuorumConfig} disabled={quorumLoading}>
              <span>Reconfigure</span>
            </button>
            <button type="button" className="btn btn-primary btn-sm" onClick={handleQuorumWrite} disabled={quorumLoading}>
              <Play size={12} />
              <span>Execute Quorum Write</span>
            </button>
            <button type="button" className="btn btn-primary btn-sm" onClick={handleQuorumRead} disabled={quorumLoading}>
              <RefreshCw size={12} />
              <span>Execute Quorum Read</span>
            </button>
          </div>

          {lastQuorumResult && (
            <div
              style={{
                padding: 12,
                borderRadius: 'var(--radius-sm)',
                backgroundColor: lastQuorumResult.success ? '#F0FDF4' : '#FEF2F2',
                border: `1px solid ${lastQuorumResult.success ? '#BBF7D0' : '#FECACA'}`,
                fontSize: 12
              }}
            >
              <div style={{ fontWeight: 700, marginBottom: 4 }}>
                Quorum {lastQuorumResult.operation} Result ({lastQuorumResult.acksReceived} ACKs)
              </div>
              <div>{lastQuorumResult.details}</div>
              <div style={{ marginTop: 6, fontSize: 11, color: '#4B5563' }}>
                Node Versions: {JSON.stringify(lastQuorumResult.nodeVersions)}
              </div>
            </div>
          )}

          <div style={{ marginTop: 14, fontSize: 11.5, color: 'var(--text-muted)', lineHeight: 1.4 }}>
            <strong>Quorum Theory:</strong> When $W + R &gt; N$, the write quorum and read quorum are mathematically guaranteed to overlap on at least one node, providing <strong>Strong Consistency</strong>. If $W + R \le N$, reads may return stale version records.
          </div>
        </div>
      </div>
    </div>
  )
}
