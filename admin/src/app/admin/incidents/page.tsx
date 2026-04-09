'use client'

import { Fragment, useEffect, useMemo, useState } from 'react'
import { AlertTriangle, ChevronDown, ChevronUp, RefreshCw, Search } from 'lucide-react'
import StatusBadge from '@/components/StatusBadge'
import LoadingSkeleton from '@/components/LoadingSkeleton'

interface Incident {
  id: string
  user_id: string
  session_id?: string | null
  trigger_type: string | null
  triggered_at: string
  resolved_at: string | null
  status: string
  primary_contact_called: string | null
  primary_contact_answered: boolean | null
  primary_contact_duration_s: number | null
  secondary_contact_called: string | null
  secondary_contact_answered: boolean | null
  tertiary_contact_called: string | null
  tertiary_contact_answered: boolean | null
  time_to_first_contact_s: number | null
  time_to_answer_s: number | null
  time_to_resolve_s: number | null
  resolution_type: string | null
  admin_notes: string | null
  location_lat: number | null
  location_lng: number | null
  has_location_enabled: boolean | null
  phone_battery_percent: number | null
  is_test_event: boolean | null
  requires_admin_review: boolean | null
  sms_sent_to: string | null
  users?: {
    display_name: string
    phone_hash: string
  }
}

const statusLabels: Record<string, string> = {
  triggered: 'Triggered',
  call_made: 'Contact Called',
  call_answered: 'Call Answered',
  in_progress: 'In Progress',
  resolved: 'Resolved'
}

