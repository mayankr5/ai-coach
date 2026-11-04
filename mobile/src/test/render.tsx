import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react-native'
import type { ReactElement } from 'react'
import { tokens } from '../api/tokens'
import { AuthProvider } from '../auth/AuthProvider'

/** Renders {@code screen} with a fresh query cache and the auth provider, as the app's root layout does. */
export async function renderScreen(screen: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return await render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{screen}</AuthProvider>
    </QueryClientProvider>,
  )
}

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** Answers each request with {@code respond}, and records them. */
export function mockApi(respond: (request: Request) => Response | Promise<Response>) {
  const fetchMock = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) =>
    respond(input instanceof Request ? input : new Request(input, init)))
  globalThis.fetch = fetchMock as typeof fetch
  return { requests: () => fetchMock.mock.calls.map(c => (c[0] instanceof Request ? c[0] : new Request(c[0], c[1]))) }
}

export const pathOf = (request: Request) => new URL(request.url).pathname

export const asha = { id: 1, email: 'a@example.com', displayName: 'Asha', timezone: 'Asia/Kolkata' }

/** Starts with a saved session for Asha; other requests go to {@code respond}. */
export async function loggedIn(respond: (request: Request) => Response | Promise<Response>) {
  await tokens.save({ accessToken: 'access', refreshToken: 'refresh' })
  return mockApi(request => {
    if (pathOf(request) === '/api/v1/auth/refresh') {
      return json({ accessToken: 'access', refreshToken: 'refresh', expiresInSeconds: 900 })
    }
    if (pathOf(request) === '/api/v1/users/me') return json(asha)
    return respond(request)
  })
}
