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

export async function GET(req: NextRequest) {
  const adminId = await getAdminId(req)
  if (!adminId) return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })

  try {
    const db = createServiceClient()
    
    // Attempt to query admin_messages table - gracefully handle if it doesn't exist
    const { data: messages, error } = await db
      .from('admin_messages')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(100)

    // If table doesn't exist or there's an error, return empty array gracefully
    if (error) {
      console.warn('admin_messages query error (table may not exist):', error.message)
      return NextResponse.json({ 
        messages: [],
        note: 'admin_messages table not yet available'
      })
    }

    return NextResponse.json({ 
      messages: messages || [],
      count: messages?.length || 0
    })
  } catch (err) {
    console.error('GET /api/admin/messages error:', err)
    return NextResponse.json({ 
      messages: [],
      error: 'Failed to fetch messages'
    }, { status: 500 })
  }
}
