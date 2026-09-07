import { useState, useCallback } from 'react'

/**
 * Custom hook to manage terminal/console log lines for experiments.
 */
export function useExperimentLogs(initialLogs = []) {
  const [logs, setLogs] = useState(initialLogs)

  const addLog = useCallback((message, prefix = '') => {
    const timestamp = new Date().toLocaleTimeString()
    const line = prefix ? `[${timestamp}] ${prefix} ${message}` : `[${timestamp}] ${message}`
    setLogs(prev => [...prev, line])
  }, [])

  const clearLogs = useCallback(() => {
    setLogs([])
  }, [])

  return { logs, addLog, clearLogs }
}
