'use client'

import { useEffect, useState } from 'react'
import { Heart, RefreshCw } from 'lucide-react'

interface Verification {
  id: string
  incident_session_id: string
  evidence_type: string
  notes: string
  verified_at: string
  users: {
    display_name: string
    phone_hash: string
  } | null
  admin_accounts: {
    display_name: string
    email: string
  } | null
}

export default function SavedPage() {
  const [verifications, setVerifications] = useState<Verification[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)

  const fetchData = async () => {
    setLoading(true)
    const res = await fetch('/api/admin/saved')
    const data = await res.json()
    setVerifications(data.verifications || [])
    setTotal(data.total || 0)
    setLoading(false)
  }

  useEffect(() => {
    fetchData()
  }, [])

  const formatDate = (value: string) =>
    new Date(value).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Saved Verifications</h1>
          <p className="mt-1 text-sm text-slate-500">Approved rescue verifications and evidence records.</p>
        </div>
        <button
          onClick={fetchData}
          className="inline-flex items-center gap-2 rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300 hover:text-white"
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </section>

      <section className="rounded-2xl border border-white/[0.06] bg-[#111219] p-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#f43f5e26]">
            <Heart size={20} className="text-[#f43f5e]" />
          </div>
          <div>
            <p className="text-2xl font-bold text-white">{loading ? '...' : total.toLocaleString()}</p>
            <p className="text-xs uppercase tracking-wide text-slate-500">Verified Rescues</p>
          </div>
        </div>
        <p className="mt-3 text-sm text-slate-500">
          Only incidents with verified evidence are listed here for trusted reporting.
        </p>
      </section>

      <section className="overflow-hidden rounded-2xl border border-white/[0.06] bg-[#111219]">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-white/5 bg-[#0c0d13] text-xs uppercase tracking-widest text-slate-600">
                <th className="px-4 py-3 text-left">Verified At</th>
                <th className="px-4 py-3 text-left">User</th>
                <th className="px-4 py-3 text-left">Verified By</th>
                <th className="px-4 py-3 text-left">Evidence Type</th>
                <th className="px-4 py-3 text-left">Notes</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-slate-600">Loading...</td>
                </tr>
              ) : verifications.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <div className="flex min-h-[200px] flex-col items-center justify-center p-8 text-center">
                      <Heart size={28} className="text-slate-700" />
                      <p className="mt-2 text-sm text-slate-600">No verified rescues yet.</p>
                    </div>
                  </td>
                </tr>
              ) : (
                verifications.map((item) => (
                  <tr key={item.id} className="border-b border-white/5 text-sm hover:bg-white/[0.02]">
                    <td className="px-4 py-3 text-slate-300">{formatDate(item.verified_at)}</td>
                    <td className="px-4 py-3">
                      <div className="text-slate-100">{item.users?.display_name || 'Unknown User'}</div>
                      <div className="font-mono text-xs text-slate-500">{item.users?.phone_hash?.slice(0, 8) || 'N/A'}...</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-slate-100">{item.admin_accounts?.display_name || 'Unknown Admin'}</div>
                      <div className="text-xs text-slate-500">{item.admin_accounts?.email || ''}</div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="rounded-lg bg-[#f59e0b26] px-2 py-1 text-xs text-[#f59e0b]">
                        {item.evidence_type.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td className="max-w-sm truncate px-4 py-3 text-slate-400">{item.notes || 'No notes'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
