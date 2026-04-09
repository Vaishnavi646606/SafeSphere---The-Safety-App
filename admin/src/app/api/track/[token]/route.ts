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

    const { data, error } = await supabase
      .from('live_location_sessions')
      .select('lat, lng, accuracy, last_updated, display_name, is_active')
      .eq('token', token.trim())
      .eq('is_active', true)
      .gt('expires_at', new Date().toISOString())
      .single()

    if (error || !data) {
      return NextResponse.json(
        { found: false, error: 'Tracking session not found or expired' },
        { status: 404 }
      )
    }

    return NextResponse.json({
      found: true,
      lat: data.lat,
      lng: data.lng,
      accuracy: data.accuracy ?? 0,
      last_updated: data.last_updated,
      display_name: data.display_name,
      is_active: data.is_active,
    })

  } catch (err) {
    console.error('Track API error:', err)
    return NextResponse.json(
      { found: false, error: 'Internal server error' },
      { status: 500 }
    )
  }
}
