import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, call, refreshTokens, setSessionExpiredHandler, type Schemas } from '../api/client'
import { tokens } from '../api/tokens'
import { AuthContext, type Auth } from './useAuth'

type User = Schemas['UserResponse']

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [restoring, setRestoring] = useState(() => tokens.refresh() !== null)

  const loadUser = useCallback(async () => {
    setUser(await call(api.GET('/api/v1/users/me')))
  }, [])

  const endSession = useCallback(() => {
    tokens.clear()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setSessionExpiredHandler(endSession)
    if (!tokens.refresh()) return
    refreshTokens()
      .then(ok => (ok ? loadUser() : undefined))
      .catch(() => tokens.clear())
      .finally(() => setRestoring(false))
  }, [endSession, loadUser])

  const value = useMemo<Auth>(() => ({
    user,
    restoring,
    async login(email, password) {
      tokens.save(await call(api.POST('/api/v1/auth/login', { body: { email, password } })))
      await loadUser()
    },
    async register(displayName, email, password) {
      const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
      tokens.save(await call(api.POST('/api/v1/auth/register', { body: { displayName, email, password, timezone } })))
      await loadUser()
    },
    async logout() {
      const refreshToken = tokens.refresh()
      if (refreshToken) {
        // Best effort: the session ends locally even if the server can't be reached
        await api.POST('/api/v1/auth/logout', { body: { refreshToken } }).catch(() => undefined)
      }
      endSession()
    },
  }), [user, restoring, loadUser, endSession])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
