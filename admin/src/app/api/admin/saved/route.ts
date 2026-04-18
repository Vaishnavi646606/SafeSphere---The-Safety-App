import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { createServiceClient } from '@/lib/supabase/server'

function isAutoVerification(record: any): boolean {
  const evidenceType = typeof record?.evidence_type === 'string' ? record.evidence_type : ''
  const notes = typeof record?.notes === 'string' ? record.notes : ''
  return evidenceType === 'auto_proximity_detection' || notes.includes('[AUTO_RESCUE]')
}

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
  
  // Step 1: fetch saved_verifications
  const { data: verifications, error } = await db
    .from('saved_verifications')
    .select('id, incident_session_id, evidence_type, notes, verified_at, user_id, verified_by')
    .order('verified_at', { ascending: false })

  if (error) {
    return NextResponse.json({ error: error.message }, { status: 500 })
  }

  if (!verifications || verifications.length === 0) {
    return NextResponse.json({ verifications: [], total: 0 })
  }

  // Step 2: fetch user display names separately
  const userIds = [...new Set(verifications.map((v: any) => v.user_id).filter(Boolean))]
  const adminIds = [...new Set(verifications.map((v: any) => v.verified_by).filter(Boolean))]

  const { data: users } = await db
    .from('users')
    .select('id, display_name, phone_hash')
    .in('id', userIds)

  const { data: admins } = await db
    .from('admin_accounts')
    .select('id, display_name, email')
    .in('id', adminIds)

  // Step 3: merge manually
  const usersMap = Object.fromEntries((users || []).map((u: any) => [u.id, u]))
  const adminsMap = Object.fromEntries((admins || []).map((a: any) => [a.id, a]))

  const enriched = verifications.map((v: any) => {
    const resolvedAdmin = adminsMap[v.verified_by] || null
    const adminFallback = isAutoVerification(v)
      ? { display_name: 'Auto System', email: 'System generated' }
      : null

    return {
      ...v,
      users: usersMap[v.user_id] || null,
      admin_accounts: resolvedAdmin || adminFallback,
    }
  })

  return NextResponse.json({ verifications: enriched, total: enriched.length })
}
