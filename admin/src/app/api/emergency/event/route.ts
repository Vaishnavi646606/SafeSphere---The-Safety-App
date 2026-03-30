import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

/**
 * POST /api/emergency/event
 * 
 * Receives emergency event from Android app.
 * Stores in emergency_events table for admin tracking.
 * 
 * Request body:
 * {
 *   "user_id": "uuid",
 *   "trigger_type": "keyword_detected|shake_detected|manual_trigger|power_button",
 *   "triggered_at": "ISO8601 timestamp",
 *   "session_id": "uuid",
 *   "location_lat": number,
 *   "location_lng": number,
 *   "battery_percent": number,
 *   "has_location_enabled": boolean
 * }
 */
export async function POST(req: NextRequest) {
  try {
    const body = await req.json()
    
    const {
      user_id,
      trigger_type,
      triggered_at,
      session_id,
      location_lat,
      location_lng,
      battery_percent,
      has_location_enabled
    } = body
    
    // Validate required fields
    if (!user_id || !trigger_type || !triggered_at || !session_id) {
      return NextResponse.json({
        error: 'Missing required fields: user_id, trigger_type, triggered_at, session_id'
      }, { status: 400 })
    }
    
    const db = createServiceClient()
    
    // Insert emergency event
    const { data: event, error } = await db
      .from('emergency_events')
      .insert({
        user_id,
        trigger_type,
        triggered_at,
        session_id,
        location_lat: location_lat || null,
        location_lng: location_lng || null,
        phone_battery_percent: battery_percent || null,
        has_location_enabled: has_location_enabled || false,
        status: 'triggered'
      })
      .select('id')
      .single()
    
    if (error) {
      console.error('Emergency event insert error:', error)
      return NextResponse.json({ error: 'Failed to insert event' }, { status: 500 })
    }
    
    // Update user last_app_open for analytics
    await db
      .from('users')
      .update({ last_app_open: new Date().toISOString() })
      .eq('id', user_id)
    
    return NextResponse.json({
      success: true,
      event_id: event.id,
      message: 'Emergency event recorded'
    }, { status: 201 })
  } catch (err) {
    console.error('POST /api/emergency/event error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

/**
 * PUT /api/emergency/event
 *
 * Update emergency event status from Android.
 * Accepts event id via query string (?id=...) or request body (id/event_id).
 */
export async function PUT(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url)
    const body = await req.json()
    const id = searchParams.get('id') || body.id || body.event_id
    
    const {
      status,
      primary_contact_called,
      primary_contact_answered,
      primary_contact_duration_s,
      time_to_answer_s,
      secondary_contact_called,
      tertiary_contact_called,
      resolved_at,
      resolution_type
    } = body
    
    if (!status) {
      return NextResponse.json({ error: 'status is required' }, { status: 400 })
    }

    if (!id) {
      return NextResponse.json({ error: 'id is required (query or body)' }, { status: 400 })
    }
    
    const db = createServiceClient()
    
    const { error } = await db
      .from('emergency_events')
      .update({
        status,
        primary_contact_called: primary_contact_called || null,
        primary_contact_answered: primary_contact_answered || null,
        primary_contact_duration_s: primary_contact_duration_s || null,
        time_to_answer_s: time_to_answer_s || null,
        secondary_contact_called: secondary_contact_called || null,
        tertiary_contact_called: tertiary_contact_called || null,
        resolved_at: resolved_at || null,
        resolution_type: resolution_type || null,
        updated_at: new Date().toISOString()
      })
      .eq('id', id)
    
    if (error) {
      console.error('Emergency event update error:', error)
      return NextResponse.json({ error: 'Failed to update event' }, { status: 500 })
    }
    
    return NextResponse.json({
      success: true,
      message: 'Emergency event updated'
    })
  } catch (err) {
    console.error('PUT /api/emergency/event error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}
