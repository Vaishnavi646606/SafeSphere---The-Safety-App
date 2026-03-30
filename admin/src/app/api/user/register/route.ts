import { NextRequest, NextResponse } from 'next/server'
import { createServiceClient } from '@/lib/supabase/server'
import { createClient } from '@/lib/supabase/server'
import crypto from 'crypto'

export async function POST(req: NextRequest) {
  let body: { name: string; phone: string }
  try { body = await req.json() }
  catch { return NextResponse.json({ error: 'Invalid body' }, { status: 400 }) }

  const { name, phone } = body
  if (!name?.trim() || !phone?.trim())
    return NextResponse.json({ error: 'name and phone are required' }, { status: 400 })

  const salt = process.env.PHONE_HASH_SALT || 'SafeSphere2024'
  const phoneHash = crypto.createHash('sha256').update(phone.trim() + salt).digest('hex')
  const phoneMasked = phone.trim().length >= 7
    ? phone.slice(0, 3) + 'XXXX' + phone.slice(-3)
    : 'XXXXXXXXXX'

  const db = createServiceClient()

  // Upsert user (handles re-registration with same phone)
  const { data: user, error } = await db
    .from('users')
    .upsert({
      phone_hash: phoneHash,
      phone_masked: phoneMasked,
      display_name: name.trim(),
      is_active: true,
      revoked_at: null,
      revocation_reason: null,
      revocation_message: null,
      re_registered_at: null,
      last_seen_at: new Date().toISOString()
    }, {
      onConflict: 'phone_hash',
      ignoreDuplicates: false
    })
    .select('id, revocation_version, registration_count')
    .single()

  if (error || !user) {
    console.error('Register error:', error)
    return NextResponse.json({ error: 'Registration failed' }, { status: 500 })
  }

  // Increment registration_count
  await db.from('users')
    .update({ registration_count: (user.registration_count || 1) })
    .eq('id', user.id)

  // Create auth user in Supabase (email = phone+@safesphere.app fake for auth)
  const fakeEmail = `${phoneHash.slice(0, 20)}@ss.internal`
  const supabaseAuth = createServiceClient()

  // Create or find Supabase auth user
  const authResult = await supabaseAuth.auth.admin.createUser({
    email: fakeEmail,
    password: phoneHash,
    email_confirm: true,
    user_metadata: { user_id: user.id, phone_masked: phoneMasked }
  })

  let supabaseUid = authResult.data?.user?.id
  if (!supabaseUid && authResult.error?.message?.includes('already')) {
    // User already exists — sign in to get token
    const { data: signIn } = await db.auth.signInWithPassword({ email: fakeEmail, password: phoneHash })
    supabaseUid = signIn.user?.id
  }

  return NextResponse.json({
    success: true,
    user_id: user.id,
    revocation_version: user.revocation_version || 0
  })
}
