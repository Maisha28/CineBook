import { useState, useEffect, useRef } from 'react'
import { exp4 } from '../api'
import Terminal from '../components/Terminal'
import { useToast } from '../components/Toast'
import styles from './Exp4Page.module.css'

const NODE_COUNT = 5
const NODE_COLORS = ['#e51937','#1a9e5c','#3b82f6','#e07b00','#7c3aed']

export default function Exp4Page() {
  const toast = useToast()
  const [clusterRunning, setClusterRunning] = useState(false)
  const [nodes,   setNodes]   = useState([])
  const [log,     setLog]     = useState([])
  const [loading, setLoading] = useState({})
  const pollRef = useRef(null)
  const logLenRef = useRef(0)

  // Check if cluster already up on mount
  useEffect(() => {
    exp4.status()
      .then(data => {
        if (data.running) {
          setClusterRunning(true)
          setNodes(data.nodes || [])
          startPolling()
          addLog('ℹ Cluster already running — resumed view')
        }
      })
      .catch(() => {})
    return () => stopPolling()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function addLog(line) { setLog(prev => [...prev, line]) }

  function startPolling() {
    if (pollRef.current) return
    pollRef.current = setInterval(async () => {
      try {
        const [statusData, logData] = await Promise.all([exp4.status(), exp4.getLogs()])
        if (statusData.running) setNodes(statusData.nodes || [])
        if (logData.logs) {
          const newLines = logData.logs.slice(logLenRef.current)
          logLenRef.current = logData.logs.length
          newLines.forEach(l => setLog(prev => [...prev, l]))
        }
      } catch { /* bridge not ready */ }
    }, 1200)
  }

  function stopPolling() {
    clearInterval(pollRef.current)
    pollRef.current = null
  }

  async function startCluster() {
    setLoading(l => ({ ...l, start: true }))
    addLog('→ Starting 5-node cluster (P1–P5, ports 1101–1105)…')
    try {
      const data = await exp4.startCluster()
      if (data.error) { addLog(`✗ ${data.error}`); toast(data.error, 'error') }
      else {
        addLog(`✓ Cluster ${data.status}`)
        setClusterRunning(true)
        setNodes(data.nodes || [])
        toast('Cluster started', 'success')
        startPolling()
      }
    } catch (e) { addLog(`✗ ${e.message}`); toast(e.message, 'error') }
    setLoading(l => ({ ...l, start: false }))
  }

  async function stopCluster() {
    stopPolling()
    setLoading(l => ({ ...l, stop: true }))
    addLog('→ Stopping cluster…')
    try {
      await exp4.stopCluster()
      setClusterRunning(false)
      setNodes([])
      logLenRef.current = 0
      addLog('✓ Cluster stopped')
      toast('Cluster stopped', 'info')
    } catch (e) { addLog(`✗ ${e.message}`); toast(e.message, 'error') }
    setLoading(l => ({ ...l, stop: false }))
  }

  async function crashNode(nodeId) {
    addLog(`→ Crashing P${nodeId}…`)
    try {
      const data = await exp4.crash(nodeId)
      if (data.error) { addLog(`✗ ${data.error}`); toast(data.error, 'error') }
      else {
        addLog(`✓ P${nodeId} crashed`)
        setNodes(prev => prev.map(n => n.id === nodeId ? { ...n, alive: false } : n))
        toast(`P${nodeId} crashed`, 'error')
      }
    } catch (e) { addLog(`✗ ${e.message}`) }
  }

  async function electFrom(nodeId) {
    addLog(`→ P${nodeId} initiating Bully election…`)
    try {
      const data = await exp4.elect(nodeId)
      if (data.error) { addLog(`✗ ${data.error}`); toast(data.error, 'error') }
      else {
        addLog(`← Election complete`)
        if (data.nodes) setNodes(data.nodes)
        toast('Election complete — new leader elected', 'success')
      }
    } catch (e) { addLog(`✗ ${e.message}`) }
  }

  const leaderNode = nodes.find(n => n.alive && n.leader === n.id)

  return (
    <div className="page-wrap">
      <div className="exp-header">
        <div className="exp-header-icon">👑</div>
        <div>
          <div className="exp-header-num">Experiment 4</div>
          <div className="exp-header-title">Bully Leader Election</div>
          <div className="exp-header-desc">
            Five RMI nodes form a cluster (P1–P5). When the coordinator crashes, the remaining processes
            use the Bully Algorithm — the highest-ID alive process becomes the new leader.
          </div>
          <div className="tag-strip">
            {['Bully Algorithm', 'Leader Election', 'Fault Tolerance', 'RMI Cluster'].map(t => (
              <span key={t} className="concept-tag">{t}</span>
            ))}
          </div>
        </div>
      </div>

      {/* Cluster controls */}
      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div className="card-title" style={{ marginBottom: 0, flex: 1 }}>
            Cluster Control
            {clusterRunning && leaderNode && (
              <span className="badge badge-green" style={{ marginLeft: 12 }}>
                Leader: P{leaderNode.id}
              </span>
            )}
            {!clusterRunning && (
              <span className="badge badge-grey" style={{ marginLeft: 12 }}>Offline</span>
            )}
          </div>
          <button className="btn btn-red" onClick={startCluster}
            disabled={clusterRunning || loading.start}>
            {loading.start ? <span className="spinner" /> : '▶'} Start Cluster
          </button>
          <button className="btn btn-outline" onClick={stopCluster}
            disabled={!clusterRunning || loading.stop}>
            {loading.stop ? <span className="spinner" /> : '■'} Stop Cluster
          </button>
        </div>
      </div>

      {/* Node cards */}
      {clusterRunning && (
        <div className={styles.nodeGrid}>
          {Array.from({ length: NODE_COUNT }, (_, i) => {
            const id = i + 1
            const node = nodes.find(n => n.id === id)
            const alive = node?.alive ?? true
            const isLeader = alive && node?.leader === id
            const leaderBelief = node?.leader

            return (
              <div key={id}
                className={`${styles.nodeCard} ${!alive ? styles.crashed : ''} ${isLeader ? styles.leader : ''}`}
                style={{ '--node-color': NODE_COLORS[i] }}
              >
                <div className={styles.nodeHead}>
                  <span className={styles.nodeId} style={{ color: NODE_COLORS[i] }}>P{id}</span>
                  {isLeader && <span className={styles.crown}>👑</span>}
                  <span className={`badge ${alive ? (isLeader ? 'badge-green' : 'badge-grey') : 'badge-red'}`}>
                    {!alive ? 'Crashed' : isLeader ? 'Leader' : 'Member'}
                  </span>
                </div>
                {alive && leaderBelief != null && !isLeader && (
                  <div className={styles.nodeBelief}>believes P{leaderBelief} is leader</div>
                )}
                {alive && (
                  <div className={styles.nodeActions}>
                    <button className="btn btn-danger btn-sm" onClick={() => crashNode(id)}>
                      💥 Crash
                    </button>
                    <button className="btn btn-outline btn-sm" onClick={() => electFrom(id)}>
                      ⚡ Elect
                    </button>
                  </div>
                )}
                {!alive && (
                  <div className={styles.nodeDown}>Process terminated</div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Log */}
      <div className="card" style={{ marginTop: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
          <div className="card-title" style={{ marginBottom: 0 }}>Election Log</div>
          <button className="btn btn-ghost btn-sm" onClick={() => { setLog([]); logLenRef.current = 0 }}>Clear</button>
        </div>
        <Terminal lines={log} />
      </div>
    </div>
  )
}
