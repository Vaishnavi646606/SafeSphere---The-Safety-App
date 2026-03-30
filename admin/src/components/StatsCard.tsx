import { LucideIcon } from 'lucide-react'

interface StatsCardProps {
  label: string
  value: string | number
  icon: LucideIcon
  color: string
  trend?: number | null
  trendUp?: boolean
  trendDirection?: 'up' | 'down' | 'neutral' | 'new'
  subtitle?: string
  loading?: boolean
}

const hexToAlpha = (hex: string, alphaHex: string) => {
  if (!hex.startsWith('#')) return hex
  if (hex.length === 7) return `${hex}${alphaHex}`
  return hex
}

export default function StatsCard({
  label,
  value,
  icon: Icon,
  color,
  trend,
  trendUp = true,
  trendDirection = 'neutral',
  subtitle,
  loading = false
}: StatsCardProps) {
  const renderTrend = () => {
    if (trendDirection === 'new') {
      return (
        <span className="rounded-lg bg-slate-500/10 px-1.5 py-0.5 text-xs font-medium text-slate-300">
          New
        </span>
      )
    }

    if (trend === null || trend === undefined || trend === 0) {
      return null
    }

    const isUp = trend > 0
    const absValue = Math.abs(trend).toFixed(1)

    return (
      <span
        className={`rounded-lg px-1.5 py-0.5 text-xs font-medium ${
          isUp ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
        }`}
      >
        {isUp ? '↑' : '↓'} {absValue}%
      </span>
    )
  }

  return (
    <div className="rounded-2xl border border-white/[0.06] bg-[#111219] p-5 transition-all duration-200 hover:border-white/[0.12]">
      <div className="flex items-start justify-between">
        <div
          className="flex h-9 w-9 items-center justify-center rounded-xl"
          style={{ backgroundColor: hexToAlpha(color, '26') }}
        >
          <Icon size={17} color={color} />
        </div>

        {renderTrend()}
      </div>

      <div className="mt-3 text-2xl font-bold tracking-tight text-white">
        {loading ? <div className="h-8 w-20 animate-pulse rounded-lg bg-white/10" /> : value}
      </div>

      <p className="mt-0.5 text-xs uppercase tracking-wide text-slate-400">{label}</p>

      {subtitle ? <p className="mt-1 text-xs text-slate-600">{subtitle}</p> : null}
    </div>
  )
}
