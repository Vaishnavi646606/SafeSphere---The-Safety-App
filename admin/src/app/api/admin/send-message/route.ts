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

  let body: { target: string; subject: string; body: string; is_critical?: boolean; message_type?: string }
  try { body = await req.json() }
  catch { return NextResponse.json({ error: 'Invalid body' }, { status: 400 }) }

  const { target, subject, body: msgBody, is_critical = false, message_type = 'policy' } = body
  if (!subject?.trim() || !msgBody?.trim())
    return NextResponse.json({ error: 'Subject and body are required' }, { status: 400 })

  const db = createServiceClient()
  let userIds: string[] = []

  if (target === 'all') {
    const { data: users } = await db.from('users').select('id').eq('is_active', true).is('revoked_at', null)
    userIds = (users || []).map((u: { id: string }) => u.id)
  } else if (target.startsWith('user_id:')) {
    userIds = [target.replace('user_id:', '')]
  }

  if (userIds.length === 0)
    return NextResponse.json({ error: 'No eligible users found' }, { status: 400 })

  // Batch insert messages (max 1000 at a time)
  const BATCH = 500
  const allIds: string[] = []
  const ip = req.headers.get('x-forwarded-for') || null

  for (let i = 0; i < userIds.length; i += BATCH) {
    const batch = userIds.slice(i, i + BATCH)
    const rows = batch.map(uid => ({
      user_id: uid, sent_by: adminId, message_type, subject, body: msgBody, is_critical
    }))
    const { data } = await db.from('admin_messages').insert(rows).select('id')
    if (data) allIds.push(...data.map((d: { id: string }) => d.id))
  }

  // Audit log
  await db.from('admin_actions').insert({
    admin_id: adminId,
    action_type: 'message_sent',
    ip_address: ip,
    payload: { subject, target, queued_count: allIds.length, message_type, is_critical }
  })

  return NextResponse.json({ success: true, queued_count: allIds.length, message_ids: allIds.slice(0, 10) })
}

export async function GET(req: NextRequest) {
  const adminId = await getAdminId(req)
  if (!adminId) return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })

  const db = createServiceClient()
  const { data: messages } = await db
    .from('admin_messages')
    .select('id, subject, body, message_type, is_critical, sent_at, fetched_at, acknowledged_at')
    .order('sent_at', { ascending: false })
    .limit(100)

  return NextResponse.json({ messages: messages || [] })
}