export default function IncidentsPage() {
  const [incidents, setIncidents] = useState<Incident[]>([])
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [savingNotes, setSavingNotes] = useState<string | null>(null)
  const [verifyingId, setVerifyingId] = useState<string | null>(null)
  const [verifyForm, setVerifyForm] = useState<Record<string, { evidence_type: string; notes: string }>>({})
  const [verifySuccess, setVerifySuccess] = useState<Record<string, boolean>>({})
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [verifiedIncidents, setVerifiedIncidents] = useState<Set<string>>(new Set())
  const [updatingOutcome, setUpdatingOutcome] = useState<string | null>(null)
  const [activeFilter, setActiveFilter] = useState('all')

  const updateOutcome = async (incidentId: string, resolutionType: string) => {
    setUpdatingOutcome(incidentId)
    try {
      const res = await fetch(`/api/admin/incidents/${incidentId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ resolution_type: resolutionType })
      })
      if (!res.ok) throw new Error('Failed to update outcome')
      setIncidents((prev) =>
        prev.map((item) =>
          item.id === incidentId ? { ...item, resolution_type: resolutionType } : item
        )
      )
    } catch (error) {
      console.error('Failed to update outcome:', error)
    } finally {
      setUpdatingOutcome(null)
    }
  }

  const fetchVerifiedIncidents = async () => {
    try {
      const res = await fetch('/api/admin/saved')
      const data = await res.json()
      const verified = new Set(
        (data.verifications || []).map((v: any) => v.incident_session_id)
      )
      setVerifiedIncidents(verified)
    } catch (error) {
      console.error('Failed to fetch verified incidents:', error)
    }
  }

  const deleteVerification = async (incident: Incident) => {
    setDeletingId(incident.id)
    try {
      const res = await fetch('/api/admin/verify-rescue', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          incident_session_id: incident.session_id || incident.id,
          user_id: incident.user_id
        })
      })
      if (res.ok) {
        setVerifiedIncidents((prev) => {
          const updated = new Set(prev)
          updated.delete(incident.session_id || incident.id)
          return updated
        })
        setVerifySuccess((prev) => {
          const updated = { ...prev }
          delete updated[incident.id]
          return updated
        })
      }
    } catch (error) {
      console.error('Failed to delete verification:', error)
    } finally {
      setDeletingId(null)
    }
  }
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)

  const filters = ['all', 'triggered', 'call_made', 'call_answered', 'in_progress', 'resolved']

  const formatDate = (value: string | null) => {
    if (!value) return 'Not resolved'
    return new Date(value).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  const pretty = (value: string | null | undefined, fallback = 'Unknown') => {
    if (!value) return fallback
    return value.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
  }

  const fetchIncidents = async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (activeFilter !== 'all') params.set('status', activeFilter)

      const res = await fetch(`/api/admin/incidents?${params.toString()}`)
      const data = await res.json()
      setIncidents(data.data || [])
      await fetchVerifiedIncidents()
    } catch (error) {
      console.error('Failed to fetch incidents:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchIncidents()
  }, [activeFilter])

  const filteredIncidents = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return incidents

    return incidents.filter((incident) => {
      const name = incident.users?.display_name?.toLowerCase() || ''
      const phone = incident.users?.phone_hash?.toLowerCase() || ''
      const trigger = incident.trigger_type?.toLowerCase() || ''
      return (
        name.includes(q) ||
        phone.includes(q) ||
        trigger.includes(q) ||
        incident.id.toLowerCase().includes(q)
      )
    })
  }, [incidents, search])

  const saveNotes = async (incidentId: string) => {
    setSavingNotes(incidentId)
    try {
      const res = await fetch(`/api/admin/incidents/${incidentId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ admin_notes: notes[incidentId] || '' })
      })
      if (!res.ok) throw new Error('Failed to save')

      setIncidents((prev) =>
        prev.map((item) =>
          item.id === incidentId ? { ...item, admin_notes: notes[incidentId] || '' } : item
        )
      )
    } catch (error) {
      console.error(error)
    } finally {
      setSavingNotes(null)
    }
  }

  const verifyRescue = async (incident: Incident) => {
    setVerifyingId(incident.id)
    try {
      const res = await fetch('/api/admin/verify-rescue', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          user_id: incident.user_id,
          incident_id: incident.id,
          session_id: incident.session_id || incident.id,
          evidence_type: verifyForm[incident.id]?.evidence_type || 'verbal_confirmation',
          notes: verifyForm[incident.id]?.notes || ''
        })
      })
      if (res.ok) {
        setVerifiedIncidents((prev) => new Set(prev).add(incident.session_id || incident.id))
        setVerifySuccess((prev) => ({ ...prev, [incident.id]: true }))
      } else if (res.status === 409) {
        // Already verified, update UI
        setVerifiedIncidents((prev) => new Set(prev).add(incident.session_id || incident.id))
        console.log('Already verified')
      } else {
        const error = await res.json()
        console.error('Verification failed:', error.error)
      }
    } catch (e) {
      console.error('Failed to verify rescue:', e)
    } finally {
      setVerifyingId(null)
    }
  }

  return (
    <div className="space-y-6">
      <section className="space-y-4">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <h1 className="text-xl font-semibold text-white">Incidents</h1>
            <p className="mt-1 text-sm text-slate-500">Emergency sessions and outcomes in compact operational view.</p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {filters.map((item) => (
              <button
                key={item}
                onClick={() => setActiveFilter(item)}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                  activeFilter === item
                    ? 'bg-emerald-500/10 text-emerald-400'
                    : 'text-slate-400 hover:bg-white/5'
                }`}
              >
                {item === 'all' ? 'All' : pretty(item)}
              </button>
            ))}
            <button
              onClick={fetchIncidents}
              className="ml-1 rounded-lg border border-white/[0.08] bg-white/5 p-1.5 text-slate-400 hover:text-white"
              title="Refresh"
            >
              <RefreshCw size={14} />
            </button>
          </div>
        </div>

        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by user, trigger, phone, or incident id"
            className="w-full rounded-xl border border-white/[0.08] bg-[#16171f] py-2.5 pl-9 pr-3 text-sm text-slate-200 outline-none focus:border-emerald-500/40"
          />
        </div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-white/[0.06] bg-[#111219]">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-4">
              <LoadingSkeleton type="table" count={6} />
            </div>
          ) : filteredIncidents.length === 0 ? (
            <div className="flex min-h-[200px] flex-col items-center justify-center p-8 text-center">
              <AlertTriangle size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">No incidents found for the current filter.</p>
            </div>
          ) : (
            <table className="w-full">
              <thead>
                <tr className="border-b border-white/5 bg-[#0c0d13] text-xs uppercase tracking-widest text-slate-600">
                  <th className="px-4 py-3 text-left">Date</th>
                  <th className="px-4 py-3 text-left">User</th>
                  <th className="px-4 py-3 text-left">Trigger</th>
                  <th className="px-4 py-3 text-center">Status</th>
                  <th className="px-4 py-3 text-center">Contact</th>
                  <th className="px-4 py-3 text-center">Outcome</th>
                  <th className="px-4 py-3 text-center">Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredIncidents.map((incident) => {
                  const isExpanded = expandedId === incident.id
                  return (
                    <Fragment key={incident.id}>
                      <tr className="border-b border-white/5 text-sm hover:bg-white/[0.02]">
                        <td className="whitespace-nowrap px-4 py-3 text-slate-300">{formatDate(incident.triggered_at)}</td>
                        <td className="px-4 py-3">
                          <p className="text-slate-100">{incident.users?.display_name || 'Unknown'}</p>
                          <p className="text-xs text-slate-500">{incident.users?.phone_hash?.slice(0, 6) || 'NA'}****</p>
                        </td>
                        <td className="px-4 py-3 text-slate-300">{pretty(incident.trigger_type)}</td>
                        <td className="px-4 py-3 text-center">
                          <StatusBadge
                            status={incident.status === 'resolved' ? 'success' : 'pending'}
                            label={pretty(statusLabels[incident.status] || incident.status)}
                            size="sm"
                            icon={false}
                          />
                        </td>
                        <td className="px-4 py-3 text-center">
                          {incident.primary_contact_answered || incident.secondary_contact_answered || incident.tertiary_contact_answered ? (
                            <StatusBadge status="success" label="Answered" size="sm" icon={false} />
                          ) : incident.primary_contact_called ? (
                            <StatusBadge status="pending" label="Called" size="sm" icon={false} />
                          ) : (
                            <StatusBadge status="warning" label="Not Called" size="sm" icon={false} />
                          )}
                        </td>
                        <td className="px-4 py-3 text-center">
                          <StatusBadge
                            status={incident.resolution_type ? 'success' : 'pending'}
                            label={pretty(incident.resolution_type, 'Pending')}
                            size="sm"
                            icon={false}
                          />
                        </td>
                        <td className="px-4 py-3 text-center">
                          <button
                            onClick={() => setExpandedId(expandedId === incident.id ? null : incident.id)}
                            className="rounded-lg border border-white/[0.08] bg-white/5 p-1.5 text-slate-400 hover:text-white"
                          >
                            {isExpanded ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
                          </button>
                        </td>
                      </tr>

                      {isExpanded ? (
                        <tr key={`${incident.id}-expanded`}>
                          <td colSpan={8} className="bg-[#0c0d13] border-b border-white/5 px-6 py-5">
                            <div className="grid gap-4 lg:grid-cols-3">
                              <div className="space-y-2 rounded-xl border border-white/[0.08] bg-[#111219] p-4 text-sm text-slate-300">
                                <h4 className="text-xs uppercase tracking-widest text-slate-500">Emergency Details</h4>
                                <p>Trigger: {pretty(incident.trigger_type)}</p>
                                <p>Status: {pretty(incident.status)}</p>
                                <p>Resolution: {pretty(incident.resolution_type, 'Pending')}</p>
                                <p>Time triggered: {formatDate(incident.triggered_at)}</p>
                                <p>Time resolved: {formatDate(incident.resolved_at)}</p>
                                <p>Battery: {incident.phone_battery_percent !== null ? `${incident.phone_battery_percent}%` : 'Unknown'}</p>
                                <p>
                                  Location:{' '}
                                  {incident.has_location_enabled && incident.location_lat !== null && incident.location_lng !== null
                                    ? `${incident.location_lat}, ${incident.location_lng}`
                                    : 'Not available'}
                                </p>
                                <p>
                                  Test event:{' '}
                                  {incident.is_test_event ? (
                                    <span className="rounded bg-amber-500/20 px-2 py-0.5 text-xs text-amber-300">Yes</span>
                                  ) : (
                                    <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-xs text-emerald-300">No</span>
                                  )}
                                </p>
                              </div>

                              <div className="space-y-2 rounded-xl border border-white/[0.08] bg-[#111219] p-4 text-sm text-slate-300">
                                <h4 className="text-xs uppercase tracking-widest text-slate-500">Contact Details</h4>
                                <p>Primary contact: {incident.primary_contact_called || 'None'}</p>
                                <p>
                                  Primary answered:{' '}
                                  {incident.primary_contact_answered ? (
                                    <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-xs text-emerald-300">Yes</span>
                                  ) : (
                                    <span className="rounded bg-rose-500/20 px-2 py-0.5 text-xs text-rose-300">No</span>
                                  )}
                                </p>
                                <p>Primary duration: {incident.primary_contact_duration_s ? `${incident.primary_contact_duration_s}s` : '-'}</p>
                                <p>Secondary contact: {incident.secondary_contact_called || 'None'}</p>
                                <p>
                                  Secondary answered:{' '}
                                  {incident.secondary_contact_answered ? (
                                    <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-xs text-emerald-300">Yes</span>
                                  ) : (
                                    incident.secondary_contact_called ? (
                                    <span className="rounded bg-rose-500/20 px-2 py-0.5 text-xs text-rose-300">No</span>
                                  ) : null
                                  )}
                                </p>
                                <p>Tertiary contact: {incident.tertiary_contact_called || 'None'}</p>
                                                                <p>
                                                                  Tertiary answered:{' '}
                                                                  {incident.tertiary_contact_answered ? (
                                                                    <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-xs text-emerald-300">Yes</span>
                                                                  ) : incident.tertiary_contact_called ? (
                                                                    <span className="rounded bg-rose-500/20 px-2 py-0.5 text-xs text-rose-300">No</span>
                                                                  ) : null}
                                                                </p>
                                <p>SMS sent to: {incident.sms_sent_to || 'None'}</p>
                              </div>

                              <div className="space-y-2 rounded-xl border border-white/[0.08] bg-[#111219] p-4 text-sm text-slate-300">
                                <h4 className="text-xs uppercase tracking-widest text-slate-500">Response Times</h4>
                                <p>Time to first contact: {incident.time_to_first_contact_s ? `${incident.time_to_first_contact_s}s` : 'N/A'}</p>
                                <p>Time to answer: {incident.time_to_answer_s ? `${incident.time_to_answer_s}s` : 'N/A'}</p>
                                <p>Time to resolve: {incident.time_to_resolve_s ? `${incident.time_to_resolve_s}s` : 'N/A'}</p>
                                {incident.requires_admin_review ? (
                                  <p>
                                    <span className="rounded bg-rose-500/20 px-2 py-1 text-xs text-rose-300">⚠ Requires Admin Review</span>
                                  </p>
                                ) : null}
                              </div>
                            </div>

                            <div className="mt-4 rounded-xl border border-white/[0.08] bg-[#111219] p-4">
                              <h4 className="text-xs uppercase tracking-widest text-slate-500">Admin Notes</h4>
                              <textarea
                                value={notes[incident.id] ?? incident.admin_notes ?? ''}
                                onChange={(e) =>
                                  setNotes((prev) => ({
                                    ...prev,
                                    [incident.id]: e.target.value
                                  }))
                                }
                                placeholder="Add admin notes about this incident..."
                                className="mt-2 w-full bg-[#16171f] border border-white/10 rounded-xl p-3 text-sm text-white"
                                rows={3}
                              />
                              <button
                                onClick={() => saveNotes(incident.id)}
                                disabled={savingNotes === incident.id}
                                className="mt-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-400 disabled:opacity-60"
                              >
                                {savingNotes === incident.id ? 'Saving...' : 'Save Notes'}

                                                          <div className="mt-4 rounded-xl border border-white/[0.08] bg-[#111219] p-4">
                                                            <h4 className="text-xs uppercase tracking-widest text-slate-500">Set Outcome</h4>
                                                            <div className="mt-2 flex items-center gap-3">
                                                              <select
                                                                value={incident.resolution_type || ''}
                                                                onChange={(e) => updateOutcome(incident.id, e.target.value)}
                                                                disabled={updatingOutcome === incident.id}
                                                                className="rounded-xl border border-white/10 bg-[#16171f] px-3 py-2 text-sm text-white"
                                                              >
                                                                <option value="">Pending</option>
                                                                <option value="rescued">Rescued</option>
                                                                <option value="safe_contact">Safe - Contact Helped</option>
                                                                <option value="safe_self">Safe - Self Resolved</option>
                                                                <option value="false_alarm">False Alarm</option>
                                                                <option value="no_response">No Response</option>
                                                                <option value="test">Test Event</option>
                                                              </select>
                                                              {updatingOutcome === incident.id && (
                                                                <span className="text-xs text-slate-400">Saving...</span>
                                                              )}
                                                            </div>
                                                          </div>

                              </button>
                            </div>

                            {(incident.resolution_type === 'rescued' ||
                              incident.resolution_type === 'safe_contact' ||
                              incident.resolution_type === 'safe_self') ? (
                              verifiedIncidents.has(incident.session_id || incident.id) ? (
                                <div className="mt-4 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-4">
                                  <div className="flex items-center justify-between gap-4">
                                    <div>
                                      <p className="text-sm font-semibold text-emerald-300">✓ Already Verified</p>
                                      <p className="mt-1 text-xs text-emerald-200/70">
                                        This rescue has been officially verified and saved to Trusted Reports.
                                      </p>
                                    </div>
                                    <button
                                      onClick={() => deleteVerification(incident)}
                                      disabled={deletingId === incident.id}
                                      className="shrink-0 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1.5 text-xs font-medium text-rose-400 hover:bg-rose-500/20 disabled:opacity-60 transition-all"
                                    >
                                      {deletingId === incident.id ? 'Deleting...' : '✕ Delete'}
                                    </button>
                                  </div>
                                </div>
                              ) : (
                                <div className="mt-4 rounded-2xl border border-emerald-500/20 bg-emerald-500/5 p-4">
                                  <h4 className="text-xs font-semibold uppercase tracking-wider text-emerald-400">
                                    ✓ Verify This Rescue
                                  </h4>
                                  <p className="mt-1 text-xs text-gray-500">
                                    Officially verify this rescue to add it to Saved Verifications for trusted reporting.
                                  </p>

                                  <div className="mt-3 grid gap-3 md:grid-cols-2">
                                    <div>
                                      <label className="text-xs text-gray-500">Evidence Type</label>
                                      <select
                                        value={verifyForm[incident.id]?.evidence_type || 'verbal_confirmation'}
                                        onChange={(e) =>
                                          setVerifyForm((prev) => ({
                                            ...prev,
                                            [incident.id]: {
                                              evidence_type: e.target.value,
                                              notes: prev[incident.id]?.notes || ''
                                            }
                                          }))
                                        }
                                        className="mt-1 w-full rounded-xl border border-white/10 bg-[#16171f] px-3 py-2 text-sm text-white"
                                      >
                                        <option value="verbal_confirmation">Verbal Confirmation</option>
                                        <option value="police_report">Police Report</option>
                                        <option value="hospital_record">Hospital Record</option>
                                        <option value="witness_statement">Witness Statement</option>
                                        <option value="call_recording">Call Recording</option>
                                        <option value="other">Other</option>
                                      </select>
                                    </div>

                                    <div>
                                      <label className="text-xs text-gray-500">Verification Notes</label>
                                      <input
                                        type="text"
                                        placeholder="Brief description of evidence..."
                                        value={verifyForm[incident.id]?.notes || ''}
                                        onChange={(e) =>
                                          setVerifyForm((prev) => ({
                                            ...prev,
                                            [incident.id]: {
                                              evidence_type: prev[incident.id]?.evidence_type || 'verbal_confirmation',
                                              notes: e.target.value
                                            }
                                          }))
                                        }
                                        className="mt-1 w-full rounded-xl border border-white/10 bg-[#16171f] px-3 py-2 text-sm text-white placeholder-gray-600"
                                      />
                                    </div>
                                  </div>

                                  <button
                                    onClick={() => verifyRescue(incident)}
                                    disabled={verifyingId === incident.id}
                                    className="mt-3 rounded-xl border border-emerald-500/40 bg-emerald-600 px-4 py-2 text-sm font-medium text-white shadow-lg shadow-emerald-500/20 hover:bg-emerald-500 disabled:opacity-60 transition-all"
                                  >
                                    {verifyingId === incident.id ? 'Verifying...' : '✓ Confirm & Save Verification'}
                                  </button>
                                </div>
                              )
                            ) : null}
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </div>
  )
}
