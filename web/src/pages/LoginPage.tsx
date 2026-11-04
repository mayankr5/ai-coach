import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError, messageOf } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Button, ErrorMessage, Field } from '../components/ui'
import { AuthLayout } from './AuthLayout'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to={from} replace />

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSubmitting(true)
    setError(null)
    try {
      await login(String(form.get('email')), String(form.get('password')))
      navigate(from, { replace: true })
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'Wrong email or password.' : messageOf(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Log in">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <ErrorMessage>{error}</ErrorMessage>}
        <Field label="Email" name="email" type="email" autoComplete="email" required />
        <Field label="Password" name="password" type="password" autoComplete="current-password" required />
        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Logging in…' : 'Log in'}
        </Button>
        <p className="text-center text-sm text-slate-500">
          New here? <Link to="/register" className="font-medium text-indigo-600 hover:underline">Create an account</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
