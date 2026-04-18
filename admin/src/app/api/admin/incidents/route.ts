import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

interface IncidentRow {
  id: string
  user_id: string
  triggered_at: string
  resolved_at: string | null
  resolution_type: string | null
  admin_notes: string | null
  [key: string]: any
}

interface HelperRescueRow {
  id: string
  user_id: string
  contact_slot: number | null
  contact_number: string | null
  token: string
  auto_rescue_at: string | null
}

function isAutoRescuedIncident(incident: IncidentRow): boolean {
  return incident.resolution_type === 'safe_contact' && Boolean(incident.admin_notes?.includes('[AUTO_RESCUE]'))
}

function toMs(iso: string | null | undefined): number | null {
  if (!iso) return null
  const value = new Date(iso).getTime()
  return Number.isFinite(value) ? value : null
}

function pickHelperForIncident(incident: IncidentRow, candidates: HelperRescueRow[]): HelperRescueRow | null {
  if (!candidates || candidates.length === 0) return null

  const triggeredMs = toMs(incident.triggered_at)
  const resolvedMs = toMs(incident.resolved_at)
  const anchorMs = resolvedMs ?? triggeredMs
  if (anchorMs === null) return candidates[0] || null

  const lowerBound = triggeredMs !== null ? triggeredMs - 10 * 60 * 1000 : anchorMs - 60 * 60 * 1000
  const upperBound = resolvedMs !== null ? resolvedMs + 6 * 60 * 60 * 1000 : anchorMs + 6 * 60 * 60 * 1000

  const windowed = candidates.filter((item) => {
    const helperMs = toMs(item.auto_rescue_at)
    if (helperMs === null) return false
    return helperMs >= lowerBound && helperMs <= upperBound
  })

  const source = windowed.length > 0 ? windowed : candidates

  return source.reduce<HelperRescueRow | null>((best, current) => {
    const currentMs = toMs(current.auto_rescue_at)
    if (currentMs === null) return best

    if (!best) return current

    const bestMs = toMs(best.auto_rescue_at)
    if (bestMs === null) return current

    return Math.abs(currentMs - anchorMs) < Math.abs(bestMs - anchorMs) ? current : best
  }, null)
}

