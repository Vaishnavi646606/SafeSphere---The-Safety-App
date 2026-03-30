import { NextRequest, NextResponse } from 'next/server'
import { createClient, createServiceClient } from '@/lib/supabase/server'

async function verifyAdmin() {
  const supabase = await createClient()
  const {
    data: { user }
  } = await supabase.auth.getUser()

  if (!user) {
    return { error: NextResponse.json({ error: 'Unauthorized' }, { status: 401 }) }
  }

  const { data: admin, error: adminError } = await supabase
    .from('admin_accounts')
    .select('id, email')
    .eq('supabase_uid', user.id)
    .eq('is_active', true)
    .single()

  if (adminError || !admin) {
    return { error: NextResponse.json({ error: 'Forbidden' }, { status: 403 }) }
  }

  return { admin }
}

export async function GET(_: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const auth = await verifyAdmin()
    if (auth.error) return auth.error

    const { id } = await params
    const serviceClient = createServiceClient()

    const { data, error } = await serviceClient
      .from('emergency_events')
      .select(`
        id,
        user_id,
        trigger_type,
        triggered_at,
        resolved_at,
        status,
        primary_contact_called,
        primary_contact_answered,
        primary_contact_duration_s,
        secondary_contact_called,
        secondary_contact_answered,
        tertiary_contact_called,
        tertiary_contact_answered,
        time_to_first_contact_s,
        time_to_answer_s,
        time_to_resolve_s,
        resolution_type,
        admin_notes,
        location_lat,
        location_lng,
        has_location_enabled,
        phone_battery_percent,
        is_test_event,
        requires_admin_review,
        sms_sent_to,
        created_at,
        updated_at,
        users:user_id (display_name, phone_hash)
      `)
      .eq('id', id)
      .single()

    if (error || !data) {
      return NextResponse.json({ error: 'Incident not found' }, { status: 404 })
    }

    return NextResponse.json({ incident: data })
  } catch (error) {
    console.error('GET /api/admin/incidents/[id] error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

export async function PUT(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const auth = await verifyAdmin()
    if (auth.error) return auth.error

    const { id } = await params
    const body = await req.json()
    const { admin_notes, status, resolution_type } = body

    const updates: Record<string, any> = { updated_at: new Date().toISOString() }
    if (admin_notes !== undefined) updates.admin_notes = admin_notes
    if (status !== undefined) updates.status = status
    if (resolution_type !== undefined) updates.resolution_type = resolution_type

    const serviceClient = createServiceClient()

    const { data: existing, error: existingError } = await serviceClient
      .from('emergency_events')
      .select('id, user_id')
      .eq('id', id)
      .single()

    if (existingError || !existing) {
      return NextResponse.json({ error: 'Incident not found' }, { status: 404 })
    }

    const { data: updated, error: updateError } = await serviceClient
      .from('emergency_events')
      .update(updates)
      .eq('id', id)
      .select('*')
      .single()

    if (updateError) {
      return NextResponse.json({ error: updateError.message }, { status: 500 })
    }

    const changes: Record<string, any> = {}
    if (admin_notes !== undefined) changes.admin_notes = admin_notes
    if (status !== undefined) changes.status = status
    if (resolution_type !== undefined) changes.resolution_type = resolution_type

    await serviceClient.from('audit_logs').insert({
      admin_id: auth.admin.id,
      action: 'incident_updated',
      target_user_id: existing.user_id,
      details: {
        incident_id: id,
        changes
      }
    })

    return NextResponse.json({ incident: updated })
  } catch (error) {
    console.error('PUT /api/admin/incidents/[id] error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}
