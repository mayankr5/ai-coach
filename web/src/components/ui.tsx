import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'

const BUTTON = `inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-medium shadow-sm
  focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600
  disabled:cursor-not-allowed disabled:opacity-60`
const VARIANTS = {
  primary: 'bg-indigo-600 text-white hover:bg-indigo-500',
  secondary: 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50',
}
type Variant = keyof typeof VARIANTS

export function Button({ className = '', variant = 'primary', ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return <button className={`${BUTTON} ${VARIANTS[variant]} ${className}`} {...props} />
}

export function LinkButton({ className = '', variant = 'primary', ...props }: LinkProps & { variant?: Variant }) {
  return <Link className={`${BUTTON} ${VARIANTS[variant]} ${className}`} {...props} />
}

const CONTROL = `mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm
  focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 aria-invalid:border-red-500`

function Label({ htmlFor, label, hint }: { htmlFor?: string; label: string; hint?: string }) {
  return (
    <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
      {label}{hint && <span className="ml-1 font-normal text-slate-400">{hint}</span>}
    </label>
  )
}

function FieldError({ error }: { error?: string }) {
  return error ? <p className="mt-1 text-sm text-red-600">{error}</p> : null
}

interface FieldProps {
  label: string
  hint?: string
  error?: string
}

export function Field({ label, hint, error, id, ...props }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const inputId = id ?? props.name
  return (
    <div>
      <Label htmlFor={inputId} label={label} hint={hint} />
      <input id={inputId} aria-invalid={error ? true : undefined} className={CONTROL} {...props} />
      <FieldError error={error} />
    </div>
  )
}

export function TextArea({ label, hint, error, id, ...props }:
  FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const inputId = id ?? props.name
  return (
    <div>
      <Label htmlFor={inputId} label={label} hint={hint} />
      <textarea id={inputId} aria-invalid={error ? true : undefined} className={CONTROL} {...props} />
      <FieldError error={error} />
    </div>
  )
}

export function Select({ label, hint, error, id, children, ...props }:
  FieldProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const inputId = id ?? props.name
  return (
    <div>
      <Label htmlFor={inputId} label={label} hint={hint} />
      <select id={inputId} aria-invalid={error ? true : undefined} className={CONTROL} {...props}>{children}</select>
      <FieldError error={error} />
    </div>
  )
}

export function ErrorMessage({ children }: { children: ReactNode }) {
  return <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{children}</p>
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 p-8 text-sm text-slate-500">
      <span className="size-4 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-600" />
      {label}…
    </div>
  )
}
