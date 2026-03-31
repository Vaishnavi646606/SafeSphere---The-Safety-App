'use client'

import { useState, useEffect } from 'react'
import { Send, RefreshCw, MessageSquare } from 'lucide-react'

interface Message {
  id: string
  subject: string
  body: string
  is_critical: boolean
  created_at: string
  recipient_count: number
  delivered_count: number
}

export default function MessagesPage() {
  const [messages, setMessages] = useState<Message[]>([])
  const [loading, setLoading] = useState(true)
  const [showCompose, setShowCompose] = useState(false)
  const [form, setForm] = useState({ target: 'all', userId: '', subject: '', body: '', isCritical: false, messageType: 'policy' })
  const [sending, setSending] = useState(false)
  const [sendResult, setSendResult] = useState('')

  const fetchMessages = async () => {
    setLoading(true)
    const res = await fetch('/api/admin/messages')
    const d = await res.json()
    setMessages(d.messages || [])
    setLoading(false)
  }

  useEffect(() => {
    fetchMessages()
  }, [])

  const handleSend = async () => {
    setSending(true)
    setSendResult('')
    try {
      const res = await fetch('/api/admin/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          target: form.target === 'user' ? `user_id:${form.userId}` : 'all',
          subject: form.subject,
          body: form.body,
          is_critical: form.isCritical,
          message_type: form.messageType
        })
      })
      const d = await res.json()
      if (res.ok) {
        setSendResult(`Sent to ${d.queued_count} user(s)`)
        setShowCompose(false)
        setForm({ target: 'all', userId: '', subject: '', body: '', isCritical: false, messageType: 'policy' })
        fetchMessages()
      } else {
        throw new Error(d.error)
      }
    } catch (e: unknown) {
      setSendResult(e instanceof Error ? e.message : 'Error')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="space-y-8">
      <div className="rounded-2xl border border-white/5 bg-[#12121a] p-6 shadow-lg shadow-black/20">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-white">Messages</h1>
            <p className="mt-2 text-sm text-gray-400">Broadcast and targeted communication center.</p>
          </div>
          <div className="flex gap-3">
            <button
              className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium text-gray-300 hover:bg-white/10"
              onClick={fetchMessages}
            >
              <RefreshCw size={14} /> Refresh
            </button>
            <button
              className="inline-flex items-center gap-2 rounded-xl border border-cyan-500/30 bg-cyan-500/15 px-4 py-2 text-sm font-semibold text-cyan-300 hover:bg-cyan-500/20"
              onClick={() => setShowCompose(true)}
            >
              <Send size={14} /> New Message
            </button>
          </div>
        </div>
      </div>

      {sendResult && (
        <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-300">{sendResult}</div>
      )}

      <div className="overflow-hidden rounded-2xl border border-white/5 bg-[#12121a] shadow-lg shadow-black/20">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="border-b border-white/5 bg-[#0f0f16] text-left text-xs uppercase tracking-wider text-gray-400">
              <tr>
                <th className="px-5 py-4">Date</th>
                <th className="px-5 py-4">Subject</th>
                <th className="px-5 py-4">Body</th>
                <th className="px-5 py-4">Critical</th>
                <th className="px-5 py-4">Delivered</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} className="px-5 py-10 text-center text-gray-500">Loading...</td></tr>
              ) : messages.length === 0 ? (
                <tr><td colSpan={5} className="px-5 py-10 text-center text-gray-500">No messages sent yet.</td></tr>
              ) : (
                messages.map((msg) => (
                  <tr key={msg.id} className="border-b border-white/5 last:border-b-0 hover:bg-white/2">
                    <td className="px-5 py-4 text-gray-300">{new Date(msg.created_at).toLocaleDateString('en-IN')}</td>
                    <td className="max-w-55 truncate px-5 py-4 font-medium text-gray-100">{msg.subject}</td>
                    <td className="max-w-65 truncate px-5 py-4 text-gray-400">{msg.body}</td>
                    <td className="px-5 py-4 text-center text-gray-300">{msg.is_critical ? 'Yes' : 'No'}</td>
                    <td className="px-5 py-4 text-center text-gray-300">{msg.delivered_count}/{msg.recipient_count}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {showCompose && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl rounded-2xl border border-white/10 bg-[#12121a] p-6 shadow-2xl shadow-black/40">
            <h2 className="mb-5 flex items-center gap-2 text-xl font-semibold text-white"><MessageSquare size={20} /> Compose Message</h2>

            <div className="mb-4 grid gap-4 md:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm text-gray-400">Target</label>
                <select className="w-full rounded-xl border border-white/10 bg-[#0f0f16] px-3 py-2.5 text-sm text-gray-200 outline-none focus:border-cyan-500/40" value={form.target} onChange={(e) => setForm((f) => ({ ...f, target: e.target.value }))}>
                  <option value="all">All Users</option>
                  <option value="user">Specific User</option>
                </select>
              </div>
              <div>
                <label className="mb-2 block text-sm text-gray-400">Type</label>
                <select className="w-full rounded-xl border border-white/10 bg-[#0f0f16] px-3 py-2.5 text-sm text-gray-200 outline-none focus:border-cyan-500/40" value={form.messageType} onChange={(e) => setForm((f) => ({ ...f, messageType: e.target.value }))}>
                  <option value="policy">Policy</option>
                  <option value="warning">Warning</option>
                  <option value="removal">Removal</option>
                </select>
              </div>
            </div>

            {form.target === 'user' && (
              <div className="mb-4">
                <label className="mb-2 block text-sm text-gray-400">User ID</label>
                <input className="w-full rounded-xl border border-white/10 bg-[#0f0f16] px-3 py-2.5 text-sm text-gray-200 outline-none focus:border-cyan-500/40" placeholder="Paste user UUID..." value={form.userId} onChange={(e) => setForm((f) => ({ ...f, userId: e.target.value }))} />
              </div>
            )}

            <div className="mb-4">
              <label className="mb-2 block text-sm text-gray-400">Subject</label>
              <input className="w-full rounded-xl border border-white/10 bg-[#0f0f16] px-3 py-2.5 text-sm text-gray-200 outline-none focus:border-cyan-500/40" placeholder="Message subject..." value={form.subject} onChange={(e) => setForm((f) => ({ ...f, subject: e.target.value }))} />
            </div>

            <div className="mb-4">
              <label className="mb-2 block text-sm text-gray-400">Message Body</label>
              <textarea className="w-full rounded-xl border border-white/10 bg-[#0f0f16] px-3 py-2.5 text-sm text-gray-200 outline-none focus:border-cyan-500/40" rows={4} placeholder="Message body..." value={form.body} onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))} />
            </div>

            <label className="mb-5 flex items-center gap-3 text-sm text-gray-300">
              <input type="checkbox" checked={form.isCritical} onChange={(e) => setForm((f) => ({ ...f, isCritical: e.target.checked }))} className="h-4 w-4 rounded border-white/20 bg-[#0f0f16]" />
              Critical message (fullscreen on device)
            </label>

            <div className="flex justify-end gap-3">
              <button className="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-gray-300 hover:bg-white/10" onClick={() => setShowCompose(false)} disabled={sending}>Cancel</button>
              <button className="inline-flex items-center gap-2 rounded-xl border border-cyan-500/30 bg-cyan-500/15 px-4 py-2 text-sm font-semibold text-cyan-300 hover:bg-cyan-500/20 disabled:opacity-60" onClick={handleSend} disabled={sending || !form.subject || !form.body}>
                <Send size={14} /> {sending ? 'Sending...' : 'Send Message'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}


