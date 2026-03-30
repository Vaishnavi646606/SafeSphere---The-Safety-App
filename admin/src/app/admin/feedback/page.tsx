'use client'

import { useEffect, useState } from 'react'
import { MessageCircle, RefreshCw } from 'lucide-react'

interface Feedback {
  id: string
  event_id: string
  user_id: string
  was_real_emergency: boolean
  was_rescued_or_helped: boolean
  rating: number
  feedback_text: string
  feedback_category: string
  helpful_features: string[]
  admin_reviewed: boolean
  admin_response: string | null
  admin_reviewed_at: string | null
  submitted_at: string
  created_at: string
}

export default function FeedbackPage() {
  const [feedback, setFeedback] = useState<Feedback[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<'pending' | 'reviewed' | 'all'>('pending')
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [markingId, setMarkingId] = useState<string | null>(null)
  const [adminResponse, setAdminResponse] = useState<Record<string, string>>({})

  const fetchFeedback = async () => {
    setLoading(true)
    try {
      const res = await fetch(`/api/admin/feedback?filter=${filter}`)
      const data = await res.json()
      setFeedback(data.feedback || [])
    } catch (e) {
      console.error('Failed to fetch feedback:', e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchFeedback()
  }, [filter])

  const toggleExpand = (id: string) => {
    setExpandedId((prev) => (prev === id ? null : id))
  }

  const markReviewed = async (feedbackId: string) => {
    setMarkingId(feedbackId)
    try {
      const res = await fetch('/api/admin/feedback', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: feedbackId,
          feedbackId,
          admin_response: adminResponse[feedbackId] || ''
        })
      })

      if (res.ok) {
        setFeedback((prev) =>
          prev.map((f) =>
            f.id === feedbackId
              ? {
                  ...f,
                  admin_reviewed: true,
                  admin_reviewed_at: new Date().toISOString(),
                  admin_response: adminResponse[feedbackId] || f.admin_response
                }
              : f
          )
        )
        setExpandedId(null)
        if (filter !== 'all') fetchFeedback()
      }
    } catch (e) {
      console.error('Failed to mark reviewed:', e)
    } finally {
      setMarkingId(null)
    }
  }

  const renderStars = (rating: number) => (
    <div className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map((i) => (
        <span key={i} className={i <= rating ? 'text-amber-400' : 'text-gray-600'}>
          ★
        </span>
      ))}
    </div>
  )

  const formatDate = (value: string) =>
    new Date(value).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit'
    })

  const emptySubtext =
    filter === 'pending'
      ? 'All feedback has been reviewed'
      : filter === 'reviewed'
        ? 'No reviewed feedback yet'
        : 'No feedback submitted yet'

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Feedback</h1>
          <p className="mt-1 text-sm text-slate-500">Post-emergency user feedback for quality and trust monitoring.</p>
        </div>
        <button
          onClick={fetchFeedback}
          className="inline-flex items-center gap-2 rounded-lg border border-white/[0.08] bg-white/5 px-3 py-1.5 text-xs text-slate-300 hover:text-white"
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </section>

      <section className="flex gap-2 border-b border-white/5">
        {(['pending', 'reviewed', 'all'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setFilter(tab)}
            className={`px-3 py-2 text-xs font-medium transition-all ${
              filter === tab
                ? 'bg-emerald-500/10 text-emerald-400 border-b-2 border-emerald-400'
                : 'text-slate-400 hover:bg-white/5'
            }`}
          >
            {tab === 'pending' ? 'Pending Review' : tab === 'reviewed' ? 'Reviewed' : 'All Feedback'}
          </button>
        ))}
      </section>

      <section className="overflow-hidden rounded-2xl border border-white/[0.06] bg-[#111219]">
        <div className="min-w-[1100px] border-b border-white/5 bg-[#0c0d13] px-5 py-3 text-xs uppercase tracking-widest text-slate-600">
          <div className="flex items-center gap-4">
            <div className="w-44">Date</div>
            <div className="w-32">User</div>
            <div className="w-28">Rating</div>
            <div className="w-32">Real Emergency</div>
            <div className="w-28">Rescued</div>
            <div className="w-28">Category</div>
            <div className="w-24">Status</div>
            <div className="flex-1 text-right">Action</div>
          </div>
        </div>

        {loading ? (
          <div>
            {Array.from({ length: 5 }).map((_, idx) => (
              <div key={idx} className="flex items-center gap-4 border-b border-white/5 px-5 py-3.5">
                <div className="h-4 w-44 animate-pulse rounded bg-white/10" />
                <div className="h-4 w-32 animate-pulse rounded bg-white/10" />
                <div className="h-4 w-28 animate-pulse rounded bg-white/10" />
                <div className="h-4 w-32 animate-pulse rounded bg-white/10" />
                <div className="h-4 w-28 animate-pulse rounded bg-white/10" />
                <div className="h-4 w-28 animate-pulse rounded bg-white/10" />
                <div className="h-4 w-24 animate-pulse rounded bg-white/10" />
                <div className="ml-auto h-7 w-20 animate-pulse rounded bg-white/10" />
              </div>
            ))}
          </div>
        ) : feedback.length === 0 ? (
          <div className="flex min-h-[280px] flex-col items-center justify-center p-8 text-center">
            <MessageCircle size={40} className="text-gray-700" />
            <p className="mt-3 text-sm text-gray-300">No feedback records</p>
            <p className="mt-1 text-xs text-gray-500">{emptySubtext}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            {feedback.map((item) => (
              <div key={item.id}>
                <div className="flex items-center gap-4 px-5 py-3.5 border-b border-white/5 hover:bg-white/[0.02] transition-colors">
                  <div className="w-44 text-sm text-gray-300">{formatDate(item.submitted_at || item.created_at)}</div>
                  <div className="w-32 font-mono text-xs text-gray-400">{item.user_id.slice(0, 8)}...</div>
                  <div className="w-28">{renderStars(item.rating)}</div>
                  <div className="w-32">
                    {item.was_real_emergency ? (
                      <span className="rounded-lg bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">Yes</span>
                    ) : (
                      <span className="rounded-lg bg-amber-500/10 px-2 py-1 text-xs text-amber-400">No</span>
                    )}
                  </div>
                  <div className="w-28">
                    {item.was_rescued_or_helped ? (
                      <span className="rounded-lg bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">Yes</span>
                    ) : (
                      <span className="rounded-lg bg-rose-500/10 px-2 py-1 text-xs text-rose-400">No</span>
                    )}
                  </div>
                  <div className="w-28 text-xs text-gray-400">{item.feedback_category || 'general'}</div>
                  <div className="w-24">
                    {item.admin_reviewed ? (
                      <span className="rounded-lg bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">Reviewed</span>
                    ) : (
                      <span className="rounded-lg bg-amber-500/10 px-2 py-1 text-xs text-amber-400">Pending</span>
                    )}
                  </div>
                  <div className="flex-1 text-right">
                    <button
                      onClick={() => toggleExpand(item.id)}
                      className="text-xs px-3 py-1.5 rounded-lg border border-white/10 bg-white/5 text-gray-300 hover:bg-white/10 transition-all cursor-pointer"
                    >
                      {expandedId === item.id ? 'Hide ↑' : 'View ↓'}
                    </button>
                  </div>
                </div>

                {expandedId === item.id ? (
                  <div className="bg-[#0c0d13] border-b border-white/5 px-5 py-5 space-y-4">
                    <div>
                      <h4 className="text-xs uppercase tracking-wider text-gray-500">Feedback Comment</h4>
                      {item.feedback_text && item.feedback_text.trim().length > 0 ? (
                        <p className="mt-2 text-sm text-gray-300 bg-[#16171f] rounded-xl p-4 border border-white/5">
                          {item.feedback_text}
                        </p>
                      ) : (
                        <p className="mt-2 text-sm text-gray-600 italic">No text feedback provided</p>
                      )}
                    </div>

                    <div className="grid gap-3 md:grid-cols-3">
                      <div className="rounded-xl border border-white/5 bg-[#16171f] p-4">
                        <p className="text-xs uppercase tracking-wider text-gray-500">Rating</p>
                        <div className="mt-2 flex items-center gap-2">
                          {renderStars(item.rating)}
                          <span className="text-xs text-gray-400">({item.rating}/5)</span>
                        </div>
                      </div>
                      <div className="rounded-xl border border-white/5 bg-[#16171f] p-4">
                        <p className="text-xs uppercase tracking-wider text-gray-500">Was Real Emergency</p>
                        <p className={`mt-2 text-sm ${item.was_real_emergency ? 'text-emerald-400' : 'text-amber-400'}`}>
                          {item.was_real_emergency ? 'Yes — Real emergency' : 'No — False alarm'}
                        </p>
                      </div>
                      <div className="rounded-xl border border-white/5 bg-[#16171f] p-4">
                        <p className="text-xs uppercase tracking-wider text-gray-500">Rescued / Helped</p>
                        <p className={`mt-2 text-sm ${item.was_rescued_or_helped ? 'text-emerald-400' : 'text-rose-400'}`}>
                          {item.was_rescued_or_helped ? 'Yes — Successfully helped' : 'No — Not helped'}
                        </p>
                      </div>
                    </div>

                    {Array.isArray(item.helpful_features) && item.helpful_features.length > 0 ? (
                      <div>
                        <h4 className="text-xs uppercase tracking-wider text-gray-500">Helpful Features</h4>
                        <div className="mt-2 flex flex-wrap gap-2">
                          {item.helpful_features.map((feature, idx) => (
                            <span
                              key={`${item.id}-${idx}`}
                              className="text-xs px-2 py-1 rounded-lg bg-white/5 text-gray-300 border border-white/10"
                            >
                              {feature}
                            </span>
                          ))}
                        </div>
                      </div>
                    ) : null}

                    {!item.admin_reviewed ? (
                      <div>
                        <h4 className="text-xs uppercase tracking-wider text-gray-500">Admin Response (optional)</h4>
                        <textarea
                          value={adminResponse[item.id] || ''}
                          onChange={(e) =>
                            setAdminResponse((prev) => ({
                              ...prev,
                              [item.id]: e.target.value
                            }))
                          }
                          placeholder="Add a response or notes about this feedback..."
                          className="mt-2 w-full bg-[#16171f] border border-white/10 rounded-xl p-3 text-sm text-white placeholder-gray-600 resize-none"
                          rows={2}
                        />
                        <button
                          onClick={() => markReviewed(item.id)}
                          disabled={markingId === item.id}
                          className="mt-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-400 disabled:opacity-60"
                        >
                          {markingId === item.id ? 'Marking...' : '✓ Mark as Reviewed'}
                        </button>
                      </div>
                    ) : (
                      <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-xl p-3 text-sm text-emerald-300">
                        <p>✓ Reviewed on {item.admin_reviewed_at ? formatDate(item.admin_reviewed_at) : 'Unknown date'}</p>
                        {item.admin_response ? <p className="mt-1 text-emerald-200">{item.admin_response}</p> : null}
                      </div>
                    )}
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
