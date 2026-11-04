import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authFetch, setSessionExpiredHandler } from './client'
import { tokens } from './tokens'

const goals = () => new Request(`${window.location.origin}/api/v1/goals`)
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('authFetch', () => {
  const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>()
  const expired = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    setSessionExpiredHandler(expired)
    tokens.save({ accessToken: 'old-access', refreshToken: 'old-refresh' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
    expired.mockReset()
    tokens.clear()
  })

  const authHeaderOf = (call: number) => (fetchMock.mock.calls[call][0] as Request).headers.get('Authorization')

  it('sends the access token', async () => {
    fetchMock.mockResolvedValueOnce(json([]))

    await authFetch(goals())

    expect(authHeaderOf(0)).toBe('Bearer old-access')
  })

  it('renews the session once on a 401 and retries with the new token', async () => {
    fetchMock
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(json({ accessToken: 'new-access', refreshToken: 'new-refresh', expiresInSeconds: 900 }))
      .mockResolvedValueOnce(json([{ id: 1 }]))

    const response = await authFetch(goals())

    expect(response.status).toBe(200)
    expect(authHeaderOf(2)).toBe('Bearer new-access')
    expect(tokens.refresh()).toBe('new-refresh')
  })

  it('shares one refresh between requests that fail together', async () => {
    let refreshCalls = 0
    fetchMock.mockImplementation(async (input, init) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.endsWith('/auth/refresh')) {
        refreshCalls++
        expect(JSON.parse(String(init?.body))).toEqual({ refreshToken: 'old-refresh' })
        return json({ accessToken: 'new-access', refreshToken: 'new-refresh', expiresInSeconds: 900 })
      }
      const auth = (input as Request).headers.get('Authorization')
      return auth === 'Bearer new-access' ? json([]) : json({}, 401)
    })

    const responses = await Promise.all([authFetch(goals()), authFetch(goals()), authFetch(goals())])

    expect(responses.map(r => r.status)).toEqual([200, 200, 200])
    expect(refreshCalls).toBe(1)
  })

  it('ends the session when it cannot be renewed', async () => {
    fetchMock.mockResolvedValueOnce(json({}, 401)).mockResolvedValueOnce(json({}, 401))

    const response = await authFetch(goals())

    expect(response.status).toBe(401)
    expect(expired).toHaveBeenCalledOnce()
    expect(tokens.refresh()).toBeNull()
  })
})
