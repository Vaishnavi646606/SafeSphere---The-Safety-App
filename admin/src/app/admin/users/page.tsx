'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { AlertTriangle, Eye, RefreshCw, Search, UserCheck, UserX, Users, X } from 'lucide-react'

interface UserRecord {
  id: string
  display_name: string
  phone_hash: string
  created_at: string
  updated_at?: string
  last_app_open: string | null
  is_active: boolean
  device_model: string | null
  android_version: string | null
  app_version: string | null
  os_type: string | null
  total_emergencies: number
}

interface UserIncident {
  id: string
  trigger_type: string | null
  triggered_at: string
  status: string
}

export default function UsersPage() {
  const router = useRouter()

  const [users, setUsers] = useState<UserRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [filter, setFilter] = useState<'all' | 'active' | 'removed'>('all')
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null)
  const [showUserModal, setShowUserModal] = useState(false)
  const [showDeactivateModal, setShowDeactivateModal] = useState(false)
  const [deactivateReason, setDeactivateReason] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [modalIncidents, setModalIncidents] = useState<UserIncident[]>([])
  const [modalIncidentsLoading, setModalIncidentsLoading] = useState(false)

  const fetchUsers = async () => {
    setLoading(true)
    try {
      const res = await fetch(`/api/admin/users?filter=${filter}&search=${encodeURIComponent(search)}`)
      const data = await res.json()
      setUsers(data.users || [])
    } catch (error) {
      console.error('Failed to fetch users:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchUsers()
  }, [filter])

  const filteredUsers = useMemo(() => {
    const q = search.toLowerCase().trim()
    if (!q) return users
    return users.filter(
      (u) => u.display_name.toLowerCase().includes(q) || u.phone_hash.toLowerCase().includes(q)
    )
  }, [users, search])

  const formatDate = (value: string | null) => {
    if (!value) return 'N/A'
    return new Date(value).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  const maskedPhone = (hash: string) => `${hash.slice(0, 6)}****`

  const openUserModal = async (user: UserRecord) => {
    setSelectedUser(user)
    setShowUserModal(true)
    setModalIncidentsLoading(true)
    try {
      const res = await fetch(`/api/admin/users/${user.id}/incidents`)
      const data = await res.json()
      setModalIncidents(data.incidents || [])
    } catch (error) {
      console.error('Failed to fetch user incidents:', error)
      setModalIncidents([])
    } finally {
      setModalIncidentsLoading(false)
    }
  }

  const deactivateUser = async (userId: string, reason: string) => {
    setActionLoading(true)
    try {
      const res = await fetch(`/api/admin/users/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ is_active: false, reason })
      })
      if (!res.ok) throw new Error('Failed')
      setShowDeactivateModal(false)
      setShowUserModal(false)
      setDeactivateReason('')
      await fetchUsers()
    } catch (error) {
      console.error(error)
    } finally {
      setActionLoading(false)
    }
  }

  const reactivateUser = async (userId: string) => {
    setActionLoading(true)
    try {
      const res = await fetch(`/api/admin/users/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ is_active: true })
      })
      if (res.ok) await fetchUsers()
    } catch (error) {
      console.error(error)
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Users</h1>
          <p className="mt-1 text-sm text-slate-500">Manage user lifecycle, status, and account actions.</p>
        </div>
        <button
          className="inline-flex items-center gap-2 rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300 hover:text-white"
          onClick={fetchUsers}
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </section>

      <section className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="relative w-full lg:max-w-md">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            className="w-full rounded-xl border border-white/[0.08] bg-[#16171f] py-2.5 pl-9 pr-3 text-sm text-slate-200 outline-none focus:border-emerald-500/40"
            placeholder="Search users by name or phone"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setSearch(searchInput)
                fetchUsers()
              }
            }}
          />
        </div>

        <div className="flex gap-1.5">
          <button
            onClick={() => {
              setSearch(searchInput)
              fetchUsers()
            }}
            className="rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300 hover:text-white"
          >
            Search
          </button>
          {(['all', 'active', 'removed'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === f ? 'bg-emerald-500/10 text-emerald-400' : 'text-slate-400 hover:bg-white/5'
              }`}
            >
              {f === 'removed' ? 'inactive' : f}
            </button>
          ))}
        </div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-white/[0.06] bg-[#111219]">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-white/5 bg-[#0c0d13] text-xs uppercase tracking-widest text-slate-600">
                <th className="px-4 py-3 text-left">Name</th>
                <th className="px-4 py-3 text-left">Phone</th>
                <th className="px-4 py-3 text-left">Registered</th>
                <th className="px-4 py-3 text-left">Last Seen</th>
                <th className="px-4 py-3 text-center">Emergencies</th>
                <th className="px-4 py-3 text-center">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>

            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-slate-600">
                    Loading users...
                  </td>
                </tr>
              ) : filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <div className="flex min-h-[200px] flex-col items-center justify-center px-6 py-10 text-center">
                      <Users size={28} className="text-slate-700" />
                      <p className="mt-2 text-sm text-slate-600">No users found for this filter.</p>
                    </div>
                  </td>
                </tr>
              ) : (
                filteredUsers.map((user) => (
                  <tr key={user.id} className="border-b border-white/5 text-sm hover:bg-white/[0.02]">
                    <td className="px-4 py-3 font-medium text-slate-100">{user.display_name}</td>
                    <td className="px-4 py-3 font-mono text-slate-400">{maskedPhone(user.phone_hash)}</td>
                    <td className="px-4 py-3 text-slate-400">{formatDate(user.created_at)}</td>
                    <td className="px-4 py-3 text-slate-400">{formatDate(user.last_app_open)}</td>
                    <td className="px-4 py-3 text-center text-slate-300">{user.total_emergencies || 0}</td>
                    <td className="px-4 py-3 text-center">
                      {user.is_active ? (
                        <span className="rounded-lg bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">Active</span>
                      ) : (
                        <span className="rounded-lg bg-rose-500/10 px-2 py-1 text-xs text-rose-400">Inactive</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1.5">
                        <button
                          onClick={() => openUserModal(user)}
                          className="inline-flex items-center gap-1 rounded-lg border border-white/[0.08] bg-white/5 px-2.5 py-1.5 text-xs text-sky-300 hover:text-sky-200"
                          title="View Profile"
                        >
                          <Eye size={13} />
                        </button>

                        {user.is_active ? (
                          <button
                            onClick={() => {
                              setSelectedUser(user)
                              setShowDeactivateModal(true)
                            }}
                            className="inline-flex items-center gap-1 rounded-lg border border-white/[0.08] bg-white/5 px-2.5 py-1.5 text-xs text-rose-300 hover:text-rose-200"
                            title="Deactivate"
                          >
                            <UserX size={13} />
                          </button>
                        ) : (
                          <button
                            onClick={() => reactivateUser(user.id)}
                            disabled={actionLoading}
                            className="inline-flex items-center gap-1 rounded-lg border border-white/[0.08] bg-white/5 px-2.5 py-1.5 text-xs text-emerald-300 hover:text-emerald-200 disabled:opacity-60"
                            title="Reactivate"
                          >
                            <UserCheck size={13} />
                          </button>
                        )}

                        <button
                          onClick={() => router.push(`/admin/incidents?user_id=${user.id}`)}
                          className="inline-flex items-center gap-1 rounded-lg border border-white/[0.08] bg-white/5 px-2.5 py-1.5 text-xs text-amber-300 hover:text-amber-200"
                          title="View Incidents"
                        >
                          <AlertTriangle size={13} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {showUserModal && selectedUser ? (
        <div className="fixed inset-0 z-50 bg-black/60 p-4 backdrop-blur-sm">
          <div className="mx-auto w-full max-w-2xl rounded-2xl border border-white/10 bg-[#111219] p-6">
            <div className="mb-4 flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/20 text-lg font-semibold text-emerald-300">
                  {selectedUser.display_name
                    .split(' ')
                    .map((part) => part[0])
                    .join('')
                    .slice(0, 2)
                    .toUpperCase()}
                </div>
                <div>
                  <h3 className="text-lg font-semibold text-white">{selectedUser.display_name}</h3>
                  <p className="text-xs text-slate-500">User profile details</p>
                </div>
              </div>
              <button
                onClick={() => setShowUserModal(false)}
                className="rounded-lg border border-white/[0.08] bg-white/5 p-1.5 text-slate-300"
              >
                <X size={14} />
              </button>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">Phone: {maskedPhone(selectedUser.phone_hash)}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">Registered: {formatDate(selectedUser.created_at)}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">Last Active: {formatDate(selectedUser.last_app_open)}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">Device: {selectedUser.device_model || 'Unknown'}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">Android Version: {selectedUser.android_version || 'N/A'}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">App Version: {selectedUser.app_version || 'N/A'}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">OS: {selectedUser.os_type || 'N/A'}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300">Total Emergencies: {selectedUser.total_emergencies || 0}</div>
              <div className="rounded-xl border border-white/[0.06] bg-[#16171f] p-3 text-sm text-slate-300 md:col-span-2">
                Status:{' '}
                {selectedUser.is_active ? (
                  <span className="rounded-lg bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">Active</span>
                ) : (
                  <span className="rounded-lg bg-rose-500/10 px-2 py-1 text-xs text-rose-400">Inactive</span>
                )}
              </div>
            </div>

            <div className="mt-4 rounded-xl border border-white/[0.06] bg-[#16171f] p-4">
              <h4 className="text-sm font-medium text-white">Recent Emergencies</h4>
              {modalIncidentsLoading ? (
                <p className="mt-2 text-sm text-slate-500">Loading...</p>
              ) : modalIncidents.length === 0 ? (
                <p className="mt-2 text-sm text-slate-500">No incident history found.</p>
              ) : (
                <div className="mt-2 space-y-2">
                  {modalIncidents.slice(0, 5).map((item) => (
                    <div key={item.id} className="rounded-lg border border-white/[0.06] bg-[#111219] px-3 py-2 text-xs text-slate-300">
                      {formatDate(item.triggered_at)} | {(item.trigger_type || 'unknown').replace(/_/g, ' ')} | {item.status}
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button
                onClick={() => router.push(`/admin/users/${selectedUser.id}`)}
                className="rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300"
              >
                View Full Profile
              </button>
              {selectedUser.is_active ? (
                <button
                  onClick={() => {
                    setShowUserModal(false)
                    setShowDeactivateModal(true)
                  }}
                  className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1.5 text-xs text-rose-300"
                >
                  Deactivate
                </button>
              ) : null}
              <button
                onClick={() => setShowUserModal(false)}
                className="rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {showDeactivateModal && selectedUser ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-xl rounded-2xl border border-white/[0.08] bg-[#111219] p-5">
            <h2 className="text-base font-semibold text-white">Deactivate User</h2>
            <p className="mt-1 text-sm text-slate-400">You are about to deactivate {selectedUser.display_name}.</p>
            <p className="mt-1 text-xs text-slate-500">This will prevent them from using SafeSphere. They can be reactivated later.</p>

            <div className="mt-3">
              <textarea
                className="w-full rounded-xl border border-white/[0.08] bg-[#16171f] p-3 text-sm text-slate-200 outline-none focus:border-emerald-500/40"
                placeholder="Reason (required)"
                value={deactivateReason}
                onChange={(e) => setDeactivateReason(e.target.value)}
                rows={3}
              />
            </div>

            <div className="mt-4 flex justify-end gap-2">
              <button
                className="rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300"
                onClick={() => setShowDeactivateModal(false)}
                disabled={actionLoading}
              >
                Cancel
              </button>
              <button
                className="rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-1.5 text-xs text-rose-300 disabled:opacity-60"
                onClick={() => deactivateUser(selectedUser.id, deactivateReason)}
                disabled={actionLoading || !deactivateReason.trim()}
              >
                {actionLoading ? 'Processing...' : 'Confirm Deactivate'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
