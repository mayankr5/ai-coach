import type { ReactNode } from 'react'

export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <p className="text-center text-sm font-semibold uppercase tracking-wide text-indigo-600">AI Coach</p>
        <h1 className="mt-2 text-center text-2xl font-bold">{title}</h1>
        <p className="mt-1 text-center text-sm text-slate-500">A realistic plan for your goal that adapts every day.</p>
        <div className="mt-8 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">{children}</div>
      </div>
    </main>
  )
}
