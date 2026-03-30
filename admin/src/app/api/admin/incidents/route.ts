import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

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
        trigger_type,
        triggered_at,
        status,
        location_lat,
        location_lng,
        has_location_enabled,
        primary_contact_called,
        primary_contact_answered,
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
    
    return NextResponse.json({
      data: incidents || [],
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

