import { NextRequest, NextResponse } from 'next/server'
import { createClient, createServiceClient } from '@/lib/supabase/server'

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient()
    const {
      data: { user }
    } = await supabase.auth.getUser()

    if (!user) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const { data: admin, error: adminError } = await supabase
      .from('admin_accounts')
      .select('id')
      .eq('supabase_uid', user.id)
      .eq('is_active', true)
      .single()

    if (adminError || !admin) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    const body = await req.json()
    const { user_id, incident_id, session_id, evidence_type, notes } = body

    if (!user_id || !incident_id || !session_id || !evidence_type) {
      return NextResponse.json({ error: 'Missing required fields' }, { status: 400 })
    }

    const serviceClient = createServiceClient()

    // Check if already verified
    const { data: existing, error: checkError } = await serviceClient
      .from('saved_verifications')
      .select('id')
      .eq('incident_session_id', session_id)
      .eq('user_id', user_id)
      .single()

    if (existing) {
      return NextResponse.json({ error: 'Already verified' }, { status: 409 })
    }

    const { error: verifyError } = await serviceClient.from('saved_verifications').insert({
      user_id,
      incident_session_id: session_id,
      verified_by: admin.id,
      evidence_type,
      notes: notes || '',
      verified_at: new Date().toISOString()
    })

    if (verifyError) {
      return NextResponse.json({ error: verifyError.message }, { status: 500 })
    }

    const { error: auditError } = await serviceClient.from('audit_logs').insert({
      admin_id: admin.id,
      action: 'rescue_verified',
      target_user_id: user_id,
      details: {
        incident_id,
        evidence_type
      }
    })

    if (auditError) {
      return NextResponse.json({ error: auditError.message }, { status: 500 })
    }

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('POST /api/admin/verify-rescue error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}

export async function DELETE(req: NextRequest) {
  try {
    const supabase = await createClient()
    const {
      data: { user }
    } = await supabase.auth.getUser()

    if (!user) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const { data: admin, error: adminError } = await supabase
      .from('admin_accounts')
      .select('id')
      .eq('supabase_uid', user.id)
      .eq('is_active', true)
      .single()

    if (adminError || !admin) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 })
    }

    const body = await req.json()
    const { incident_session_id, user_id } = body

    if (!incident_session_id || !user_id) {
      return NextResponse.json({ error: 'Missing required fields' }, { status: 400 })
    }

    const serviceClient = createServiceClient()

    // Delete the verification
    const { error: deleteError } = await serviceClient
      .from('saved_verifications')
      .delete()
      .eq('incident_session_id', incident_session_id)
      .eq('user_id', user_id)

    if (deleteError) {
      return NextResponse.json({ error: deleteError.message }, { status: 500 })
    }

    // Log the deletion
    const { error: auditError } = await serviceClient.from('audit_logs').insert({
      admin_id: admin.id,
      action: 'rescue_verification_deleted',
      target_user_id: user_id,
      details: {
        incident_session_id
      }
    })

    if (auditError) {
      return NextResponse.json({ error: auditError.message }, { status: 500 })
    }

    return NextResponse.json({ success: true })
  } catch (error) {
    console.error('DELETE /api/admin/verify-rescue error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}
