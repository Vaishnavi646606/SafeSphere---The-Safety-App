'use client'

import { useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'

interface AuditEntry {
  id: string
  action: string
  details: Record<string, any> | null
  created_at: string
  admin_id: string | null
  target_user_id: string | null
  admin_accounts: { display_name: string; email: string } | null
  users: { display_name: string; phone_hash: string } | null
}

const actionColors: Record<string, string> = {
  user_deactivated: 'border-rose-500/20 bg-rose-500/10 text-rose-300',
  user_reactivated: 'border-emerald-500/20 bg-emerald-500/10 text-emerald-300',
  rescue_verified: 'border-amber-500/20 bg-amber-500/10 text-amber-300',
  incident_updated: 'border-sky-500/20 bg-sky-500/10 text-sky-300'
}

const actionLabels: Record<string, string> = {
  user_deactivated: 'User Deactivated',
  user_reactivated: 'User Reactivated',
  rescue_verified: 'Rescue Verified',
  incident_updated: 'Incident Updated'
}

export default function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)

  const fetchEntries = async (pageNum = 0) => {
    setLoading(true)
    const res = await fetch(`/api/admin/audit?page=${pageNum}`)
    const data = await res.json()
    setEntries(data.entries || [])
    setLoading(false)
  }

  useEffect(() => {
    fetchEntries(page)
  }, [page])

  const formatAction = (action: string) => actionLabels[action] || action.replace(/_/g, ' ')

  const detailText = (entry: AuditEntry) => {
    if (!entry.details) return '—'
    if (entry.details.reason) return String(entry.details.reason)
    if (entry.details.evidence_type) return String(entry.details.evidence_type)
    return JSON.stringify(entry.details).slice(0, 80)
  }

  return (
    <div className="space-y-8">
      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-white">Audit Log</h1>
            <p className="mt-2 text-sm text-gray-400">Immutable and append-only trail of admin actions.</p>
          </div>
          <button
            className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium text-gray-300 hover:bg-white/10"
            onClick={() => fetchEntries(page)}
          >
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      <div className="rounded-2xl border border-cyan-500/20 bg-cyan-500/10 px-4 py-3 text-sm text-cyan-100">
        This log is tamper-evident and permanently records removals, verifications, messages, and privileged operations.
      </div>

      <div className="overflow-hidden rounded-2xl border border-white/5 bg-[#12121a] shadow-lg shadow-black/20">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="border-b border-white/5 bg-[#0f0f16] text-left text-xs uppercase tracking-wider text-gray-400">
              <tr>
                <th className="px-5 py-4">Timestamp</th>
                <th className="px-5 py-4">Admin</th>
                <th className="px-5 py-4">Action</th>
                <th className="px-5 py-4">Target User</th>
                <th className="px-5 py-4">Details</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} className="px-5 py-10 text-center text-gray-500">Loading...</td></tr>
              ) : entries.length === 0 ? (
                <tr><td colSpan={5} className="px-5 py-10 text-center text-gray-500">No audit entries.</td></tr>
              ) : (
                entries.map((entry) => (
                  <tr key={entry.id} className="border-b border-white/5 last:border-b-0 hover:bg-white/2">
                    <td className="whitespace-nowrap px-5 py-4 text-gray-300">{new Date(entry.created_at).toLocaleString('en-IN')}</td>
                    <td className="px-5 py-4">
                      <div className="font-medium text-gray-100">{entry.admin_accounts?.display_name || entry.admin_id?.slice(0, 8) || '—'}</div>
                      <div className="text-xs text-gray-500">{entry.admin_accounts?.email || ''}</div>
                    </td>
                    <td className="px-5 py-4">
                      <span className={`inline-flex rounded-xl border px-2.5 py-1 text-xs ${actionColors[entry.action] || 'border-white/10 bg-white/5 text-gray-300'}`}>
                        {formatAction(entry.action)}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      {entry.users ? (
                        <div>
                          <div className="font-medium text-gray-100">{entry.users.display_name}</div>
                          <div className="font-mono text-xs text-gray-500">{entry.users.phone_hash.slice(0, 8)}...</div>
                        </div>
                      ) : (
                        <span className="text-gray-500">{entry.target_user_id?.slice(0, 8) || '—'}</span>
                      )}
                    </td>
                    <td className="max-w-65 truncate px-5 py-4 text-xs text-gray-400">{detailText(entry)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex items-center justify-center gap-3">
        <button
          className="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-gray-300 hover:bg-white/10 disabled:opacity-50"
          onClick={() => setPage((p) => Math.max(0, p - 1))}
          disabled={page === 0 || loading}
        >
          Previous
        </button>
        <span className="text-sm text-gray-400">Page {page + 1}</span>
        <button
          className="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-gray-300 hover:bg-white/10 disabled:opacity-50"
          onClick={() => setPage((p) => p + 1)}
          disabled={entries.length < 50 || loading}
        >
          Next
        </button>
      </div>
    </div>
  )
}
