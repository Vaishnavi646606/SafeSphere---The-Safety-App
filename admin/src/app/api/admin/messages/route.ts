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

  let body: {
    target?: string
    subject?: string
    body?: string
    is_critical?: boolean
    message_type?: string
  }

  try {
    body = await req.json()
  } catch {
    return NextResponse.json({ error: 'Invalid request body' }, { status: 400 })
  }

  const target = body.target || 'all'
  const subject = (body.subject || '').trim()
  const msgBody = (body.body || '').trim()
  const isCritical = Boolean(body.is_critical)
  const messageType = (body.message_type || 'policy').trim() || 'policy'

  if (!subject || !msgBody) {
    return NextResponse.json({ error: 'Subject and body are required' }, { status: 400 })
  }

  const db = createServiceClient()

  try {
    let targetUserId: string | null = null
    if (target.startsWith('user_id:')) {
      targetUserId = target.replace('user_id:', '').trim()
      if (!targetUserId) {
        return NextResponse.json({ error: 'Invalid target user id' }, { status: 400 })
      }
    }

    const { data: insertedMessage, error: messageError } = await db
      .from('admin_messages')
      .insert({
        subject,
        body: msgBody,
        target_user_id: targetUserId,
        is_critical: isCritical
      })
      .select('id')
      .single()

    if (messageError || !insertedMessage?.id) {
      return NextResponse.json({ error: messageError?.message || 'Failed to create message' }, { status: 500 })
    }

    let recipients: string[] = []
    if (targetUserId) {
      const { data: users } = await db
        .from('users')
        .select('id')
        .eq('id', targetUserId)
        .eq('is_active', true)
      recipients = (users || []).map((u: { id: string }) => u.id)
    } else {
      const { data: users } = await db
        .from('users')
        .select('id')
        .eq('is_active', true)
      recipients = (users || []).map((u: { id: string }) => u.id)
    }

    if (recipients.length > 0) {
      const pendingRows = recipients.map((userId) => ({
        user_id: userId,
        message_id: insertedMessage.id,
        status: 'pending'
      }))

      const { error: pendingError } = await db.from('pending_messages').insert(pendingRows)
      if (pendingError) {
        return NextResponse.json({ error: pendingError.message }, { status: 500 })
      }
    }

    await db.from('audit_logs').insert({
      admin_id: adminId,
      action: 'message_sent',
      target_user_id: targetUserId,
      details: {
        target,
        recipient_count: recipients.length,
        message_type: messageType,
        is_critical: isCritical
      }
    })

    return NextResponse.json({
      success: true,
      message_id: insertedMessage.id,
      queued_count: recipients.length
    })
  } catch (err) {
    console.error('POST /api/admin/messages error:', err)
    return NextResponse.json({ error: 'Failed to send message' }, { status: 500 })
  }
}

export async function GET(req: NextRequest) {
  const adminId = await getAdminId(req)
  if (!adminId) return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })

  try {
    const db = createServiceClient()

    const { data: messages, error } = await db
      .from('admin_messages')
      .select('id, subject, body, is_critical, created_at, target_user_id')
      .order('created_at', { ascending: false })
      .limit(100)

    if (error) {
      return NextResponse.json({ error: error.message }, { status: 500 })
    }

    const messageIds = (messages || []).map((m: { id: string }) => m.id)
    const countsByMessage = new Map<string, { recipient: number; delivered: number }>()

    if (messageIds.length > 0) {
      const { data: pendings } = await db
        .from('pending_messages')
        .select('message_id, status')
        .in('message_id', messageIds)

      for (const row of pendings || []) {
        const messageId = row.message_id as string
        const bucket = countsByMessage.get(messageId) || { recipient: 0, delivered: 0 }
        bucket.recipient += 1
        if (row.status === 'delivered') bucket.delivered += 1
        countsByMessage.set(messageId, bucket)
      }
    }

    const normalized = (messages || []).map((m: {
      id: string
      subject: string
      body: string
      is_critical: boolean
      created_at: string
      target_user_id: string | null
    }) => {
      const c = countsByMessage.get(m.id) || { recipient: 0, delivered: 0 }
      return {
        id: m.id,
        subject: m.subject,
        body: m.body,
        is_critical: m.is_critical,
        created_at: m.created_at,
        target_user_id: m.target_user_id,
        recipient_count: c.recipient,
        delivered_count: c.delivered
      }
    })

    return NextResponse.json({
      messages: normalized,
      count: normalized.length
    })
  } catch (err) {
    console.error('GET /api/admin/messages error:', err)
    return NextResponse.json({
      messages: [],
      error: 'Failed to fetch messages'
    }, { status: 500 })
  }
}
