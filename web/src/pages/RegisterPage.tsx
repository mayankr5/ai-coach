import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, messageOf } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Button, ErrorMessage, Field } from '../components/ui'
import { AuthLayout } from './AuthLayout'

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone

  if (user) return <Navigate to="/" replace />

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await register(String(form.get('displayName')), String(form.get('email')), String(form.get('password')))
      navigate('/', { replace: true })
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
        setFieldErrors(e.fieldErrors)
      } else {
        setError(messageOf(e))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Create your account">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <ErrorMessage>{error}</ErrorMessage>}
        <Field label="Name" name="displayName" autoComplete="name" required maxLength={100}
          error={fieldErrors.displayName} />
        <Field label="Email" name="email" type="email" autoComplete="email" required error={fieldErrors.email} />
        <Field label="Password" name="password" type="password" autoComplete="new-password" required minLength={8}
          maxLength={72} error={fieldErrors.password} />
        <p className="text-xs text-slate-500">
          Your days start and end in <span className="font-medium">{timezone}</span>.
        </p>
        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
        <p className="text-center text-sm text-slate-500">
          Already have an account? <Link to="/login" className="font-medium text-indigo-600 hover:underline">Log in</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
