import { NextRequest, NextResponse } from 'next/server'
import { createClient, createServiceClient } from '@/lib/supabase/server'

type RawAuditEntry = {
  id: string
  action: string
  details: Record<string, any> | null
  created_at: string
  admin_id: string | null
  target_user_id: string | null
}

async function getAdminId(req: NextRequest): Promise<string | null> {
  const supabase = await createClient()
  const {
    data: { user }
  } = await supabase.auth.getUser()
  if (!user) return null

  const { data: admin } = await supabase
    .from('admin_accounts')
    .select('id')
    .eq('supabase_uid', user.id)
    .eq('is_active', true)
    .single()

  return admin?.id || null
}

export async function GET(req: NextRequest) {
  const adminId = await getAdminId(req)
  if (!adminId) return NextResponse.json({ error: 'Unauthorized' }, { status: 403 })

  const { searchParams } = new URL(req.url)
  const page = parseInt(searchParams.get('page') || '0', 10)
  const PAGE_SIZE = 50

  const serviceClient = createServiceClient()

  const start = page * PAGE_SIZE
  const end = (page + 1) * PAGE_SIZE - 1

  const { data: joinedData, count, error: joinError } = await serviceClient
    .from('audit_logs')
    .select(
      `
      id, action, details, created_at,
      admin_id,
      target_user_id,
      admin_accounts!audit_logs_admin_id_fkey(display_name, email),
      users!audit_logs_target_user_id_fkey(display_name, phone_hash)
    `,
      { count: 'exact' }
    )
    .order('created_at', { ascending: false })
    .range(start, end)

  if (!joinError && joinedData) {
    return NextResponse.json({
      entries: joinedData,
      count: count || 0,
      page,
      page_size: PAGE_SIZE
    })
  }

  const { data: rawEntries, count: rawCount, error: rawError } = await serviceClient
    .from('audit_logs')
    .select('id, action, details, created_at, admin_id, target_user_id', { count: 'exact' })
    .order('created_at', { ascending: false })
    .range(start, end)

  if (rawError) {
    return NextResponse.json({ error: rawError.message }, { status: 500 })
  }

  const raw = (rawEntries || []) as RawAuditEntry[]

  const adminIds = Array.from(new Set(raw.map((entry) => entry.admin_id).filter(Boolean)))
  const userIds = Array.from(new Set(raw.map((entry) => entry.target_user_id).filter(Boolean)))

  const { data: admins } = adminIds.length
    ? await serviceClient
        .from('admin_accounts')
        .select('id, display_name, email')
        .in('id', adminIds)
    : { data: [] as any[] }

  const { data: users } = userIds.length
    ? await serviceClient
        .from('users')
        .select('id, display_name, phone_hash')
        .in('id', userIds)
    : { data: [] as any[] }

  const adminMap = new Map((admins || []).map((item: any) => [item.id, item]))
  const userMap = new Map((users || []).map((item: any) => [item.id, item]))

  const normalized = raw.map((entry) => ({
    ...entry,
    admin_accounts: entry.admin_id ? adminMap.get(entry.admin_id) || null : null,
    users: entry.target_user_id ? userMap.get(entry.target_user_id) || null : null
  }))

  return NextResponse.json({
    entries: normalized,
    count: rawCount || 0,
    page,
    page_size: PAGE_SIZE
  })
}
