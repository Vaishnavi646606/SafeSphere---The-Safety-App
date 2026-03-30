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

  const db = createServiceClient()
  const { data } = await db
    .from('saved_verifications')
    .select(`
      id, incident_session_id, evidence_type, notes, verified_at,
      users!saved_verifications_user_id_fkey(display_name, phone_hash),
      admin_accounts!saved_verifications_verified_by_fkey(display_name, email)
    `)
    .order('verified_at', { ascending: false })

  return NextResponse.json({ verifications: data || [], total: data?.length || 0 })
}
