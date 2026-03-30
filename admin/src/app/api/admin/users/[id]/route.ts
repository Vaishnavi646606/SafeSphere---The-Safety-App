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

    const { data: user, error: userError } = await serviceClient
      .from('users')
      .select('*')
      .eq('id', id)
      .single()

    if (userError || !user) {
      return NextResponse.json({ error: 'User not found' }, { status: 404 })
    }

    const { data: emergencies, error: emergenciesError } = await serviceClient
      .from('emergency_events')
      .select(`
        id,
        user_id,
        trigger_type,
        triggered_at,
        status,
        resolution_type,
        primary_contact_called,
        primary_contact_answered,
        time_to_resolve_s,
        has_location_enabled,
        admin_notes,
        requires_admin_review
      `)
      .eq('user_id', id)
      .order('triggered_at', { ascending: false })
      .limit(10)

    if (emergenciesError) {
      return NextResponse.json({ error: emergenciesError.message }, { status: 500 })
    }

    const { data: feedback, error: feedbackError } = await serviceClient
      .from('emergency_feedback')
      .select(`
        id,
        event_id,
        user_id,
        was_real_emergency,
        was_rescued_or_helped,
        rating,
        feedback_text,
        feedback_category,
        helpful_features,
        admin_reviewed,
        admin_response,
        admin_reviewed_at,
        submitted_at,
        created_at
      `)
      .eq('user_id', id)
      .order('submitted_at', { ascending: false })
      .limit(5)

    if (feedbackError) {
      return NextResponse.json({ error: feedbackError.message }, { status: 500 })
    }

    return NextResponse.json({ user, emergencies: emergencies || [], feedback: feedback || [] })
  } catch (error) {
    console.error('GET /api/admin/users/[id] error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

export async function PUT(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const auth = await verifyAdmin()
    if (auth.error) return auth.error

    const { id } = await params
    const body = await req.json()
    const { is_active, reason } = body

    if (typeof is_active !== 'boolean') {
      return NextResponse.json({ error: 'is_active boolean is required' }, { status: 400 })
    }

    const serviceClient = createServiceClient()

    const { data: updatedUser, error: updateError } = await serviceClient
      .from('users')
      .update({
        is_active,
        updated_at: new Date().toISOString()
      })
      .eq('id', id)
      .select('*')
      .single()

    if (updateError || !updatedUser) {
      return NextResponse.json({ error: updateError?.message || 'User update failed' }, { status: 500 })
    }

    await serviceClient.from('audit_logs').insert({
      admin_id: auth.admin.id,
      action: is_active ? 'user_reactivated' : 'user_deactivated',
      target_user_id: id,
      details: {
        reason: reason || null,
        changed_by: auth.admin.email
      }
    })

    return NextResponse.json({ success: true, user: updatedUser })
  } catch (error) {
    console.error('PUT /api/admin/users/[id] error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}
