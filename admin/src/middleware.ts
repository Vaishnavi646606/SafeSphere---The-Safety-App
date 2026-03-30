import { NextResponse, type NextRequest } from 'next/server'
import { createServerClient } from '@supabase/ssr'

export async function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname
  let supabaseResponse = NextResponse.next({ request })

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll()
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value }) =>
            request.cookies.set(name, value)
          )
          supabaseResponse = NextResponse.next({ request })
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options)
          )
        },
      },
    }
  )

  const { data: { user } } = await supabase.auth.getUser()

  const isAdminRoute = pathname.startsWith('/admin')
  const isLoginPage = pathname === '/admin/login'
  const isApiAdminRoute = pathname.startsWith('/api/admin')

  const publicApiRoutes = [
    '/api/user/register'
  ]

  const isPublicEmergencyApi = pathname.startsWith('/api/emergency/')
  const isPublicApiRoute = isPublicEmergencyApi || publicApiRoutes.includes(pathname)

  if (isPublicApiRoute) {
    return supabaseResponse
  }

  let isActiveAdmin = false
  if (user) {
    const { data: adminData } = await supabase
      .from('admin_accounts')
      .select('id')
      .eq('supabase_uid', user.id)
      .eq('is_active', true)
      .single()
    isActiveAdmin = !!adminData
  }

  if ((isAdminRoute && !isLoginPage) || isApiAdminRoute) {
    if (!user || !isActiveAdmin) {
      const loginUrl = request.nextUrl.clone()
      loginUrl.pathname = '/admin/login'
      return NextResponse.redirect(loginUrl)
    }
  }

  if (isLoginPage && user && isActiveAdmin) {
    const dashboardUrl = request.nextUrl.clone()
    dashboardUrl.pathname = '/admin/dashboard'
    return NextResponse.redirect(dashboardUrl)
  }

  // Redirect root to admin dashboard
  if (pathname === '/') {
    const dashboardUrl = request.nextUrl.clone()
    dashboardUrl.pathname = '/admin/dashboard'
    return NextResponse.redirect(dashboardUrl)
  }

  return supabaseResponse
}

export const config = {
  matcher: ['/', '/admin/:path*', '/api/:path*'],
}
