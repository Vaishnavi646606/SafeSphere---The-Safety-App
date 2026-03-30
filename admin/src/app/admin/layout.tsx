'use client'

import { Bell, ChevronRight } from 'lucide-react'
import { usePathname } from 'next/navigation'
import AdminSidebar from '@/components/AdminSidebar'

const pageMeta: Record<string, { title: string; section: string }> = {
  '/admin/dashboard': { title: 'Dashboard', section: 'Overview' },
  '/admin/incidents': { title: 'Incidents', section: 'Safety' },
  '/admin/users': { title: 'Users', section: 'Safety' },
  '/admin/feedback': { title: 'Feedback', section: 'Safety' },
  '/admin/analytics': { title: 'Analytics', section: 'Reports' },
  '/admin/saved': { title: 'Saved Verifications', section: 'Reports' },
  '/admin/messages': { title: 'Messages', section: 'Reports' },
  '/admin/audit': { title: 'Audit', section: 'Reports' }
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const isLoginPage = pathname === '/admin/login'

  if (isLoginPage) {
    return <>{children}</>
  }

  const meta = pageMeta[pathname] || { title: 'Admin', section: 'Console' }

  return (
    <div className="flex min-h-screen bg-[#08090e]">
      <AdminSidebar />
      <div className="ml-60 flex min-h-screen flex-1 flex-col">
        <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-white/5 bg-[#08090e]/80 px-6 backdrop-blur-sm">
          <div className="flex items-center gap-2 text-sm">
            <span className="font-semibold text-white">{meta.title}</span>
            <span className="text-slate-600"><ChevronRight size={14} /></span>
            <span className="text-xs uppercase tracking-widest text-slate-500">{meta.section}</span>
          </div>

          <div className="flex items-center gap-3">
            <button className="text-slate-400 transition-colors hover:text-slate-200" title="Notifications">
              <Bell size={16} />
            </button>
            <div className="h-5 w-px bg-white/10" />
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-emerald-500/20 text-xs font-semibold text-emerald-400">AD</div>
            <span className="text-sm text-slate-300">Admin</span>
          </div>
        </header>

        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  )
}
