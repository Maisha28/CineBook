import { useState, useEffect } from 'react'
import { catalog } from '../api'
import { Database, Table, RefreshCw, Layers, ShieldCheck, CheckCircle2, ChevronRight } from 'lucide-react'

const TABLES = ['movies', 'theatres', 'shows', 'seats', 'bookings']

export default function SupabaseExplorerPage() {
  const [activeTable, setActiveTable] = useState('movies')
  const [tableData, setTableData] = useState(null)
  const [dbInspect, setDbInspect] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchInspection()
  }, [])

  useEffect(() => {
    fetchTable(activeTable)
  }, [activeTable])

  function fetchInspection() {
    catalog.inspectSupabase()
      .then(res => setDbInspect(res))
      .catch(() => {})
  }

  function fetchTable(name) {
    setLoading(true)
    catalog.getTableData(name)
      .then(res => {
        setTableData(res)
        setLoading(false)
      })
      .catch(() => setLoading(false))
  }

  return (
    <div className="container" style={{ padding: '36px 20px 80px' }}>
      {/* ── Page Header ── */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        flexWrap: 'wrap',
        gap: '20px',
        paddingBottom: '24px',
        borderBottom: '1px solid #E5E7EB',
        marginBottom: '28px'
      }}>
        <div>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            backgroundColor: '#EEF2FF',
            color: '#4F46E5',
            border: '1px solid #C7D2FE',
            padding: '4px 10px',
            borderRadius: '16px',
            fontSize: '11px',
            fontWeight: 700,
            textTransform: 'uppercase',
            marginBottom: '8px'
          }}>
            <Database size={13} />
            <span>PostgreSQL 17.6 Live Instance</span>
          </div>
          <h1 style={{ fontSize: '28px', fontWeight: 800, color: '#111827', marginBottom: '6px' }}>
            Supabase Database Explorer
          </h1>
          <p style={{ fontSize: '14px', color: '#6B7280', maxWidth: '640px' }}>
            Inspect all tables, column schemas, and raw records stored in your connected Supabase PostgreSQL cloud instance (<code>aws-0-ap-south-1.pooler.supabase.com</code>).
          </p>
        </div>

        <button
          type="button"
          onClick={() => { fetchInspection(); fetchTable(activeTable); }}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '9px 16px',
            backgroundColor: '#FFFFFF',
            border: '1px solid #D1D5DB',
            borderRadius: '6px',
            fontSize: '13px',
            fontWeight: 600,
            cursor: 'pointer',
            color: '#374151'
          }}
        >
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
          <span>Refresh Database</span>
        </button>
      </div>

      {/* ── Top Summary Grid ── */}
      {dbInspect && dbInspect.tables && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '12px',
          marginBottom: '28px'
        }}>
          {TABLES.map(t => {
            const count = dbInspect.tables[t] !== undefined ? dbInspect.tables[t] : 0
            const isActive = activeTable === t
            return (
              <div
                key={t}
                onClick={() => setActiveTable(t)}
                style={{
                  backgroundColor: isActive ? '#FFFFFF' : '#F9FAFB',
                  border: isActive ? '2px solid #E51937' : '1px solid #E5E7EB',
                  borderRadius: '8px',
                  padding: '14px',
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  boxShadow: isActive ? '0 4px 12px rgba(229, 25, 55, 0.08)' : 'none'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                  <span style={{ fontSize: '12px', fontWeight: 700, color: isActive ? '#E51937' : '#4B5563', textTransform: 'uppercase' }}>
                    {t}
                  </span>
                  <Table size={14} color={isActive ? '#E51937' : '#9CA3AF'} />
                </div>
                <div style={{ fontSize: '22px', fontWeight: 800, color: '#111827' }}>
                  {count} <span style={{ fontSize: '12px', fontWeight: 500, color: '#6B7280' }}>rows</span>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Table Viewer ── */}
      <div style={{
        backgroundColor: '#FFFFFF',
        borderRadius: '10px',
        border: '1px solid #E5E7EB',
        overflow: 'hidden',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
      }}>
        <div style={{
          padding: '16px 20px',
          backgroundColor: '#F9FAFB',
          borderBottom: '1px solid #E5E7EB',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Table size={16} color="#E51937" />
            <span style={{ fontSize: '15px', fontWeight: 800, color: '#111827' }}>
              Table: <code>{activeTable}</code>
            </span>
          </div>
          {tableData && (
            <span style={{ fontSize: '12.5px', color: '#6B7280', fontWeight: 600 }}>
              Showing {tableData.total} records
            </span>
          )}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div className="spinner" />
            <p style={{ marginTop: '12px', color: '#6B7280', fontSize: '14px' }}>Querying Supabase table...</p>
          </div>
        ) : !tableData || !tableData.rows || tableData.rows.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: '#6B7280', fontSize: '13px' }}>
            No records found in this table.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '12px' }}>
              <thead>
                <tr style={{ backgroundColor: '#F3F4F6', borderBottom: '1px solid #E5E7EB', color: '#4B5563', fontWeight: 700 }}>
                  {tableData.columns.map(c => (
                    <th key={c.name} style={{ padding: '10px 14px', whiteSpace: 'nowrap' }}>
                      {c.name} <span style={{ fontSize: '10px', color: '#9CA3AF', fontWeight: 400 }}>({c.type})</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {tableData.rows.map((row, rIdx) => (
                  <tr key={rIdx} style={{ borderBottom: '1px solid #F3F4F6' }}>
                    {tableData.columns.map(c => (
                      <td key={c.name} style={{
                        padding: '10px 14px',
                        color: '#111827',
                        fontFamily: (c.name.includes('id') || c.name.includes('time')) ? 'monospace' : 'inherit',
                        fontSize: c.name.includes('id') ? '11px' : '12px',
                        whiteSpace: 'nowrap',
                        maxWidth: '260px',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis'
                      }}>
                        {row[c.name] !== null ? String(row[c.name]) : <span style={{ color: '#9CA3AF' }}>NULL</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
