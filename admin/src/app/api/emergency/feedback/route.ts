import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

/**
 * POST /api/emergency/feedback
 * 
 * Receives user feedback after emergency event.
 * Stores in emergency_feedback table for admin review.
 * 
 * Request body:
 * {
 *   "event_id": "uuid",
 *   "user_id": "uuid",
 *   "was_real_emergency": boolean,
 *   "was_rescued_or_helped": boolean,
 *   "rating": 1-5,
 *   "feedback_text": "optional comment"
 * }
 */
export async function POST(req: NextRequest) {
  try {
    const body = await req.json()
    
    const {
      event_id,
      user_id,
      was_real_emergency,
      was_rescued_or_helped,
      rating,
      feedback_text
    } = body
    
    // Validate required fields
    if (!event_id || !user_id || typeof was_real_emergency !== 'boolean' ||
        typeof was_rescued_or_helped !== 'boolean' || !rating) {
      return NextResponse.json({
        error: 'Missing required fields'
      }, { status: 400 })
    }
    
    if (rating < 1 || rating > 5) {
      return NextResponse.json({
        error: 'Rating must be between 1 and 5'
      }, { status: 400 })
    }
    
    const db = createServiceClient()
    
    // Verify event exists and belongs to user
    const { data: event, error: eventError } = await db
      .from('emergency_events')
      .select('id, user_id')
      .eq('id', event_id)
      .single()
    
    if (eventError || event?.user_id !== user_id) {
      console.error('Event validation error:', eventError)
      return NextResponse.json({
        error: 'Event not found or unauthorized'
      }, { status: 404 })
    }
    
    // Insert feedback
    const { data: feedback, error: insertError } = await db
      .from('emergency_feedback')
      .insert({
        event_id,
        user_id,
        was_real_emergency,
        was_rescued_or_helped,
        rating,
        feedback_text: feedback_text || null
      })
      .select('id')
      .single()
    
    if (insertError) {
      console.error('Feedback insert error:', insertError)
      return NextResponse.json({
        error: 'Failed to submit feedback'
      }, { status: 500 })
    }
    
    // Update event to mark it should be flagged for admin review if false alarm
    if (!was_real_emergency || !was_rescued_or_helped) {
      await db
        .from('emergency_events')
        .update({ requires_admin_review: true })
        .eq('id', event_id)
    }
    
    return NextResponse.json({
      success: true,
      feedback_id: feedback.id,
      message: 'Feedback received, thank you!'
    }, { status: 201 })
  } catch (err) {
    console.error('POST /api/emergency/feedback error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

/**
 * GET /api/emergency/feedback?limit=20&offset=0
 * 
 * Retrieve all emergency feedback (admin only).
 * Supports pagination and filtering.
 */
export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url)
    const limit = parseInt(searchParams.get('limit') || '20')
    const offset = parseInt(searchParams.get('offset') || '0')
    const adminReviewedOnly = searchParams.get('not_reviewed') === 'true'
    
    const db = createServiceClient()
    
    let query = db
      .from('emergency_feedback')
      .select(`
        id,
        event_id,
        user_id,
        was_real_emergency,
        was_rescued_or_helped,
        rating,
        feedback_text,
        admin_reviewed,
        submitted_at,
        emergency_events(trigger_type, triggered_at)
      `, { count: 'exact' })
    
    if (adminReviewedOnly) {
      query = query.eq('admin_reviewed', false)
    }
    
    const { data: feedback, error, count } = await query
      .order('submitted_at', { ascending: false })
      .range(offset, offset + limit - 1)
    
    if (error) {
      console.error('Feedback query error:', error)
      return NextResponse.json({ error: 'Failed to fetch feedback' }, { status: 500 })
    }
    
    return NextResponse.json({
      data: feedback,
      count,
      limit,
      offset,
      total_pages: Math.ceil((count || 0) / limit)
    })
  } catch (err) {
    console.error('GET /api/emergency/feedback error:', err)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}