/**
 * GET /api/admin/incidents?limit=50&offset=0&status=triggered&trigger=keyword_detected
 * 
 * Retrieve emergency incidents with filtering and pagination.
 * Uses the emergency_events table from the real database schema.
 */
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url)
    
    const limit = Math.min(parseInt(searchParams.get('limit') || '50'), 500)
    const offset = parseInt(searchParams.get('offset') || '0')
    const status = searchParams.get('status')
    const trigger = searchParams.get('trigger')
    const userId = searchParams.get('user_id')
    const dateFrom = searchParams.get('date_from')
    const dateTo = searchParams.get('date_to')
    const requiresReview = searchParams.get('requires_review')
    const sortBy = searchParams.get('sort_by') || 'triggered_at'
    const sortOrder = searchParams.get('sort_order') === 'asc' ? 'asc' : 'desc'
    
    const db = createServiceClient()
    
    // Build dynamic query
    let query = db
      .from('emergency_events')
      .select(`
        id,
        user_id,
        session_id,
        trigger_type,
        triggered_at,
        resolved_at,
        status,
        location_lat,
        location_lng,
        has_location_enabled,
        phone_battery_percent,
        primary_contact_called,
        primary_contact_answered,
        primary_contact_duration_s,
        secondary_contact_called,
        secondary_contact_answered,
        secondary_contact_duration_s,
        tertiary_contact_called,
        tertiary_contact_answered,
        tertiary_contact_duration_s,
        sms_sent_to,
        time_to_first_contact_s,
        time_to_answer_s,
        time_to_resolve_s,
        resolution_type,
        requires_admin_review,
        admin_notes,
        is_test_event,
        created_at,
        updated_at,
        users!inner(id, display_name, phone_hash, device_model)
      `, { count: 'exact' })
    
    // Apply filters
    if (status) query = query.eq('status', status)
    if (trigger) query = query.eq('trigger_type', trigger)
    if (userId) query = query.eq('user_id', userId)
    if (requiresReview === 'true') query = query.eq('requires_admin_review', true)
    
    // Date range filters
    if (dateFrom) query = query.gte('triggered_at', dateFrom + 'T00:00:00Z')
    if (dateTo) query = query.lte('triggered_at', dateTo + 'T23:59:59Z')
    
    // Sorting and pagination
    const { data: incidents, error, count } = await query
      .order(sortBy, { ascending: sortOrder === 'asc' })
      .range(offset, offset + limit - 1)
    
    if (error) {
      console.error('Incidents query error:', error)
      return NextResponse.json({ error: 'Failed to fetch incidents' }, { status: 500 })
    }
    
    const rows = (incidents || []) as IncidentRow[]
    let enrichedRows: IncidentRow[] = rows

    if (rows.length > 0) {
      const autoRescuedRows = rows.filter(isAutoRescuedIncident)
      const userIds = [...new Set(autoRescuedRows.map((item) => item.user_id).filter(Boolean))]

      if (userIds.length > 0) {
        const { data: helperRows, error: helperError } = await db
          .from('emergency_helper_links')
          .select('id, user_id, contact_slot, contact_number, token, auto_rescue_at')
          .in('user_id', userIds)
          .eq('auto_rescue_triggered', true)
          .not('auto_rescue_at', 'is', null)
          .order('auto_rescue_at', { ascending: false })

        if (!helperError && helperRows) {
          const helperByUser = new Map<string, HelperRescueRow[]>()
          ;(helperRows as HelperRescueRow[]).forEach((item) => {
            const list = helperByUser.get(item.user_id) || []
            list.push(item)
            helperByUser.set(item.user_id, list)
          })

          const origin = new URL(req.url).origin
          enrichedRows = rows.map((incident) => {
            if (!isAutoRescuedIncident(incident)) {
              return {
                ...incident,
                auto_rescue_helper_slot: null,
                auto_rescue_helper_number: null,
                auto_rescue_helper_token: null,
                auto_rescue_helper_url: null,
                auto_rescue_helper_at: null,
              }
            }

            const matched = pickHelperForIncident(incident, helperByUser.get(incident.user_id) || [])
            return {
              ...incident,
              auto_rescue_helper_slot: matched?.contact_slot ?? null,
              auto_rescue_helper_number: matched?.contact_number ?? null,
              auto_rescue_helper_token: matched?.token ?? null,
              auto_rescue_helper_url: matched?.token ? `${origin}/track/${matched.token}` : null,
              auto_rescue_helper_at: matched?.auto_rescue_at ?? null,
            }
          })
        }
      }
    }

    return NextResponse.json({
      data: enrichedRows,
      count: count || 0,
      limit,
      offset,
      total_pages: Math.ceil((count || 0) / limit)
    })
  } catch (err) {
    console.error('GET /api/admin/incidents error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

/**
 * PUT /api/admin/incidents
 * 
 * Admin updates incident notes or status.
 */
export async function PUT(req: NextRequest) {
  try {
    const body = await req.json()
    const { incident_id, admin_notes, status } = body
    
    if (!incident_id) {
      return NextResponse.json({ error: 'incident_id required' }, { status: 400 })
    }
    
    const db = createServiceClient()
    
    const updateData: Record<string, any> = {}
    
    if (admin_notes !== undefined) updateData.admin_notes = admin_notes
    if (status !== undefined) updateData.status = status
    
    updateData.updated_at = new Date().toISOString()
    
    const { error } = await db
      .from('emergency_events')
      .update(updateData)
      .eq('id', incident_id)
    
    if (error) {
      console.error('Incident update error:', error)
      return NextResponse.json({ error: 'Failed to update incident' }, { status: 500 })
    }
    
    return NextResponse.json({
      success: true,
      message: 'Incident updated'
    })
  } catch (err) {
    console.error('PUT /api/admin/incidents error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

