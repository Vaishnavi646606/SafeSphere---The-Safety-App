import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

type TrendDirection = 'up' | 'down' | 'neutral' | 'new'

function computeTrend(current: number, previous: number): { value: number | null; direction: TrendDirection } {
  if (previous === 0 && current > 0) return { value: null, direction: 'new' }
  if (previous === 0 && current === 0) return { value: null, direction: 'neutral' }
  const raw = ((current - previous) / previous) * 100
  const rounded = Math.round(raw * 10) / 10
  if (rounded > 0) return { value: rounded, direction: 'up' }
  if (rounded < 0) return { value: rounded, direction: 'down' }
  return { value: 0, direction: 'neutral' }
}

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url)
  const range = searchParams.get('range') || '30d'
  const days = parseInt(range.replace('d', '')) || 30
  const nowMs = Date.now()
  const periodMs = days * 86400000
  const since = new Date(nowMs - periodMs).toISOString()
  const prevSince = new Date(nowMs - periodMs * 2).toISOString()
  const prevUntil = since
  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)

  const serviceClient = createServiceClient()

  const [
    { count: totalUsers },
    { count: activeUsers },
    { count: removedUsers },
    { count: totalIncidents },
    { count: contactsCalled },
    { count: callsAnswered },
    { count: activeToday },
    { count: usersCurrent },
    { count: usersPrevious },
    { count: incidentsCurrent },
    { count: incidentsPrevious },
    { count: feedbackCount },
    { count: realEmergencies },
    { count: usersRescued },
    { data: ratingsRaw },
    { data: dailyEventsRaw },
    { data: triggerEventsRaw }
  ] = await Promise.all([
    serviceClient.from('users').select('*', { count: 'exact', head: true }),
    serviceClient.from('users').select('*', { count: 'exact', head: true }).eq('is_active', true),
    serviceClient.from('users').select('*', { count: 'exact', head: true }).eq('is_active', false),
    serviceClient.from('emergency_events').select('*', { count: 'exact', head: true }).gte('triggered_at', since),
    serviceClient.from('emergency_events').select('*', { count: 'exact', head: true }).not('primary_contact_called', 'is', null).gte('triggered_at', since),
    serviceClient.from('emergency_events').select('*', { count: 'exact', head: true }).or('primary_contact_answered.eq.true,secondary_contact_answered.eq.true,tertiary_contact_answered.eq.true').gte('triggered_at', since),
    serviceClient.from('emergency_events').select('*', { count: 'exact', head: true }).gte('triggered_at', todayStart.toISOString()),
    serviceClient.from('users').select('*', { count: 'exact', head: true }).gte('created_at', since),
    serviceClient.from('users').select('*', { count: 'exact', head: true }).gte('created_at', prevSince).lt('created_at', prevUntil),
    serviceClient.from('emergency_events').select('*', { count: 'exact', head: true }).gte('triggered_at', since),
    serviceClient.from('emergency_events').select('*', { count: 'exact', head: true }).gte('triggered_at', prevSince).lt('triggered_at', prevUntil),
    serviceClient.from('emergency_feedback').select('*', { count: 'exact', head: true }).gte('submitted_at', since),
    serviceClient.from('emergency_feedback').select('*', { count: 'exact', head: true }).eq('was_real_emergency', true).gte('submitted_at', since),
    serviceClient.from('emergency_feedback').select('*', { count: 'exact', head: true }).eq('was_rescued_or_helped', true).gte('submitted_at', since),
    serviceClient.from('emergency_feedback').select('rating').gte('submitted_at', since),
    serviceClient.from('emergency_events').select('triggered_at').gte('triggered_at', since),
    serviceClient.from('emergency_events').select('trigger_type').gte('triggered_at', since)
  ])

  let avgRating = 0
  if (ratingsRaw && ratingsRaw.length > 0) {
    const validRatings = ratingsRaw.filter((r: any) => r.rating != null && r.rating > 0)
    if (validRatings.length > 0) {
      const sum = validRatings.reduce((acc: number, r: any) => acc + r.rating, 0)
      avgRating = Math.round((sum / validRatings.length) * 10) / 10
    }
  }

  const usersTrend = computeTrend(usersCurrent || 0, usersPrevious || 0)
  const incidentsTrend = computeTrend(incidentsCurrent || 0, incidentsPrevious || 0)

  const dailyGrouped: Record<string, number> = {}
  ;(dailyEventsRaw || []).forEach((e: any) => {
    const day = e.triggered_at.split('T')[0]
    dailyGrouped[day] = (dailyGrouped[day] || 0) + 1
  })
  const daily_series = Object.entries(dailyGrouped)
    .map(([day, incidents]) => ({ day, incidents }))
    .sort((a, b) => a.day.localeCompare(b.day))

  const triggerGrouped: Record<string, number> = {}
  ;(triggerEventsRaw || []).forEach((e: any) => {
    const type = e.trigger_type || 'unknown'
    triggerGrouped[type] = (triggerGrouped[type] || 0) + 1
  })
  const trigger_sources = Object.entries(triggerGrouped)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)

  const totalCount = totalIncidents || 0
  const calledCount = contactsCalled || 0
  const answeredCount = callsAnswered || 0
  const feedbackGiven = feedbackCount || 0

  const funnel = [
    { name: 'Triggered', value: totalCount, pct: 100 },
    { name: 'Contact Called', value: calledCount, pct: totalCount > 0 ? Math.round((calledCount / totalCount) * 100) : 0 },
    { name: 'Call Answered', value: answeredCount, pct: totalCount > 0 ? Math.round((answeredCount / totalCount) * 100) : 0 },
    { name: 'Feedback Given', value: feedbackGiven, pct: totalCount > 0 ? Math.round((feedbackGiven / totalCount) * 100) : 0 }
  ]

  return NextResponse.json({
    summary: {
      total_users: totalUsers || 0,
      total_users_trend: usersTrend.value,
      total_users_trend_direction: usersTrend.direction,
      active_users: activeUsers || 0,
      removed_users: removedUsers || 0,
      total_incidents: totalIncidents || 0,
      total_incidents_trend: incidentsTrend.value,
      total_incidents_trend_direction: incidentsTrend.direction,
      feedback_received: feedbackCount || 0,
      real_emergencies: realEmergencies || 0,
      users_rescued: usersRescued || 0,
      incidents_call_connected: callsAnswered || 0,
      avg_rating: avgRating,
      events_last_24h: activeToday || 0,
      active_today_trend: null,
      active_today_trend_direction: 'neutral'
    },
    daily_series,
    funnel,
    trigger_sources
  })
}
