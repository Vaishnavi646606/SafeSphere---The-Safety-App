'use client'

import { useEffect, useState } from 'react'
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts'
import {
  Users,
  AlertTriangle,
  MessageSquare,
  Star,
  Activity,
  Phone,
  Heart,
  Navigation,
  RefreshCw,
  Radio,
  BarChart3
} from 'lucide-react'
import StatsCard from '@/components/StatsCard'

interface Metrics {
  total_users: number
  total_users_trend: number | null
  total_users_trend_direction: 'up' | 'down' | 'neutral' | 'new'
  active_users: number
  removed_users: number
  total_incidents: number
  total_incidents_trend: number | null
  total_incidents_trend_direction: 'up' | 'down' | 'neutral' | 'new'
  feedback_received: number
  real_emergencies: number
  users_rescued: number
  auto_rescues: number
  verified_rescues: number
  incidents_call_connected: number
  avg_rating: number
  events_last_24h: number
  active_today_trend: number | null
  active_today_trend_direction: 'up' | 'down' | 'neutral' | 'new'
}

interface DailySeries {
  day: string
  incidents: number
  registrations: number
}

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [dailyData, setDailyData] = useState<DailySeries[]>([])
  const [funnelData, setFunnelData] = useState<{ name: string; value: number; pct: number }[]>([])
  const [triggerData, setTriggerData] = useState<{ name: string; value: number }[]>([])
  const [loading, setLoading] = useState(true)
  const [range, setRange] = useState('30')

  useEffect(() => {
    fetchMetrics()
  }, [range])

  const fetchMetrics = async () => {
    setLoading(true)
    try {
      const res = await fetch(`/api/admin/metrics?range=${range}d`)
      const data = await res.json()
      setMetrics(data.summary)
      setDailyData(data.daily_series || [])
      setFunnelData(data.funnel || [])
      setTriggerData(data.trigger_sources || [])
    } catch (e) {
      console.error('Failed to fetch metrics:', e)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <section className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <h2 className="text-xl font-semibold text-white">Safety Operations Overview</h2>
          <p className="mt-1 text-sm text-slate-500">Live command center metrics across users, incidents, and response flow.</p>
        </div>
        <div className="flex items-center gap-2">
          {['7', '30', '90'].map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`rounded-lg border px-3 py-1.5 text-xs transition-all ${
                range === r
                  ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
                  : 'border-white/8 bg-white/5 text-slate-400 hover:text-white'
              }`}
            >
              {r}d
            </button>
          ))}
          <button
            onClick={fetchMetrics}
            className="rounded-lg border border-white/8 bg-white/5 p-1.5 text-slate-400 transition-colors hover:text-white"
            title="Refresh"
          >
            <RefreshCw size={14} />
          </button>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-4">
        <StatsCard
          label="Total Users"
          value={metrics?.total_users ?? 0}
          icon={Users}
          color="#10b981"
          trend={metrics?.total_users_trend ?? null}
          trendUp={metrics?.total_users_trend_direction === 'up'}
          trendDirection={metrics?.total_users_trend_direction ?? 'neutral'}
          loading={loading}
        />
        <StatsCard
          label="Total Emergencies"
          value={metrics?.total_incidents ?? 0}
          icon={AlertTriangle}
          color="#f59e0b"
          trend={metrics?.total_incidents_trend ?? null}
          trendUp={metrics?.total_incidents_trend_direction === 'up'}
          trendDirection={metrics?.total_incidents_trend_direction ?? 'neutral'}
          loading={loading}
        />
        <StatsCard
          label="Feedback Received"
          value={metrics?.feedback_received ?? 0}
          icon={MessageSquare}
          color="#8b5cf6"
          trend={null}
          trendDirection="neutral"
          subtitle="User responses"
          loading={loading}
        />
        <StatsCard
          label="Active Today"
          value={metrics?.events_last_24h ?? 0}
          icon={Activity}
          color="#06b6d4"
          trend={null}
          trendDirection="neutral"
          subtitle="Last 24h events"
          loading={loading}
        />
      </section>

      <section className="grid gap-4 lg:grid-cols-6">
        <StatsCard label="Real Emergencies" value={metrics?.real_emergencies ?? 0} icon={AlertTriangle} color="#f43f5e" subtitle="Confirmed by user" loading={loading} />
        <StatsCard label="Users Rescued" value={metrics?.users_rescued ?? 0} icon={Heart} color="#10b981" subtitle="Help received" loading={loading} />
        <StatsCard label="Auto Rescues" value={metrics?.auto_rescues ?? 0} icon={Navigation} color="#14b8a6" subtitle="Within 50m" loading={loading} />
        <StatsCard label="Verified Rescues" value={metrics?.verified_rescues ?? 0} icon={Heart} color="#22c55e" subtitle="Manual confirmation" loading={loading} />
        <StatsCard label="Call Connected" value={metrics?.incidents_call_connected ?? 0} icon={Phone} color="#3b82f6" loading={loading} />
        <StatsCard label="Avg Rating" value={metrics?.avg_rating ?? 0} icon={Star} color="#f59e0b" subtitle="Out of 5" loading={loading} />
      </section>

      <section className="grid gap-4 xl:grid-cols-3">
        <div className="rounded-2xl border border-white/6 bg-[#111219] p-5 xl:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-white">Emergency Trends</h3>
            <BarChart3 size={16} className="text-slate-500" />
          </div>
          {loading || dailyData.length === 0 ? (
            <div className="flex min-h-50 flex-col items-center justify-center text-center">
              <Radio size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading trend data...' : 'No trend data available'}</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={dailyData}>
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="day" stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} tickFormatter={(v: string) => v.slice(5)} />
                <YAxis stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <Tooltip contentStyle={{ background: '#16171f', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px' }} />
                <Line type="monotone" dataKey="incidents" stroke="#10b981" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="registrations" stroke="#3b82f6" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="rounded-2xl border border-white/6 bg-[#111219] p-5">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-white">Trigger Sources</h3>
            <AlertTriangle size={16} className="text-slate-500" />
          </div>
          {loading || triggerData.length === 0 ? (
            <div className="flex min-h-50 flex-col items-center justify-center text-center">
              <Radio size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading trigger data...' : 'No trigger data available'}</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={triggerData}>
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="name" stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <YAxis stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <Tooltip contentStyle={{ background: '#16171f', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px' }} />
                <Bar dataKey="value" fill="#06b6d4" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>

      <section className="rounded-2xl border border-white/6 bg-[#111219] p-5">
        <h3 className="mb-4 text-sm font-semibold text-white">Response Funnel</h3>
        {loading || funnelData.length === 0 ? (
          <div className="flex min-h-40 flex-col items-center justify-center text-center">
            <Radio size={28} className="text-slate-700" />
            <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading funnel data...' : 'No incident data available'}</p>
          </div>
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            {funnelData.map((step, idx) => (
              <div key={step.name} className="rounded-xl bg-[#0c0d13] p-4 text-center">
                <p className="text-xl font-bold text-white">{step.value.toLocaleString()}</p>
                <p className="mt-1 text-xs uppercase tracking-wide text-slate-500">{step.name}</p>
                {idx > 0 ? (
                  <p className={`mt-2 text-xs font-medium ${step.pct > 50 ? 'text-emerald-400' : 'text-amber-400'}`}>
                    {step.pct}% conversion
                  </p>
                ) : (
                  <p className="mt-2 text-xs font-medium text-slate-600">Baseline</p>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
