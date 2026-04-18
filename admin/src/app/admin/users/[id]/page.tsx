'use client'

import { useEffect, useMemo, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { AlertTriangle, ArrowLeft, Shield, RefreshCw, UserCheck, UserX } from 'lucide-react'
import StatsCard from '@/components/StatsCard'
import LoadingSkeleton from '@/components/LoadingSkeleton'

interface UserDetail {
  id: string
  display_name: string
  phone_hash: string
  created_at: string
  updated_at: string
  last_app_open: string | null
  is_active: boolean
  device_model: string | null
  android_version: string | null
  app_version: string | null
  os_type: string | null
  total_emergencies: number
  keyword: string | null
  emergency_contact_1: string | null
  emergency_contact_2: string | null
  emergency_contact_3: string | null
}

interface EmergencyEvent {
  id: string
  trigger_type: string | null
  triggered_at: string
  status: string
  resolution_type: string | null
  primary_contact_called: string | null
  primary_contact_answered: boolean | null
  time_to_resolve_s: number | null
  has_location_enabled: boolean | null
  requires_admin_review: boolean | null
}

interface FeedbackItem {
  id: string
  rating: number
  was_real_emergency: boolean
  was_rescued_or_helped: boolean | null
  feedback_text: string | null
  feedback_category: string | null
  submitted_at: string
}

interface VerificationItem {
  id: string
  incident_session_id: string
  evidence_type: string
  notes: string | null
  verified_at: string
}

interface LiveSession {
  found: boolean
  lat?: number
  lng?: number
  accuracy?: number
  last_updated?: string
  is_active?: boolean
}

export default function UserDetailPage() {
  const params = useParams()
  const router = useRouter()
  const userId = params.id as string

  const [user, setUser] = useState<UserDetail | null>(null)
  const [emergencies, setEmergencies] = useState<EmergencyEvent[]>([])
  const [feedback, setFeedback] = useState<FeedbackItem[]>([])
  const [verifications, setVerifications] = useState<VerificationItem[]>([])
  const [liveSession, setLiveSession] = useState<LiveSession | null>(null)
  const [liveLoading, setLiveLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—'
    return new Date(dateStr).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  const fetchUserData = async () => {
    setLoading(true)
    try {
      const res = await fetch(`/api/admin/users/${userId}`)
      const data = await res.json()
      setUser(data.user || null)
      setEmergencies(data.emergencies || [])
      setFeedback(data.feedback || [])
      setVerifications(data.verifications || [])
    } catch (error) {
      console.error('Failed to fetch user data:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchLiveSession = async () => {
    setLiveLoading(true)
    try {
      // Fetch live location token from users table via admin API
      const res = await fetch(`/api/admin/users/${userId}`)
      const data = await res.json()
      const token = data.user?.live_location_token
      if (!token) {
        setLiveSession({ found: false })
        return
      }
      const liveRes = await fetch(`/api/track/${token}`)
      const liveData = await liveRes.json()
      setLiveSession(liveData)
    } catch {
      setLiveSession({ found: false })
    } finally {
      setLiveLoading(false)
    }
  }

  useEffect(() => {
    fetchUserData()
  }, [userId])

  useEffect(() => {
    if (user) {
      fetchLiveSession()
      const interval = setInterval(fetchLiveSession, 30000)
      return () => clearInterval(interval)
    }
  }, [user])

  const toggleUserStatus = async () => {
    if (!user) return
    setActionLoading(true)
    try {
      const res = await fetch(`/api/admin/users/${user.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          is_active: !user.is_active,
          reason: user.is_active ? 'Deactivated from user detail page' : 'Reactivated from user detail page'
        })
      })
      if (!res.ok) throw new Error('Status update failed')
      await fetchUserData()
    } catch (error) {
      console.error(error)
    } finally {
      setActionLoading(false)
    }
  }

  const stats = useMemo(() => {
    const total = emergencies.length
    const rescued = emergencies.filter((event) => event.resolution_type === 'rescued' || event.resolution_type === 'safe_contact').length
    const falseAlarms = emergencies.filter((event) => event.resolution_type === 'false_alarm').length
    const reviewedNeeded = emergencies.filter((event) => event.requires_admin_review === true).length
    const verifiedRescues = verifications.length

    return { total, rescued, falseAlarms, reviewedNeeded, verifiedRescues }
  }, [emergencies, verifications])

const avgRating = useMemo(() => {
  if (feedback.length === 0) return 0
  const validRatings = feedback.filter((f) => f.rating > 0)
  if (validRatings.length === 0) return 0
  const sum = validRatings.reduce((acc, f) => acc + f.rating, 0)
  return Math.round((sum / validRatings.length) * 10) / 10
}, [feedback])

  if (loading) {
    return <LoadingSkeleton type="card" count={4} />
  }

  if (!user) {
    return <div className="rounded-2xl border border-white/5 bg-[#12121a] p-8 text-center text-gray-400">User not found</div>
  }

  return (
    <div className="space-y-6">
      {!user.is_active ? (
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm font-medium text-rose-300">
          INACTIVE: This user account is currently deactivated.
        </div>
      ) : null}

      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div className="flex items-center gap-4">
            <button
              onClick={() => router.back()}
              className="inline-flex items-center justify-center rounded-xl border border-white/10 bg-white/5 p-2 text-gray-300 transition-all duration-200 hover:bg-white/10"
            >
              <ArrowLeft size={18} />
            </button>
            <div>
              <h1 className="text-3xl font-bold tracking-tight text-white">{user.display_name}</h1>
              <p className="mt-2 text-sm text-gray-400">User ID: {user.id.slice(0, 8)}...</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={fetchUserData}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium text-gray-300 transition-all duration-200 hover:bg-white/10 disabled:opacity-60"
            >
              <RefreshCw size={16} /> Refresh
            </button>
            <button
              onClick={toggleUserStatus}
              disabled={actionLoading}
              className={`inline-flex items-center gap-2 rounded-xl border px-4 py-2 text-sm font-medium transition-all duration-200 disabled:opacity-60 ${
                user.is_active
                  ? 'border-rose-500/30 bg-rose-500/10 text-rose-300'
                  : 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
              }`}
            >
              {user.is_active ? <UserX size={16} /> : <UserCheck size={16} />}
              {actionLoading ? 'Updating...' : user.is_active ? 'Deactivate' : 'Reactivate'}
            </button>
          </div>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
          <h3 className="text-sm font-medium uppercase tracking-wider text-gray-400">Profile</h3>
          <div className="mt-4 space-y-3 text-sm text-gray-300">
            <p>Phone: {user.phone_hash.slice(0, 6)}****</p>
            <p>Registered: {formatDate(user.created_at)}</p>
            <p>Last Active: {formatDate(user.last_app_open)}</p>
            <p>Device: {user.device_model || 'Unknown'}</p>
            <p>Android Version: {user.android_version || 'N/A'}</p>
            <p>App Version: {user.app_version || 'N/A'}</p>
            <p>OS Type: {user.os_type || 'N/A'}</p>
            <p>Status: {user.is_active ? 'Active' : 'Inactive'}</p>
                      <div className="mt-4 border-t border-white/5 pt-3">
                        <p className="text-xs uppercase tracking-wider text-slate-500 mb-2">Safety Settings</p>
                        <p>Keyword: {user.keyword || 'Not set'}</p>
                        <p>Contact 1: {user.emergency_contact_1 || 'Not set'}</p>
                        <p>Contact 2: {user.emergency_contact_2 || 'Not set'}</p>
                        <p>Contact 3: {user.emergency_contact_3 || 'Not set'}</p>
                      </div>
          </div>
        </div>

        <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
          <h3 className="text-sm font-medium uppercase tracking-wider text-gray-400">Emergency Summary</h3>
          <div className="mt-4 space-y-3 text-sm text-gray-300">
            <p>Total Emergencies: {user.total_emergencies || 0}</p>
            <p>Recent Records Loaded: {stats.total}</p>
            <p>Rescued/Safe: {stats.rescued}</p>
            <p>Verified Rescues: {stats.verifiedRescues}</p>
            <p>False Alarms: {stats.falseAlarms}</p>
            <p>Requires Review: {stats.reviewedNeeded}</p>
          </div>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <StatsCard label="Total Emergencies" value={user.total_emergencies || 0} icon={AlertTriangle} color="#f59e0b" />
        <StatsCard label="Rescued/Safe" value={stats.rescued} icon={Shield} color="#10b981" />
        <StatsCard label="False Alarms" value={stats.falseAlarms} icon={AlertTriangle} color="#ef4444" />
        <StatsCard label="Avg Rating" value={avgRating} icon={Shield} color="#06b6d4" />
      </div>

      {/* Live Location Card */}
      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-xl font-semibold text-white">Live Location</h3>
          <div className="flex items-center gap-2">
            {liveSession?.found && liveSession?.is_active && liveSession?.last_updated && (() => {
              const diffMin = (Date.now() - new Date(liveSession.last_updated).getTime()) / 60000
              const color = diffMin < 5 ? 'bg-emerald-500' : diffMin < 15 ? 'bg-amber-500' : 'bg-red-500'
              const label = diffMin < 5 ? 'Live' : diffMin < 15 ? 'Recent' : 'Delayed'
              return (
                <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium text-white ${color}`}>
                  <span className="h-1.5 w-1.5 rounded-full bg-white/80" />
                  {label}
                </span>
              )
            })()}
            <button
              onClick={fetchLiveSession}
              disabled={liveLoading}
              className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-medium text-gray-300 hover:bg-white/10 disabled:opacity-60"
            >
              Refresh
            </button>
          </div>
        </div>
        {liveLoading && !liveSession ? (
          <p className="text-sm text-gray-500">Loading live session...</p>
        ) : !liveSession?.found ? (
          <div className="rounded-xl border border-white/5 bg-[#16171f] p-4">
            <p className="text-sm text-gray-500">No active live session found for this user.</p>
            <p className="text-xs text-gray-600 mt-1">Live sessions are created when the user has protection ON and location enabled.</p>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-xl border border-white/5 bg-[#16171f] p-3">
                <p className="text-xs text-gray-500 mb-1">Latitude</p>
                <p className="text-sm font-mono text-white">{liveSession.lat?.toFixed(6)}</p>
              </div>
              <div className="rounded-xl border border-white/5 bg-[#16171f] p-3">
                <p className="text-xs text-gray-500 mb-1">Longitude</p>
                <p className="text-sm font-mono text-white">{liveSession.lng?.toFixed(6)}</p>
              </div>
            </div>
            <div className="rounded-xl border border-white/5 bg-[#16171f] p-3 flex justify-between items-center">
              <div>
                <p className="text-xs text-gray-500 mb-1">Last Updated</p>
                <p className="text-sm text-white">{formatDate(liveSession.last_updated ?? null)}</p>
              </div>
              {liveSession.accuracy != null && liveSession.accuracy > 0 && (
                <div className="text-right">
                  <p className="text-xs text-gray-500 mb-1">Accuracy</p>
                  <p className="text-sm text-white">±{Math.round(liveSession.accuracy)}m</p>
                </div>
              )}
            </div>
            <a
              href={`https://maps.google.com/?q=${liveSession.lat},${liveSession.lng}`}
              target="_blank"
              rel="noopener noreferrer"
              className="flex rounded-xl bg-emerald-600 hover:bg-emerald-500 px-4 py-2 text-center text-sm font-medium text-white transition-colors justify-center"
            >
              Open in Google Maps
            </a>
            <div className="overflow-hidden rounded-xl border border-white/5 bg-[#16171f]">
              <iframe
                title="Live location map preview"
                src={`https://www.google.com/maps?q=${liveSession.lat},${liveSession.lng}&z=16&output=embed`}
                className="h-72 w-full"
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <h3 className="text-xl font-semibold text-white">Rescue Verification</h3>
        <p className="mt-1 text-sm text-gray-500">Manual confirmations saved by admins and feedback marked as helped.</p>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <div className="rounded-xl border border-white/5 bg-[#16171f] p-4 text-sm text-gray-300">
            <p className="text-xs uppercase tracking-wider text-gray-500">Manual verifications</p>
            <p className="mt-2 text-2xl font-semibold text-white">{verifications.length}</p>
            <p className="mt-1 text-xs text-gray-500">Saved rescue confirmations for this user.</p>
          </div>
          <div className="rounded-xl border border-white/5 bg-[#16171f] p-4 text-sm text-gray-300">
            <p className="text-xs uppercase tracking-wider text-gray-500">Feedback helped</p>
            <p className="mt-2 text-2xl font-semibold text-white">{feedback.filter((item) => item.was_rescued_or_helped === true).length}</p>
            <p className="mt-1 text-xs text-gray-500">User feedback that says help was received.</p>
          </div>
        </div>

        <div className="mt-4 space-y-3">
          {verifications.length === 0 ? (
            <p className="text-sm text-gray-500">No manual rescue verification has been saved yet.</p>
          ) : (
            verifications.map((item) => (
              <div key={item.id} className="rounded-xl border border-white/5 bg-[#16171f] p-4 text-sm text-gray-300">
                <p className="font-medium text-white">{item.evidence_type.replace(/_/g, ' ')}</p>
                <p className="mt-1 text-xs text-gray-500">Verified at {formatDate(item.verified_at)}</p>
                <p className="mt-1 text-xs text-gray-500">Session: {item.incident_session_id}</p>
                <p className="mt-2 text-slate-400">{item.notes || 'No notes provided'}</p>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <h3 className="mb-4 text-xl font-semibold text-white">Emergency History</h3>
        {emergencies.length === 0 ? (
          <p className="text-sm text-gray-500">No emergency history</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-white/5 text-xs uppercase tracking-widest text-slate-500">
                  <th className="px-3 py-2 text-left">Date</th>
                  <th className="px-3 py-2 text-left">Trigger</th>
                  <th className="px-3 py-2 text-left">Status</th>
                  <th className="px-3 py-2 text-left">Outcome</th>
                  <th className="px-3 py-2 text-left">Contact</th>
                </tr>
              </thead>
              <tbody>
                {emergencies.map((event) => (
                  <tr key={event.id} className="border-b border-white/5 text-sm text-slate-300">
                    <td className="px-3 py-2">{formatDate(event.triggered_at)}</td>
                    <td className="px-3 py-2">{(event.trigger_type || 'unknown').replace(/_/g, ' ')}</td>
                    <td className="px-3 py-2">{event.status}</td>
                    <td className="px-3 py-2">{(event.resolution_type || 'pending').replace(/_/g, ' ')}</td>
                    <td className="px-3 py-2">{event.primary_contact_called || 'None'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <h3 className="mb-4 text-xl font-semibold text-white">Feedback Given</h3>
        {feedback.length === 0 ? (
          <p className="text-sm text-gray-500">No feedback records</p>
        ) : (
          <div className="space-y-3">
            {feedback.map((item) => (
              <div key={item.id} className="rounded-xl border border-white/5 bg-[#16171f] p-3 text-sm text-slate-300">
                <p className="text-xs text-slate-500">{formatDate(item.submitted_at)}</p>
                <p className="mt-1">Rating: {item.rating || 0}/5</p>
                <p>Real Emergency: {item.was_real_emergency ? 'Yes' : 'No'}</p>
                <p>Rescued/Helped: {item.was_rescued_or_helped === null ? 'N/A' : item.was_rescued_or_helped ? 'Yes' : 'No'}</p>
                <p>Category: {item.feedback_category || 'N/A'}</p>
                <p className="mt-1 text-slate-400">{item.feedback_text || 'No text feedback.'}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
