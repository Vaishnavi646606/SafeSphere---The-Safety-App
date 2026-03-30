'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Shield, Eye, EyeOff, AlertCircle } from 'lucide-react'

export default function AdminLoginPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const supabase = createClient()
      const { data, error: authError } = await supabase.auth.signInWithPassword({ email, password })

      if (authError) throw new Error(authError.message)
      if (!data.user) throw new Error('Login failed')

      // Verify admin role
      const { data: adminData, error: adminError } = await supabase
        .from('admin_accounts')
        .select('id, display_name')
        .eq('supabase_uid', data.user.id)
        .eq('is_active', true)
        .single()

      if (adminError || !adminData) {
        await supabase.auth.signOut()
        throw new Error('Access denied. You are not an authorized admin.')
      }

      router.push('/admin/dashboard')
      router.refresh()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-linear-to-br from-gray-950 via-slate-950 to-gray-900 px-6">
      <div className="pointer-events-none absolute -left-20 top-16 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />
      <div className="pointer-events-none absolute -right-12 bottom-10 h-80 w-80 rounded-full bg-emerald-500/10 blur-3xl" />

      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-linear-to-br from-emerald-500 to-cyan-500 shadow-lg shadow-emerald-500/20">
            <Shield size={30} className="text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white">SafeSphere Admin</h1>
          <p className="mt-2 text-sm text-gray-400">Admin access only</p>
        </div>

        <div className="rounded-2xl border border-gray-800/80 bg-gray-900/80 p-6 backdrop-blur">
          <h2 className="mb-6 text-lg font-semibold text-white">Sign in to continue</h2>

          {error && (
            <div className="mb-4 flex items-center gap-2 rounded-lg border border-red-900 bg-red-950/50 px-3 py-2 text-sm text-red-300">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label htmlFor="email" className="mb-2 block text-sm font-medium text-gray-300">
                Email Address
              </label>
              <input
                id="email"
                type="email"
                placeholder="admin@safesphere.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={loading}
                className="w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-white placeholder-gray-500 outline-none transition-colors focus:border-emerald-500 disabled:opacity-70"
              />
            </div>

            <div>
              <label htmlFor="password" className="mb-2 block text-sm font-medium text-gray-300">
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  disabled={loading}
                  className="w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 pr-10 text-white placeholder-gray-500 outline-none transition-colors focus:border-emerald-500 disabled:opacity-70"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading || !email || !password}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 font-semibold text-white transition-colors hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {loading ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  Signing in...
                </>
              ) : (
                'Sign In'
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
