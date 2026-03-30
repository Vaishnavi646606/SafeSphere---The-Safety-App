import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url)
  const userId = searchParams.get('user_id')
  if (!userId) return NextResponse.json({ error: 'user_id required' }, { status: 400 })

  const db = createServiceClient()

  // Update last_checked_at
  await db.from('revocation_tokens')
    .update({ last_checked_at: new Date().toISOString() })
    .eq('user_id', userId)

  const [{ data: token }, { data: messages }] = await Promise.all([
    db.from('revocation_tokens')
      .select('revocation_version, revoked_at, revocation_message')
      .eq('user_id', userId)
      .single(),
    db.from('admin_messages')
      .select('id, subject, body, is_critical, sent_at, message_type')
      .eq('user_id', userId)
      .is('fetched_at', null)
      .order('sent_at', { ascending: false })
      .limit(5)
  ])

  if (!token) return NextResponse.json({ error: 'User not found' }, { status: 404 })

  // Mark messages as fetched
  if (messages?.length) {
    await db.from('admin_messages')
      .update({ fetched_at: new Date().toISOString() })
      .in('id', messages.map((m: { id: string }) => m.id))
  }

  return NextResponse.json({
    user_id: userId,
    revocation_version: token.revocation_version,
    is_revoked: !!token.revoked_at,
    revoked_at: token.revoked_at,
    message: token.revocation_message,
    pending_messages: messages || []
  })
}
