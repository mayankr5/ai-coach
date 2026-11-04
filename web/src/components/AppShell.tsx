import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'

export function AppShell() {
  const { user, logout } = useAuth()
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-sm font-semibold uppercase tracking-wide text-indigo-600">AI Coach</Link>
          <div className="flex items-center gap-4 text-sm">
            <span className="text-slate-600">{user?.displayName}</span>
            <button onClick={logout} className="font-medium text-slate-600 hover:text-slate-900">Log out</button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
