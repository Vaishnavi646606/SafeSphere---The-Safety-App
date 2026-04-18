import { createServiceClient } from '@/lib/supabase/server'
import { NextRequest, NextResponse } from 'next/server'

interface HelperContactInput {
  slot: number
  phone: string
}

interface ExistingHelperLinkRow {
  id: string
  token: string
  contact_slot: number
  contact_number: string
  expires_at: string
}

/**
 * POST /api/emergency/helper-links
 * Public endpoint for Android app to generate per-contact tracking links.
 * One unique token per emergency contact (1,2,3) with 24-hour validity.
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const userId = typeof body?.user_id === 'string' ? body.user_id.trim() : ''
    const contacts = Array.isArray(body?.contacts) ? (body.contacts as HelperContactInput[]) : []

    if (!userId) {
      return NextResponse.json({ success: false, error: 'user_id is required' }, { status: 400 })
    }

    const validContacts = contacts
      .filter((item) => item && typeof item.phone === 'string' && item.phone.trim().length > 0)
      .filter((item) => Number.isInteger(item.slot) && item.slot >= 1 && item.slot <= 3)
      .slice(0, 3)

    if (validContacts.length === 0) {
      return NextResponse.json({ success: true, links: [] })
    }

    const supabase = createServiceClient()
    const now = new Date()
    const expiresAt = new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString()
    const origin = new URL(request.url).origin

    const links = []

    for (const contact of validContacts) {
      const normalizedPhone = normalizePhone(contact.phone)
      if (!normalizedPhone) continue

      const { data: existingLinks, error: fetchError } = await supabase
        .from('emergency_helper_links')
        .select('id, token, contact_slot, contact_number, expires_at')
        .eq('user_id', userId)
        .eq('contact_slot', contact.slot)
        .eq('is_active', true)
        .gt('expires_at', now.toISOString())
        .order('created_at', { ascending: false })
        .limit(10)

      if (fetchError) {
        console.error('helper-links fetch error', fetchError)
        return NextResponse.json({ success: false, error: 'Failed to read helper links' }, { status: 500 })
      }

      const existingLink = (existingLinks || []).find((item) => {
        const existingPhone = normalizePhone(item.contact_number)
        return existingPhone && existingPhone === normalizedPhone
      }) as ExistingHelperLinkRow | undefined

      if (existingLink?.token) {
        links.push({
          slot: existingLink.contact_slot,
          contact_number: existingLink.contact_number,
          token: existingLink.token,
          url: `${origin}/track/${existingLink.token}`,
          reused: true,
        })
        continue
      }

      const token = crypto.randomUUID()
      const row = {
        token,
        user_id: userId,
        contact_slot: contact.slot,
        contact_number: contact.phone.trim(),
        created_at: now.toISOString(),
        expires_at: expiresAt,
        is_active: true,
      }

      const { error: insertError } = await supabase
        .from('emergency_helper_links')
        .insert(row)

      if (insertError) {
        console.error('helper-links insert error', insertError)
        return NextResponse.json({ success: false, error: 'Failed to generate helper links' }, { status: 500 })
      }

      links.push({
        slot: row.contact_slot,
        contact_number: row.contact_number,
        token: row.token,
        url: `${origin}/track/${row.token}`,
        reused: false,
      })
    }

    return NextResponse.json({ success: true, links })
  } catch (err) {
    console.error('helper-links route error', err)
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 })
  }
}

function normalizePhone(phone: string): string {
  return phone.replace(/\D/g, '').replace(/^0+/, '').trim()
}
