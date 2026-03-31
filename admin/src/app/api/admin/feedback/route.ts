import { NextRequest, NextResponse } from 'next/server'
import { createClient, createServiceClient } from '@/lib/supabase/server'

export async function GET(req: NextRequest) {
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

  const filter = req.nextUrl.searchParams.get('filter') || 'all'
  const serviceClient = createServiceClient()
  let query = serviceClient
    .from('emergency_feedback')
    .select('*, users(display_name, phone_hash)')
    .order('created_at', { ascending: false })
    .limit(200)

  if (filter === 'reviewed') query = query.eq('admin_reviewed', true)
  if (filter === 'pending') query = query.eq('admin_reviewed', false)

  const { data, error } = await query
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ feedback: data || [], total: data?.length || 0 })
}

export async function PUT(req: NextRequest) {
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

  const { feedbackId } = await req.json()
  if (!feedbackId) return NextResponse.json({ error: 'Missing feedbackId' }, { status: 400 })

  const serviceClient = createServiceClient()
  const { error } = await serviceClient
    .from('emergency_feedback')
    .update({ admin_reviewed: true, admin_reviewed_at: new Date().toISOString() })
    .eq('id', feedbackId)

  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ success: true })
}
