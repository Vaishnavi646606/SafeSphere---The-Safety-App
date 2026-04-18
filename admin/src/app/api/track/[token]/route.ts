import { createServiceClient } from '@/lib/supabase/server';
import { NextRequest, NextResponse } from 'next/server'

const AUTO_RESCUE_RADIUS_METERS = 50

interface HelperLinkRow {
  id: string
  user_id: string
  contact_slot: number | null
  contact_number: string | null
  opened_at: string | null
  open_count: number | null
  helper_lat?: number | null
  helper_lng?: number | null
  helper_accuracy?: number | null
  helper_last_updated?: string | null
  helper_distance_m?: number | null
  auto_rescue_triggered?: boolean | null
  auto_rescue_at?: string | null
}

interface AutoRescueResult {
  triggered: boolean
  eventId: string | null
  sessionId: string | null
  verificationCreated: boolean
}

interface AutoRescueEventRow {
  id: string
  session_id: string | null
  triggered_at: string
  status: string | null
  resolution_type: string | null
  admin_notes: string | null
}

function toRadians(value: number): number {
  return (value * Math.PI) / 180
}

function distanceMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const earthRadiusMeters = 6371000
  const dLat = toRadians(lat2 - lat1)
  const dLng = toRadians(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRadians(lat1)) *
      Math.cos(toRadians(lat2)) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2)

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return earthRadiusMeters * c
}

