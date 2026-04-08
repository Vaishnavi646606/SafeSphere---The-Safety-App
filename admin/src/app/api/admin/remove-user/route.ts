import { NextRequest, NextResponse } from 'next/server'
import { createClient, createServiceClient } from '@/lib/supabase/server'

async function getAdminId(req: NextRequest): Promise<{ id: string; email: string } | null> {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return null
  const { data: admin } = await supabase
    .from('admin_accounts')
    .select('id, email')
    .eq('supabase_uid', user.id)
    .eq('is_active', true)
    .single()
  return admin || null
}

export async function POST(req: NextRequest) {
  const admin = await getAdminId(req)
  if (!admin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })
  }

  let body: { user_id: string; reason: string; note?: string }
  try {
    body = await req.json()
  } catch {
    return NextResponse.json({ error: 'Invalid JSON' }, { status: 400 })
  }

  const { user_id, reason, note } = body
  if (!user_id || !reason?.trim()) {
    return NextResponse.json(
      { error: 'user_id and reason are required' },
      { status: 400 }
    )
  }

  const db = await createServiceClient()

  // Check user exists
  const { data: user, error: userErr } = await db
    .from('users')
    .select('id, is_active')
    .eq('id', user_id)
    .single()

  if (userErr || !user) {
    return NextResponse.json({ error: 'User not found' }, { status: 404 })
  }

  // Idempotency — already removed
  if (!user.is_active) {
    return NextResponse.json(
      { success: true, already_removed: true },
      { status: 200 }
    )
  }

  const message = `You have been removed from SafeSphere because: ${reason}.`

  // Step 1 — Deactivate user using only real columns
  const { error: removeError } = await db
    .from('users')
    .update({
      is_active: false,
      updated_at: new Date().toISOString()
    })
    .eq('id', user_id)

  if (removeError) {
    console.error('Remove user error:', removeError)
    return NextResponse.json(
      { error: 'Failed to remove user' },
      { status: 500 }
    )
  }

  // Step 2 — Record in revocation_tokens
  // Only uses verified columns: id, user_id, reason, created_at
  await db.from('revocation_tokens').insert({
    user_id,
    reason
  })

  // Step 3 — Send admin message to user
  // Only uses verified columns: subject, body, target_user_id, is_critical
  await db.from('admin_messages').insert({
    target_user_id: user_id,
    subject: 'Account Removed',
    body: message,
    is_critical: true
  })

  // Step 4 — Log to audit_logs
  // Only uses verified columns: admin_id, action, target_user_id, details
  const { data: auditData } = await db
    .from('audit_logs')
    .insert({
      admin_id: admin.id,
      action: 'user_removed',
      target_user_id: user_id,
      details: {
        reason,
        note: note || null,
        message,
        changed_by: admin.email
      }
    })
    .select('id')
    .single()

  return NextResponse.json({
    success: true,
    audit_id: auditData?.id || null
  })
}
