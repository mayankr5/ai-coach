import { useQueryClient } from '@tanstack/react-query'
import { getCalendars } from 'expo-localization'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, call, refreshTokens, setSessionExpiredHandler, type Schemas } from '../api/client'
import { tokens } from '../api/tokens'
import { AuthContext, type Auth } from './useAuth'

type User = Schemas['UserResponse']

/** The phone's time zone, so "today" on the server matches the user's day. */
export function deviceTimeZone(): string {
  return getCalendars()[0]?.timeZone ?? Intl.DateTimeFormat().resolvedOptions().timeZone
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [restoring, setRestoring] = useState(true)

  const loadUser = useCallback(async () => {
    setUser(await call(api.GET('/api/v1/users/me')))
  }, [])

  const endSession = useCallback(() => {
    void tokens.clear()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  // Secure storage is async, so a saved session is always looked for before the first screen shows
  useEffect(() => {
    setSessionExpiredHandler(endSession)
    refreshTokens()
      .then(ok => (ok ? loadUser() : undefined))
      .catch(() => tokens.clear())
      .finally(() => setRestoring(false))
  }, [endSession, loadUser])

  const value = useMemo<Auth>(() => ({
    user,
    restoring,
    async login(email, password) {
      await tokens.save(await call(api.POST('/api/v1/auth/login', { body: { email, password } })))
      await loadUser()
    },
    async register(displayName, email, password) {
      await tokens.save(await call(api.POST('/api/v1/auth/register',
        { body: { displayName, email, password, timezone: deviceTimeZone() } })))
      await loadUser()
    },
    async logout() {
      const refreshToken = await tokens.refresh()
      if (refreshToken) {
        // Best effort: the session ends locally even if the server can't be reached
        await api.POST('/api/v1/auth/logout', { body: { refreshToken } }).catch(() => undefined)
      }
      endSession()
    },
  }), [user, restoring, loadUser, endSession])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