function readCoordinate(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

function hasAutoRescueNote(notes: string | null | undefined): boolean {
  return typeof notes === 'string' && notes.includes('[AUTO_RESCUE]')
}

function hasManualResolution(value: string | null | undefined): boolean {
  if (!value || value.trim().length === 0) return false
  return value.trim() !== 'safe_contact'
}

async function maybeCreateAutoVerification(
  supabase: ReturnType<typeof createServiceClient>,
  userId: string,
  incidentSessionId: string,
  distanceM: number,
  nowIso: string
): Promise<boolean> {
  const cleanSessionId = incidentSessionId.trim()
  if (!cleanSessionId) return false

  const { data: existingVerification } = await supabase
    .from('saved_verifications')
    .select('id')
    .eq('user_id', userId)
    .eq('incident_session_id', cleanSessionId)
    .maybeSingle()

  if (existingVerification) {
    return false
  }

  const verificationNote = `[AUTO_RESCUE] Auto-verified by helper proximity (${Math.round(distanceM)}m).`
  const { error: insertVerificationError } = await supabase
    .from('saved_verifications')
    .insert({
      user_id: userId,
      incident_session_id: cleanSessionId,
      verified_by: null,
      evidence_type: 'auto_proximity_detection',
      notes: verificationNote,
      verified_at: nowIso,
    })

  if (insertVerificationError) {
    return false
  }

  await supabase
    .from('audit_logs')
    .insert({
      admin_id: null,
      action: 'rescue_auto_verified',
      target_user_id: userId,
      details: {
        incident_session_id: cleanSessionId,
        source: 'helper_proximity',
        distance_m: Math.round(distanceM),
      },
    })

  return true
}

async function fetchHelperLink(
  supabase: ReturnType<typeof createServiceClient>,
  cleanedToken: string,
  nowIso: string
): Promise<HelperLinkRow | null> {
  const { data: extended, error: extendedError } = await supabase
    .from('emergency_helper_links')
    .select('id, user_id, contact_slot, contact_number, opened_at, open_count, helper_lat, helper_lng, helper_accuracy, helper_last_updated, helper_distance_m, auto_rescue_triggered, auto_rescue_at')
    .eq('token', cleanedToken)
    .eq('is_active', true)
    .gt('expires_at', nowIso)
    .single()

  if (extended && !extendedError) {
    return extended as HelperLinkRow
  }

  const { data: basic, error: basicError } = await supabase
    .from('emergency_helper_links')
    .select('id, user_id, contact_slot, contact_number, opened_at, open_count')
    .eq('token', cleanedToken)
    .eq('is_active', true)
    .gt('expires_at', nowIso)
    .single()

  if (basicError || !basic) {
    return null
  }

  return {
    ...(basic as HelperLinkRow),
    helper_lat: null,
    helper_lng: null,
    helper_accuracy: null,
    helper_last_updated: null,
    helper_distance_m: null,
    auto_rescue_triggered: false,
    auto_rescue_at: null,
  }
}

async function fetchVictimSession(
  supabase: ReturnType<typeof createServiceClient>,
  userId: string,
  nowIso: string
) {
  const { data, error } = await supabase
    .from('live_location_sessions')
    .select('lat, lng, accuracy, last_updated, display_name, is_active')
    .eq('user_id', userId)
    .eq('is_active', true)
    .gt('expires_at', nowIso)
    .single()

  if (error || !data) return null
  return data
}

async function maybeMarkEmergencyAsRescued(
  supabase: ReturnType<typeof createServiceClient>,
  userId: string,
  distanceM: number,
  nowIso: string
): Promise<AutoRescueResult> {
  if (distanceM > AUTO_RESCUE_RADIUS_METERS) {
    return {
      triggered: false,
      eventId: null,
      sessionId: null,
      verificationCreated: false,
    }
  }

  const lookbackMs = 24 * 60 * 60 * 1000
  const lookbackIso = new Date(new Date(nowIso).getTime() - lookbackMs).toISOString()

  const { data: events } = await supabase
    .from('emergency_events')
    .select('id, session_id, triggered_at, status, resolution_type, admin_notes')
    .eq('user_id', userId)
    .gte('triggered_at', lookbackIso)
    .order('triggered_at', { ascending: false })
    .limit(1)

  const candidateEvents = (events || []) as AutoRescueEventRow[]

  if (candidateEvents.length === 0) {
    return {
      triggered: false,
      eventId: null,
      sessionId: null,
      verificationCreated: false,
    }
  }

  const event = candidateEvents[0]
  if (!event) {
    return {
      triggered: false,
      eventId: null,
      sessionId: null,
      verificationCreated: false,
    }
  }

  if (hasAutoRescueNote(event.admin_notes)) {
    return {
      triggered: true,
      eventId: event.id,
      sessionId: event.session_id ?? event.id,
      verificationCreated: false,
    }
  }

  // Respect explicit manual outcomes set by admin/user and do not override them.
  if (hasManualResolution(event.resolution_type) && !hasAutoRescueNote(event.admin_notes)) {
    return {
      triggered: false,
      eventId: event.id,
      sessionId: event.session_id ?? event.id,
      verificationCreated: false,
    }
  }

  const triggeredAtMs = new Date(event.triggered_at).getTime()
  const resolvedAtMs = new Date(nowIso).getTime()
  const timeToResolveS = Number.isFinite(triggeredAtMs)
    ? Math.max(0, Math.round((resolvedAtMs - triggeredAtMs) / 1000))
    : null

  const autoNote = `[AUTO_RESCUE] Helper proximity detected within ${Math.round(distanceM)}m from live location.`
  const mergedNotes = hasAutoRescueNote(event.admin_notes)
    ? event.admin_notes
    : event.admin_notes && event.admin_notes.trim().length > 0
    ? `${event.admin_notes}\n${autoNote}`
    : autoNote

  const { error: updateError } = await supabase
    .from('emergency_events')
    .update({
      status: 'resolved',
      resolution_type: 'safe_contact',
      resolved_at: nowIso,
      time_to_resolve_s: timeToResolveS,
      requires_admin_review: false,
      admin_notes: mergedNotes,
      updated_at: nowIso,
    })
    .eq('id', event.id)

  if (updateError) {
    return {
      triggered: false,
      eventId: event.id,
      sessionId: event.session_id ?? event.id,
      verificationCreated: false,
    }
  }

  const sessionId = event.session_id ?? event.id
  const verificationCreated = await maybeCreateAutoVerification(
    supabase,
    userId,
    sessionId,
    distanceM,
    nowIso
  )

  return {
    triggered: true,
    eventId: event.id,
    sessionId,
    verificationCreated,
  }
}

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

    const nowIso = new Date().toISOString()
    const helperLink = await fetchHelperLink(supabase, cleanedToken, nowIso)

    if (!helperLink) {
      return NextResponse.json(
        { found: false, error: 'Tracking session not found or expired' },
        { status: 404 }
      )
    }

    await supabase
      .from('emergency_helper_links')
      .update({
        opened_at: helperLink.opened_at ?? nowIso,
        last_opened_at: nowIso,
        open_count: (helperLink.open_count ?? 0) + 1,
      })
      .eq('id', helperLink.id)

    const victimSession = await fetchVictimSession(supabase, helperLink.user_id, nowIso)

    if (!victimSession) {
      return NextResponse.json(
        { found: false, error: 'Victim live location unavailable' },
        { status: 404 }
      )
    }

    const hasHelperCoords =
      typeof helperLink.helper_lat === 'number' &&
      Number.isFinite(helperLink.helper_lat) &&
      typeof helperLink.helper_lng === 'number' &&
      Number.isFinite(helperLink.helper_lng)

    const helperDistance = hasHelperCoords
      ? distanceMeters(
          victimSession.lat,
          victimSession.lng,
          helperLink.helper_lat as number,
          helperLink.helper_lng as number
        )
      : helperLink.helper_distance_m ?? null

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
      helper_lat: helperLink.helper_lat ?? null,
      helper_lng: helperLink.helper_lng ?? null,
      helper_accuracy: helperLink.helper_accuracy ?? null,
      helper_last_updated: helperLink.helper_last_updated ?? null,
      helper_distance_m: helperDistance,
      within_rescue_radius: helperDistance !== null ? helperDistance <= AUTO_RESCUE_RADIUS_METERS : false,
      auto_rescue_triggered: Boolean(helperLink.auto_rescue_triggered),
      auto_rescue_at: helperLink.auto_rescue_at ?? null,
    })

  } catch (err) {
    console.error('Track API error:', err)
    return NextResponse.json(
      { found: false, error: 'Internal server error' },
      { status: 500 }
    )
  }
}

