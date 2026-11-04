import { createContext, useContext } from 'react'
import type { Schemas } from '../api/client'

export interface Auth {
  user: Schemas['UserResponse'] | null
  /** True until a saved session has been restored or found missing. */
  restoring: boolean
  login(email: string, password: string): Promise<void>
  register(displayName: string, email: string, password: string): Promise<void>
  logout(): Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}
