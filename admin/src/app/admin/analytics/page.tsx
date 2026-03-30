'use client'

import { useEffect, useState } from 'react'
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  AreaChart,
  Area
} from 'recharts'
import {
  TrendingUp,
  Users,
  AlertTriangle,
  PhoneOff,
  CheckCircle,
  RefreshCw,
  Download,
  Radio,
  Activity
} from 'lucide-react'
import StatsCard from '@/components/StatsCard'

interface AnalyticsData {
  total_emergencies: number
  total_calls_made: number
  avg_response_time: number
  successful_outcomes: number
  response_rate: number
}

interface WaveformItem {
  period: string
  emergencies: number
  resolved: number
  pending: number
}

interface TriggerData {
  trigger: string
  count: number
}

interface OutcomeData {
  outcome: string
  count: number
}

export default function AnalyticsPage() {
  const [analytics, setAnalytics] = useState<AnalyticsData | null>(null)
  const [waveformData, setWaveformData] = useState<WaveformItem[]>([])
  const [triggerData, setTriggerData] = useState<TriggerData[]>([])
  const [outcomeData, setOutcomeData] = useState<OutcomeData[]>([])
  const [loading, setLoading] = useState(true)
  const [range, setRange] = useState('30')

  useEffect(() => {
    fetchAnalytics()
  }, [range])

  const fetchAnalytics = async () => {
    setLoading(true)
    try {
      const res = await fetch(`/api/admin/analytics?days=${range}`)
      const data = await res.json()
      setAnalytics(data.summary || data)
      setWaveformData(data.time_series || data.charts?.time_series || [])
      setTriggerData(data.trigger_distribution || data.charts?.trigger_distribution || [])
      setOutcomeData(data.outcome_distribution || data.charts?.outcome_distribution || [])
    } catch (e) {
      console.error('Failed to fetch analytics:', e)
    } finally {
      setLoading(false)
    }
  }

  const colors = ['#10b981', '#3b82f6', '#f59e0b', '#f43f5e', '#8b5cf6', '#06b6d4']

  const handleExport = () => {
    const data = {
      range: `${range}d`,
      generated_at: new Date().toISOString(),
      summary: analytics,
      time_series: waveformData,
      trigger_distribution: triggerData,
      outcome_distribution: outcomeData
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `analytics-${range}d-${new Date().toISOString().split('T')[0]}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Analytics</h1>
          <p className="mt-1 text-sm text-slate-500">Operational metrics for incident response and reliability.</p>
        </div>
        <div className="flex items-center gap-2">
          {['7', '30', '90'].map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`rounded-lg border px-3 py-1.5 text-xs ${
                range === r
                  ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
                  : 'border-white/[0.08] bg-white/5 text-slate-400 hover:text-white'
              }`}
            >
              {r}d
            </button>
          ))}
          <button onClick={fetchAnalytics} className="rounded-lg border border-white/[0.08] bg-white/5 p-1.5 text-slate-400 hover:text-white">
            <RefreshCw size={14} />
          </button>
          <button
            onClick={handleExport}
            className="inline-flex items-center gap-1 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-400"
          >
            <Download size={13} /> Export
          </button>
        </div>
      </section>

      <section className="grid gap-3 xl:grid-cols-5">
        <StatsCard label="Emergencies" value={analytics?.total_emergencies ?? 0} icon={AlertTriangle} color="#f59e0b" loading={loading} />
        <StatsCard label="Calls Made" value={analytics?.total_calls_made ?? 0} icon={PhoneOff} color="#3b82f6" loading={loading} />
        <StatsCard label="Successful" value={analytics?.successful_outcomes ?? 0} icon={CheckCircle} color="#10b981" loading={loading} />
        <StatsCard label="Response Rate" value={analytics?.response_rate ? `${analytics.response_rate}%` : '—'} icon={TrendingUp} color="#8b5cf6" loading={loading} />
        <StatsCard label="Avg Response" value={analytics?.avg_response_time ? `${analytics.avg_response_time}m` : '—'} icon={Users} color="#06b6d4" loading={loading} />
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-2xl border border-white/[0.06] bg-[#111219] p-5">
          <h3 className="mb-3 text-sm font-semibold text-white">Emergencies Over Time</h3>
          {loading || waveformData.length === 0 ? (
            <div className="flex min-h-[200px] flex-col items-center justify-center text-center">
              <Radio size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading...' : 'No data available'}</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={waveformData}>
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="period" stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} tickFormatter={(v: string) => v.slice(-5)} />
                <YAxis stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <Tooltip contentStyle={{ background: '#16171f', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px' }} />
                <Line type="monotone" dataKey="emergencies" stroke="#10b981" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="resolved" stroke="#3b82f6" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="pending" stroke="#f43f5e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="rounded-2xl border border-white/[0.06] bg-[#111219] p-5">
          <h3 className="mb-3 text-sm font-semibold text-white">Outcome Distribution</h3>
          {loading || outcomeData.length === 0 ? (
            <div className="flex min-h-[200px] flex-col items-center justify-center text-center">
              <Radio size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading...' : 'No data available'}</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={outcomeData} dataKey="count" nameKey="outcome" cx="50%" cy="50%" outerRadius={72}>
                  {outcomeData.map((_, idx) => (
                    <Cell key={`pie-${idx}`} fill={colors[idx % colors.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ background: '#16171f', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px' }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="rounded-2xl border border-white/[0.06] bg-[#111219] p-5">
          <h3 className="mb-3 text-sm font-semibold text-white">Trigger Breakdown</h3>
          {loading || triggerData.length === 0 ? (
            <div className="flex min-h-[200px] flex-col items-center justify-center text-center">
              <Radio size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading...' : 'No data available'}</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={triggerData}>
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="trigger" stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <YAxis stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <Tooltip contentStyle={{ background: '#16171f', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px' }} />
                <Bar dataKey="count" fill="#06b6d4" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="rounded-2xl border border-white/[0.06] bg-[#111219] p-5">
          <h3 className="mb-3 text-sm font-semibold text-white">Performance Curve</h3>
          {loading || waveformData.length === 0 ? (
            <div className="flex min-h-[200px] flex-col items-center justify-center text-center">
              <Activity size={28} className="text-slate-700" />
              <p className="mt-2 text-sm text-slate-600">{loading ? 'Loading...' : 'No data available'}</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={waveformData}>
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="period" stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} tickFormatter={(v: string) => v.slice(-5)} />
                <YAxis stroke="#475569" tick={{ fontSize: 11, fill: '#475569' }} />
                <Tooltip contentStyle={{ background: '#16171f', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px' }} />
                <Area type="monotone" dataKey="resolved" stroke="#10b981" fill="#10b98133" />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </div>
  )
}