/**
 * POST /api/track/[token]
 * Public endpoint used by helper browsers to submit their own live location.
 * If helper and victim are within 50m, emergency is auto-marked as rescued.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ token: string }> }
) {
  try {
    const { token } = await params

    if (!token || token.trim().length === 0) {
      return NextResponse.json(
        { success: false, error: 'Token is required' },
        { status: 400 }
      )
    }

    const body = await request.json()
    const helperLat = readCoordinate(body?.lat)
    const helperLng = readCoordinate(body?.lng)
    const helperAccuracy = readCoordinate(body?.accuracy)

    if (helperLat === null || helperLng === null) {
      return NextResponse.json(
        { success: false, error: 'lat and lng are required numeric values' },
        { status: 400 }
      )
    }

    if (helperLat < -90 || helperLat > 90 || helperLng < -180 || helperLng > 180) {
      return NextResponse.json(
        { success: false, error: 'Coordinates are out of range' },
        { status: 400 }
      )
    }

    const supabase = createServiceClient()
    const cleanedToken = token.trim()
    const nowIso = new Date().toISOString()

    const helperLink = await fetchHelperLink(supabase, cleanedToken, nowIso)
    if (!helperLink) {
      return NextResponse.json(
        { success: false, error: 'Helper tracking link not found or expired' },
        { status: 404 }
      )
    }

    const victimSession = await fetchVictimSession(supabase, helperLink.user_id, nowIso)
    if (!victimSession) {
      return NextResponse.json(
        { success: false, error: 'Victim live location unavailable' },
        { status: 404 }
      )
    }

    const helperDistanceM = distanceMeters(victimSession.lat, victimSession.lng, helperLat, helperLng)
    const withinRescueRadius = helperDistanceM <= AUTO_RESCUE_RADIUS_METERS
    const alreadyAutoRescueTriggered = Boolean(helperLink.auto_rescue_triggered)

    let autoRescueResult: AutoRescueResult = {
      triggered: false,
      eventId: null,
      sessionId: null,
      verificationCreated: false,
    }

    if (withinRescueRadius && !alreadyAutoRescueTriggered) {
      autoRescueResult = await maybeMarkEmergencyAsRescued(
        supabase,
        helperLink.user_id,
        helperDistanceM,
        nowIso
      )
    }

    const finalAutoRescueTriggered = alreadyAutoRescueTriggered || autoRescueResult.triggered
    const finalAutoRescueAt = helperLink.auto_rescue_at ?? (autoRescueResult.triggered ? nowIso : null)

    const updatePayload: Record<string, unknown> = {
      opened_at: helperLink.opened_at ?? nowIso,
      last_opened_at: nowIso,
      open_count: (helperLink.open_count ?? 0) + 1,
      helper_lat: helperLat,
      helper_lng: helperLng,
      helper_accuracy: helperAccuracy ?? null,
      helper_last_updated: nowIso,
      helper_distance_m: helperDistanceM,
    }

    if (withinRescueRadius && !alreadyAutoRescueTriggered && autoRescueResult.triggered) {
      updatePayload.auto_rescue_triggered = true
      updatePayload.auto_rescue_at = nowIso
    }

    const { error: helperUpdateError } = await supabase
      .from('emergency_helper_links')
      .update(updatePayload)
      .eq('id', helperLink.id)

    if (helperUpdateError) {
      await supabase
        .from('emergency_helper_links')
        .update({
          opened_at: helperLink.opened_at ?? nowIso,
          last_opened_at: nowIso,
          open_count: (helperLink.open_count ?? 0) + 1,
        })
        .eq('id', helperLink.id)
    }

    return NextResponse.json({
      success: true,
      found: true,
      lat: victimSession.lat,
      lng: victimSession.lng,
      accuracy: victimSession.accuracy ?? 0,
      last_updated: victimSession.last_updated,
      display_name: victimSession.display_name,
      is_active: victimSession.is_active,
      helper_contact_slot: helperLink.contact_slot,
      helper_contact_number: helperLink.contact_number,
      helper_lat: helperLat,
      helper_lng: helperLng,
      helper_accuracy: helperAccuracy ?? null,
      helper_last_updated: nowIso,
      helper_distance_m: helperDistanceM,
      within_rescue_radius: withinRescueRadius,
      auto_rescue_triggered: finalAutoRescueTriggered,
      auto_rescue_at: finalAutoRescueAt,
      verification_auto_created: autoRescueResult.verificationCreated,
    })
  } catch (err) {
    console.error('Track helper-location POST error:', err)
    return NextResponse.json(
      { success: false, error: 'Internal server error' },
      { status: 500 }
    )
  }
}
