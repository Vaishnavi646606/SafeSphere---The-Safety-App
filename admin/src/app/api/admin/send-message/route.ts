import { NextRequest, NextResponse } from 'next/server'
import { createClient, createServiceClient } from '@/lib/supabase/server'

export async function POST(req: NextRequest) {
  // Verify admin auth
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const { data: admin } = await supabase
    .from('admin_accounts')
    .select('id')
    .eq('supabase_uid', user.id)
    .eq('is_active', true)
    .single()
  if (!admin) return NextResponse.json({ error: 'Forbidden' }, { status: 403 })

  // Parse request body
  const { userId, subject, body, is_critical } = await req.json()
  if (!userId || !subject || !body) {
    return NextResponse.json({ error: 'Missing required fields' }, { status: 400 })
  }

  const serviceClient = createServiceClient()

  try {
    // Insert into admin_messages
    const { data: adminMessage, error: messageError } = await serviceClient
      .from('admin_messages')
      .insert({
        subject,
        body,
        target_user_id: userId,
        is_critical: is_critical || false,
        created_at: new Date().toISOString()
      })
      .select()
      .single()

    if (messageError) throw new Error(`Failed to create message: ${messageError.message}`)

    // Insert into pending_messages
    const { data: pendingMessage, error: pendingError } = await serviceClient
      .from('pending_messages')
      .insert({
        user_id: userId,
        message_id: adminMessage.id,
        status: 'pending',
        created_at: new Date().toISOString()
      })
      .select()
      .single()

    if (pendingError) throw new Error(`Failed to queue message: ${pendingError.message}`)

    return NextResponse.json({
      success: true,
      message_id: adminMessage.id,
      pending_id: pendingMessage.id
    })
  } catch (error: any) {
    console.error('Send message error:', error)
    return NextResponse.json({ error: error.message }, { status: 500 })
  }
}
