import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

/**
 * GET /api/admin/analytics?days=30
 * 
 * Returns comprehensive analytics for the admin dashboard.
 * Includes emergency stats, trends, and system health metrics.
 */
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url)
    const days = parseInt(searchParams.get('days') || '30', 10)
    const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString()
    
    const db = createServiceClient()
    
    // 1. Get all emergency events for the period
    const { data: allEvents } = await db
      .from('emergency_events')
      .select(`
        id, triggered_at, resolved_at, resolution_type, trigger_type, status,
        primary_contact_called, primary_contact_answered,
        secondary_contact_called, secondary_contact_answered,
        tertiary_contact_called, tertiary_contact_answered,
        time_to_resolve_s
      `)
      .gte('triggered_at', since)
    
    // 2. Process statistics from raw data
    const triggerCounts: Record<string, number> = {}
    const statusCounts: Record<string, number> = {}
    const resolutionCounts: Record<string, number> = {}
    const byDate: Record<string, { emergencies: number; resolved: number; pending: number }> = {}
    let totalAnswered = 0
    let totalWithAnswer = 0

    ;(allEvents || []).forEach((e: any) => {
      const period = new Date(e.triggered_at).toISOString().split('T')[0]
      if (!byDate[period]) {
        byDate[period] = { emergencies: 0, resolved: 0, pending: 0 }
      }
      byDate[period].emergencies += 1
      if (e.status === 'resolved') {
        byDate[period].resolved += 1
      } else {
        byDate[period].pending += 1
      }

      triggerCounts[e.trigger_type] = (triggerCounts[e.trigger_type] || 0) + 1
      statusCounts[e.status || 'unknown'] = (statusCounts[e.status || 'unknown'] || 0) + 1
      resolutionCounts[e.resolution_type || 'unknown'] = (resolutionCounts[e.resolution_type || 'unknown'] || 0) + 1
      
      if (e.primary_contact_answered !== null) {
        totalWithAnswer++
        if (e.primary_contact_answered === true) totalAnswered++
      }
      
    })

    const total = allEvents?.length || 0

    let callsMade = 0
    ;(allEvents || []).forEach((e: any) => {
      if (e.primary_contact_called) callsMade++
      if (e.secondary_contact_called) callsMade++
      if (e.tertiary_contact_called) callsMade++
    })

    const successful = allEvents?.filter((e: any) =>
      e.primary_contact_answered === true ||
      e.secondary_contact_answered === true ||
      e.tertiary_contact_answered === true
    ).length || 0

    const responseRate = total > 0 ? Math.round((successful / total) * 100) : 0

    const validTimes: number[] = []
    ;(allEvents || []).forEach((e: any) => {
      if (e.triggered_at && e.resolved_at) {
        const diffMs = new Date(e.resolved_at).getTime() - new Date(e.triggered_at).getTime()
        if (diffMs > 0) validTimes.push(diffMs / 1000)
      }
    })

    const avgResponseTime = validTimes.length > 0
      ? Math.round((validTimes.reduce((a: number, b: number) => a + b, 0) / validTimes.length / 60) * 10) / 10
      : 0
    
    // 3. Get feedback statistics
    const { data: feedbackData } = await db
      .from('emergency_feedback')
      .select('rating, was_real_emergency, was_rescued_or_helped')
      .gte('submitted_at', since)
    
    let avgRating = 0
    let realEmergencyPercent = 0
    let rescuedPercent = 0
    const feedbackCount = feedbackData?.length || 0
    
    if (feedbackData && feedbackData.length > 0) {
      const sumRating = feedbackData.reduce((acc: number, f: any) => acc + (f.rating || 0), 0)
      avgRating = Math.round((sumRating / feedbackData.length) * 100) / 100
      
      const realCount = feedbackData.filter((f: any) => f.was_real_emergency === true).length
      const rescuedCount = feedbackData.filter((f: any) => f.was_rescued_or_helped === true).length
      realEmergencyPercent = Math.round((realCount / feedbackData.length) * 100)
      rescuedPercent = Math.round((rescuedCount / feedbackData.length) * 100)
    }
    
    // 4. User statistics
    const { data: users } = await db.from('users').select('is_active')
    const activeUsers = users?.filter((u: any) => u.is_active === true).length || 0
    const totalUsers = users?.length || 0
    
    // 5. Events needing admin review
    const { data: reviewNeeded } = await db
      .from('emergency_events')
      .select('id')
      .eq('requires_admin_review', true)
    
    return NextResponse.json({
      total_emergencies: total,
      total_calls_made: callsMade,
      avg_response_time: avgResponseTime,
      successful_outcomes: successful,
      response_rate: responseRate,
      time_series: Object.entries(byDate)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([period, value]) => ({ period, ...value })),
      trigger_distribution: Object.entries(triggerCounts).map(([trigger, count]) => ({ trigger, count })),
      outcome_distribution: Object.entries(resolutionCounts).map(([outcome, count]) => ({ outcome, count })),
      period_days: days,
      timestamp: new Date().toISOString(),
      summary: {
        total_emergencies: total,
        total_calls_made: callsMade,
        successful_outcomes: successful,
        response_rate: responseRate,
        avg_response_time: avgResponseTime
      },
      metrics: {
        first_contact_answer_rate: totalWithAnswer > 0 
          ? Math.round((totalAnswered / totalWithAnswer) * 100) 
          : 0,
        avg_response_time_seconds: validTimes.length > 0
          ? Math.round(validTimes.reduce((a: number, b: number) => a + b, 0) / validTimes.length)
          : 0,
        avg_feedback_rating: avgRating,
        real_emergency_percent: realEmergencyPercent,
        rescued_percent: rescuedPercent,
        feedback_submissions: feedbackCount,
        user_retention: totalUsers > 0 ? Math.round((activeUsers / totalUsers) * 100) : 0
      },
      charts: {
        trigger_distribution: Object.entries(triggerCounts).map(([name, value]) => ({ name, value })),
        status_distribution: Object.entries(statusCounts).map(([name, value]) => ({ name, value })),
        resolution_distribution: Object.entries(resolutionCounts).map(([name, value]) => ({ name, value })),
        trigger_sources: [
          { name: 'Keyword Detected', value: triggerCounts['keyword_detected'] || 0 },
          { name: 'Shake Detected', value: triggerCounts['shake_detected'] || 0 },
          { name: 'Manual Trigger', value: triggerCounts['manual_trigger'] || 0 },
          { name: 'Power Button', value: triggerCounts['power_button'] || 0 }
        ]
      }
    })
  } catch (err) {
    console.error('GET /api/admin/analytics error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

