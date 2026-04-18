import { createServiceClient } from '@/lib/supabase/server';
import { NextRequest, NextResponse } from 'next/server'

/**
 * GET /api/track/[token]
 * Public endpoint — no auth required.
 * Returns live location data for a given tracking token.
 * Called by /track/[token] page every 3 minutes.
 * Returns data only when is_active=true and expires_at is in the future.
 *
 * Verified table: live_location_sessions
 * Verified columns: token, lat, lng, accuracy, last_updated, display_name, is_active
 *
 * Response when found:
 *   { found: true, lat, lng, accuracy, last_updated, display_name, is_active }
 * Response when not found:
 *   { found: false, error: string }
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ token: string }> }
) {
  try {
    const { token } = await params

    if (!token || token.trim().length === 0) {
      return NextResponse.json(
        { found: false, error: 'Token is required' },
        { status: 400 }
      )
    }

    const supabase = createServiceClient()

    const cleanedToken = token.trim()

    const { data, error } = await supabase
      .from('live_location_sessions')
      .select('lat, lng, accuracy, last_updated, display_name, is_active')
      .eq('token', cleanedToken)
      .eq('is_active', true)
      .gt('expires_at', new Date().toISOString())
      .single()

    if (data && !error) {
      return NextResponse.json({
        found: true,
        lat: data.lat,
        lng: data.lng,
        accuracy: data.accuracy ?? 0,
        last_updated: data.last_updated,
        display_name: data.display_name,
        is_active: data.is_active,
      })
    }

    const { data: helperLink } = await supabase
      .from('emergency_helper_links')
      .select('id, user_id, contact_slot, contact_number, opened_at, open_count')
      .eq('token', cleanedToken)
      .eq('is_active', true)
      .gt('expires_at', new Date().toISOString())
      .single()

    if (!helperLink) {
      return NextResponse.json(
        { found: false, error: 'Tracking session not found or expired' },
        { status: 404 }
      )
    }

    const nowIso = new Date().toISOString()
    await supabase
      .from('emergency_helper_links')
      .update({
        opened_at: helperLink.opened_at ?? nowIso,
        last_opened_at: nowIso,
        open_count: (helperLink.open_count ?? 0) + 1,
      })
      .eq('id', helperLink.id)

    const { data: victimSession, error: victimError } = await supabase
      .from('live_location_sessions')
      .select('lat, lng, accuracy, last_updated, display_name, is_active')
      .eq('user_id', helperLink.user_id)
      .eq('is_active', true)
      .gt('expires_at', nowIso)
      .single()

    if (victimError || !victimSession) {
      return NextResponse.json(
        { found: false, error: 'Victim live location unavailable' },
        { status: 404 }
      )
    }

    return NextResponse.json({
      found: true,
      lat: victimSession.lat,
      lng: victimSession.lng,
      accuracy: victimSession.accuracy ?? 0,
      last_updated: victimSession.last_updated,
      display_name: victimSession.display_name,
      is_active: victimSession.is_active,
      helper_contact_slot: helperLink.contact_slot,
      helper_contact_number: helperLink.contact_number,
    })

  } catch (err) {
    console.error('Track API error:', err)
    return NextResponse.json(
      { found: false, error: 'Internal server error' },
      { status: 500 }
    )
  }
}
