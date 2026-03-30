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
    .select('id')
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
      .select('id, user_id, trigger_type, triggered_at, status, resolution_type')
      .eq('user_id', id)
      .order('triggered_at', { ascending: false })
      .limit(10)

    if (error) {
      return NextResponse.json({ error: error.message }, { status: 500 })
    }

    return NextResponse.json({ incidents: data || [] })
  } catch (error) {
    console.error('GET /api/admin/users/[id]/incidents error:', error)
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 })
  }
}
