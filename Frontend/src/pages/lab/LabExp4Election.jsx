import { useState, useEffect, useRef } from 'react'
import { exp4 } from '../../api'
import { useToast } from '../../components/Toast'
import Terminal from '../../components/Terminal'
import { Server, Play, Square, Flame, Zap, RotateCcw, ShieldCheck, AlertTriangle } from 'lucide-react'
import styles from './Lab.module.css'

export default function LabExp4Election() {
  const toast = useToast()
  const [running, setRunning] = useState(false)
  const [nodes, setNodes] = useState([])
  const [logs, setLogs] = useState([])
  const [actionLoading, setActionLoading] = useState(false)

  const pollTimerRef = useRef(null)
  const logLenRef = useRef(0)

  // Status polling
  useEffect(() => {
    exp4.status().then(data => {
      if (data.running) {
        setRunning(true)
        setNodes(data.nodes || [])
        startPolling()
      }
    }).catch(() => {})

    return () => stopPolling()
  }, [])

  function startPolling() {
    if (pollTimerRef.current) return
    pollTimerRef.current = setInterval(async () => {
      try {
        const [statusData, logData] = await Promise.all([exp4.status(), exp4.getLogs()])
        if (statusData.running) {
          setRunning(true)
          setNodes(statusData.nodes || [])
        }
        if (logData.logs && logData.logs.length > logLenRef.current) {
          const newLines = logData.logs.slice(logLenRef.current)
          logLenRef.current = logData.logs.length
          setLogs(prev => [...prev, ...newLines])
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

  async function handleStart() {
    setActionLoading(true)
    try {
      const data = await exp4.startCluster()
      if (data.status === 'started' || data.status === 'already running') {
        setRunning(true)
        setNodes(data.nodes?.map(id => ({ id, alive: true, leader: 5, electionInProgress: false })) || [])
        startPolling()
        toast('5-Node cluster initialized on ports 1101-1105', 'success')
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error starting cluster', 'error')
    }
    setActionLoading(false)
  }

  async function handleStop() {
    setActionLoading(true)
    stopPolling()
    try {
      await exp4.stopCluster()
      setRunning(false)
      setNodes([])
      logLenRef.current = 0
      toast('Cluster terminated', 'info')
    } catch (e) {
      toast(e.message || 'Error stopping cluster', 'error')
    }
    setActionLoading(false)
  }

  async function handleCrash(nodeId) {
    try {
      const data = await exp4.crash(nodeId)
      if (data.status === 'crashed') {
        setNodes(prev => prev.map(n => n.id === nodeId ? { ...n, alive: false, leader: -1 } : n))
        toast(`Process P${nodeId} crashed (unexported from RMI registry)`, 'warning')
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error crashing node', 'error')
    }
  }

  async function handleElect(nodeId) {
    try {
      toast(`P${nodeId} initiated Bully election...`, 'info')
      const data = await exp4.elect(nodeId)
      if (data.nodes) {
        setNodes(data.nodes)
        const leader = data.nodes.find(n => n.alive && n.leader === n.id)
        if (leader) {
          toast(`Election completed: P${leader.id} elected as coordinator`, 'success')
        }
      } else if (data.error) {
        toast(data.error, 'error')
      }
    } catch (e) {
      toast(e.message || 'Error running election', 'error')
    }
  }

  const currentLeader = nodes.find(n => n.alive && n.leader === n.id)

  return (
    <div className={styles.expContainer}>
      {/* ── Header & Status ── */}
      <div className={styles.expHeaderRow}>
        <div>
          <h2 className={styles.expTitle}>Experiment 4: Bully Leader Election Algorithm</h2>
          <p className={styles.expSubtitle}>
            A 5-node peer network (P1..P5 on ports 1101..1105). When the leader fails, survivor nodes elect the highest-ID alive process.
          </p>
        </div>

        <div className={styles.statusGroup}>
          <span className={styles.statusPill}>
            <span className={running ? styles.statusDotGreen : styles.statusDotRed} />
            <span>Cluster: <strong>{running ? 'Online' : 'Offline'}</strong></span>
          </span>
          {currentLeader && (
            <span className={styles.statusPill}>
              <ShieldCheck size={13} color="#059669" />
              <span>Coordinator: <strong>P{currentLeader.id}</strong></span>
            </span>
          )}
        </div>
      </div>

      {/* ── Cluster Control Bar ── */}
      <div className={styles.clusterControlBar}>
        <div className={styles.clusterInfoText}>
          <strong>Cluster Management:</strong> 5 independent JVM RMI registries running on ports 1101–1105.
        </div>

        <div className={styles.clusterBtns}>
          {!running ? (
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleStart}
              disabled={actionLoading}
            >
              {actionLoading ? <span className="spinner" /> : <Play size={14} />}
              <span>Start 5-Node Cluster</span>
            </button>
          ) : (
            <>
              <button
                type="button"
                className="btn btn-outline"
                onClick={handleStop}
                disabled={actionLoading}
              >
                <Square size={14} />
                <span>Stop Cluster</span>
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => { setLogs([]); logLenRef.current = 0 }}
              >
                <RotateCcw size={13} />
                <span>Clear Log</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* ── 5 Peer Node Objects ── */}
      {running && (
        <div className={styles.nodeGrid}>
          {[1, 2, 3, 4, 5].map(id => {
            const node = nodes.find(n => n.id === id)
            const alive = node ? node.alive : true
            const isLeader = alive && currentLeader && currentLeader.id === id
            const recognizedLeader = node ? node.leader : -1

            return (
              <div
                key={id}
                className={`${styles.nodeBox} ${!alive ? styles.nodeBoxCrashed : ''} ${isLeader ? styles.nodeBoxLeader : ''}`}
              >
                {/* Node Header */}
                <div className={styles.nodeHeader}>
                  <div className={styles.nodeIdentity}>
                    <Server size={16} color={isLeader ? '#059669' : !alive ? '#DC2626' : '#111827'} />
                    <span className={styles.nodeIdText}>NODE P{id}</span>
                  </div>
                  <span className={`badge ${!alive ? 'badge-red' : isLeader ? 'badge-green' : 'badge-neutral'}`}>
                    {!alive ? 'CRASHED' : isLeader ? 'COORDINATOR' : 'PEER'}
                  </span>
                </div>

                {/* Node Port & State */}
                <div className={styles.nodePortText}>
                  Port: <code>{1100 + id}</code>
                </div>

                <div className={styles.nodeLeaderText}>
                  {!alive ? (
                    <span className={styles.valRed}>Process unexported</span>
                  ) : isLeader ? (
                    <span className={styles.valGreen}>Active Coordinator</span>
                  ) : (
                    <span>Believes P{recognizedLeader > 0 ? recognizedLeader : '?'} is leader</span>
                  )}
                </div>

                {/* Node Actions */}
                {alive ? (
                  <div className={styles.nodeBtnRow}>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      onClick={() => handleCrash(id)}
                    >
                      <Flame size={12} color="#DC2626" />
                      <span>Crash</span>
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => handleElect(id)}
                    >
                      <Zap size={12} />
                      <span>Elect</span>
                    </button>
                  </div>
                ) : (
                  <div className={styles.crashedNotice}>
                    Node disconnected from mesh
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Live Election Log Terminal ── */}
      <div className={styles.testCard} style={{ marginTop: 24 }}>
        <h3 className={styles.cardHeader}>Real-Time RMI Protocol Message Stream</h3>
        <Terminal lines={logs} />
      </div>

      {/* ── Technical Summary ── */}
      <div className={styles.summaryCard}>
        <h4 className={styles.summaryTitle}>What This Demonstrates</h4>
        <p className={styles.summaryText}>
          The <strong>Bully Algorithm</strong> is a distributed leader election algorithm for crash-stop systems.
          When a process notices that the coordinator is down, it sends an <code>ELECTION</code> message to all peers with higher IDs.
          If any higher-ID peer responds with <code>OK</code>, the initiating process steps aside. If no higher-ID process responds within the timeout,
          the process proclaims itself the new coordinator and broadcasts a <code>COORDINATOR</code> message to all alive members.
        </p>
      </div>
    </div>
  )
}
