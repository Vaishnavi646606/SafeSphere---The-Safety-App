import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { createServiceClient } from '@/lib/supabase/server'

async function getAdminId(req: NextRequest): Promise<string | null> {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return null
  const { data: admin } = await supabase
    .from('admin_accounts').select('id').eq('supabase_uid', user.id).eq('is_active', true).single()
  return admin?.id || null
}

export async function POST(req: NextRequest) {
  const adminId = await getAdminId(req)
  if (!adminId) return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })

  let body: { user_id: string; reason: string; note?: string; message_template?: string }
  try { body = await req.json() }
  catch { return NextResponse.json({ error: 'Invalid JSON' }, { status: 400 }) }

  const { user_id, reason, note, message_template } = body
  if (!user_id || !reason?.trim())
    return NextResponse.json({ error: 'user_id and reason are required' }, { status: 400 })

  const db = createServiceClient()

  // Check user exists
  const { data: user, error: userErr } = await db.from('users').select('id, revoked_at, revocation_version').eq('id', user_id).single()
  if (userErr || !user) return NextResponse.json({ error: 'User not found' }, { status: 404 })

  // Idempotency — already revoked
  if (user.revoked_at) {
    return NextResponse.json({ success: true, revocation_version: user.revocation_version, already_revoked: true }, { status: 200 })
  }

  const newVersion = (user.revocation_version || 0) + 1
  const message = message_template || `You have been removed from SafeSphere because: ${reason}.`
  const ip = req.headers.get('x-forwarded-for') || req.headers.get('x-real-ip') || null

  // Atomic operations
  const [removeResult, tokenResult, msgResult, auditResult] = await Promise.all([
    db.from('users').update({
      is_active: false,
      revoked_at: new Date().toISOString(),
      revocation_version: newVersion,
      revocation_reason: reason,
      revocation_message: message,
      revoked_by: adminId
    }).eq('id', user_id),

    db.from('revocation_tokens').upsert({
      user_id,
      revocation_version: newVersion,
      revoked_at: new Date().toISOString(),
      revocation_message: message
    }, { onConflict: 'user_id' }),

    db.from('admin_messages').insert({
      user_id,
      sent_by: adminId,
      message_type: 'removal',
      subject: 'Account Removed',
      body: message,
      is_critical: true
    }).select('id').single(),

    db.from('admin_actions').insert({
      admin_id: adminId,
      action_type: 'user_removed',
      target_user_id: user_id,
      ip_address: ip,
      payload: { reason, note: note || null, message }
    }).select('id').single()
  ])

  if (removeResult.error) {
    console.error('Remove error:', removeResult.error)
    return NextResponse.json({ error: 'Failed to remove user' }, { status: 500 })
  }

  return NextResponse.json({
    success: true,
    revocation_version: newVersion,
    message_queued: !msgResult.error,
    audit_id: auditResult.data?.id
  })
}
