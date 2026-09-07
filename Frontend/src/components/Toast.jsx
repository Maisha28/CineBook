import { createContext, useContext, useState, useCallback } from 'react'
import { CheckCircle2, XCircle, Info, AlertTriangle } from 'lucide-react'

const ToastCtx = createContext(null)

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const addToast = useCallback((message, type = 'info', duration = 3500) => {
    const id = Date.now() + Math.random()
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), duration)
  }, [])

  return (
    <ToastCtx.Provider value={addToast}>
      {children}
      <div style={styles.container}>
        {toasts.map(t => (
          <div key={t.id} style={{ ...styles.toast, ...styles[t.type] }}>
            <span style={styles.icon}>{renderIcon(t.type)}</span>
            <span style={styles.message}>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  )
}

export const useToast = () => useContext(ToastCtx)

function renderIcon(type) {
  switch (type) {
    case 'success': return <CheckCircle2 size={16} color="#059669" />
    case 'error':   return <XCircle size={16} color="#DC2626" />
    case 'warning': return <AlertTriangle size={16} color="#D97706" />
    default:        return <Info size={16} color="#2563EB" />
  }
}

const styles = {
  container: {
    position: 'fixed',
    bottom: 24,
    right: 24,
    display: 'flex',
    flexDirection: 'column',
    gap: 10,
    zIndex: 9999,
    pointerEvents: 'none',
  },
  toast: {
    padding: '10px 16px',
    borderRadius: 6,
    fontSize: 13,
    fontWeight: 500,
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    maxWidth: 360,
    backgroundColor: '#FFFFFF',
    boxShadow: '0 4px 16px rgba(0,0,0,.12)',
    border: '1px solid #E5E7EB',
    animation: 'slideIn 0.2s ease',
  },
  message: {
    color: '#1F2937',
  },
  success: { borderLeft: '3px solid #059669' },
  error:   { borderLeft: '3px solid #DC2626' },
  info:    { borderLeft: '3px solid #2563EB' },
  warning: { borderLeft: '3px solid #D97706' },
  icon:    { display: 'flex', alignItems: 'center' },
}
