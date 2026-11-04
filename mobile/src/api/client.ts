import Constants from 'expo-constants'
import createClient from 'openapi-fetch'
import type { components, paths } from './schema'
import { tokens, type TokenPair } from './tokens'

/** Request and response shapes, generated from the backend's OpenAPI spec (`npm run api:types`). */
export type Schemas = components['schemas']

/**
 * EXPO_PUBLIC_API_URL when set. Otherwise, in development, the backend on the machine running the Expo dev
 * server: the phone reaches it by the same LAN address it loaded the app from (localhost would be the phone).
 */
export function apiBaseUrl(): string {
  const configured = process.env.EXPO_PUBLIC_API_URL
  if (configured) return configured.replace(/\/$/, '')
  const devHost = Constants.expoConfig?.hostUri?.split(':')[0]
  return `http://${devHost ?? 'localhost'}:8080`
}

const BASE_URL = apiBaseUrl()

let refreshing: Promise<boolean> | null = null
let onSessionExpired: () => void = () => {}

/** Called when a request is rejected and the session can't be renewed. */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

/** Swaps the refresh token for a new pair. Requests that fail together share one refresh. */
export function refreshTokens(): Promise<boolean> {
  refreshing ??= (async () => {
    const refreshToken = await tokens.refresh()
    if (!refreshToken) return false
    const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) {
      await tokens.clear()
      return false
    }
    await tokens.save((await response.json()) as TokenPair)
    return true
  })().finally(() => {
    refreshing = null
  })
  return refreshing
}

/** Adds the access token; on a 401 renews the session once and retries. */
export async function authFetch(request: Request): Promise<Response> {
  const retry = request.clone()
  const response = await fetch(withToken(request))
  if (response.status !== 401 || new URL(request.url).pathname.startsWith('/api/v1/auth/')) {
    return response
  }
  if (await refreshTokens()) {
    return fetch(withToken(retry))
  }
  onSessionExpired()
  return response
}

function withToken(request: Request): Request {
  const token = tokens.access()
  if (token) request.headers.set('Authorization', `Bearer ${token}`)
  return request
}

export const api = createClient<paths>({ baseUrl: BASE_URL, fetch: authFetch })

/** An error response, with the backend's ProblemDetail message and any per-field validation errors. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

interface Problem {
  title?: string
  detail?: string
  errors?: Record<string, string>
}

/** Resolves to the response data, or throws an {@link ApiError}. */
export async function call<T>(request: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await request
  if (response.ok) return data as T
  const problem = (error ?? {}) as Problem
  throw new ApiError(response.status, problem.detail ?? problem.title ?? `Request failed (${response.status})`,
    problem.errors ?? {})
}

/** True when the request never reached the server: no connection, or the server is down. */
export function isNetworkError(e: unknown): boolean {
  return e instanceof TypeError && /network request failed|failed to fetch/i.test(e.message)
}

/** A message to show the user for any thrown value; a failed fetch on a phone usually means no connection. */
export function messageOf(e: unknown): string {
  if (isNetworkError(e)) return 'Can’t reach the server. Check your connection and try again.'
  return e instanceof Error ? e.message : 'Something went wrong. Please try again.'
}
