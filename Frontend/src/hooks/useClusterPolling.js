import { useState, useEffect, useRef, useCallback } from 'react'
import { exp4 } from '../api'

/**
 * Custom hook for Experiment 4 cluster status and log polling.
 */
export function useClusterPolling(pollIntervalMs = 1200) {
  const [running, setRunning] = useState(false)
  const [nodes, setNodes] = useState([])
  const [clusterLogs, setClusterLogs] = useState([])
  const pollTimerRef = useRef(null)
  const logLenRef = useRef(0)

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }, [])

  const startPolling = useCallback(() => {
    stopPolling()
    pollTimerRef.current = setInterval(async () => {
      try {
        const [statusData, logData] = await Promise.all([
          exp4.status(),
          exp4.getLogs()
        ])
        if (statusData.running) {
          setRunning(true)
          setNodes(statusData.nodes || [])
        } else {
          setRunning(false)
        }
        if (logData.logs && logData.logs.length > logLenRef.current) {
          const newLines = logData.logs.slice(logLenRef.current)
          logLenRef.current = logData.logs.length
          setClusterLogs(prev => [...prev, ...newLines])
        }
      } catch {
        // Bridge offline or network issue
      }
    }, pollIntervalMs)
  }, [pollIntervalMs, stopPolling])

  useEffect(() => {
    // Check initial status on mount
    exp4.status().then(data => {
      if (data.running) {
        setRunning(true)
        setNodes(data.nodes || [])
        startPolling()
      }
    }).catch(() => {})

    return () => stopPolling()
  }, [startPolling, stopPolling])

  const clearClusterLogs = useCallback(() => {
    setClusterLogs([])
    logLenRef.current = 0
  }, [])

  return {
    running,
    setRunning,
    nodes,
    setNodes,
    clusterLogs,
    clearClusterLogs,
    startPolling,
    stopPolling,
  }
}
