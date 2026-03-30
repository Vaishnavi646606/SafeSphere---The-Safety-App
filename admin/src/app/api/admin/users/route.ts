import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient, createClient } from '@/lib/supabase/server'

export async function GET(req: NextRequest) {
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

  const { searchParams } = new URL(req.url)
  const filter = searchParams.get('filter') || 'all'
  const search = searchParams.get('search') || ''

  const serviceClient = createServiceClient()
  
  let query = serviceClient
    .from('users')
    .select('id, display_name, phone_hash, is_active, created_at, last_app_open, device_model, total_emergencies')
    .order('created_at', { ascending: false })
    .limit(200)

  if (filter === 'active') query = query.eq('is_active', true)
  if (filter === 'removed') query = query.eq('is_active', false)
  if (search) query = query.ilike('display_name', `%${search}%`)

  const { data, error } = await query
  
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  
  return NextResponse.json({ users: data || [], total: data?.length || 0 })
}
