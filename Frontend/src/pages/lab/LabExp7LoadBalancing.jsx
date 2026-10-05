import { useState, useEffect } from 'react'
import { exp7 } from '../../api'
import { useToast } from '../../components/Toast'
import {
  Server,
  Zap,
  RotateCcw,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Play,
  Sliders,
  Activity,
  ArrowRight,
  Shield,
  Layers,
  Sparkles,
  RefreshCw,
  Terminal,
  BarChart2
} from 'lucide-react'
import styles from './Lab.module.css'

export default function LabExp7LoadBalancing() {
  const toast = useToast()

  // Load Balancer Cluster State
  const [algorithm, setAlgorithm] = useState('ROUND_ROBIN')
  const [servers, setServers] = useState([])
  const [totalRequests, setTotalRequests] = useState(0)
  const [totalFailovers, setTotalFailovers] = useState(0)
  const [recentHistory, setRecentHistory] = useState([])
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(false)
  const [lastDispatchedServer, setLastDispatchedServer] = useState(null)

  // Single Dispatch State
  const [seatId, setSeatId] = useState('seat-A1')
  const [userName, setUserName] = useState('Alice')
  const [dispatchResult, setDispatchResult] = useState(null)

  // Batch Simulation State
  const [batchSize, setBatchSize] = useState(12)
  const [batchResult, setBatchResult] = useState(null)
  const [batchLoading, setBatchLoading] = useState(false)

  useEffect(() => {
    fetchStatus()
    fetchLogs()
  }, [])

  async function fetchStatus() {
    try {
      const data = await exp7.status()
      if (data) {
        setAlgorithm(data.algorithm)
        setServers(data.servers || [])
        setTotalRequests(data.totalRequests || 0)
        setTotalFailovers(data.totalFailovers || 0)
        setRecentHistory(data.recentHistory || [])
      }
    } catch {}
  }

  async function fetchLogs() {
    try {
      const data = await exp7.getLogs()
      if (Array.isArray(data)) {
        setLogs(data)
      }
    } catch {}
  }

  async function handleSwitchAlgorithm(newAlgo) {
    try {
      setLoading(true)
      await exp7.config({ algorithm: newAlgo })
      setAlgorithm(newAlgo)
      toast(`Algorithm switched to ${newAlgo === 'ROUND_ROBIN' ? 'Round Robin' : 'Weighted Round Robin'}`, 'info')
      fetchStatus()
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Failed to switch algorithm', 'error')
    } finally {
      setLoading(false)
    }
  }

  async function handleUpdateWeight(nodeId, newWeight) {
    try {
      await exp7.config({ [nodeId]: newWeight.toString() })
      toast(`Updated ${nodeId} weight to ${newWeight}`, 'info')
      fetchStatus()
    } catch (e) {
      toast(e.message || 'Failed to update weight', 'error')
    }
  }

  async function handleToggleHealth(nodeId, currentlyAlive) {
    const action = currentlyAlive ? 'crash' : 'recover'
    try {
      await exp7.health(nodeId, action)
      toast(
        currentlyAlive ? `${nodeId} crashed (offline)` : `${nodeId} recovered (online)`,
        currentlyAlive ? 'warning' : 'success'
      )
      fetchStatus()
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Health update failed', 'error')
    }
  }

  async function handlePingAll() {
    try {
      await exp7.pingAll()
      toast('Health check complete for all cluster nodes', 'success')
      fetchStatus()
      fetchLogs()
    } catch (e) {
      toast('Health check failed', 'error')
    }
  }

  async function handleDispatchSingle() {
    if (!userName.trim() || !seatId.trim()) {
      toast('User Name and Seat ID required', 'warning')
      return
    }

    setLoading(true)
    setDispatchResult(null)

    try {
      const res = await exp7.dispatch({
        seatId: seatId.trim(),
        userName: userName.trim()
      })

      setDispatchResult(res)
      setLastDispatchedServer(res.serverId)

      if (res.wasFailover) {
        toast(`⚠️ Failover Redirect: Handled by ${res.serverId} after ${res.failedServerId} failed!`, 'warning')
      } else if (res.success) {
        toast(`Routed to ${res.serverId} (${res.confirmationCode})`, 'success')
      } else {
        toast(`Routing error: ${res.message}`, 'error')
      }

      fetchStatus()
      fetchLogs()

      // Randomize seat for next click
      const nextSeatNum = Math.floor(Math.random() * 20) + 1
      const nextLetter = ['A', 'B', 'C', 'D'][Math.floor(Math.random() * 4)]
      setSeatId(`seat-${nextLetter}${nextSeatNum}`)
    } catch (e) {
      toast(e.message || 'Dispatch failed', 'error')
    } finally {
      setLoading(false)
    }
  }

  async function handleRunBatch(count) {
    setBatchLoading(true)
    setBatchResult(null)

    try {
      const res = await exp7.batch({ count })
      setBatchResult(res)
      toast(`Batch of ${count} requests routed via ${res.algorithm}`, 'success')
      fetchStatus()
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Batch dispatch failed', 'error')
    } finally {
      setBatchLoading(false)
    }
  }

  async function handleResetAll() {
    try {
      await exp7.reset()
      setBatchResult(null)
      setDispatchResult(null)
      setLastDispatchedServer(null)
      toast('Load balancer and backend server states reset', 'info')
      fetchStatus()
      fetchLogs()
    } catch (e) {
      toast(e.message || 'Reset failed', 'error')
    }
  }

  // Calculate live cluster totals
  const totalWeight = servers.reduce((acc, s) => acc + (s.alive ? s.weight : 0), 0)
  const healthyCount = servers.filter(s => s.alive).length

  return (
    <div className={styles.expPanel}>
      {/* ── Experiment Header Strip ── */}
      <div className={styles.sectionHeader}>
        <div>
          <div className={styles.hubBadge} style={{ backgroundColor: '#EEF2FF', color: '#4F46E5', borderColor: '#C7D2FE' }}>
            <Layers size={13} />
            <span>High-Availability Traffic Architecture</span>
          </div>
          <h2 className={styles.sectionTitle}>
            Experiment 7: Load Balancing & Fault Tolerance
          </h2>
          <p className={styles.sectionDesc}>
            Distributes CineBook movie ticket booking requests across a multi-server cluster using{' '}
            <strong>Round Robin</strong> and <strong>Weighted Round Robin (3:2:1)</strong>, with active health
            monitoring and instant failover redirection when server nodes crash.
          </p>
        </div>

        {/* Global Cluster Stats */}
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <div className={styles.statBox}>
            <div className={styles.statNum} style={{ color: healthyCount === servers.length ? '#059669' : '#DC2626' }}>
              {healthyCount} / {servers.length}
            </div>
            <div className={styles.statLabel}>Healthy Nodes</div>
          </div>
          <div className={styles.statBox}>
            <div className={styles.statNum} style={{ color: '#2563EB' }}>
              {totalRequests}
            </div>
            <div className={styles.statLabel}>Total Dispatched</div>
          </div>
          <div className={styles.statBox}>
            <div className={styles.statNum} style={{ color: totalFailovers > 0 ? '#D97706' : '#6B7280' }}>
              {totalFailovers}
            </div>
            <div className={styles.statLabel}>Failover Redirects</div>
          </div>
        </div>
      </div>

      {/* ── Algorithm Control & Quick Actions Toolbar ── */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '16px',
        padding: '16px 20px',
        backgroundColor: '#F8FAFC',
        border: '1px solid #E2E8F0',
        borderRadius: '8px',
        marginBottom: '24px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '13px', fontWeight: '700', color: '#334155' }}>
            Active Algorithm:
          </span>
          <div style={{ display: 'inline-flex', borderRadius: '6px', overflow: 'hidden', border: '1px solid #CBD5E1' }}>
            <button
              type="button"
              onClick={() => handleSwitchAlgorithm('ROUND_ROBIN')}
              disabled={loading}
              style={{
                padding: '8px 16px',
                fontSize: '13px',
                fontWeight: '600',
                border: 'none',
                cursor: 'pointer',
                backgroundColor: algorithm === 'ROUND_ROBIN' ? '#4F46E5' : '#FFFFFF',
                color: algorithm === 'ROUND_ROBIN' ? '#FFFFFF' : '#475569',
                transition: 'all 0.15s'
              }}
            >
              Round Robin (1:1:1)
            </button>
            <button
              type="button"
              onClick={() => handleSwitchAlgorithm('WEIGHTED_ROUND_ROBIN')}
              disabled={loading}
              style={{
                padding: '8px 16px',
                fontSize: '13px',
                fontWeight: '600',
                border: 'none',
                cursor: 'pointer',
                borderLeft: '1px solid #CBD5E1',
                backgroundColor: algorithm === 'WEIGHTED_ROUND_ROBIN' ? '#4F46E5' : '#FFFFFF',
                color: algorithm === 'WEIGHTED_ROUND_ROBIN' ? '#FFFFFF' : '#475569',
                transition: 'all 0.15s'
              }}
            >
              Weighted Round Robin (3:2:1)
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            type="button"
            className={styles.btnSecondary}
            onClick={handlePingAll}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Activity size={14} color="#059669" />
            <span>Health Check Ping</span>
          </button>
          <button
            type="button"
            className={styles.btnSecondary}
            onClick={handleResetAll}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <RotateCcw size={14} />
            <span>Reset Cluster</span>
          </button>
        </div>
      </div>

      {/* ── Architecture Diagram & Server Nodes Grid ── */}
      <div style={{ marginBottom: '28px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: '700', color: '#1E293B', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Server size={18} color="#4F46E5" />
          <span>Backend Server Cluster & Capacity Pools</span>
        </h3>

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))',
          gap: '16px'
        }}>
          {servers.map((server) => {
            const isAlive = server.alive
            const isLastHit = lastDispatchedServer === server.nodeId
            const reqPercent = totalRequests > 0
              ? Math.round((server.requestsHandled / totalRequests) * 100)
              : 0
            const expectedWeightPct = totalWeight > 0 && isAlive
              ? Math.round((server.weight / totalWeight) * 100)
              : 0

            return (
              <div
                key={server.nodeId}
                style={{
                  backgroundColor: '#FFFFFF',
                  borderRadius: '10px',
                  border: isLastHit ? '2px solid #4F46E5' : '1px solid #E2E8F0',
                  boxShadow: isLastHit ? '0 0 0 3px rgba(79, 70, 229, 0.15)' : '0 1px 3px rgba(0,0,0,0.06)',
                  padding: '20px',
                  position: 'relative',
                  transition: 'all 0.2s ease',
                  opacity: isAlive ? 1 : 0.75
                }}
              >
                {/* Header: Node Name & Health Status */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                  <div>
                    <div style={{ fontSize: '15px', fontWeight: '800', color: '#0F172A' }}>
                      {server.name}
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748B', fontFamily: 'monospace' }}>
                      {server.nodeId}
                    </div>
                  </div>

                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '5px',
                      padding: '3px 9px',
                      borderRadius: '12px',
                      fontSize: '11px',
                      fontWeight: '700',
                      textTransform: 'uppercase',
                      backgroundColor: isAlive ? '#DCFCE7' : '#FEE2E2',
                      color: isAlive ? '#166534' : '#991B1B',
                      border: isAlive ? '1px solid #86EFAC' : '1px solid #FCA5A5'
                    }}
                  >
                    {isAlive ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                    {isAlive ? 'ONLINE' : 'CRASHED'}
                  </span>
                </div>

                {/* Weight & Capacity Ratio */}
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  backgroundColor: '#F8FAFC',
                  padding: '8px 12px',
                  borderRadius: '6px',
                  marginBottom: '14px',
                  fontSize: '12.5px'
                }}>
                  <span style={{ color: '#475569', fontWeight: '600' }}>Weight (Capacity):</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button
                      type="button"
                      disabled={server.weight <= 1}
                      onClick={() => handleUpdateWeight(server.nodeId, server.weight - 1)}
                      style={{
                        width: '24px',
                        height: '24px',
                        borderRadius: '4px',
                        border: '1px solid #CBD5E1',
                        background: '#FFF',
                        cursor: server.weight <= 1 ? 'not-allowed' : 'pointer',
                        fontWeight: '700'
                      }}
                    >
                      -
                    </button>
                    <span style={{ fontWeight: '800', fontSize: '14px', minWidth: '16px', textAlign: 'center', color: '#1E293B' }}>
                      {server.weight}
                    </span>
                    <button
                      type="button"
                      disabled={server.weight >= 10}
                      onClick={() => handleUpdateWeight(server.nodeId, server.weight + 1)}
                      style={{
                        width: '24px',
                        height: '24px',
                        borderRadius: '4px',
                        border: '1px solid #CBD5E1',
                        background: '#FFF',
                        cursor: server.weight >= 10 ? 'not-allowed' : 'pointer',
                        fontWeight: '700'
                      }}
                    >
                      +
                    </button>
                    <span style={{ fontSize: '11px', color: '#64748B' }}>
                      ({expectedWeightPct}% WRR target)
                    </span>
                  </div>
                </div>

                {/* Requests Handled Metrics */}
                <div style={{ marginBottom: '14px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12.5px', marginBottom: '4px' }}>
                    <span style={{ color: '#64748B' }}>Requests Handled:</span>
                    <span style={{ fontWeight: '700', color: '#0F172A' }}>
                      {server.requestsHandled} ({reqPercent}%)
                    </span>
                  </div>
                  {/* Load Bar */}
                  <div style={{ width: '100%', height: '8px', backgroundColor: '#F1F5F9', borderRadius: '4px', overflow: 'hidden' }}>
                    <div
                      style={{
                        width: `${Math.min(100, reqPercent)}%`,
                        height: '100%',
                        backgroundColor: isAlive ? '#4F46E5' : '#EF4444',
                        transition: 'width 0.3s ease'
                      }}
                    />
                  </div>
                </div>

                {/* Fault Injection Button */}
                <div>
                  <button
                    type="button"
                    onClick={() => handleToggleHealth(server.nodeId, isAlive)}
                    style={{
                      width: '100%',
                      padding: '8px',
                      fontSize: '12px',
                      fontWeight: '700',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      border: isAlive ? '1px solid #F87171' : '1px solid #4ADE80',
                      backgroundColor: isAlive ? '#FEF2F2' : '#F0FDF4',
                      color: isAlive ? '#DC2626' : '#15803D',
                      transition: 'all 0.15s'
                    }}
                  >
                    {isAlive ? '💥 Crash Server (Simulate Outage)' : '💚 Recover Server (Rejoin Pool)'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* ── Two-Column Interactive Workbench ── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: '24px',
        marginBottom: '28px'
      }}>
        {/* Panel A: Single Request Dispatcher */}
        <div style={{
          backgroundColor: '#FFFFFF',
          border: '1px solid #E2E8F0',
          borderRadius: '10px',
          padding: '20px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
        }}>
          <h3 style={{ fontSize: '15px', fontWeight: '700', color: '#1E293B', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Zap size={18} color="#2563EB" />
            <span>Single Client Booking Dispatcher</span>
          </h3>
          <p style={{ fontSize: '13px', color: '#64748B', marginBottom: '16px' }}>
            Sends an individual seat reservation request through the Load Balancer to observe route selection and failover.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#475569', marginBottom: '4px' }}>
                Customer Name
              </label>
              <input
                type="text"
                value={userName}
                onChange={(e) => setUserName(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  borderRadius: '6px',
                  border: '1px solid #CBD5E1',
                  fontSize: '13px'
                }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#475569', marginBottom: '4px' }}>
                Seat ID
              </label>
              <input
                type="text"
                value={seatId}
                onChange={(e) => setSeatId(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  borderRadius: '6px',
                  border: '1px solid #CBD5E1',
                  fontSize: '13px'
                }}
              />
            </div>
          </div>

          <button
            type="button"
            className={styles.btnPrimary}
            onClick={handleDispatchSingle}
            disabled={loading}
            style={{ width: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px' }}
          >
            <Play size={15} />
            <span>Route Booking Request</span>
          </button>

          {/* Dispatch Result Card */}
          {dispatchResult && (
            <div style={{
              marginTop: '16px',
              padding: '14px',
              borderRadius: '8px',
              border: dispatchResult.wasFailover ? '1px solid #FCD34D' : '1px solid #BFDBFE',
              backgroundColor: dispatchResult.wasFailover ? '#FFFBEB' : '#EFF6FF'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: dispatchResult.wasFailover ? '#B45309' : '#1E40AF' }}>
                  {dispatchResult.wasFailover ? '⚡ Automatic Failover Redirect' : '✅ Request Successfully Routed'}
                </span>
                <span style={{ fontSize: '11px', fontFamily: 'monospace', color: '#64748B' }}>
                  {dispatchResult.requestId}
                </span>
              </div>
              <div style={{ fontSize: '12.5px', color: '#334155', lineHeight: '1.5' }}>
                <div><strong>Server Chosen:</strong> {dispatchResult.serverName} ({dispatchResult.serverId})</div>
                <div><strong>Confirmation:</strong> <span style={{ fontFamily: 'monospace' }}>{dispatchResult.confirmationCode || 'N/A'}</span></div>
                {dispatchResult.wasFailover && (
                  <div style={{ color: '#B45309', marginTop: '4px', fontWeight: '600' }}>
                    Server {dispatchResult.failedServerId} was unreachable. Request redirected without error!
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Panel B: Batch Traffic Burst Simulator */}
        <div style={{
          backgroundColor: '#FFFFFF',
          border: '1px solid #E2E8F0',
          borderRadius: '10px',
          padding: '20px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
        }}>
          <h3 style={{ fontSize: '15px', fontWeight: '700', color: '#1E293B', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BarChart2 size={18} color="#7C3AED" />
            <span>Batch Traffic Burst Simulator</span>
          </h3>
          <p style={{ fontSize: '13px', color: '#64748B', marginBottom: '16px' }}>
            Fires consecutive requests to empirically verify the distribution ratios across cycles (1:1:1 vs 3:2:1).
          </p>

          <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', flexWrap: 'wrap' }}>
            <button
              type="button"
              className={styles.btnSecondary}
              onClick={() => handleRunBatch(6)}
              disabled={batchLoading}
              style={{ flex: 1, minWidth: '90px', fontSize: '12.5px' }}
            >
              Send 6 (1 Cycle)
            </button>
            <button
              type="button"
              className={styles.btnSecondary}
              onClick={() => handleRunBatch(12)}
              disabled={batchLoading}
              style={{ flex: 1, minWidth: '90px', fontSize: '12.5px' }}
            >
              Send 12 (2 Cycles)
            </button>
            <button
              type="button"
              className={styles.btnSecondary}
              onClick={() => handleRunBatch(30)}
              disabled={batchLoading}
              style={{ flex: 1, minWidth: '90px', fontSize: '12.5px' }}
            >
              Send 30 (Burst)
            </button>
          </div>

          {/* Batch Result Visualization */}
          {batchResult && (
            <div style={{
              padding: '14px',
              borderRadius: '8px',
              border: '1px solid #E2E8F0',
              backgroundColor: '#F8FAFC'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                <span style={{ fontSize: '12.5px', fontWeight: '700', color: '#1E293B' }}>
                  Batch Result ({batchResult.totalRequests} Requests via {batchResult.algorithm})
                </span>
                <span style={{ fontSize: '11px', color: '#64748B' }}>
                  {batchResult.failoversCount} failovers
                </span>
              </div>

              {/* Per-server count breakdown */}
              <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
                {Object.entries(batchResult.counts).map(([srvId, cnt]) => {
                  const pct = batchResult.percentages[srvId] || 0
                  return (
                    <div
                      key={srvId}
                      style={{
                        flex: 1,
                        backgroundColor: '#FFFFFF',
                        border: '1px solid #CBD5E1',
                        borderRadius: '6px',
                        padding: '8px',
                        textAlign: 'center'
                      }}
                    >
                      <div style={{ fontSize: '11px', color: '#64748B', fontWeight: '600' }}>{srvId}</div>
                      <div style={{ fontSize: '16px', fontWeight: '800', color: '#1E293B' }}>{cnt}</div>
                      <div style={{ fontSize: '11px', color: '#4F46E5', fontWeight: '700' }}>{pct}%</div>
                    </div>
                  )
                })}
              </div>

              {/* Routing Sequence preview */}
              <div style={{ fontSize: '11.5px', color: '#475569' }}>
                <strong>Sequence:</strong>{' '}
                <span style={{ fontFamily: 'monospace', color: '#0F172A' }}>
                  {batchResult.sequence.slice(0, 12).join(' → ')}
                  {batchResult.sequence.length > 12 ? ' ...' : ''}
                </span>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Recent Routing History & Live Cluster Logs ── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: '24px'
      }}>
        {/* Table: Recent Dispatches */}
        <div style={{
          backgroundColor: '#FFFFFF',
          border: '1px solid #E2E8F0',
          borderRadius: '10px',
          padding: '20px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
        }}>
          <h3 style={{ fontSize: '14px', fontWeight: '700', color: '#1E293B', marginBottom: '12px' }}>
            Recent Routing History (Last 10)
          </h3>

          {recentHistory.length === 0 ? (
            <div style={{ fontSize: '13px', color: '#94A3B8', textAlign: 'center', padding: '24px 0' }}>
              No requests dispatched yet. Use the single dispatcher or batch simulator above.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #E2E8F0', color: '#64748B', textAlign: 'left' }}>
                    <th style={{ padding: '6px 8px' }}>Req ID</th>
                    <th style={{ padding: '6px 8px' }}>Server</th>
                    <th style={{ padding: '6px 8px' }}>Algorithm</th>
                    <th style={{ padding: '6px 8px' }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {recentHistory.slice(0, 10).map((r) => (
                    <tr key={r.requestId} style={{ borderBottom: '1px solid #F1F5F9' }}>
                      <td style={{ padding: '6px 8px', fontFamily: 'monospace' }}>{r.requestId}</td>
                      <td style={{ padding: '6px 8px', fontWeight: '600', color: '#1E293B' }}>{r.serverId}</td>
                      <td style={{ padding: '6px 8px', color: '#475569' }}>
                        {r.algorithm === 'WEIGHTED_ROUND_ROBIN' ? 'WRR' : 'RR'}
                      </td>
                      <td style={{ padding: '6px 8px' }}>
                        {r.wasFailover ? (
                          <span style={{ color: '#D97706', fontWeight: '700' }}>Failover</span>
                        ) : r.success ? (
                          <span style={{ color: '#16A34A', fontWeight: '700' }}>OK</span>
                        ) : (
                          <span style={{ color: '#DC2626', fontWeight: '700' }}>Failed</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Live Cluster Logs */}
        <div style={{
          backgroundColor: '#0F172A',
          borderRadius: '10px',
          padding: '16px',
          color: '#E2E8F0',
          fontFamily: 'monospace',
          fontSize: '11.5px',
          display: 'flex',
          flexDirection: 'column',
          maxHeight: '320px'
        }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '10px',
            borderBottom: '1px solid #334155',
            paddingBottom: '8px'
          }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#38BDF8', fontWeight: '700' }}>
              <Terminal size={14} />
              <span>Load Balancer Realtime Console</span>
            </span>
            <button
              type="button"
              onClick={fetchLogs}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#94A3B8',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '11px'
              }}
            >
              <RefreshCw size={12} />
              <span>Refresh</span>
            </button>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {logs.length === 0 ? (
              <span style={{ color: '#64748B' }}>// Waiting for traffic events...</span>
            ) : (
              logs.slice(-25).map((l, idx) => {
                let color = '#E2E8F0'
                if (l.includes('FAILOVER') || l.includes('ALERT')) color = '#FBBF24'
                else if (l.includes('CRASHED') || l.includes('Error')) color = '#F87171'
                else if (l.includes('RECOVERY') || l.includes('HEALTHY')) color = '#4ADE80'
                else if (l.includes('LB-Route')) color = '#38BDF8'
                else if (l.includes('LB-Batch')) color = '#C084FC'

                return (
                  <div key={idx} style={{ color, wordBreak: 'break-all' }}>
                    {l}
                  </div>
                )
              })
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
