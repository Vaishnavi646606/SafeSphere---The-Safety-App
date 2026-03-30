import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

// Rate limiting store (in-memory, resets on cold start — good enough for free tier)
const rateLimit = new Map<string, { count: number; reset: number }>()

function checkRateLimit(userId: string): boolean {
  const now = Date.now()
  const entry = rateLimit.get(userId)
  if (!entry || entry.reset < now) {
    rateLimit.set(userId, { count: 1, reset: now + 60000 })
    return true
  }
  if (entry.count >= 10) return false
  entry.count++
  return true
}

export async function POST(req: NextRequest) {
  let body: {
    batch_id: string; user_id: string; app_version?: string; android_version?: string
    events: Array<{
      event_id: string; session_id: string; event_type: string; schema_version?: number
      client_ts: string; payload?: Record<string, unknown>
    }>
  }
  try { body = await req.json() }
  catch { return NextResponse.json({ error: 'Invalid JSON' }, { status: 400 }) }

  const { user_id, events = [], app_version, android_version } = body
  if (!user_id || !Array.isArray(events) || events.length === 0)
    return NextResponse.json({ error: 'user_id and events are required' }, { status: 400 })

  // Rate limit check
  if (!checkRateLimit(user_id))
    return NextResponse.json({ error: 'Rate limit exceeded. Wait 60s.' }, { status: 429 })

  const db = createServiceClient()

  // Check revocation status
  const { data: revocationData } = await db
    .from('revocation_tokens')
    .select('revocation_version, revoked_at, revocation_message')
    .eq('user_id', user_id)
    .single()

  const isRevoked = !!revocationData?.revoked_at

  if (isRevoked) {
    return NextResponse.json({
      accepted: [],
      duplicates: [],
      rejected: events.map(e => e.event_id),
      revocation_check: {
        revocation_version: revocationData.revocation_version,
        is_revoked: true,
        message: revocationData.revocation_message
      }
    }, { status: 403 })
  }

  // Validate and prepare events (max 100)
  const VALID_TYPES = new Set([
    'registration','login','protection_enabled','protection_disabled','trigger_source',
    'sms_sent','call_attempt','call_connected','location_shared','session_end',
    'safe_acknowledged','admin_message_received','revocation_detected','app_foregrounded','app_backgrounded'
  ])

  const geoCountry = req.headers.get('x-vercel-ip-country') || null
  const toInsert = events.slice(0, 100).filter(e => VALID_TYPES.has(e.event_type)).map(e => ({
    event_id: e.event_id,
    user_id,
    session_id: e.session_id,
    event_type: e.event_type,
    schema_version: e.schema_version || 1,
    client_ts: e.client_ts,
    payload: e.payload || {},
    ip_country: geoCountry,
    app_version: app_version || null,
    android_version: android_version || null,
    is_duplicate: false
  }))

  if (toInsert.length === 0)
    return NextResponse.json({ accepted: [], duplicates: [], rejected: events.map(e => e.event_id) })

  // Upsert with idempotency — conflict on event_id marks as duplicate
  const { data: inserted, error } = await db
    .from('analytics_events')
    .upsert(toInsert, { onConflict: 'event_id', ignoreDuplicates: false })
    .select('event_id, is_duplicate')

  if (error) {
    console.error('Ingest error:', error)
    return NextResponse.json({ error: 'Write failed' }, { status: 500 })
  }

  const accepted = (inserted || []).filter((e: { is_duplicate: boolean }) => !e.is_duplicate).map((e: { event_id: string }) => e.event_id)
  const duplicates = (inserted || []).filter((e: { is_duplicate: boolean }) => e.is_duplicate).map((e: { event_id: string }) => e.event_id)

  // Update last_seen_at
  await db.from('users').update({ last_seen_at: new Date().toISOString() }).eq('id', user_id)

  // Piggyback pending messages
  const { data: pendingMessages } = await db
    .from('admin_messages')
    .select('id, subject, body, is_critical, sent_at')
    .eq('user_id', user_id)
    .is('fetched_at', null)
    .order('sent_at', { ascending: false })
    .limit(5)

  if (pendingMessages?.length) {
    await db.from('admin_messages')
      .update({ fetched_at: new Date().toISOString() })
      .in('id', pendingMessages.map((m: { id: string }) => m.id))
  }

  return NextResponse.json({
    accepted,
    duplicates,
    rejected: [],
    revocation_check: { revocation_version: 0, is_revoked: false, message: null },
    pending_messages: pendingMessages || []
  })
}
