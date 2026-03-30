'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import {
  Shield,
  LayoutDashboard,
  AlertTriangle,
  Users,
  MessageSquare,
  BarChart3,
  CheckSquare,
  ScrollText,
  Mail,
  LogOut
} from 'lucide-react'
import { createClient } from '@/lib/supabase/client'

type NavItem = {
  label: string
  href: string
  icon: React.ElementType
}

type NavGroup = {
  label: string
  items: NavItem[]
}

export default function AdminSidebar() {
  const pathname = usePathname()
  const router = useRouter()

  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [adminName, setAdminName] = useState('Admin')
  const [adminEmail, setAdminEmail] = useState('')

  useEffect(() => {
    const resolveAdmin = async () => {
      const supabase = createClient()
      const {
        data: { user }
      } = await supabase.auth.getUser()

      if (!user) return
      setAdminEmail(user.email || '')

      const { data } = await supabase
        .from('admin_accounts')
        .select('display_name')
        .eq('supabase_uid', user.id)
        .eq('is_active', true)
        .single()

      if (data?.display_name) {
        setAdminName(data.display_name)
      }
    }

    resolveAdmin()
  }, [])

  const navGroups: NavGroup[] = useMemo(
    () => [
      {
        label: 'Overview',
        items: [{ label: 'Dashboard', href: '/admin/dashboard', icon: LayoutDashboard }]
      },
      {
        label: 'Safety',
        items: [
          { label: 'Incidents', href: '/admin/incidents', icon: AlertTriangle },
          { label: 'Users', href: '/admin/users', icon: Users },
          { label: 'Feedback', href: '/admin/feedback', icon: MessageSquare }
        ]
      },
      {
        label: 'Reports',
        items: [
          { label: 'Analytics', href: '/admin/analytics', icon: BarChart3 },
          { label: 'Saved Verifications', href: '/admin/saved', icon: CheckSquare },
          { label: 'Messages', href: '/admin/messages', icon: Mail },
          { label: 'Audit', href: '/admin/audit', icon: ScrollText }
        ]
      }
    ],
    []
  )

  const isActive = (href: string) => pathname === href || pathname.startsWith(href + '/')

  const initials = adminName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'AD'

  const handleLogout = async () => {
    setIsLoggingOut(true)
    try {
      const supabase = createClient()
      await supabase.auth.signOut()
      router.replace('/admin/login')
      router.refresh()
    } finally {
      setIsLoggingOut(false)
    }
  }

  return (
    <aside className="fixed left-0 top-0 h-full w-60 border-r border-white/5 bg-[#0c0d13]">
      <div className="relative flex h-full flex-col">
        <div className="p-5">
          <div className="flex items-center gap-2.5">
            <Shield size={22} className="text-[#10b981]" />
            <span className="text-lg font-bold text-white">SafeSphere</span>
          </div>
          <p className="mt-1 text-xs uppercase tracking-[0.2em] text-slate-500">Admin Console</p>
        </div>

        <nav className="mt-6 flex-1 overflow-y-auto px-3 pb-28">
          {navGroups.map((group) => (
            <div key={group.label} className="mb-6">
              <p className="mb-2 px-3 text-xs uppercase tracking-[0.2em] text-slate-600">{group.label}</p>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const Icon = item.icon
                  const active = isActive(item.href)
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={`mx-1 flex items-center gap-3 rounded-xl py-2.5 pr-3 text-sm font-medium transition-all duration-200 ${
                        active
                          ? 'border-l-2 border-emerald-400 bg-emerald-500/10 pl-[10px] text-emerald-400'
                          : 'px-3 text-slate-400 hover:bg-white/5 hover:text-slate-200'
                      }`}
                    >
                      <Icon size={17} className={active ? 'text-emerald-400' : 'text-slate-500'} />
                      <span>{item.label}</span>
                    </Link>
                  )
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="absolute bottom-0 left-0 right-0 border-t border-white/5 p-4">
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-500/20 text-xs font-bold text-emerald-400">
                {initials}
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-white">{adminName}</p>
                <p className="truncate text-xs text-slate-500">{adminEmail || 'admin@safesphere'}</p>
              </div>
            </div>
            <button
              onClick={handleLogout}
              disabled={isLoggingOut}
              className="rounded-lg p-1.5 text-slate-500 transition-colors hover:text-rose-400 disabled:opacity-50"
              title="Sign out"
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </div>
    </aside>
  )
}
