import { useRef, useEffect } from 'react'

/** Console-style terminal log panel */
export default function Terminal({ lines = [], className = '' }) {
  const bottomRef = useRef(null)
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [lines])

  return (
    <div className={`terminal ${className}`}>
      {lines.length === 0
        ? <span className="t-dim">No output yet. Run an operation above.</span>
        : lines.map((line, i) => (
            <div key={i} className={classifyLine(line)}>{line}</div>
          ))
      }
      <div ref={bottomRef} />
    </div>
  )
}

function classifyLine(line) {
  const l = line.toLowerCase()
  if (l.includes('error') || l.includes('fail') || l.includes('crash') || l.includes('✗')) return 't-err'
  if (l.includes('success') || l.includes('ok') || l.includes('✓') || l.includes('leader')) return 't-ok'
  if (l.includes('warn') || l.includes('election') || l.includes('→')) return 't-warn'
  if (l.includes('[p') || l.includes('[server') || l.includes('←')) return 't-info'
  return ''
}
