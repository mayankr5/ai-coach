import { useAuth } from '../auth/useAuth'

/** Placeholder until the goals list arrives in the next step. */
export function HomePage() {
  const { user, logout } = useAuth()
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Hi, {user?.displayName}</h1>
        <button onClick={logout} className="text-sm font-medium text-slate-600 hover:text-slate-900">Log out</button>
      </header>
      <p className="mt-6 text-slate-500">Your goals will appear here.</p>
    </main>
  )
}
